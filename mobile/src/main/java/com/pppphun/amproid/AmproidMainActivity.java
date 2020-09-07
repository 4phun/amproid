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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.Equalizer;
import android.media.session.MediaController;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.view.Gravity.CENTER_VERTICAL;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.widget.LinearLayout.HORIZONTAL;


public class AmproidMainActivity extends AppCompatActivity
{
    private MediaBrowserCompat mediaBrowser;

    private TextView  title    = null;
    private TextView  artist   = null;
    private TextView  album    = null;
    private ImageView art      = null;
    private SeekBar   position = null;

    private boolean firstConnection  = true;
    private boolean increasePosition = false;
    private String  lastSubscription = "";

    private MediaUIValuesCache mediaUIValuesCache;

    private Timer positionTimer  = null;
    private long  eqDialogEnable = System.currentTimeMillis();


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        RecyclerView browser = findViewById(R.id.browser);
        browser.setLayoutManager(new LinearLayoutManager(this));

        MainActivityBroacastReceiver broadcastReceiver = new MainActivityBroacastReceiver();
        registerReceiver(broadcastReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter(getString(R.string.eq_values_broadcast_action)));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter(getString(R.string.playlist_values_broadcast_action)));
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter(getString(R.string.quit_broadcast_action)));

        Intent intentAmproidService = new Intent(this, AmproidService.class);

        Intent intentMain = getIntent();

        if ((intentMain.getAction() != null) && (intentMain.getExtras() != null) && (intentMain.getAction().equals("android.media.action.MEDIA_PLAY_FROM_SEARCH"))) {
            intentAmproidService.setAction("android.media.action.MEDIA_PLAY_FROM_SEARCH");
            intentAmproidService.putExtras(intentMain.getExtras());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intentAmproidService);
        }
        else {
            startService(intentAmproidService);
        }

        SharedPreferences preferences = getSharedPreferences(getString(R.string.options_preferences), Context.MODE_PRIVATE);
        if (preferences.getBoolean(getString(R.string.persistence_use_preference), true)) {
            togglePresenceForever(true);
        }

        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, AmproidService.class), new ConnectionCallback(), null);

        mediaUIValuesCache = new MediaUIValuesCache();

        title    = findViewById(R.id.title);
        artist   = findViewById(R.id.artist);
        album    = findViewById(R.id.album);
        art      = findViewById(R.id.art);
        position = findViewById(R.id.positionIndicator);

        // @formatter:off
        int metaWidth =
                mediaUIValuesCache.screenWidth - 1 -
                Math.round(
                        getResources().getDimension(R.dimen.image_size)         +
                        getResources().getDimension(R.dimen.distance_between)   +
                        getResources().getDimension(R.dimen.distance_from_edge) * 2
                );
        // @formatter:on

        title.setWidth(metaWidth);
        artist.setWidth(metaWidth);
        album.setWidth(metaWidth);

        // @formatter:off
        int buttonMinWidth =
                Math.round(
                    (
                        mediaUIValuesCache.screenWidth - 1 -
                        getResources().getDimension(R.dimen.distance_between)   * 4 -
                        getResources().getDimension(R.dimen.distance_from_edge) * 2 -
                        getResources().getDimension(R.dimen.distance_from_edge) * 3      // for space on both sides
                    )
                    / 6
                );
        // @formatter:on

        for(int buttonId : new int[] { R.id.playButton, R.id.pauseButton, R.id.prevButton, R.id.nextButton, R.id.searchButton, R.id.stopButton }) {
            ImageButton button = findViewById(buttonId);
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setMinimumWidth(buttonMinWidth);
        }

        title.setClickable(true);
        title.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // request playlist items
                Amproid.sendLocalBroadcast(R.string.playlist_request_broadcast_action);
            }
        });

        art.setClickable(true);
        art.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // image won't be as big as the screen inside a dialog of course, but the point is to ask Android to make it as big as possible
                int desiredMinSize = Math.min(mediaUIValuesCache.screenWidth, mediaUIValuesCache.displayMetrics.heightPixels);

                ImageView artView = new ImageView(AmproidMainActivity.this);

                artView.setMinimumWidth(desiredMinSize);
                artView.setMinimumHeight(desiredMinSize);
                artView.setPadding(mediaUIValuesCache.sidePadding, 0, mediaUIValuesCache.sidePadding, 0);

                artView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                artView.setImageDrawable(art.getDrawable());

                AlertDialog.Builder artDialogBuilder = new AlertDialog.Builder(AmproidMainActivity.this).setTitle(R.string.art).setView(artView).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        dialog.cancel();
                    }
                });

                artDialogBuilder.show();
            }
        });

        position.setClickable(true);
    }


    @Override
    public void onStart()
    {
        super.onStart();

        try {
            mediaBrowser.connect();
        }
        catch (Exception e) {
            // nothing to do here, just preventing error if already connecting or connected
        }

        if (positionTimer == null) {
            positionTimer = new Timer();
        }
        positionTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                boolean inc;
                synchronized (AmproidMainActivity.this) {
                    inc = increasePosition;
                }

                if (inc) {
                    int pos = position.getProgress();
                    if (pos < position.getMax()) {
                        position.setProgress(pos + 1);
                    }
                }
            }
        }, 100, 1000);
    }


    @Override
    protected void onStop()
    {
        super.onStop();

        positionTimer.cancel();
        positionTimer.purge();
        positionTimer = null;

        try {
            mediaBrowser.disconnect();
        }
        catch (Exception e) {
            // nothing to do here, just preventing error if already disconnecting or disconnected
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }


    @SuppressLint ("InflateParams")
    public void menuAbout(MenuItem menuItem)
    {
        // get and format strings

        String          title   = String.format(getString(R.string.about_title), BuildConfig.VERSION_NAME);
        SpannableString license = new SpannableString(getString(R.string.license));

        SpannableString about = new SpannableString(String.format(getString(R.string.about_text), Calendar.getInstance().get(Calendar.YEAR)));

        Linkify.addLinks(about, Linkify.WEB_URLS);
        Linkify.addLinks(license, Linkify.WEB_URLS);

        // get the about dialog layout
        View view = getLayoutInflater().inflate(R.layout.dialog_about, null);

        // set strings in dialog layout

        TextView aboutText = view.findViewById(R.id.about_text);
        aboutText.setText(about);
        aboutText.setMovementMethod(LinkMovementMethod.getInstance());

        TextView licenseText = view.findViewById(R.id.license);
        licenseText.setText(license);
        licenseText.setMovementMethod(LinkMovementMethod.getInstance());

        // build the dialog
        AlertDialog.Builder aboutDialogBuilder = new AlertDialog.Builder(AmproidMainActivity.this).setTitle(title).setView(view).setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.cancel();
            }
        });

        // show the dialog
        aboutDialogBuilder.show();
    }


    public void menuAccount(MenuItem menuItem)
    {
        final Intent intent = new Intent(this, AmproidAuthenticatorActivity.class);
        intent.putExtra(KEY_ACCOUNT_TYPE, getString(R.string.account_type));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    public void menuEqualizer(MenuItem menuItem)
    {
        // request equalizer settings from service, it will reply (see MainActivityBroacastReceiver)
        Amproid.sendLocalBroadcast(R.string.eq_request_broadcast_action);
    }


    @SuppressLint ("InflateParams")
    public void menuOptions(MenuItem menuItem)
    {
        final SharedPreferences preferences = getSharedPreferences(getString(R.string.options_preferences), Context.MODE_PRIVATE);

        AlertDialog.Builder builder = new AlertDialog.Builder(AmproidMainActivity.this);
        builder.setTitle(R.string.options);

        View view = getLayoutInflater().inflate(R.layout.dialog_options, null);
        builder.setView(view);

        final CheckBox usePresenceForever = view.findViewById(R.id.use_presence_forever);
        usePresenceForever.setChecked(preferences.getBoolean(getString(R.string.persistence_use_preference), true));

        final TextView presenceForeverInfo = view.findViewById(R.id.presence_forever_info);
        presenceForeverInfo.setTextSize(usePresenceForever.getTextSize() / getResources().getDisplayMetrics().scaledDensity - 2);

        final CheckBox hideDotPlaylists = view.findViewById(R.id.hide_dot_playlists);
        hideDotPlaylists.setChecked(preferences.getBoolean(getString(R.string.dot_playlists_hide_preference), true));

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
        {
            @SuppressLint ("ApplySharedPref")
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                SharedPreferences.Editor preferencesEditor = preferences.edit();
                preferencesEditor.putBoolean(getString(R.string.persistence_use_preference), usePresenceForever.isChecked());
                preferencesEditor.putBoolean(getString(R.string.dot_playlists_hide_preference), hideDotPlaylists.isChecked());
                preferencesEditor.commit();

                togglePresenceForever(usePresenceForever.isChecked());
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


    // simple helper to subscribe / unsubscribe to media browser info
    private void mediaBrowserSubscribe(String id)
    {
        if (!lastSubscription.isEmpty()) {
            mediaBrowser.unsubscribe(lastSubscription);
        }

        if ((id == null) || id.isEmpty()) {
            id = mediaBrowser.getRoot();
        }
        mediaBrowser.subscribe(id, new MediaSubscriptionCallback());
        lastSubscription = id;
    }


    private void togglePresenceForever(boolean on)
    {
        Intent intent = new Intent(this, PresenceForever.class);

        if (on) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            }
            else {
                startService(intent);
            }
            return;
        }

        stopService(intent);
    }


    private static class AdapterItem
    {
        private String id;
        private String title;
        private int    flags;


        int getFlags()
        {
            return flags;
        }


        void setFlags(int flags)
        {
            this.flags = flags;
        }


        public String getId()
        {
            return id;
        }


        public void setId(String id)
        {
            this.id = id;
        }


        String getTitle()
        {
            return title;
        }


        void setTitle(String title)
        {
            this.title = title;
        }
    }


    @SuppressLint ("InflateParams")
    private final class ConnectionCallback extends MediaBrowserCompat.ConnectionCallback
    {
        @Override
        public void onConnected()
        {
            MediaSessionCompat.Token token = mediaBrowser.getSessionToken();

            if (firstConnection) {
                firstConnection = false;
                mediaBrowserSubscribe(null);
            }

            MediaControllerCompat mediaController = null;
            try {
                mediaController = new MediaControllerCompat(AmproidMainActivity.this, token);
            }
            catch (RemoteException e) {
                // nothing to do here, mediaController remains null, this is handled below
            }

            if (mediaController != null) {
                mediaController.registerCallback(new ControllerCallback());

                MediaControllerCompat.setMediaController(AmproidMainActivity.this, mediaController);

                findViewById(R.id.playButton).setOnClickListener(new View.OnClickListener()
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

                findViewById(R.id.pauseButton).setOnClickListener(new View.OnClickListener()
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

                findViewById(R.id.prevButton).setOnClickListener(new View.OnClickListener()
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

                findViewById(R.id.nextButton).setOnClickListener(new View.OnClickListener()
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

                findViewById(R.id.stopButton).setOnClickListener(new View.OnClickListener()
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

                findViewById(R.id.searchButton).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        final MediaController mediaController = getMediaController();
                        if (mediaController != null) {
                            AlertDialog.Builder builder = new AlertDialog.Builder(AmproidMainActivity.this);
                            builder.setTitle(R.string.search);

                            View view = getLayoutInflater().inflate(R.layout.dialog_search, null);
                            builder.setView(view);

                            final TextInputEditText searchInput = view.findViewById(R.id.search_input);

                            builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
                                    if (query.length() < 3) {
                                        Toast.makeText(AmproidMainActivity.this, R.string.error_searchinput_insufficient, Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    mediaBrowser.search(query, new Bundle(), new MediaSearchCallback());
                                }
                            });

                            builder.setNeutralButton(R.string.play, new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
                                    if (query.length() < 3) {
                                        Toast.makeText(AmproidMainActivity.this, R.string.error_searchinput_insufficient, Toast.LENGTH_LONG).show();
                                        return;
                                    }
                                    mediaController.getTransportControls().playFromSearch(query, new Bundle());
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

                position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
                {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
                    {
                        if (fromUser) {
                            MediaController mediaController = getMediaController();
                            if (mediaController != null) {
                                mediaController.getTransportControls().seekTo(progress * 1000);
                            }
                        }
                    }


                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar)
                    {
                    }


                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar)
                    {
                    }
                });

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
        }

    }


    private final class ControllerCallback extends MediaControllerCompat.Callback
    {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state)
        {
            if ((state.getState() == PlaybackStateCompat.STATE_CONNECTING) || (state.getState() == PlaybackStateCompat.STATE_BUFFERING)) {
                findViewById(R.id.loading).setVisibility(View.VISIBLE);
                return;
            }
            else {
                findViewById(R.id.loading).setVisibility(View.GONE);
            }

            if (position != null) {
                synchronized (this) {
                    increasePosition = (state.getState() == STATE_PLAYING);
                }

                int pos = (int) (state.getPosition() / 1000);
                if ((pos >= 0) && (pos <= position.getMax())) {
                    position.setProgress(pos);
                }
            }
        }


        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata)
        {
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
            if (position != null) {
                long duration = metadata.getLong(METADATA_KEY_DURATION);
                if (duration < 0) {
                    duration = 0;
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    position.setMin(0);
                }
                position.setMax((int) (duration / 1000));
            }
        }
    }


    private final class MediaSubscriptionCallback extends MediaBrowserCompat.SubscriptionCallback
    {
        @Override
        public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children)
        {
            findViewById(R.id.loading).setVisibility(View.GONE);

            AdapterItem[] items = new AdapterItem[children.size()];
            for(int i = 0; i < children.size(); i++) {
                String title = getString(R.string.unknown);
                try {
                    title = Objects.requireNonNull(children.get(i).getDescription().getTitle()).toString();
                }
                catch (NullPointerException e) {
                    // oh well
                }

                items[i] = new AdapterItem();
                items[i].setId(children.get(i).getDescription().getMediaId());
                items[i].setTitle(title);
                items[i].setFlags(children.get(i).getFlags());
            }

            RecyclerView browser = findViewById(R.id.browser);
            browser.setAdapter(new MediaItemsAdapter(items));
        }


        @Override
        public void onError(@NonNull String parentId)
        {
            super.onError(parentId);
        }
    }


    private final class MediaSearchCallback extends MediaBrowserCompat.SearchCallback
    {
        @Override
        public void onSearchResult(@NonNull String query, Bundle extras, @NonNull List<MediaBrowserCompat.MediaItem> items)
        {
            findViewById(R.id.loading).setVisibility(View.GONE);

            AdapterItem[] adapterItems = new AdapterItem[items.size()];
            for(int i = 0; i < items.size(); i++) {
                String title = getString(R.string.unknown);
                try {
                    title = Objects.requireNonNull(items.get(i).getDescription().getTitle()).toString();
                }
                catch (NullPointerException e) {
                    // oh well
                }

                adapterItems[i] = new AdapterItem();
                adapterItems[i].setId(items.get(i).getDescription().getMediaId());
                adapterItems[i].setTitle(title);
                adapterItems[i].setFlags(items.get(i).getFlags());
            }

            RecyclerView browser = findViewById(R.id.browser);
            browser.setAdapter(new MediaItemsAdapter(adapterItems));
        }


        @Override
        public void onError(@NonNull String query, Bundle extras)
        {
            super.onError(query, extras);
        }
    }


    private class MediaUIValuesCache
    {
        final int MEDIA_ITEM_TITLE_ID = 100;
        final int MEDIA_ITEM_ENTER_ID = 101;

        final int MEDIA_ITEM_CLICK_PLAY  = 10;
        final int MEDIA_ITEM_CLICK_ENTER = 11;

        final int STRIPE_COLOR_DIFFERENCE = 0x16;

        int sidePadding;
        int topBottomPadding;
        int borderWidth;

        int backgroundColor;
        int backgroundColorStripe;
        int textColor;

        GradientDrawable borderDrawable;

        DisplayMetrics displayMetrics;
        int            screenWidth;


        MediaUIValuesCache()
        {
            sidePadding      = Math.round(getResources().getDimension(R.dimen.media_item_padding_side));
            topBottomPadding = Math.round(getResources().getDimension(R.dimen.media_item_padding_top_bottom));
            borderWidth      = Math.round(getResources().getDimension(R.dimen.media_item_border_width));

            TypedArray array = getTheme().obtainStyledAttributes(new int[] { android.R.attr.colorBackground, android.R.attr.textColorPrimary, });
            backgroundColor = array.getColor(0, 0x000000);
            textColor       = array.getColor(1, 0xFFFFFF);

            int    backgroundA          = (backgroundColor & 0xFF000000) >> 24;
            int    backgroundR          = (backgroundColor & 0x00FF0000) >> 16;
            int    backgroundG          = (backgroundColor & 0x0000FF00) >> 8;
            int    backgroundB          = (backgroundColor & 0x000000FF);
            double backgroundBrightness = Math.sqrt(0.299 * (backgroundR * backgroundR) + 0.587 * (backgroundG * backgroundG) + 0.114 * (backgroundB * backgroundB));

            int backgroundStripeR = backgroundBrightness > 127.5 ? Math.max(backgroundR - STRIPE_COLOR_DIFFERENCE, 0) : Math.min(backgroundR + STRIPE_COLOR_DIFFERENCE, 0xFF);
            int backgroundStripeG = backgroundBrightness > 127.5 ? Math.max(backgroundG - STRIPE_COLOR_DIFFERENCE, 0) : Math.min(backgroundG + STRIPE_COLOR_DIFFERENCE, 0xFF);
            int backgroundStripeB = backgroundBrightness > 127.5 ? Math.max(backgroundB - STRIPE_COLOR_DIFFERENCE, 0) : Math.min(backgroundB + STRIPE_COLOR_DIFFERENCE, 0xFF);

            backgroundColorStripe = (backgroundA << 24) | (backgroundStripeR << 16) | (backgroundStripeG << 8) | backgroundStripeB;

            borderDrawable = new GradientDrawable();
            borderDrawable.setColor(backgroundColor);
            borderDrawable.setStroke(borderWidth, backgroundColorStripe);

            displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            screenWidth = displayMetrics.widthPixels;
        }
    }


    public class MediaItemsAdapter extends RecyclerView.Adapter<MediaItemsAdapter.MediaItemViewHolder>
    {
        private AdapterItem[] items;


        MediaItemsAdapter(AdapterItem[] items)
        {
            this.items = items;
        }


        @NonNull
        @Override
        public MediaItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
        {
            LinearLayout.LayoutParams rowLayoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
            rowLayoutParams.setMargins(0, 0, 0, Math.round(getResources().getDimension(R.dimen.distance_between)));

            LinearLayout row = new LinearLayout(AmproidMainActivity.this);
            row.setOrientation(HORIZONTAL);
            row.setLayoutParams(rowLayoutParams);
            row.setPadding(mediaUIValuesCache.sidePadding, mediaUIValuesCache.topBottomPadding, mediaUIValuesCache.sidePadding, mediaUIValuesCache.topBottomPadding);

            TextView title = new TextView(parent.getContext());
            title.setId(mediaUIValuesCache.MEDIA_ITEM_TITLE_ID);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                title.setTextAppearance(R.style.TextAppearance_AppCompat_Medium);
            }
            title.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
            title.setMaxWidth((int) Math.round(0.85 * mediaUIValuesCache.screenWidth));
            row.addView(title);

            LinearLayout.LayoutParams spaceLayoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT);
            spaceLayoutParams.weight = 1;

            Space space = new Space(parent.getContext());
            space.setLayoutParams(spaceLayoutParams);
            row.addView(space);

            TextView enter = new TextView(parent.getContext());
            enter.setId(mediaUIValuesCache.MEDIA_ITEM_ENTER_ID);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                title.setTextAppearance(R.style.TextAppearance_AppCompat_Medium);
            }
            enter.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT));
            enter.setPadding(Math.round(getResources().getDimension(R.dimen.distance_between)), 0, 0, 0);
            enter.setText(">>");
            enter.setGravity(CENTER_VERTICAL);
            row.addView(enter);

            return new MediaItemViewHolder(row);
        }


        @Override
        public void onBindViewHolder(@NonNull MediaItemViewHolder holder, int position)
        {
            String text = items[position].getTitle();
            if ((text == null) || text.isEmpty()) {
                text = getString(R.string.unknown);
            }

            TextView title = holder.row.findViewById(mediaUIValuesCache.MEDIA_ITEM_TITLE_ID);
            title.setText(text);

            TextView enter = holder.row.findViewById(mediaUIValuesCache.MEDIA_ITEM_ENTER_ID);
            enter.setVisibility((items[position].getFlags() & MediaBrowserCompat.MediaItem.FLAG_BROWSABLE) > 0 ? View.VISIBLE : View.GONE);

            if (position % 2 > 0) {
                holder.row.setBackgroundColor(mediaUIValuesCache.backgroundColorStripe);
            }
            else {
                holder.row.setBackground(mediaUIValuesCache.borderDrawable);
            }

            holder.position = position;
        }


        @Override
        public int getItemCount()
        {
            return items.length;
        }


        public class MediaItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
        {
            LinearLayout row;
            int          position;


            MediaItemViewHolder(@NonNull LinearLayout itemView)
            {
                super(itemView);

                TextView title = itemView.findViewById(mediaUIValuesCache.MEDIA_ITEM_TITLE_ID);
                title.setOnClickListener(this);

                TextView enter = itemView.findViewById(mediaUIValuesCache.MEDIA_ITEM_ENTER_ID);
                enter.setOnClickListener(this);

                itemView.setOnClickListener(this);

                row = itemView;
            }


            @Override
            public void onClick(View v)
            {
                int clickedId = v.getId();
                int flags     = items[position].getFlags();

                int clickAction = mediaUIValuesCache.MEDIA_ITEM_CLICK_PLAY;
                if (((flags & MediaBrowserCompat.MediaItem.FLAG_BROWSABLE) > 0) && ((flags & MediaBrowserCompat.MediaItem.FLAG_PLAYABLE) > 0)) {
                    if (clickedId == mediaUIValuesCache.MEDIA_ITEM_ENTER_ID) {
                        clickAction = mediaUIValuesCache.MEDIA_ITEM_CLICK_ENTER;
                    }
                }
                else if ((flags & MediaBrowserCompat.MediaItem.FLAG_BROWSABLE) > 0) {
                    clickAction = mediaUIValuesCache.MEDIA_ITEM_CLICK_ENTER;
                }

                if (clickAction == mediaUIValuesCache.MEDIA_ITEM_CLICK_ENTER) {
                    mediaBrowserSubscribe(items[position].getId());
                }
                else {
                    MediaController mediaController = getMediaController();
                    if (mediaController != null) {
                        mediaController.getTransportControls().playFromMediaId(items[position].getId(), new Bundle());
                    }
                }
            }
        }
    }


    private final class MainActivityBroacastReceiver extends BroadcastReceiver
    {
        private short eqNumBands;
        private short minLevel;


        @Override
        @SuppressLint ("InflateParams")
        public void onReceive(Context context, Intent intent)
        {
            // service quits or screen is turned off
            if ((intent.getAction() != null) && (intent.getAction().equals(getString(R.string.quit_broadcast_action)) || intent.getAction().equals(Intent.ACTION_SCREEN_OFF))) {
                finishAndRemoveTask();
            }

            // service send equalizer values
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.eq_values_broadcast_action))) {
                // this is an X-files case: while on Android Auto, sometimes this broadcast comes twice, but not always
                if (System.currentTimeMillis() < eqDialogEnable) {
                    return;
                }
                eqDialogEnable = System.currentTimeMillis() + (24 * 60 * 60 * 1000);

                // get extras from intent
                final Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }

                // "decode" equalizer settings from extras
                Equalizer.Settings equalizerSettings;
                try {
                    equalizerSettings = new Equalizer.Settings(extras.getString(getString(R.string.eq_broadcast_key_settings)));
                }
                catch (Exception e) {
                    return;
                }

                // get frequency labels from extras - don't fail on error, just set all to zero
                int[] frequencies;
                try {
                    frequencies = extras.getIntArray(getString(R.string.eq_broadcast_key_freqs));
                }
                catch (Exception e) {
                    frequencies = new int[equalizerSettings.numBands];
                    for(short i = 0; i < equalizerSettings.numBands; i++) {
                        frequencies[i] = 0;
                    }
                }

                // get loudness gain
                int loudnessGain = extras.getInt(getString(R.string.eq_broadcast_key_loudness_gain), AmproidService.LOUDNESS_GAIN_DEFAULT);

                // these will be needed when constructing return value
                eqNumBands = equalizerSettings.numBands;
                minLevel   = extras.getShort(getString(R.string.eq_broadcast_key_min), (short) 0);

                // and this will be needed here soon
                short maxLevel = extras.getShort(getString(R.string.eq_broadcast_key_max), (short) 0);

                // create dialog instance
                AlertDialog.Builder builder = new AlertDialog.Builder(AmproidMainActivity.this);
                builder.setTitle(R.string.equalizer);

                // build UI from layout XML
                View view = getLayoutInflater().inflate(R.layout.dialog_eq, null);
                builder.setView(view);

                // set loudness gain
                final SeekBar loudness = view.findViewById(R.id.loudnessGain);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // older Android versions always start from zero
                    loudness.setMin(0);
                }
                loudness.setProgress(loudnessGain);

                // get reference to dialog's main layout
                final LinearLayout mainLayout = view.findViewById(R.id.eq_layout);

                // add bands to dialog's main layout
                for(short i = 0; i < equalizerSettings.numBands; i++) {
                    // parameters for band's layout
                    LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);

                    // @formatter:off
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
                    // @formatter:on

                    // band's layout
                    LinearLayout layout = new LinearLayout(AmproidMainActivity.this);
                    layout.setOrientation(HORIZONTAL);
                    layout.setLayoutParams(layoutParams);

                    // frequency
                    TextView freq = new TextView(AmproidMainActivity.this);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        freq.setTextAppearance(R.style.TextAppearance_AppCompat_Small);
                    }
                    freq.setGravity(Gravity.END);
                    freq.setLayoutParams(new LinearLayout.LayoutParams(Math.round(getResources().getDimension(R.dimen.eq_freq_size)), WRAP_CONTENT));
                    if (frequencies != null) {
                        freq.setText(frequencies[i] > 1000000 ? (float) Math.round((float) frequencies[i] / 100000) / 10 + " kHz" : Math.round((float) frequencies[i] / 1000) + " Hz");
                    }

                    // level
                    SeekBar level = new SeekBar(AmproidMainActivity.this);
                    level.setId(1000 + i);
                    level.setLayoutParams(new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, 1));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        // older Android versions always start from zero, so the values have to be transposed for the UI
                        level.setMin(0);
                    }
                    level.setMax(Math.abs(minLevel) + maxLevel);
                    level.setProgress(Math.abs(minLevel) + equalizerSettings.bandLevels[i]);

                    // add to band's layout
                    layout.addView(freq);
                    layout.addView(level);

                    // add to main layout
                    mainLayout.addView(layout);
                }

                // handle "OK" button
                builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        // paranoia check
                        if (eqNumBands < 1) {
                            return;
                        }

                        // get levels from user's input
                        short[] levels = new short[eqNumBands];
                        for(int i = 0; i < eqNumBands; i++) {
                            SeekBar level = mainLayout.findViewById(1000 + i);
                            levels[i] = (level == null ? 0 : (short) (level.getProgress() - Math.abs(minLevel)));
                        }

                        // create equalizer settings instance
                        Equalizer.Settings equalizerSettings = new Equalizer.Settings();
                        equalizerSettings.numBands   = eqNumBands;
                        equalizerSettings.bandLevels = levels;

                        // build intent by "encoding" equalizer settings and send it off to the service
                        Intent intent = new Intent();
                        intent.putExtra(getString(R.string.eq_broadcast_key_settings), equalizerSettings.toString());
                        intent.putExtra(getString(R.string.eq_broadcast_key_loudness_gain), loudness.getProgress());
                        Amproid.sendLocalBroadcast(R.string.eq_apply_broadcast_action, intent);
                    }
                });

                // handle "Cancel" button
                builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
                {
                    @Override
                    public void onClick(DialogInterface dialog, int which)
                    {
                        // nothing much to do here
                        dialog.cancel();
                    }
                });

                // things that need to be done when the dialog closes for any reason
                builder.setOnDismissListener(new DialogInterface.OnDismissListener()
                {
                    @Override
                    public void onDismiss(DialogInterface dialog)
                    {
                        eqDialogEnable = System.currentTimeMillis() + 500;
                    }
                });

                // show dialog
                try {
                    builder.show();
                }
                catch (WindowManager.BadTokenException e) {
                    // oh well
                }
            }

            // service sends playlist items
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.playlist_values_broadcast_action))) {
                // get extras from intent
                final Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }

                // get playlist from extras
                // noinspection unchecked - we know what we added in there
                Vector<Track> playlist = (Vector<Track>) extras.getSerializable(getString(R.string.playlist_broadcast_key_list));
                if (playlist == null) {
                    return;
                }

                // create list view
                ListView playlistView = new ListView(AmproidMainActivity.this);
                playlistView.setPadding(mediaUIValuesCache.sidePadding, Math.round(getResources().getDimension(R.dimen.separation)), mediaUIValuesCache.sidePadding, 0);
                playlistView.setDivider(null);

                // create adapter that holds playlist's tracks
                ArrayAdapter<String> playlistAdapter = new ArrayAdapter<>(AmproidMainActivity.this, R.layout.dialog_playlist_item);
                for(int i = 0; i < playlist.size(); i++) {
                    playlistAdapter.add(String.format(Locale.US, "%d. %s", i + 1, playlist.get(i).getTitle()));
                }
                playlistView.setAdapter(playlistAdapter);

                // clicking an item requests skip to it
                playlistView.setOnItemClickListener(new AdapterView.OnItemClickListener()
                {
                    @Override
                    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
                    {
                        final Intent playlistIntent = new Intent();
                        playlistIntent.putExtra(getString(R.string.playlist_broadcast_key_index), position);
                        Amproid.sendLocalBroadcast(R.string.playlist_apply_broadcast_action, playlistIntent);
                    }
                });

                // build and show dialog
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
                catch (WindowManager.BadTokenException e) {
                    // oh well
                }
            }
        }
    }
}
