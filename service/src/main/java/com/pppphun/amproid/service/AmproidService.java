/*
 * This file is part of Amproid
 *
 * Copyright (c) 2021. Peter Papp
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


package com.pppphun.amproid.service;


import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;
import static android.accounts.AccountManager.KEY_AUTHTOKEN;
import static android.accounts.AccountManager.KEY_ERROR_CODE;
import static android.accounts.AccountManager.KEY_ERROR_MESSAGE;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import com.pppphun.amproid.shared.Amproid;

import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Vector;


public class AmproidService extends MediaBrowserServiceCompat
{
    final static String PREFIX_ALBUM    = "album~";
    final static String PREFIX_ARTIST   = "artist~";
    final static String PREFIX_PLAYLIST = "playlist~";
    final static String PREFIX_SONG     = "song~";
    final static String PREFIX_RADIO    = "radio~";

    final static int PLAY_MODE_UNKNOWN  = 0;
    final static int PLAY_MODE_RANDOM   = 1;
    final static int PLAY_MODE_PLAYLIST = 2;
    final static int PLAY_MODE_BROWSE   = 3;
    final static int PLAY_MODE_ALBUM    = 4;
    final static int PLAY_MODE_ARTIST   = 5;

    // TODO: support radio stations
    // TODO: this must depend on Ampache version
    private final static boolean RADIO_ENABLED = false;

    private final static int    NOTIFICATION_ID         = 1001;
    private final static String NOTIFICATION_CHANNEL_ID = "AmproidServiceNotificationChannel";

    private final static int MAX_AUTH_ATTEMPTS = 50;

    Equalizer.Settings equalizerSettings   = null;
    int                loudnessGainSetting = 0;

    private final PlaybackStateCompat.Builder stateBuilder         = new PlaybackStateCompat.Builder();
    private final MediaMetadataCompat.Builder metadataBuilder      = new MediaMetadataCompat.Builder();
    private final MediaSessionCallback        mediaSessionCallback = new MediaSessionCallback();
    private       MediaSessionCompat          mediaSession         = null;

    private NotificationCompat.Builder notificationBuilder  = null;
    private NotificationManagerCompat  notificationManager  = null;
    private boolean                    imageNeverDownloaded = true;

    private AudioFocusRequest                       audioFocusRequest        = null;
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = null;

    private AmproidMediaPlayer mediaPlayer = null;

    private final AmproidBroadcastReceiver      amproidBroadcastReceiver     = new AmproidBroadcastReceiver(mediaSessionCallback);
    private final IBinder                       amproidServiceBinder         = new AmproidServiceBinder();
    private       IAmproidServiceBinderCallback amproidServiceBinderCallback = null;

    private int     authAttempts    = 0;
    private Account selectedAccount = null;
    private String  authToken       = null;

    private       int           playMode         = PLAY_MODE_UNKNOWN;
    private       String        playlistId       = null;
    private       String        artistId         = null;
    private       String        albumId          = null;
    private       String        browseId         = null;
    private final Vector<Track> comingUpTracks   = new Vector<>();
    private       int           comingUpIndex    = 0;
    private       boolean       pausedByUser     = false;
    private       boolean       haveAudioFocus   = false;
    private       Bundle        searchParameters = null;

    private final Vector<ThreadCancellable> startedThreads = new Vector<>();

    private Vector<HashMap<String, String>> radios               = new Vector<>();
    private PlaylistsCache                  playlistsCache       = null;
    private RecommendationsCache            recommendationsCache = null;
    private SearchCache                     searchCache          = null;

    private final Runnable getAuthToken = new Runnable()
    {
        @Override
        public void run()
        {
            stateBuilder.setState(PlaybackStateCompat.STATE_CONNECTING, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());
            fakeTrackMessage(R.string.login_handshake, selectedAccount.name);
            AccountManager.get(AmproidService.this).getAuthToken(selectedAccount, "", null, true, new AmproidAccountManagerCallback(), null);
        }
    };

    private final Runnable mediaSessionUpdateDurationPositionIfPlaying = new Runnable()
    {
        @Override
        public void run()
        {
            boolean isPlaying;
            try {
                isPlaying = (mediaPlayer != null) && mediaPlayer.isPlaying();
            }
            catch (Exception e) {
                return;
            }

            if (isPlaying) {
                try {
                    metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, mediaPlayer.getDuration());
                    mediaSession.setMetadata(metadataBuilder.build());

                    stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1);
                    mediaSession.setPlaybackState(stateBuilder.build());
                }
                catch (Exception ignored) {
                }
            }
        }
    };

    private final Handler mainHandler = new Handler(Looper.getMainLooper())
    {
        @Override
        public void handleMessage(@NonNull Message msg)
        {
            Bundle arguments = null;
            try {
                if (msg.obj instanceof Bundle) {
                    arguments = (Bundle) msg.obj;
                }
            }
            catch (Exception ignored) {
            }

            super.handleMessage(msg);

            if (arguments != null) {
                processMsg(arguments);
            }
        }
    };


    public void accountSelected()
    {
        // authenticator activity already saved user's account selection to preferences
        startAuth();
    }


    public void downloadPicture(URL url, Handler handler, int maxSize, long identifier)
    {
        ImageDownloaderThread imageDownloader = new ImageDownloaderThread(url, handler, maxSize, identifier);
        startedThreads.add(imageDownloader);
        imageDownloader.start();
    }


    public void downloadPictureCancel(long identifier)
    {
        for (ThreadCancellable task : startedThreads) {
            if (task instanceof ImageDownloaderThread) {
                if (((ImageDownloaderThread) task).getIdentifier() == identifier) {
                    task.cancel();
                }
            }
        }
    }


    public Bundle getAudioEffectsSettings()
    {
        Bundle returnValue = new Bundle();

        if (mediaPlayer == null) {
            return returnValue;
        }
        if ((mediaPlayer.equalizer == null) || (mediaPlayer.loudnessEnhancer == null)) {
            return returnValue;
        }

        Equalizer.Settings equalizerSettings = mediaPlayer.equalizer.getProperties();

        int[] frequencies = new int[equalizerSettings.numBands];
        for (short i = 0; i < equalizerSettings.numBands; i++) {
            frequencies[i] = mediaPlayer.equalizer.getCenterFreq(i);
        }

        returnValue.putString(getString(R.string.eq_key_settings), equalizerSettings.toString());
        returnValue.putShort(getString(R.string.eq_key_min), mediaPlayer.equalizer.getBandLevelRange()[0]);
        returnValue.putShort(getString(R.string.eq_key_max), mediaPlayer.equalizer.getBandLevelRange()[1]);
        returnValue.putIntArray(getString(R.string.eq_key_freqs), frequencies);
        returnValue.putFloat(getString(R.string.eq_key_loudness_gain), Math.round(mediaPlayer.loudnessEnhancer.getTargetGain()));

        return returnValue;
    }


    public Vector<Track> getComingUpTracks()
    {
        int[] modes = {PLAY_MODE_ARTIST, PLAY_MODE_ALBUM, PLAY_MODE_PLAYLIST};

        boolean modeAllowed = false;
        for (int m : modes) {
            if (playMode == m) {
                modeAllowed = true;
                break;
            }
        }
        if (!modeAllowed || (comingUpTracks.size() < 1)) {
            return new Vector<>();
        }

        return comingUpTracks;
    }


    public void mediaSessionUpdateDurationPosition()
    {
        mainHandler.post(mediaSessionUpdateDurationPositionIfPlaying);
    }


    @Override
    public void onCreate()
    {
        super.onCreate();

        loudnessGainSetting = getResources().getInteger(R.integer.default_loudness_gain);

        stateBuilder.setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH);
        stateBuilder.setState(PlaybackStateCompat.STATE_STOPPED, 0, 0);

        mediaSession = new MediaSessionCompat(this, getString(R.string.service_name));
        mediaSession.setCallback(mediaSessionCallback);
        mediaSession.setPlaybackState(stateBuilder.build());

        setSessionToken(mediaSession.getSessionToken());

        mediaSession.setActive(true);

        registerReceiver(amproidBroadcastReceiver, new IntentFilter("android.media.AUDIO_BECOMING_NOISY"));

        Uri notifyUri = Uri.parse(String.format("%s://%s/%s", ContentResolver.SCHEME_ANDROID_RESOURCE, getApplicationContext().getPackageName(), R.raw.notify));

        notificationManager = NotificationManagerCompat.from(AmproidService.this);

        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        notificationChannel.setImportance(NotificationManager.IMPORTANCE_HIGH);
        notificationChannel.setSound(notifyUri, new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
        notificationManager.createNotificationChannel(notificationChannel);

        notificationBuilder = new NotificationCompat.Builder(AmproidService.this, NOTIFICATION_CHANNEL_ID)
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, getString(R.string.play), MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, PlaybackStateCompat.ACTION_PLAY)))
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, getString(R.string.pause), MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, PlaybackStateCompat.ACTION_PAUSE)))
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.next), MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))
                .addAction(new NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, PlaybackStateCompat.ACTION_STOP)))
                .setContentText(getString(R.string.initializing))
                .setContentTitle(getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setSound(notifyUri)
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowCancelButton(true).setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, PlaybackStateCompat.ACTION_STOP)).setShowActionsInCompactView(0, 1));

        notificationBuilder.setChannelId(NOTIFICATION_CHANNEL_ID);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());

        SharedPreferences preferences             = getSharedPreferences(getString(R.string.eq_preferences), Context.MODE_PRIVATE);
        String            equalizerSettingsString = preferences.getString(getString(R.string.eq_settings_preference), null);
        loudnessGainSetting = preferences.getInt(getString(R.string.eq_loudness_gain_preference), getResources().getInteger(R.integer.default_loudness_gain));

        if (equalizerSettingsString != null) try {
            equalizerSettings = new Equalizer.Settings(equalizerSettingsString);
        }
        catch (Exception ignored) {
        }

        audioFocusChangeListener = focusChange ->
        {
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                haveAudioFocus = true;
                if ((mediaPlayer != null) && !pausedByUser) {
                    startService(new Intent(AmproidService.this, AmproidService.class));
                    mediaPlayer.start();
                }
                return;
            }

            haveAudioFocus = false;
            if (mediaPlayer != null) {
                mediaPlayer.pause();
            }
        };

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
                    .build();

            int focus = audioManager.requestAudioFocus(audioFocusRequest);
            haveAudioFocus = (focus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
        }

        mainHandler.post(AmproidService.this::startAuth);
    }


    @Override
    public IBinder onBind(Intent intent)
    {
        if (intent.getAction().compareTo("amproid.intent.action.binder") == 0) {
            return amproidServiceBinder;
        }

        mainHandler.postDelayed(mediaSessionUpdateDurationPositionIfPlaying, 250);
        return super.onBind(intent);
    }


    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, Bundle rootHints)
    {
        return new BrowserRoot(getString(R.string.item_root_id), null);
    }


    @Override
    public void onLoadChildren(@NonNull final String parentMediaId, @NonNull final Result<List<MediaBrowserCompat.MediaItem>> result)
    {
        if (parentMediaId.compareTo(getString(R.string.item_root_id)) == 0) {
            ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(getString(R.string.item_recommendations_id)).setTitle(getString(R.string.item_recommendations_desc)).build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(getString(R.string.item_search_results_id)).setTitle(getString(R.string.item_search_results_desc)).build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(getString(R.string.item_playlists_id)).setTitle(getString(R.string.item_playlists_desc)).build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(getString(R.string.item_recent_id)).setTitle(getString(R.string.item_recent_desc)).build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

            result.sendResult(results);
            return;
        }

        String serverUrl = Amproid.getServerUrl(selectedAccount);

        stateBuilder.setState(PlaybackStateCompat.STATE_CONNECTING, 0, 0);
        mediaSession.setPlaybackState(stateBuilder.build());

        if (parentMediaId.compareTo(getString(R.string.item_playlists_id)) == 0) {
            result.detach();
            if (playlistsCache != null) {
                playlistsCache.getPlaylists(result);
            }
            return;
        }

        if (parentMediaId.compareTo(getString(R.string.item_recommendations_id)) == 0) {
            result.detach();
            if (recommendationsCache != null) {
                recommendationsCache.getRecommendations(result);
            }
            return;
        }

        if (parentMediaId.compareTo(getString(R.string.item_search_results_id)) == 0) {
            result.detach();
            if (searchCache != null) {
                searchCache.getSearchResults(result);
            }
            return;
        }

        if (parentMediaId.compareTo(getString(R.string.item_recent_id)) == 0) {
            result.detach();

            GetRecentAlbumsThread getRecentAlbums = new GetRecentAlbumsThread(authToken, serverUrl, mainHandler, result);
            startedThreads.add(getRecentAlbums);
            getRecentAlbums.start();
        }
    }


    @Override
    public void onSearch(@NonNull String query, Bundle extras, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result)
    {
        if (searchCache == null) {
            return;
        }

        stateBuilder.setState(PlaybackStateCompat.STATE_CONNECTING, 0, 0);
        mediaSession.setPlaybackState(stateBuilder.build());

        fakeTrackMessage(R.string.getting_search_results, "");

        result.detach();

        if (!extras.containsKey("query")) {
            extras.putString("query", query);
        }

        searchCache.refreshSearch(extras);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        MediaButtonReceiver.handleIntent(mediaSession, intent);

        if ((intent != null) && (intent.getAction() != null) && (intent.getExtras() != null) && (intent.getAction().equals("android.media.action.MEDIA_PLAY_FROM_SEARCH"))) {
            searchParameters = intent.getExtras();

            // check if app not just started (already playing), otherwise onPlayFromSearch will be called after login to Ampache server
            if (playMode != PLAY_MODE_UNKNOWN) {
                mediaSessionCallback.onPlayFromSearch(Amproid.bundleGetString(intent.getExtras(), "query"), intent.getExtras());
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public void onDestroy()
    {
        try {
            mainHandler.removeCallbacks(getAuthToken);
            mainHandler.removeCallbacks(mediaSessionUpdateDurationPositionIfPlaying);
        }
        catch (Exception ignored) {
        }

        cancelAllThreads();

        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(amproidBroadcastReceiver);
            unregisterReceiver(amproidBroadcastReceiver);
        }
        catch (Exception ignored) {
        }

        amproidServiceBinderCallback = null;

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        }

        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }

        if (notificationManager != null) {
            notificationManager.cancelAll();
        }

        super.onDestroy();
    }


    @Override
    public void onRebind(Intent intent)
    {
        amproidServiceBinderCallback = null;

        mainHandler.postDelayed(mediaSessionUpdateDurationPositionIfPlaying, 250);
        super.onRebind(intent);
    }


    public void refreshRootItem(String rootItem)
    {
        if (rootItem.compareTo(getString(R.string.item_recommendations_id)) == 0) {
            if (recommendationsCache != null) {
                recommendationsCache.refreshRecommendations();
                return;
            }
        }
        else if (rootItem.compareTo(getString(R.string.item_search_results_id)) == 0) {
            if (searchCache != null) {
                searchCache.refreshSearch(searchParameters);
                return;
            }
        }
        else if (rootItem.compareTo(getString(R.string.item_playlists_id)) == 0) {
            if (playlistsCache != null) {
                playlistsCache.refreshPlaylists();
                return;
            }
        }
        amproidServiceBinderCallback.refreshRootItemCompleted(rootItem);
    }


    @SuppressLint("ApplySharedPref")
    public void setAudioEffectsSettings(String equalizerSettingsString, int loudnessGainSetting)
    {
        this.loudnessGainSetting = loudnessGainSetting;

        try {
            equalizerSettings = new Equalizer.Settings(equalizerSettingsString);

            SharedPreferences        preferences       = getSharedPreferences(getString(R.string.eq_preferences), Context.MODE_PRIVATE);
            SharedPreferences.Editor preferencesEditor = preferences.edit();
            preferencesEditor.putString(getString(R.string.eq_settings_preference), equalizerSettings.toString());
            preferencesEditor.putInt(getString(R.string.eq_loudness_gain_preference), loudnessGainSetting);
            preferencesEditor.commit();
        }
        catch (Exception ignored) {
            // equalizerSettings remains unchanged, null is handled in AmproidMediaPlayer's setEffects
        }

        mediaPlayer.setEffects();
    }


    public void setBinderCallback(IAmproidServiceBinderCallback amproidServiceBinderCallback)
    {
        this.amproidServiceBinderCallback = amproidServiceBinderCallback;
    }


    public void skipToComingUpIndex(int index)
    {
        if ((playMode != PLAY_MODE_ARTIST) && (playMode != PLAY_MODE_ALBUM) && (playMode != PLAY_MODE_PLAYLIST)) {
            return;
        }
        if ((index < 0) || (index >= comingUpTracks.size())) {
            return;
        }
        comingUpIndex = index;
        startTrack(comingUpTracks.get(comingUpIndex));
    }


    void asyncHousekeeping()
    {
        Vector<ThreadCancellable> toBeRemoved = new Vector<>();

        for (ThreadCancellable task : startedThreads) {
            if (task.getState() == Thread.State.TERMINATED) {
                toBeRemoved.add(task);
            }
        }

        for (ThreadCancellable task : toBeRemoved) {
            startedThreads.remove(task);
        }
    }


    void asyncProcessResultsGetTracks(Bundle data)
    {
        if (data == null) {
            return;
        }

        asyncHousekeeping();

        String errorMessage = data.getString("errorMessage", "");
        if (!errorMessage.isEmpty()) {
            fakeTrackMessage(R.string.error_error, data.getString("errorMessage"));
            return;
        }

        @SuppressWarnings("unchecked")
        Vector<Track> tracks = (Vector<Track>) data.getSerializable("tracks");
        if ((tracks == null) || tracks.isEmpty()) {
            fakeTrackMessage(R.string.error_error, getString(R.string.error_tracks_empty));
            return;
        }


        int trackIndex = 0;
        if ((playMode == PLAY_MODE_PLAYLIST) || (playMode == PLAY_MODE_ARTIST) || (playMode == PLAY_MODE_ALBUM)) {
            comingUpTracks.clear();
            comingUpTracks.addAll(tracks);

            if ((comingUpIndex >= 0) && (comingUpIndex < tracks.size())) {
                trackIndex = comingUpIndex;
            }
            else {
                // this could happen if playlist is modified on Ampache server between Amproid restarts
                comingUpIndex = trackIndex;
            }
        }

        startTrack(tracks.get(trackIndex));
    }


    void asyncProcessResultsValidateToken(Bundle data)
    {
        asyncHousekeeping();

        if (selectedAccount == null) {
            fakeTrackMessage(R.string.error_error, getString(R.string.error_no_account_selection));
            return;
        }

        boolean isTokenValid = data.getBoolean("isTokenValid", false);
        String  errorMessage = data.getString("errorMessage", "");

        // this can happen for good reason, that is, cached token expired
        if (!isTokenValid) {
            getNewAuthToken(errorMessage);
            return;
        }
        authAttempts = 0;

        playlistsCache       = new PlaylistsCache(authToken, Amproid.getServerUrl(selectedAccount), mainHandler);
        recommendationsCache = new RecommendationsCache(authToken, Amproid.getServerUrl(selectedAccount), mainHandler);
        searchCache          = new SearchCache(authToken, Amproid.getServerUrl(selectedAccount), mainHandler);

        if (searchParameters != null) {
            // continued from onStartCommand
            mediaSessionCallback.onPlayFromSearch(Amproid.bundleGetString(searchParameters, "query"), searchParameters);
            return;
        }

        loadPlayMode();

        if (playMode == PLAY_MODE_PLAYLIST) {
            fakeTrackMessage(R.string.getting_playlist_tracks, "");
        }
        else if (playMode == PLAY_MODE_ARTIST) {
            fakeTrackMessage(R.string.getting_artist_tracks, "");
        }
        else if (playMode == PLAY_MODE_ALBUM) {
            fakeTrackMessage(R.string.getting_album_tracks, "");
        }

        String ampacheId = "";
        switch (playMode) {
            case PLAY_MODE_PLAYLIST:
                ampacheId = playlistId;

                // smart playlists are generated dynamically, so it's meaningless to start one from where it left off
                if (ampacheId.startsWith("smart_")) {
                    comingUpIndex = 0;
                }

                break;
            case PLAY_MODE_ARTIST:
                ampacheId = artistId;
                comingUpIndex = 0;
                break;
            case PLAY_MODE_ALBUM:
                ampacheId = albumId;
                break;
            case PLAY_MODE_BROWSE:
                ampacheId = browseId;
        }

        stateBuilder.setState(PlaybackStateCompat.STATE_CONNECTING, 0, 0);
        mediaSession.setPlaybackState(stateBuilder.build());

        // this will start play when the async operation is completed
        GetTracksThread getTracks = new GetTracksThread(authToken, Amproid.getServerUrl(selectedAccount), playMode, ampacheId, mainHandler);
        startedThreads.add(getTracks);
        getTracks.start();
    }


    void cancelAllThreads()
    {
        while (startedThreads.size() > 0) {
            cancelThread(startedThreads.get(0));
        }
    }


    void cancelThread(ThreadCancellable task)
    {
        task.cancel();
        startedThreads.remove(task);
    }


    void downloadPicture(URL url)
    {
        ImageDownloaderThread imageDownloader = new ImageDownloaderThread(url, mainHandler);
        startedThreads.add(imageDownloader);
        imageDownloader.start();
    }


    void fakeTrackMessage(String message, String subMessage)
    {
        if ((notificationBuilder != null) && (notificationManager != null)) {
            notificationBuilder.setContentTitle(message);
            notificationBuilder.setContentText("");
            if (imageNeverDownloaded) {
                notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
            }
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, message);
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, subMessage);
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "");
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1);
        if (imageNeverDownloaded) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
        }
        mediaSession.setMetadata(metadataBuilder.build());
    }


    void fakeTrackMessage(int stringResource, String subMessage)
    {
        fakeTrackMessage(getString(stringResource), subMessage);
    }


    void genuineTrackMessage(Track track)
    {
        if (track == null) {
            return;
        }

        String title = track.getTitle();
        if ((playMode == PLAY_MODE_PLAYLIST) || (playMode == PLAY_MODE_ARTIST) || (playMode == PLAY_MODE_ALBUM)) {
            title = String.format(Locale.US, "%d/%d: %s", comingUpIndex + 1, comingUpTracks.size(), title);
        }

        if ((notificationBuilder != null) && (notificationManager != null)) {
            notificationBuilder.setContentTitle(title);
            notificationBuilder.setContentText(track.getArtist());
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title);
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.getAlbum());
        metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.getArtist());
        metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, -1);
        mediaSession.setMetadata(metadataBuilder.build());
    }


    void getNewAuthToken(@NotNull String errorMessage)
    {
        getNewAuthToken(errorMessage, true);
    }


    void getNewAuthToken(@NotNull String errorMessage, boolean retry)
    {
        if (authAttempts >= MAX_AUTH_ATTEMPTS) {
            fakeTrackMessage(R.string.error_login_failed, errorMessage.isEmpty() ? selectedAccount.name : errorMessage);
            return;
        }

        if ((authAttempts >= 1) || !errorMessage.isEmpty() || !retry) {
            fakeTrackMessage(getString(retry ? R.string.login_delay : R.string.error_login_failed), errorMessage.isEmpty() ? selectedAccount.name : errorMessage);
        }

        AccountManager.get(this).invalidateAuthToken(selectedAccount.type, authToken);
        authToken = "";

        if (!retry) {
            return;
        }

        mainHandler.postDelayed(getAuthToken, retryDelay(authAttempts));

        authAttempts++;
    }


    @SuppressLint("ApplySharedPref")
    void savePlayMode()
    {
        SharedPreferences        preferences       = getApplicationContext().getSharedPreferences(getApplicationContext().getString(R.string.play_mode_preferences), Context.MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = preferences.edit();

        preferencesEditor.putInt(getApplicationContext().getString(R.string.play_mode_play_mode_preference), playMode);
        preferencesEditor.putString(getApplicationContext().getString(R.string.play_mode_playlist_id_preference), playlistId);
        preferencesEditor.putInt(getApplicationContext().getString(R.string.play_mode_playlist_index_preference), comingUpIndex);
        preferencesEditor.putString(getApplicationContext().getString(R.string.play_mode_browse_id_preference), browseId);
        preferencesEditor.putString(getApplicationContext().getString(R.string.play_mode_artist_id_preference), artistId);
        preferencesEditor.putString(getApplicationContext().getString(R.string.play_mode_album_id_preference), albumId);

        preferencesEditor.commit();
    }


    void skipToNext()
    {
        mediaSessionCallback.onSkipToNext();
    }


    void startTrack(Track track)
    {
        startService(new Intent(this, AmproidService.class));

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        mediaPlayer = new AmproidMediaPlayer(this, track, !pausedByUser);
    }


    void stateUpdate(int state, long position)
    {
        stateBuilder.setState(state, position, state == PlaybackStateCompat.STATE_PLAYING ? 1 : 0);
        mediaSession.setPlaybackState(stateBuilder.build());
    }


    private void loadPlayMode()
    {
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(getApplicationContext().getString(R.string.play_mode_preferences), Context.MODE_PRIVATE);

        playMode      = preferences.getInt(getApplicationContext().getString(R.string.play_mode_play_mode_preference), PLAY_MODE_RANDOM);
        playlistId    = preferences.getString(getApplicationContext().getString(R.string.play_mode_playlist_id_preference), "");
        comingUpIndex = preferences.getInt(getApplicationContext().getString(R.string.play_mode_playlist_index_preference), 0);
        browseId      = preferences.getString(getApplicationContext().getString(R.string.play_mode_browse_id_preference), "");
        artistId      = preferences.getString(getApplicationContext().getString(R.string.play_mode_artist_id_preference), "");
        albumId       = preferences.getString(getApplicationContext().getString(R.string.play_mode_album_id_preference), "");

        if ((playMode == PLAY_MODE_BROWSE) && browseId.isEmpty()) {
            playMode = PLAY_MODE_RANDOM;
        }
        else if ((playMode == PLAY_MODE_PLAYLIST) && playlistId.isEmpty()) {
            playMode = PLAY_MODE_RANDOM;
        }
        else if ((playMode == PLAY_MODE_ARTIST) && artistId.isEmpty()) {
            playMode = PLAY_MODE_RANDOM;
        }
        else if ((playMode == PLAY_MODE_ALBUM) && albumId.isEmpty()) {
            playMode = PLAY_MODE_RANDOM;
        }

        if ((playMode < PLAY_MODE_RANDOM) || (playMode > PLAY_MODE_ARTIST)) {
            playMode = PLAY_MODE_RANDOM;
        }
    }


    private void processMsg(Bundle arguments)
    {
        if (!arguments.containsKey(getString(R.string.msg_action))) {
            return;
        }

        String action = arguments.getString(getString(R.string.msg_action));

        if (action.equals(getString(R.string.msg_action_async_finished))) {
            mainHandler.post(mediaSessionUpdateDurationPositionIfPlaying);

            if (!arguments.containsKey(getString(R.string.msg_async_finished_type))) {
                return;
            }

            int asyncType = arguments.getInt(getString(R.string.msg_async_finished_type));

            if (asyncType == getResources().getInteger(R.integer.async_validate_token)) {
                asyncProcessResultsValidateToken(arguments);
            }
            else if (asyncType == getResources().getInteger(R.integer.async_get_tracks)) {
                asyncProcessResultsGetTracks(arguments);
            }
            else if (asyncType == getResources().getInteger(R.integer.recommendations_now_valid)) {
                if (amproidServiceBinderCallback != null) {
                    amproidServiceBinderCallback.refreshRootItemCompleted(getString(R.string.item_recommendations_id));
                }
            }
            else if (asyncType == getResources().getInteger(R.integer.search_now_valid)) {
                if (mediaPlayer != null) {
                    genuineTrackMessage(mediaPlayer.getTrack());
                }
                if (searchParameters != null) {
                    String query = searchParameters.get("query").toString();
                    searchParameters = null;

                    if (searchCache != null) {
                        Vector<HashMap<String, String>> searchResults = searchCache.getSearchResults();

                        if ((searchResults != null) && (searchResults.size() > 0)) {
                            int matchIndex = -1;

                            for (int i = 0; i < searchResults.size(); i++) {
                                HashMap<String, String> item  = searchResults.get(i);
                                String                  title = item.get("title");
                                if ((title != null) && (query.compareToIgnoreCase(title) == 0)) {
                                    matchIndex = i;
                                    break;
                                }
                            }
                            if (matchIndex == -1) {
                                for (int i = 0; i < searchResults.size(); i++) {
                                    HashMap<String, String> item = searchResults.get(i);
                                    if (Amproid.stringContains(query, item.get("title"))) {
                                        matchIndex = i;
                                        break;
                                    }
                                }
                            }
                            if (matchIndex == -1) {
                                matchIndex = 0;
                            }

                            String id = searchResults.get(matchIndex).get("id");
                            if (id != null) {
                                pausedByUser = false;
                                mediaSessionCallback.onPlayFromMediaId(id, new Bundle());
                            }
                        }
                    }
                }
                if (amproidServiceBinderCallback != null) {
                    amproidServiceBinderCallback.refreshRootItemCompleted(getString(R.string.item_search_results_id));
                }
            }
            else if (asyncType == getResources().getInteger(R.integer.playlists_now_valid)) {
                if (amproidServiceBinderCallback != null) {
                    amproidServiceBinderCallback.refreshRootItemCompleted(getString(R.string.item_playlists_id));
                }
            }
            else if (asyncType == getResources().getInteger((R.integer.async_get_radios))) {
                String errorMessage = arguments.getString("errorMessage", "");
                if (errorMessage.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Vector<HashMap<String, String>> radios = (Vector<HashMap<String, String>>) arguments.getSerializable("radios");
                    if ((radios != null) && !radios.isEmpty()) {
                        AmproidService.this.radios = radios;
                    }
                }
            }
            else if (asyncType == getResources().getInteger(R.integer.async_image_downloader)) {
                if ((mediaPlayer != null) && (arguments.containsKey("url"))) {
                    boolean equal = false;
                    try {
                        URL imageURL = new URL(arguments.getString("url"));
                        equal = imageURL.equals(mediaPlayer.getPictureUrl());
                    }
                    catch (Exception ignored) {
                    }
                    if (equal) {
                        Bitmap bitmap = arguments.getParcelable("image");
                        if (bitmap != null) {
                            if ((notificationBuilder != null) && (notificationManager != null)) {
                                notificationBuilder.setLargeIcon(bitmap);
                                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
                            }

                            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap);
                            mediaSession.setMetadata(metadataBuilder.build());

                            imageNeverDownloaded = false;
                        }
                    }
                }
            }
            return;
        }

        if (action.equals(getString(R.string.async_no_network_broadcast_action))) {
            long ms = arguments.getLong("elapsedMS", 0);
            fakeTrackMessage(R.string.error_no_network, String.format(Locale.US, "%s %ds", getString(R.string.error_network_wait), ms / 1000));
        }
    }


    private int retryDelay(int attempt)
    {
        return Math.min(Math.max(100, attempt * 500), 10000);
    }


    @SuppressLint("ApplySharedPref")
    private void startAuth()
    {
        AccountManager accountManager = AccountManager.get(this);
        Account[]      accounts       = accountManager.getAccountsByType(getString(R.string.account_type));

        if (accounts.length == 0) {
            // this eventually will open the authenticator activity (which, in turn, should result in returning to this startAuth method again, unless the user cancels)
            accountManager.addAccount(getString(R.string.account_type), null, null, null, null, new AmproidAccountManagerCallback(), null);
            return;
        }

        SharedPreferences preferences         = getSharedPreferences(getString(R.string.account_preferences), Context.MODE_PRIVATE);
        String            selectedAccountName = preferences.getString(getString(R.string.account_selected_preference), null);

        if (selectedAccountName == null) {
            if (accounts.length == 1) {
                selectedAccountName = accounts[0].name;

                SharedPreferences.Editor preferencesEditor = preferences.edit();
                preferencesEditor.putString(getString(R.string.account_selected_preference), selectedAccountName);
                preferencesEditor.commit();
            }
            else {
                if (amproidServiceBinderCallback != null) {
                    try {
                        amproidServiceBinderCallback.startAuthenticatorActivity();
                    }
                    catch (Exception ignored) {
                    }
                }
                return;
            }
        }

        selectedAccount = null;
        for (Account account : accounts) {
            if (account.name.compareTo(selectedAccountName) == 0) {
                selectedAccount = account;
                break;
            }
        }
        if (selectedAccount == null) {
            if (amproidServiceBinderCallback != null) {
                try {
                    amproidServiceBinderCallback.startAuthenticatorActivity();
                }
                catch (Exception ignored) {
                }
            }
            return;
        }

        getNewAuthToken("");
    }


    public interface IAmproidServiceBinderCallback
    {
        void quitNow();

        void refreshRootItemCompleted(String itemId);

        @SuppressWarnings("unused")
        void showToast(int stringResource);

        void startAuthenticatorActivity();
    }


    public final class AmproidServiceBinder extends Binder
    {
        public AmproidService getAmproidService()
        {
            return AmproidService.this;
        }
    }


    private final class MediaSessionCallback extends MediaSessionCompat.Callback
    {
        @Override
        public void onPlay()
        {
            if (!haveAudioFocus) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                if (audioManager != null) {
                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build();
                    audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setWillPauseWhenDucked(true)
                            .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
                            .build();

                    int focus = audioManager.requestAudioFocus(audioFocusRequest);

                    haveAudioFocus = (focus == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                }
            }
            if (!haveAudioFocus) {
                return;
            }

            if (mediaPlayer != null) {
                pausedByUser = false;

                if (mediaPlayer.wasError()) {
                    startTrack(mediaPlayer.getTrack());
                    return;
                }

                startService(new Intent(AmproidService.this, AmproidService.class));
                mediaPlayer.start();
            }
        }


        @SuppressWarnings("CommentedOutCode")   // TODO remove this after "shuffle" button is added
        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras)
        {
            String serverUrl = Amproid.getServerUrl(selectedAccount);

            stateBuilder.setState(PlaybackStateCompat.STATE_CONNECTING, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            // TODO add "shuffle" button to UI
            /*
            if (mediaId.compareTo(getString(R.string.item_random_id)) == 0) {
                if (playMode != PLAY_MODE_RANDOM) {
                    playMode = PLAY_MODE_RANDOM;
                    GetTracksThread getTracks = new GetTracksThread(authToken, serverUrl, playMode, "");
                    startedThreads.add(getTracks);
                    getTracks.start();
                }
                return;
            }
            */

            if (mediaId.startsWith(PREFIX_PLAYLIST)) {
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }

                playMode      = PLAY_MODE_PLAYLIST;
                playlistId    = mediaId.replace(PREFIX_PLAYLIST, "");
                comingUpIndex = 0;

                fakeTrackMessage(R.string.getting_playlist_tracks, "");

                GetTracksThread getTrack = new GetTracksThread(authToken, serverUrl, playMode, playlistId, mainHandler);
                startedThreads.add(getTrack);
                getTrack.start();
                return;
            }

            if (mediaId.startsWith(PREFIX_ARTIST)) {
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }

                playMode      = PLAY_MODE_ARTIST;
                artistId      = mediaId.replace(PREFIX_ARTIST, "");
                comingUpIndex = 0;

                fakeTrackMessage(R.string.getting_artist_tracks, "");

                GetTracksThread getTracks = new GetTracksThread(authToken, serverUrl, playMode, artistId, mainHandler);
                startedThreads.add(getTracks);
                getTracks.start();
                return;
            }

            if (mediaId.startsWith(PREFIX_ALBUM)) {
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }

                playMode      = PLAY_MODE_ALBUM;
                albumId       = mediaId.replace(PREFIX_ALBUM, "");
                comingUpIndex = 0;

                fakeTrackMessage(R.string.getting_album_tracks, "");

                GetTracksThread getTracks = new GetTracksThread(authToken, serverUrl, playMode, albumId, mainHandler);
                startedThreads.add(getTracks);
                getTracks.start();
                return;
            }

            if (mediaId.startsWith(PREFIX_SONG)) {
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }

                playMode = PLAY_MODE_BROWSE;
                browseId = mediaId.replace(PREFIX_SONG, "");

                GetTracksThread getTracks = new GetTracksThread(authToken, serverUrl, playMode, browseId, mainHandler);
                startedThreads.add(getTracks);
                getTracks.start();
                return;
            }

            if (mediaId.startsWith(PREFIX_RADIO)) {
                if (!RADIO_ENABLED) {
                    return;
                }
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }

                browseId = mediaId.replace(PREFIX_RADIO, "");

                URL url;
                try {
                    url = new URL(browseId);
                }
                catch (Exception e) {
                    fakeTrackMessage(R.string.error_invalid_radio_url, "");
                    return;
                }

                String name = getString(R.string.radio_station);
                for (HashMap<String, String> radio : radios) {
                    String radioUrl = radio.get("url");
                    if ((radioUrl != null) && (radioUrl.compareTo(browseId) == 0)) {
                        name = StringEscapeUtils.unescapeHtml4(radio.get("name"));
                    }
                }

                // pretend that this is just another track in shuffle
                playMode = PLAY_MODE_RANDOM;

                Track track = new Track();
                track.setTitle(name);
                track.setArtist("");
                track.setAlbum(getString(R.string.radio_station));
                track.setRadio(true);
                track.setUrl(url);

                startTrack(track);

                if ((notificationBuilder != null) && (notificationManager != null)) {
                    notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
                }

                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher));
                mediaSession.setMetadata(metadataBuilder.build());
            }
        }


        @Override
        public void onPlayFromSearch(final String query, final Bundle extras)
        {
            if ((query == null) || query.isEmpty() || (searchCache == null)) {
                onSkipToNext();
                return;
            }

            if (extras.containsKey("android.intent.extra.REFERRER_NAME")) {
                searchParameters = extras;
            }

            stateBuilder.setState(PlaybackStateCompat.STATE_CONNECTING, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            fakeTrackMessage(R.string.getting_search_results, "");

            if (!extras.containsKey("query")) {
                extras.putString("query", query);
            }

            searchCache.refreshSearch(extras);
        }


        @Override
        public void onPause()
        {
            pausedByUser = true;
            if (mediaPlayer != null) {
                mediaPlayer.pause();
            }
        }


        @Override
        public void onSkipToNext()
        {
            if (mediaPlayer != null) {
                mediaPlayer.pause();
            }

            if ((playMode == PLAY_MODE_PLAYLIST) || (playMode == PLAY_MODE_ARTIST) || (playMode == PLAY_MODE_ALBUM)) {
                comingUpIndex++;
                if ((comingUpIndex >= 0) && (comingUpIndex < comingUpTracks.size())) {
                    startTrack(comingUpTracks.get(comingUpIndex));
                    return;
                }

                // end of playlist: switch back to to our default mode, "shuffle"
                playMode = PLAY_MODE_RANDOM;
            }

            if (playMode == PLAY_MODE_BROWSE) {
                // browse is always one song only, so let's go back to "shuffle"
                playMode = PLAY_MODE_RANDOM;
            }

            stateBuilder.setState(PlaybackStateCompat.STATE_CONNECTING, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            GetTracksThread getTracks = new GetTracksThread(authToken, Amproid.getServerUrl(selectedAccount), playMode, playlistId, mainHandler);
            startedThreads.add(getTracks);
            getTracks.start();
        }


        @Override
        public void onSkipToPrevious()
        {
            if ((mediaPlayer != null) && mediaPlayer.isRadio()) {
                return;
            }

            // if user presses previous button after the first 2.5 seconds already played, then just do a rewind
            if ((mediaPlayer != null) && (mediaPlayer.getCurrentPosition() > 2500)) {
                mediaPlayer.seekTo(0);
                return;
            }

            if (((playMode == PLAY_MODE_PLAYLIST) || (playMode == PLAY_MODE_ARTIST) || (playMode == PLAY_MODE_ALBUM)) && (comingUpIndex > 0)) {
                comingUpIndex--;
                if ((comingUpIndex < comingUpTracks.size()) && (mediaPlayer != null)) {
                    startTrack(comingUpTracks.get(comingUpIndex));
                }
            }
        }


        @Override
        public void onStop()
        {
            // playback is never stopped: it's either playing or paused
            // stop command actually means quit
            fakeTrackMessage(R.string.quitting, "");
            if (amproidServiceBinderCallback != null) {
                try {
                    amproidServiceBinderCallback.quitNow();
                }
                catch (Exception ignored) {
                }
            }
            stopSelf();
        }


        @Override
        public void onSeekTo(long pos)
        {
            if ((mediaPlayer != null) && mediaPlayer.isPlaying() && (pos >= 0) && (pos <= mediaPlayer.getDuration())) {
                mediaPlayer.seekTo((int) pos);
            }
        }
    }


    private final class AmproidAccountManagerCallback implements AccountManagerCallback<Bundle>
    {
        @Override
        public void run(AccountManagerFuture<Bundle> future)
        {
            Bundle bundle = null;
            try {
                bundle = future.getResult();
            }
            catch (Exception ignored) {
            }
            if (bundle == null) {
                return;
            }

            if (bundle.containsKey(AccountManager.KEY_INTENT)) {
                Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);

                if (intent.getBooleanExtra("AmproidAuthenticatorActivity", false)) {
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                return;
            }

            if (bundle.containsKey(KEY_AUTHTOKEN)) {
                if (!bundle.containsKey(KEY_ACCOUNT_TYPE) || (bundle.getString(KEY_ACCOUNT_TYPE, "").compareTo(getString(R.string.account_type)) != 0)) {
                    getNewAuthToken(getString(R.string.error_account_type_mismatch));
                    return;
                }

                boolean retry = true;
                if (bundle.containsKey(KEY_ERROR_CODE)) {
                    retry = bundle.getString(KEY_ERROR_CODE, "").compareTo("0001") == 0;
                }
                if (bundle.containsKey(KEY_ERROR_MESSAGE)) {
                    getNewAuthToken(bundle.getString(KEY_ERROR_MESSAGE, ""), retry);
                    return;
                }

                authToken = bundle.getString(KEY_AUTHTOKEN, "");

                if (authToken.isEmpty()) {
                    getNewAuthToken(getString(R.string.error_blank_token), retry);
                    return;
                }

                ValidateTokenThread validateToken = new ValidateTokenThread(authToken, Amproid.getServerUrl(selectedAccount), mainHandler);
                startedThreads.add(validateToken);
                validateToken.start();
            }
        }
    }
}