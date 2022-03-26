/*
 * This file is part of Amproid
 *
 * Copyright (c) 2019. Peter Papp
 *
 * Please visit https://github.com/4phun/Amproid for details
 *
 * Amproid is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Amproid is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Amproid. If not, see http://www.gnu.org/licenses/
 */


package com.pppphun.amproid;


import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.media.audiofx.Equalizer;
import android.media.session.MediaController;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.LruCache;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.tabs.TabLayout;
import com.pppphun.amproid.service.AmproidService;
import com.pppphun.amproid.service.Track;
import com.pppphun.amproid.shared.Amproid;

import java.net.URL;
import java.util.Calendar;
import java.util.Locale;
import java.util.Vector;


public class AmproidMainActivity extends AppCompatActivity
{
    private MediaBrowserCompat mediaBrowser     = null;
    private String             lastSubscription = "";

    private ComponentName                       serviceComponentName;
    private AmproidService.AmproidServiceBinder amproidServiceBinder;

    private LruCache<Uri, Bitmap> iconsCache;

    private final AmproidServiceBinderCallback serviceBinderCallback = new AmproidServiceBinderCallback(this);


    private final ServiceConnection amproidServiceBindConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            amproidServiceBinder = (AmproidService.AmproidServiceBinder) service;
            amproidServiceBinder.getAmproidService().setBinderCallback(serviceBinderCallback);
        }


        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            amproidServiceBinder = null;
        }


        @Override
        public void onBindingDied(ComponentName name)
        {
            amproidServiceBinder = null;
        }


        @Override
        public void onNullBinding(ComponentName name)
        {
            amproidServiceBinder = null;
        }
    };


    public void downloadImage(URL url, Handler resultsHandler, int maxSize, long identifier)
    {
        if (amproidServiceBinder != null) {
            amproidServiceBinder.getAmproidService().downloadPicture(url, resultsHandler, maxSize, identifier);
        }
    }


    public void downloadImageCancel(long identifier)
    {
        if (amproidServiceBinder != null) {
            amproidServiceBinder.getAmproidService().downloadPictureCancel(identifier);
        }
    }


    public void menuAbout(MenuItem menuItem)
    {
        String          title   = String.format(getString(R.string.about_title), BuildConfig.VERSION_NAME);
        SpannableString license = new SpannableString(getString(R.string.license));
        SpannableString about   = new SpannableString(String.format(getString(R.string.about_text), Calendar.getInstance().get(Calendar.YEAR)));

        Linkify.addLinks(license, Linkify.WEB_URLS);
        Linkify.addLinks(about, Linkify.WEB_URLS);

        View view = getLayoutInflater().inflate(R.layout.dialog_about, null);

        TextView aboutText = view.findViewById(R.id.about_text);
        aboutText.setText(about);
        aboutText.setMovementMethod(LinkMovementMethod.getInstance());

        TextView licenseText = view.findViewById(R.id.license);
        licenseText.setText(license);
        licenseText.setMovementMethod(LinkMovementMethod.getInstance());

        AlertDialog.Builder aboutDialogBuilder = new AlertDialog.Builder(AmproidMainActivity.this)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.cancel();
                            }
                        }
                );

        aboutDialogBuilder.show();
    }


    public void menuAccount(MenuItem menuItem)
    {
        startAuthenticatorActivity();
    }


    public void menuEqualizer(MenuItem menuItem)
    {
        if (amproidServiceBinder == null) {
            return;
        }

        Bundle eqSettings = amproidServiceBinder.getAmproidService().getAudioEffectsSettings();

        Equalizer.Settings equalizerSettings;
        try {
            equalizerSettings = new Equalizer.Settings(eqSettings.getString(getString(R.string.eq_key_settings)));
        }
        catch (Exception e) {
            return;
        }

        int[] frequencies;
        try {
            frequencies = eqSettings.getIntArray(getString(R.string.eq_key_freqs));
        }
        catch (Exception e) {
            frequencies = new int[equalizerSettings.numBands];
            for (short i = 0; i < equalizerSettings.numBands; i++) {
                frequencies[i] = 0;
            }
        }
        int loudnessGain = Math.round(eqSettings.getFloat(getString(R.string.eq_key_loudness_gain), getResources().getInteger(R.integer.default_loudness_gain)));

        short eqNumBands = equalizerSettings.numBands;
        short minLevel   = eqSettings.getShort(getString(R.string.eq_key_min), (short) 0);
        short maxLevel   = eqSettings.getShort(getString(R.string.eq_key_max), (short) 0);

        AlertDialog.Builder builder = new AlertDialog.Builder(AmproidMainActivity.this);
        builder.setTitle(R.string.equalizer);

        View view = getLayoutInflater().inflate(R.layout.dialog_eq, null);
        builder.setView(view);

        final SeekBar loudness = view.findViewById(R.id.loudnessGain);
        loudness.setMin(0);
        loudness.setProgress(loudnessGain);

        final LinearLayout mainLayout = view.findViewById(R.id.eq_layout);

        for (short i = 0; i < equalizerSettings.numBands; i++) {
            TextView freq = new TextView(AmproidMainActivity.this);
            freq.setTextAppearance(R.style.TextAppearance_AppCompat_Small);
            freq.setGravity(Gravity.END);
            freq.setLayoutParams(new LinearLayout.LayoutParams(Math.round(getResources().getDimension(R.dimen.eq_freq_size)), WRAP_CONTENT));
            if (frequencies != null) {
                freq.setText(frequencies[i] > 1000000 ? (float) Math.round((float) frequencies[i] / 100000) / 10 + " kHz" : Math.round((float) frequencies[i] / 1000) + " Hz");
            }

            SeekBar level = new SeekBar(AmproidMainActivity.this);
            level.setId(1000 + i);
            level.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1));
            level.setMin(0);
            level.setMax(Math.abs(minLevel) + maxLevel);
            level.setProgress(Math.abs(minLevel) + equalizerSettings.bandLevels[i]);

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            if (i == 0) {
                layoutParams.setMargins(
                        Math.round(getResources().getDimension(R.dimen.distance_from_edge)),
                        Math.round(getResources().getDimension(R.dimen.separation)),
                        Math.round(getResources().getDimension(R.dimen.distance_from_edge)),
                        0
                );
            }
            else {
                layoutParams.setMargins(
                        Math.round(getResources().getDimension(R.dimen.distance_from_edge)),
                        Math.round(getResources().getDimension(R.dimen.distance_between)),
                        Math.round(getResources().getDimension(R.dimen.distance_from_edge)),
                        0
                );
            }

            LinearLayout layout = new LinearLayout(AmproidMainActivity.this);
            layout.setOrientation(HORIZONTAL);
            layout.setLayoutParams(layoutParams);

            layout.addView(freq);
            layout.addView(level);

            mainLayout.addView(layout);
        }

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                if (eqNumBands < 1) {
                    return;
                }

                short[] levels = new short[eqNumBands];
                for (int i = 0; i < eqNumBands; i++) {
                    SeekBar level = mainLayout.findViewById(1000 + i);
                    levels[i] = (level == null ? 0 : (short) (level.getProgress() - Math.abs(minLevel)));
                }

                Equalizer.Settings equalizerSettings = new Equalizer.Settings();
                equalizerSettings.numBands   = eqNumBands;
                equalizerSettings.bandLevels = levels;

                if (amproidServiceBinder != null) {
                    amproidServiceBinder.getAmproidService().setAudioEffectsSettings(equalizerSettings.toString(), loudness.getProgress());
                }
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.cancel();
            }
        });

        try {
            builder.show();
        }
        catch (Exception ignored) {
        }
    }


    public void menuOptions(MenuItem menuItem)
    {
        final SharedPreferences preferences = getSharedPreferences(getString(R.string.options_preferences), Context.MODE_PRIVATE);

        AlertDialog.Builder builder = new AlertDialog.Builder(AmproidMainActivity.this);
        builder.setTitle(R.string.options);

        final View view = getLayoutInflater().inflate(R.layout.dialog_options, null);
        builder.setView(view);

        final CheckBox hideDotPlaylists = view.findViewById(R.id.hide_dot_playlists);
        hideDotPlaylists.setChecked(preferences.getBoolean(getString(R.string.dot_playlists_hide_preference), true));

        final TypedArray recommendationsModes = getResources().obtainTypedArray(R.array.recommendations_modes);
        String           forYouModeSetting    = preferences.getString(getString(R.string.recommendations_mode_preference), recommendationsModes.getString(0));

        int forYouIndex = 0;
        for (int i = 0; i < recommendationsModes.length(); i++) {
            if (forYouModeSetting.compareTo(recommendationsModes.getString(i)) == 0) {
                forYouIndex = i;
                break;
            }
        }

        recommendationsModes.recycle();

        final Spinner forYouMode = view.findViewById(R.id.recommendations_mode);
        forYouMode.setSelection(forYouIndex);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
        {
            @SuppressLint("ApplySharedPref")
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                SharedPreferences.Editor preferencesEditor = preferences.edit();
                preferencesEditor.putBoolean(getString(R.string.dot_playlists_hide_preference), hideDotPlaylists.isChecked());
                preferencesEditor.putString(getString(R.string.recommendations_mode_preference), forYouMode.getSelectedItem().toString());
                preferencesEditor.commit();
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.cancel();
            }
        });

        builder.show();
    }


    public void menuQSG(MenuItem menuItem)
    {
        SpannableString qsg = new SpannableString(getString(R.string.qsg_text));

        Linkify.addLinks(qsg, Linkify.WEB_URLS);

        View view = getLayoutInflater().inflate(R.layout.dialog_qsg, null);

        TextView qsgText = view.findViewById(R.id.qsg_text);
        qsgText.setText(qsg);
        qsgText.setMovementMethod(LinkMovementMethod.getInstance());

        AlertDialog.Builder qsgDialogBuilder = new AlertDialog.Builder(AmproidMainActivity.this)
                .setTitle(getString(R.string.qsg))
                .setView(view)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                        {
                            @Override
                            public void onClick(DialogInterface dialog, int which)
                            {
                                dialog.cancel();
                            }
                        }
                );

        qsgDialogBuilder.show();
    }


    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TabLayout tabs = findViewById(R.id.tabs);
        if (tabs != null) {
            TabLayout.Tab nowPlayingTab = tabs.getTabAt(0);
            if (nowPlayingTab != null) {
                nowPlayingTab.setTag(getString(R.string.fragment_tag_now_playing));
            }
        }

        iconsCache = new LruCache<Uri, Bitmap>(Math.min((int) (Runtime.getRuntime().maxMemory() / 1024 / 10), 64 * 1024))
        {
            @Override
            protected int sizeOf(Uri key, Bitmap bitmap)
            {
                return bitmap.getByteCount() / 1024;
            }
        };

        serviceComponentName = new ComponentName("com.pppphun.amproid", "com.pppphun.amproid.service.AmproidService");
        amproidServiceBinder = null;

        mediaBrowser = new MediaBrowserCompat(this, serviceComponentName, new MediaConnectionCallback(this), null);

        if (savedInstanceState == null) {
            switchToNowPlaying();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }


    @Override
    public void onStart()
    {
        super.onStart();

        try {
            mediaBrowser.connect();
        }
        catch (Exception ignored) {
        }

        Intent serviceIntent = new Intent("amproid.intent.action.binder");
        serviceIntent.setComponent(serviceComponentName);
        bindService(serviceIntent, amproidServiceBindConnection, BIND_AUTO_CREATE);
    }


    @Override
    public void onStop()
    {
        super.onStop();

        try {
            mediaBrowser.disconnect();
        }
        catch (Exception ignored) {
        }

        if (amproidServiceBinder != null) {
            try {
                unbindService(amproidServiceBindConnection);
                amproidServiceBinder = null;
            }
            catch (Exception ignored) {
            }
        }
    }


    public void showComingUpTracks()
    {
        if (amproidServiceBinder == null) {
            return;
        }

        Vector<Track> list = amproidServiceBinder.getAmproidService().getComingUpTracks();
        if (list.size() < 1) {
            return;
        }

        ListView playlistView = new ListView(AmproidMainActivity.this);
        playlistView.setPadding(Math.round(getResources().getDimension(R.dimen.distance_from_edge)), Math.round(getResources().getDimension(R.dimen.separation)), Math.round(getResources().getDimension(R.dimen.distance_from_edge)), 0);
        playlistView.setDivider(null);

        ArrayAdapter<String> playlistAdapter = new ArrayAdapter<>(AmproidMainActivity.this, R.layout.dialog_playlist_item);
        for (int i = 0; i < list.size(); i++) {
            playlistAdapter.add(String.format(Locale.US, "%d. %s", i + 1, list.get(i).getTitle()));
        }
        playlistView.setAdapter(playlistAdapter);

        playlistView.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                if (amproidServiceBinder != null) {
                    amproidServiceBinder.getAmproidService().skipToComingUpIndex(position);
                }
            }
        });

        AlertDialog.Builder playlistDialogBuilder = new AlertDialog.Builder(AmproidMainActivity.this).setTitle(R.string.tracks).setView(playlistView).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.cancel();
            }
        });
        try {
            playlistDialogBuilder.show();
        }
        catch (WindowManager.BadTokenException ignored) {
        }
    }


    void browseFragmentRefreshPressed()
    {
        if (amproidServiceBinder != null) {
            TabLayout tabs = findViewById(R.id.tabs);
            if ((tabs != null) && (tabs.getSelectedTabPosition() != 0)) {
                String tag = null;
                try {
                    tag = (String) tabs.getTabAt(tabs.getSelectedTabPosition()).getTag();
                }
                catch (Exception ignored) {
                }
                if (tag != null) {
                    amproidServiceBinder.getAmproidService().refreshRootItem(tag);
                }
            }
        }
    }


    LruCache<Uri, Bitmap> getIconsCache()
    {
        return iconsCache;
    }


    MediaBrowserCompat getMediaBrowser()
    {
        return mediaBrowser;
    }


    void mediaBrowserSubscribe(String id)
    {
        if (!lastSubscription.isEmpty()) {
            mediaBrowser.unsubscribe(lastSubscription);
        }

        if ((id == null) || id.isEmpty()) {
            id = mediaBrowser.getRoot();
        }
        mediaBrowser.subscribe(id, new MediaSubscriptionCallback(this));
        lastSubscription = id;
    }


    void mediaSeek(long pos)
    {
        MediaController mediaController = getMediaController();
        if (mediaController != null) {
            mediaController.getTransportControls().seekTo(pos);
        }
    }


    void startAuthenticatorActivity()
    {
        Intent intent = new Intent(this, AmproidAuthenticatorActivity.class);
        intent.putExtra(KEY_ACCOUNT_TYPE, getString(R.string.account_type));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    void switchToNowPlaying()
    {
        TabLayout tabs = findViewById(R.id.tabs);
        if ((tabs != null) && (tabs.getSelectedTabPosition() != 0)) {
            tabs.selectTab(tabs.getTabAt(0));
        }

        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(false)
                .replace(R.id.fragment_container, NowPlayingFragment.class, null, getString(R.string.fragment_tag_now_playing))
                .commit();

        if (amproidServiceBinder != null) {
            amproidServiceBinder.getAmproidService().mediaSessionUpdateDurationPosition(true);
        }
    }


    final class MediaConnectionCallback extends MediaBrowserCompat.ConnectionCallback
    {
        private final AmproidMainActivity amproidMainActivity;
        private       boolean             firstConnection = true;


        public MediaConnectionCallback(AmproidMainActivity amproidMainActivity)
        {
            this.amproidMainActivity = amproidMainActivity;
        }


        @Override
        public void onConnected()
        {
            MediaSessionCompat.Token token = mediaBrowser.getSessionToken();

            MediaControllerCompat mediaController = null;
            try {
                mediaController = new MediaControllerCompat(AmproidMainActivity.this, token);
            }
            catch (Exception ignored) {
            }
            if (mediaController != null) {
                MediaControllerCompat.setMediaController(AmproidMainActivity.this, mediaController);

                mediaController.registerCallback(new MediaControllerCallback(AmproidMainActivity.this));

                TabLayout tabLayout = findViewById(R.id.tabs);
                if (tabLayout != null) {
                    tabLayout.clearOnTabSelectedListeners();
                    tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener()
                    {
                        @Override
                        public void onTabSelected(TabLayout.Tab tab)
                        {
                            String tag = null;
                            try {
                                tag = (String) tab.getTag();
                            }
                            catch (Exception ignored) {
                            }
                            if (tag != null) {
                                if (tag.equals(getString(R.string.fragment_tag_now_playing))) {
                                    switchToNowPlaying();
                                }
                                else {
                                    getSupportFragmentManager().beginTransaction()
                                            .setReorderingAllowed(false)
                                            .replace(R.id.fragment_container, MediaBrowserFragment.class, null, getString(R.string.fragment_tag_media_browser))
                                            .commit();

                                    mediaBrowserSubscribe(tag);
                                }
                            }
                        }


                        @Override
                        public void onTabUnselected(TabLayout.Tab tab)
                        {
                        }


                        @Override
                        public void onTabReselected(TabLayout.Tab tab)
                        {
                        }
                    });
                }

                ImageButton playButton   = findViewById(R.id.playButton);
                ImageButton pauseButton  = findViewById(R.id.pauseButton);
                ImageButton prevButton   = findViewById(R.id.prevButton);
                ImageButton nextButton   = findViewById(R.id.nextButton);
                ImageButton stopButton   = findViewById(R.id.stopButton);
                ImageButton searchButton = findViewById(R.id.searchButton);

                if (playButton != null) {
                    playButton.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v)
                        {
                            MediaController mediaController = getMediaController();
                            if (mediaController != null) {
                                mediaController.getTransportControls().play();
                            }
                        }
                    });
                }

                if (pauseButton != null) {
                    pauseButton.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v)
                        {
                            MediaController mediaController = getMediaController();
                            if (mediaController != null) {
                                mediaController.getTransportControls().pause();
                            }
                        }
                    });
                }

                if (prevButton != null) {
                    prevButton.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v)
                        {
                            MediaController mediaController = getMediaController();
                            if (mediaController != null) {
                                mediaController.getTransportControls().skipToPrevious();
                            }
                        }
                    });
                }

                if (nextButton != null) {
                    nextButton.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v)
                        {
                            MediaController mediaController = getMediaController();
                            if (mediaController != null) {
                                mediaController.getTransportControls().skipToNext();
                            }
                        }
                    });
                }

                if (stopButton != null) {
                    stopButton.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v)
                        {
                            MediaController mediaController = getMediaController();
                            if (mediaController != null) {
                                mediaController.getTransportControls().stop();
                            }
                        }
                    });
                }

                if (searchButton != null) {
                    searchButton.setOnClickListener(new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v)
                        {
                            final MediaController mediaController = getMediaController();
                            if (mediaController != null) {
                                Vector<String> recentSearches = Amproid.loadRecentSearches();

                                AlertDialog.Builder builder = new AlertDialog.Builder(AmproidMainActivity.this);
                                builder.setTitle(R.string.search);

                                View view = getLayoutInflater().inflate(R.layout.dialog_search, null);
                                builder.setView(view);

                                final AutoCompleteTextView searchInput = view.findViewById(R.id.search_input);
                                searchInput.setAdapter(new ArrayAdapter<>(AmproidMainActivity.this, android.R.layout.simple_dropdown_item_1line, recentSearches));

                                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
                                        if (query.length() < 3) {
                                            Toast.makeText(view.getContext(), R.string.error_search_input_insufficient, Toast.LENGTH_LONG).show();
                                            return;
                                        }

                                        recentSearches.remove(query);
                                        while (recentSearches.size() > 99) {
                                            recentSearches.remove(0);
                                        }
                                        recentSearches.add(query);
                                        Amproid.saveRecentSearches(recentSearches);

                                        TabLayout tabs = findViewById(R.id.tabs);
                                        if (tabs != null) {
                                            int searchTabIndex = -1;
                                            for (int i = 0; i < tabs.getTabCount(); i++) {
                                                String tag = null;
                                                try {
                                                    tag = (String) tabs.getTabAt(i).getTag();
                                                }
                                                catch (Exception ignored) {
                                                }
                                                if (tag == null) {
                                                    continue;
                                                }
                                                if (tag.compareTo(getString(R.string.item_search_results_id)) == 0) {
                                                    searchTabIndex = i;
                                                    break;
                                                }
                                            }
                                            if (searchTabIndex >= 0) {
                                                tabs.selectTab(tabs.getTabAt(searchTabIndex));

                                                getSupportFragmentManager().beginTransaction()
                                                        .setReorderingAllowed(false)
                                                        .replace(R.id.fragment_container, MediaBrowserFragment.class, null, getString(R.string.fragment_tag_media_browser))
                                                        .commit();
                                            }
                                        }

                                        mediaBrowser.search(query, new Bundle(), new MediaSearchCallback(AmproidMainActivity.this));
                                    }
                                });

                                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
                                {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which)
                                    {
                                        dialog.cancel();
                                    }
                                });

                                builder.show();
                            }
                        }
                    });
                }

                TextView  title  = findViewById(R.id.title);
                TextView  artist = findViewById(R.id.artist);
                TextView  album  = findViewById(R.id.album);
                ImageView art    = findViewById(R.id.art);

                MediaMetadataCompat metadata = mediaController.getMetadata();
                if (metadata != null) {
                    if (title != null) {
                        title.setText(metadata.getText(METADATA_KEY_TITLE));
                    }
                    if (artist != null) {
                        artist.setText(metadata.getText(METADATA_KEY_ARTIST));
                    }
                    if (album != null) {
                        album.setText(metadata.getText(METADATA_KEY_ALBUM));
                    }
                    if (art != null) {
                        art.setImageBitmap(metadata.getBitmap(METADATA_KEY_ART));
                    }
                }
            }

            if (firstConnection) {
                firstConnection = false;
                amproidMainActivity.mediaBrowserSubscribe(null);
            }
        }
    }
}
