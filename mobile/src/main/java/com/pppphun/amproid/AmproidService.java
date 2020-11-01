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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;
import static android.accounts.AccountManager.KEY_AUTHTOKEN;
import static android.accounts.AccountManager.KEY_ERROR_MESSAGE;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PAUSE;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_NEXT;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS;
import static android.support.v4.media.session.PlaybackStateCompat.ACTION_STOP;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_BUFFERING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_CONNECTING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PAUSED;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_STOPPED;
import static com.pppphun.amproid.Amproid.bundleGetString;
import static com.pppphun.amproid.Amproid.sendLocalBroadcast;


@SuppressWarnings("FieldMayBeFinal")
public class AmproidService extends MediaBrowserServiceCompat
{
    // miscellaneous package-wide constants
    final static int LOUDNESS_GAIN_DEFAULT = 300;

    // browse children prefixes
    final static String PREFIX_ALBUM    = "album~";
    final static String PREFIX_ARTIST   = "artist~";
    final static String PREFIX_PLAYLIST = "playlist~";
    final static String PREFIX_SONG     = "song~";

    // play mode constants
    final static int PLAY_MODE_UNKNOWN  = 0;
    final static int PLAY_MODE_RANDOM   = 1;
    final static int PLAY_MODE_PLAYLIST = 2;
    final static int PLAY_MODE_BROWSE   = 3;
    final static int PLAY_MODE_ALBUM    = 4;

    // miscellaneous constants
    private final static int    NOTIFICATION_ID         = 1001;
    private final static String NOTIFICATION_CHANNEL_ID = "AmproidServiceNotificationChannel";

    // attempts
    private final static int MAX_AUTH_ATTEMPTS = 50;
    private final static int MAX_QUIT_ATTEMPTS = 5;

    // for effects
    Equalizer.Settings equalizerSettings   = null;
    int                loudnessGainSetting = LOUDNESS_GAIN_DEFAULT;

    // objects to implement media session
    private PlaybackStateCompat.Builder stateBuilder         = new PlaybackStateCompat.Builder();
    private MediaMetadataCompat.Builder metadataBuilder      = new MediaMetadataCompat.Builder();
    private MediaSessionCallback        mediaSessionCallback = new MediaSessionCallback();
    private MediaSessionCompat          mediaSession         = null;

    // to handle our notification
    private NotificationCompat.Builder notificationBuilder = null;
    private NotificationManagerCompat  notificationManager = null;

    // for playing tracks
    private AmproidMediaPlayer mediaPlayer = null;

    // to handle audio focus
    private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = null;

    // IPC with this project's activities
    private AmproidBroacastReceiver amproidBroacastReceiver = new AmproidBroacastReceiver();

    // fields to support connection with Ampache server
    private int     authAttempts    = 0;
    private Account selectedAccount = null;
    private String  authToken       = null;

    // play mode and related fields
    private int           playMode       = PLAY_MODE_UNKNOWN;
    private String        playlistId     = null;
    private String        albumId        = null;
    private String        browseId       = null;
    private Vector<Track> playlistTracks = new Vector<>();
    private int           playlistIndex  = 0;
    private boolean       pausedByUser   = false;
    private boolean       haveAudioFocus = false;

    // Google Assistant support
    private Bundle  searchParameters = null;
    private boolean hasBoundClients  = false;
    private int     quitAttempts     = 0;
    private boolean quitting         = false;

    // async tasks
    @SuppressWarnings("rawtypes")
    private Vector<AsyncTask> asyncTasks = new Vector<>();

    // delayed tasks

    private Handler mainHandler              = new Handler(Looper.getMainLooper());
    private int     delayedStopSecsRemaining = 0;

    private Runnable delayedGetAuthToken                                = new Runnable()
    {
        @Override
        public void run()
        {
            fakeTrackMessage(R.string.login_handshake, selectedAccount.name);
            AccountManager.get(AmproidService.this).getAuthToken(selectedAccount, "", null, true, new AmproidService.AmproidAccountManagerCallback(), null);
        }
    };
    private Runnable delayedMediaSessionUpdateDurationPositionIfPlaying = new Runnable()
    {
        @Override
        public void run()
        {
            mediaSessionUpdateDurationPositionIfPlaying();
        }
    };
    private Runnable delayedQuit                                        = new Runnable()
    {
        @Override
        public void run()
        {
            mainHandler.removeCallbacks(delayedStop);

            if (hasBoundClients) {
                // this closes the main activity if needed
                sendLocalBroadcast(R.string.quit_broadcast_action);

                // if user wants to quit and we can't, let's at least pause playback
                if (mediaPlayer != null) {
                    pausedByUser = true;
                    mediaPlayer.pause();
                }

                // let's wait a bit more for clients to unbound
                if (quitAttempts < MAX_QUIT_ATTEMPTS) {
                    quitAttempts++;
                    mainHandler.postDelayed(delayedQuit, retryDelay(quitAttempts));
                    return;
                }

                // gave up, can't quit, oh well, c'est la vie
                quitting = false;

                // show a toast notification for user to know what's going on
                Toast.makeText(getApplicationContext(), R.string.still_bound, Toast.LENGTH_LONG).show();

                // restore current song title and such

                if (mediaPlayer != null) {
                    mediaPlayer.metaNotify();
                }

                // this is all we could do
                return;
            }

            // stop the media browser service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE);
            }
            stopSelf();
        }
    };
    private Runnable delayedStop                                        = new Runnable()
    {
        @Override
        public void run()
        {
            delayedStopSecsRemaining--;
            fakeTrackMessage(String.format(getString(R.string.aa_quit_time), delayedStopSecsRemaining), getString(R.string.aa_quit_desc));

            if (delayedStopSecsRemaining <= 0) {
                mediaSessionCallback.onStop();
                return;
            }

            mainHandler.postDelayed(delayedStop, 1000);
        }
    };


    @Override
    public void onCreate()
    {
        super.onCreate();

        // initialize media session's state builder with supported actions and current state

        stateBuilder.setActions(ACTION_PLAY | ACTION_PAUSE | ACTION_STOP | ACTION_SKIP_TO_NEXT | ACTION_SKIP_TO_PREVIOUS | ACTION_PLAY_FROM_SEARCH);
        stateBuilder.setState(STATE_STOPPED, 0, 0);

        // create and initialize media session
        mediaSession = new MediaSessionCompat(this, getString(R.string.service_name));
        mediaSession.setCallback(mediaSessionCallback);
        mediaSession.setPlaybackState(stateBuilder.build());

        // noinspection deprecation - this is required for older Android versions
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        // set the session token for this media browser service
        setSessionToken(mediaSession.getSessionToken());

        // activate the media session - this makes it discoverable
        mediaSession.setActive(true);

        // other initializations take place in a separate thread, so this onCreate can return quickly
        new Thread(new OnCreateDeferred()).start();
    }


    @Override
    public IBinder onBind(Intent intent)
    {
        hasBoundClients = true;

        mainHandler.postDelayed(delayedMediaSessionUpdateDurationPositionIfPlaying, 250);

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
        // root, immediate children are hardwired
        if (parentMediaId.compareTo(getString(R.string.item_root_id)) == 0) {
            ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(getString(R.string.item_random_id)).setTitle(getString(R.string.item_random_desc)).build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(getString(R.string.item_playlists_id)).setTitle(getString(R.string.item_playlists_desc)).build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(getString(R.string.item_browse_id)).setTitle(getString(R.string.item_browse_desc)).build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

            result.sendResult(results);
            return;
        }

        // get the Ampache server's URL from the account
        String serverUrl = Amproid.getServerUrl(selectedAccount);

        // set state to "connecting" before starting network operations so clients can display hourglass or some other visual cue
        stateBuilder.setState(STATE_CONNECTING, 0, 0);
        mediaSession.setPlaybackState(stateBuilder.build());

        // playlists top level - get all playlists
        if (parentMediaId.compareTo(getString(R.string.item_playlists_id)) == 0) {
            result.detach();
            asyncTasks.add(new AsyncGetPlaylists(authToken, serverUrl, result).execute());
            return;
        }

        // browse top level - get all artists
        if (parentMediaId.compareTo(getString(R.string.item_browse_id)) == 0) {
            result.detach();
            asyncTasks.add(new AsyncGetArtists(authToken, serverUrl, result).execute());
            return;
        }

        // browse mid-level - get all albums for selected artist
        if (parentMediaId.startsWith(PREFIX_ARTIST)) {
            result.detach();
            browseId = parentMediaId.replace(PREFIX_ARTIST, "");
            asyncTasks.add(new AsyncGetAlbums(authToken, serverUrl, browseId, result).execute());
            return;
        }

        // browse final level - get all songs for selected album
        if (parentMediaId.startsWith(PREFIX_ALBUM)) {
            result.detach();
            String artistBrowseId = browseId;
            browseId = parentMediaId.replace(PREFIX_ALBUM, "");
            asyncTasks.add(new AsyncGetTracks(authToken, serverUrl, playMode, browseId, result, artistBrowseId).execute());
        }
    }


    @Override
    public void onSearch(@NonNull String query, Bundle extras, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result)
    {
        // set state to "connecting" before starting network operations so clients can display hourglass or some other visual cue
        stateBuilder.setState(STATE_CONNECTING, 0, 0);
        mediaSession.setPlaybackState(stateBuilder.build());

        // more feedback
        fakeTrackMessage(R.string.getting_search_results, "");

        result.detach();

        if (!extras.containsKey("query")) {
            extras.putString("query", query);
        }

        asyncTasks.add(new AsyncSearch(authToken, Amproid.getServerUrl(selectedAccount), extras, result).execute());
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        // handle media buttons
        MediaButtonReceiver.handleIntent(mediaSession, intent);

        // Google Assistant support
        if ((intent != null) && (intent.getAction() != null) && (intent.getExtras() != null) && (intent.getAction().equals("android.media.action.MEDIA_PLAY_FROM_SEARCH"))) {
            if (playMode != PLAY_MODE_UNKNOWN) {
                String query = intent.getExtras().getString("query");
                mediaSessionCallback.onPlayFromSearch(query == null ? "" : query, intent.getExtras());
            } else {
                searchParameters = intent.getExtras();
            }
        }

        // if started by Android Auto: don't start playback right away, pause if already playing
        if ((intent != null) && (intent.getExtras() != null) && intent.getExtras().containsKey("PresenceForever") && intent.getExtras().getBoolean("PresenceForever")) {
            mediaSessionCallback.onPause();
        }

        return super.onStartCommand(intent, flags, startId);
    }


    @Override
    public void onDestroy()
    {
        try {
            // cancel scheduled stuffs
            mainHandler.removeCallbacks(delayedGetAuthToken);
        }
        catch (Exception e) {
            // nothing to do here
        }

        asyncCancelAll();

        try {
            // unregister IPC receiver
            LocalBroadcastManager.getInstance(this).unregisterReceiver(amproidBroacastReceiver);
            unregisterReceiver(amproidBroacastReceiver);
        }
        catch (Exception e) {
            // nothing to do here
        }

        // stop and release media player & co.
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        // abandon audio focus
        if (audioFocusChangeListener != null) {
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            // noinspection ConstantConditions - AUDIO_SERVICE exists for sure
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }

        // release media session
        if (mediaSession != null) {
            mediaSession.setActive(false);
            mediaSession.release();
        }

        // cancel the notification
        if (notificationManager != null) {
            notificationManager.cancelAll();
        }

        // trying to stop Android Auto from constantly switching the album art
        // see https://support.google.com/androidauto/thread/3960822?hl=en
        delDirOnExit(getCacheDir());

        super.onDestroy();
    }


    private void delDirOnExit(File dir)
    {
        if ((dir == null) || !dir.exists()) {
            return;
        }

        File[] children = dir.listFiles();

        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                delDirOnExit(child);
            }

            try {
                child.deleteOnExit();
            }
            catch (Exception e) {
                // nothing we can do about it
            }
        }
    }


    @Override
    public boolean onUnbind(Intent intent)
    {
        // this is called only when ALL clients have disconnected
        hasBoundClients = false;

        super.onUnbind(intent);
        return true;
    }


    @Override
    public void onRebind(Intent intent)
    {
        hasBoundClients = true;

        mainHandler.postDelayed(delayedMediaSessionUpdateDurationPositionIfPlaying, 250);

        super.onRebind(intent);
    }


    // cancel async task
    @SuppressWarnings("rawtypes")
    void asyncCancel(AsyncTask task)
    {
        task.cancel(false);
        asyncTasks.remove(task);
    }


    // cancel all async tasks
    void asyncCancelAll()
    {
        while (asyncTasks.size() > 0) {
            asyncCancel(asyncTasks.get(0));
        }
    }


    @SuppressWarnings("rawtypes")
    void asyncHousekeeping()
    {
        Vector<AsyncTask> toBeRemoved = new Vector<>();

        for (AsyncTask task : asyncTasks) {
            if (task.getStatus() == AsyncTask.Status.FINISHED) {
                toBeRemoved.add(task);
            }
        }

        for (AsyncTask task : toBeRemoved) {
            asyncTasks.remove(task);
        }
    }


    // getting tracks finished
    void asyncProcessResultsGetTracks(Bundle data)
    {
        asyncHousekeeping();

        // handle error
        String errorMessage = data.getString("errorMessage", "");
        if (!errorMessage.isEmpty()) {
            fakeTrackMessage(R.string.error_error, data.getString("errorMessage"));
            return;
        }

        // get the result
        // noinspection unchecked - we know what we have put in to the bundle in AsyncGetTracks
        Vector<Track> tracks = (Vector<Track>) data.getSerializable("tracks");
        if ((tracks == null) || tracks.isEmpty()) {
            fakeTrackMessage(R.string.error_error, getString(R.string.error_tracks_empty));
            return;
        }

        // let's start playing

        int trackIndex = 0;
        if ((playMode == PLAY_MODE_PLAYLIST) || (playMode == PLAY_MODE_ALBUM)) {
            // add tracks to playlist cache
            playlistTracks.clear();
            playlistTracks.addAll(tracks);

            // playlist index is set to zero when user selects a playlist to play (in onPlayFromMediaId), or it might be a positive number if starting with saved play mode
            if ((playlistIndex >= 0) && (playlistIndex < tracks.size())) {
                trackIndex = playlistIndex;
            } else {
                // this could happen if playlist is modified on Ampache server between Amproid restarts
                playlistIndex = trackIndex;
            }
        }

        startTrack(tracks.get(trackIndex));
    }


    // search finished
    void asyncProcessResultsSearch(Bundle data)
    {
        // handle error
        String errorMessage = data.getString("errorMessage", "");
        if (!errorMessage.isEmpty()) {
            fakeTrackMessage(R.string.error_error, data.getString("errorMessage"));
            return;
        }

        // get the result
        // noinspection unchecked - we know what we have put in to the bundle in AsyncGetTracks
        HashMap<Integer, Vector<HashMap<String, String>>> found = (HashMap<Integer, Vector<HashMap<String, String>>>) data.getSerializable("found");
        if (found == null) {
            fakeTrackMessage(R.string.error_error, getString(R.string.error_searchresults_empty));
            return;
        }

        // extract songs and albums
        Vector<HashMap<String, String>> songs         = found.get(AmpacheAPICaller.SEARCH_RESULTS_SONGS);
        Vector<HashMap<String, String>> albums        = found.get(AmpacheAPICaller.SEARCH_RESULTS_ALBUMS);
        Vector<HashMap<String, String>> artist_albums = found.get(AmpacheAPICaller.SEARCH_RESULTS_ARTIST_ALBUMS);

        if (songs == null) {
            songs = new Vector<>();
        }
        if (albums == null) {
            albums = new Vector<>();
        }
        if (artist_albums == null) {
            artist_albums = new Vector<>();
        }

        // what should we play depends on how the search was requested
        Bundle searchParameters = data.getBundle("searchParameters");
        String artist           = bundleGetString(searchParameters, "android.intent.extra.artist");
        String album            = bundleGetString(searchParameters, "android.intent.extra.album");
        String title            = bundleGetString(searchParameters, "android.intent.extra.title");

        // let's try to figure this out
        // give preference to albums as opposed to songs
        // sometimes Google Assistant queries the album as title (even though it knows that this is an album not a song), which makes the whole thing complicated

        if (!title.isEmpty()) {
            if (searchResultPlay(searchResultFilter(artist_albums, "name", title), PREFIX_ALBUM)) {
                return;
            }

            Vector<HashMap<String, String>> matching_albums = searchResultFilter(albums, "name", title);
            if (!artist.isEmpty() && (searchResultPlay(searchResultFilter(matching_albums, "artist", artist), PREFIX_ALBUM))) {
                return;
            }
            if (searchResultPlay(matching_albums, PREFIX_ALBUM)) {
                return;
            }

            Vector<HashMap<String, String>> matching_songs = searchResultFilter(songs, "title", title);
            if (!album.isEmpty()) {
                Vector<HashMap<String, String>> matching_songs_albums = searchResultFilter(matching_songs, "album", album);
                if (!artist.isEmpty() && searchResultPlay(searchResultFilter(matching_songs_albums, "artist", artist), PREFIX_SONG)) {
                    return;
                }
                if (searchResultPlay(matching_songs_albums, PREFIX_SONG)) {
                    return;
                }
            }
            if (!artist.isEmpty() && searchResultPlay(searchResultFilter(matching_songs, "artist", artist), PREFIX_SONG)) {
                return;
            }
            if (searchResultPlay(matching_songs, PREFIX_SONG)) {
                return;
            }
        }

        if (!album.isEmpty()) {
            Vector<HashMap<String, String>> matching_albums = searchResultFilter(albums, "name", album);
            if (!artist.isEmpty() && searchResultPlay(searchResultFilter(matching_albums, "artist", artist), PREFIX_ALBUM)) {
                return;
            }
            if (searchResultPlay(matching_albums, PREFIX_ALBUM)) {
                return;
            }
        }

        if (!artist.isEmpty() && searchResultPlay(searchResultFilter(artist_albums, "artist", artist), PREFIX_ALBUM)) {
            return;
        }

        // no exact matches found or only "query" was supplied
        if (searchResultPlay(artist_albums, PREFIX_ALBUM)) {
            return;
        }
        if (searchResultPlay(albums, PREFIX_ALBUM)) {
            return;
        }
        if (searchResultPlay(songs, PREFIX_SONG)) {
            return;
        }

        fakeTrackMessage(R.string.error_error, getString(R.string.error_searchresults_irrelevant));
    }


    // account validation finished
    void asyncProcessResultsValidateToken(Bundle data)
    {
        asyncHousekeeping();

        // this should never happen, but I have a certain amount of paranoia
        if (selectedAccount == null) {
            fakeTrackMessage(R.string.error_error, getString(R.string.error_no_account_selection));
            return;
        }

        // get the result
        boolean isTokenValid = data.getBoolean("isTokenValid", false);
        String  errorMessage = data.getString("errorMessage", "");

        // token is invalid - this can happen for good reason, that is, cached token expired
        if (!isTokenValid) {
            getNewAuthToken(errorMessage);
            return;
        }

        // finally... token is valid
        authAttempts = 0;

        // app was started by Google Assistant
        if (searchParameters != null) {
            String query = searchParameters.getString("query");
            if (query == null) {
                query = "";
            }

            mediaSessionCallback.onPlayFromSearch(query, searchParameters);
            return;
        }

        // get saved play mode
        loadPlayMode();

        // more feedback
        if (playMode == PLAY_MODE_PLAYLIST) {
            fakeTrackMessage(R.string.getting_playlist_tracks, "");
        } else if (playMode == PLAY_MODE_ALBUM) {
            fakeTrackMessage(R.string.getting_album_tracks, "");
        }

        String ampacheId = "";
        switch (playMode) {
            case PLAY_MODE_PLAYLIST:
                ampacheId = playlistId;

                // Ampache's smart playlists are generated dynamically, so it's meaningless to start one from where it left off
                if (ampacheId.startsWith("smart_")) {
                    playlistIndex = 0;
                }

                break;
            case PLAY_MODE_ALBUM:
                ampacheId = albumId;
                break;
            case PLAY_MODE_BROWSE:
                ampacheId = browseId;
        }

        // set state to "connecting" before starting network operations so clients can display hourglass or some other visual cue
        stateBuilder.setState(STATE_CONNECTING, 0, 0);
        mediaSession.setPlaybackState(stateBuilder.build());

        // get track, this will start play too
        asyncTasks.add(new AsyncGetTracks(authToken, Amproid.getServerUrl(selectedAccount), playMode, ampacheId).execute());
    }


    void getNewAuthToken(@NotNull String errorMessage)
    {
        // exhausted our chances
        if (authAttempts >= MAX_AUTH_ATTEMPTS) {
            fakeTrackMessage(R.string.error_login_failed, errorMessage.isEmpty() ? selectedAccount.name : errorMessage);
            return;
        }

        if ((authAttempts >= 1) || !errorMessage.isEmpty()) {
            fakeTrackMessage(getString(R.string.login_delay), errorMessage.isEmpty() ? selectedAccount.name : errorMessage);
        }

        // invalidate cached token
        AccountManager.get(this).invalidateAuthToken(selectedAccount.type, authToken);
        authToken = "";

        // get new token with delay
        mainHandler.postDelayed(delayedGetAuthToken, retryDelay(authAttempts));

        authAttempts++;
    }


    boolean searchResultPlay(Vector<HashMap<String, String>> vector, String prefix)
    {
        if (vector.size() < 1) {
            return false;
        }

        mediaSessionCallback.onPlayFromMediaId(prefix + vector.get(new Random().nextInt(vector.size())).get("id"), new Bundle());
        return true;
    }


    void startTrack(Track track)
    {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }

        mediaPlayer = new AmproidMediaPlayer(track, !pausedByUser);
    }


    // display a message in place of the track's title
    private void fakeTrackMessage(String message, String subMessage)
    {
        if ((notificationBuilder != null) && (notificationManager != null)) {
            notificationBuilder.setContentTitle(message);
            notificationBuilder.setContentText("");
            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }

        metadataBuilder.putString(METADATA_KEY_TITLE, message);
        metadataBuilder.putString(METADATA_KEY_ALBUM, subMessage);
        metadataBuilder.putString(METADATA_KEY_ARTIST, "");
        metadataBuilder.putLong(METADATA_KEY_DURATION, -1);
        mediaSession.setMetadata(metadataBuilder.build());
    }


    // display a message in place of the track's title - overload
    private void fakeTrackMessage(int stringResource, String subMessage)
    {
        fakeTrackMessage(getString(stringResource), subMessage);
    }


    private void loadPlayMode()
    {
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(getApplicationContext().getString(R.string.playmode_preferences), MODE_PRIVATE);

        playMode      = preferences.getInt(getApplicationContext().getString(R.string.playmode_playmode_preference), PLAY_MODE_RANDOM);
        playlistId    = preferences.getString(getApplicationContext().getString(R.string.playmode_playlist_id_preference), "");
        playlistIndex = preferences.getInt(getApplicationContext().getString(R.string.playmode_playlist_index_preference), 0);
        browseId      = preferences.getString(getApplicationContext().getString(R.string.playmode_browse_id_preference), "");
        albumId       = preferences.getString(getApplicationContext().getString(R.string.playmode_album_id_preference), "");

        if ((playMode == PLAY_MODE_BROWSE) && browseId.isEmpty()) {
            playMode = PLAY_MODE_RANDOM;
        } else if ((playMode == PLAY_MODE_PLAYLIST) && playlistId.isEmpty()) {
            playMode = PLAY_MODE_RANDOM;
        } else if ((playMode == PLAY_MODE_ALBUM) && albumId.isEmpty()) {
            playMode = PLAY_MODE_RANDOM;
        }

        if ((playMode < PLAY_MODE_RANDOM) || (playMode > PLAY_MODE_ALBUM)) {
            playMode = PLAY_MODE_RANDOM;
        }
    }


    private void mediaSessionUpdateDurationPositionIfPlaying()
    {
        boolean isPlaying;
        try {
            isPlaying = (mediaPlayer != null) && mediaPlayer.isPlaying();
        }
        catch (Exception e) {
            return;
        }

        if (isPlaying) {
            // update duration first
            metadataBuilder.putLong(METADATA_KEY_DURATION, mediaPlayer.getDuration());
            mediaSession.setMetadata(metadataBuilder.build());

            // set playing state
            stateBuilder.setState(STATE_PLAYING, mediaPlayer.getCurrentPosition(), 1);
            mediaSession.setPlaybackState(stateBuilder.build());
        }
    }


    private int retryDelay(int attempt)
    {
        return Math.min(Math.max(100, attempt * 500), 10000);
    }


    @SuppressLint("ApplySharedPref")
    private void savePlayMode()
    {
        SharedPreferences        preferences       = getApplicationContext().getSharedPreferences(getApplicationContext().getString(R.string.playmode_preferences), MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = preferences.edit();

        preferencesEditor.putInt(getApplicationContext().getString(R.string.playmode_playmode_preference), playMode);
        preferencesEditor.putString(getApplicationContext().getString(R.string.playmode_playlist_id_preference), playlistId);
        preferencesEditor.putInt(getApplicationContext().getString(R.string.playmode_playlist_index_preference), playlistIndex);
        preferencesEditor.putString(getApplicationContext().getString(R.string.playmode_browse_id_preference), browseId);
        preferencesEditor.putString(getApplicationContext().getString(R.string.playmode_album_id_preference), albumId);

        preferencesEditor.commit();
    }


    private Vector<HashMap<String, String>> searchResultFilter(Vector<HashMap<String, String>> source, @NotNull String key, @NotNull String value)
    {
        Vector<HashMap<String, String>> returnValue = new Vector<>();

        for (HashMap<String, String> item : source) {
            if (item.containsKey(key)) {
                String mapValue = item.get(key);

                if ((mapValue != null) && mapValue.equalsIgnoreCase(value)) {
                    returnValue.add(item);
                }
            }
        }

        return returnValue;
    }


    // starts authentication with Ampache server - actual authentication involves asynchronous task, but this makes sure things are ready for that before starting it
    @SuppressLint("ApplySharedPref")
    private void startAuth()
    {
        // accounts are managed by Android's built-in Accounts settings
        // an Amproid account is essentially the URL and credentials to any Ampache server
        // the goal here is to find an account to use

        // get all Amproid accounts
        AccountManager accountManager = AccountManager.get(this);
        Account[]      accounts       = accountManager.getAccountsByType(getString(R.string.account_type));

        // no account exist - user must create at least one
        if (accounts.length == 0) {
            // this eventually will open the authenticator activity (which, in turn, should result in returning to this startAuth method again, unless the user cancels)
            accountManager.addAccount(getString(R.string.account_type), null, null, null, null, new AmproidAccountManagerCallback(), null);
            return;
        }

        // get last used account
        SharedPreferences preferences         = getSharedPreferences(getString(R.string.account_preferences), Context.MODE_PRIVATE);
        String            selectedAccountName = preferences.getString(getString(R.string.account_selected_preference), null);

        // no last used account
        if (selectedAccountName == null) {
            // if only one account exist, then let's use that
            if (accounts.length == 1) {
                selectedAccountName = accounts[0].name;

                // save it for next time
                SharedPreferences.Editor preferencesEditor = preferences.edit();
                preferencesEditor.putString(getString(R.string.account_selected_preference), selectedAccountName);
                preferencesEditor.commit();
            }
            // otherwise user must choose
            else {
                // stupid AbstractAccountAuthenticator has no method to choose account, so let's just start the authenticator activity manually
                startAuthenticatorActivity();
                return;
            }
        }

        // we have to loop through our accounts to find the last used one
        selectedAccount = null;
        for (Account account : accounts) {
            if (account.name.compareTo(selectedAccountName) == 0) {
                selectedAccount = account;
                break;
            }
        }
        if (selectedAccount == null) {
            // last used account not found in accounts (user probably deleted it from Android's Account settings), user has to choose or create new
            startAuthenticatorActivity();
            return;
        }

        // get token to continue authentication
        getNewAuthToken("");
    }


    // this is just a simple helper to open the authenticator activity
    private void startAuthenticatorActivity()
    {
        Intent intent = new Intent(this, AmproidAuthenticatorActivity.class);
        intent.putExtra(KEY_ACCOUNT_TYPE, getString(R.string.account_type));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    // called from onCreate to finish initializations
    class OnCreateDeferred implements Runnable
    {
        @SuppressLint("ApplySharedPref")
        @Override
        public void run()
        {
            // initialize handler of incoming IPC messages
            registerReceiver(amproidBroacastReceiver, new IntentFilter("android.media.AUDIO_BECOMING_NOISY"));
            LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(AmproidService.this);
            localBroadcastManager.registerReceiver(amproidBroacastReceiver, new IntentFilter(getString(R.string.auto_exited_broadcast_action)));
            localBroadcastManager.registerReceiver(amproidBroacastReceiver, new IntentFilter(getString(R.string.account_selected_broadcast_action)));
            localBroadcastManager.registerReceiver(amproidBroacastReceiver, new IntentFilter(getString(R.string.async_finished_broadcast_action)));
            localBroadcastManager.registerReceiver(amproidBroacastReceiver, new IntentFilter(getString(R.string.async_no_network_broadcast_action)));
            localBroadcastManager.registerReceiver(amproidBroacastReceiver, new IntentFilter(getString(R.string.eq_request_broadcast_action)));
            localBroadcastManager.registerReceiver(amproidBroacastReceiver, new IntentFilter(getString(R.string.eq_apply_broadcast_action)));
            localBroadcastManager.registerReceiver(amproidBroacastReceiver, new IntentFilter(getString(R.string.playlist_request_broadcast_action)));
            localBroadcastManager.registerReceiver(amproidBroacastReceiver, new IntentFilter(getString(R.string.playlist_apply_broadcast_action)));

            // Uri of custom notification sound
            Uri notifyUri = Uri.parse(String.format("%s://%s/%s", ContentResolver.SCHEME_ANDROID_RESOURCE, getApplicationContext().getPackageName(), R.raw.notify));

            // notification manager will be used to update notification with track data
            notificationManager = NotificationManagerCompat.from(AmproidService.this);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_PUBLIC);
                notificationChannel.setSound(notifyUri, new AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
                notificationManager.createNotificationChannel(notificationChannel);
            }

            // create and initialize notification builder
            notificationBuilder = new NotificationCompat.Builder(AmproidService.this, NOTIFICATION_CHANNEL_ID)
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_play, getString(R.string.play), MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, ACTION_PLAY)))
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_pause, getString(R.string.pause), MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, ACTION_PAUSE)))
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_media_next, getString(R.string.next), MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, ACTION_SKIP_TO_NEXT)))
                    .addAction(new NotificationCompat.Action(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, ACTION_STOP)))
                    .setContentText(getString(R.string.initializing))
                    .setContentTitle(getString(R.string.app_name))
                    .setSmallIcon(R.drawable.amproid_playing)
                    .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.amproid_playing))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSound(notifyUri)
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(mediaSession.getSessionToken()).setShowCancelButton(true).setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(AmproidService.this, ACTION_STOP)).setShowActionsInCompactView(0, 1));

            // put this media browser service in the foreground
            startForeground(NOTIFICATION_ID, notificationBuilder.build());

            // get and apply user's saved audio effects settings
            SharedPreferences preferences             = getSharedPreferences(getString(R.string.eq_preferences), Context.MODE_PRIVATE);
            String            equalizerSettingsString = preferences.getString(getString(R.string.eq_settings_preference), null);
            loudnessGainSetting = preferences.getInt(getString(R.string.eq_loudness_gain_preference), LOUDNESS_GAIN_DEFAULT);

            if (equalizerSettingsString != null) {
                try {
                    // settings are saved in string format, let's attempt to convert it to actual values
                    equalizerSettings = new Equalizer.Settings(equalizerSettingsString);
                }
                catch (Exception e) {
                    // nothing to do here, equalizerSettings remains null, which is handled in AmproidMediaPLayer's setEffects
                }
            }

            // handle audio focus
            audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener()
            {
                @Override
                public void onAudioFocusChange(int focusChange)
                {
                    // audio focus received
                    if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                        haveAudioFocus = true;
                        if ((mediaPlayer != null) && !pausedByUser) {
                            mediaPlayer.start();
                        }
                        return;
                    }

                    // audio focus lost
                    haveAudioFocus = false;
                    if (mediaPlayer != null) {
                        mediaPlayer.pause();
                    }
                }
            };
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            // noinspection ConstantConditions - AUDIO_SERVICE exists for sure
            haveAudioFocus = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

            // migrate legacy preferences
            SharedPreferences legacyPreferences = getSharedPreferences(getString(R.string.persistence_preferences), Context.MODE_PRIVATE);
            if (legacyPreferences.contains(getString(R.string.persistence_use_preference))) {
                SharedPreferences        optionsPreferences       = getSharedPreferences(getString(R.string.options_preferences), Context.MODE_PRIVATE);
                SharedPreferences.Editor optionsPreferencesEditor = optionsPreferences.edit();
                optionsPreferencesEditor.putBoolean(getString(R.string.persistence_use_preference), legacyPreferences.getBoolean(getString(R.string.persistence_use_preference), true));
                optionsPreferencesEditor.commit();

                boolean deleted = false;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    deleted = deleteSharedPreferences(getString(R.string.persistence_preferences));
                }
                if (!deleted) {
                    SharedPreferences.Editor legacyPreferencesEditor = legacyPreferences.edit();
                    legacyPreferencesEditor.remove(getString(R.string.persistence_use_preference));
                    legacyPreferencesEditor.commit();
                }
            }

            // start authentication with Ampache server (on the main thread)
            mainHandler.post(new Runnable()
            {
                @Override
                public void run()
                {
                    startAuth();
                }
            });
        }
    }


    // receives media button commands
    private final class MediaSessionCallback extends MediaSessionCompat.Callback
    {
        @Override
        public void onPlay()
        {
            if (!haveAudioFocus) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                // noinspection ConstantConditions - AUDIO_SERVICE exists for sure
                haveAudioFocus = audioManager.requestAudioFocus(audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            }
            if (!haveAudioFocus) {
                return;
            }

            mainHandler.removeCallbacks(delayedStop);

            if (mediaPlayer != null) {
                pausedByUser = false;

                if (mediaPlayer.wasError()) {
                    startTrack(mediaPlayer.getTrack());
                    return;
                }

                mediaPlayer.start();
            }
        }


        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras)
        {
            String serverUrl = Amproid.getServerUrl(selectedAccount);

            // set state to "connecting" before starting network operations so clients can display hourglass or some other visual cue
            stateBuilder.setState(STATE_CONNECTING, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            // user selected random mode (a.k.a. "shuffle")
            if (mediaId.compareTo(getString(R.string.item_random_id)) == 0) {
                // don't switch mode if random mode already selected (don't want it to act like another "skip to next" button)
                if (playMode != PLAY_MODE_RANDOM) {
                    playMode = PLAY_MODE_RANDOM;
                    asyncTasks.add(new AsyncGetTracks(authToken, serverUrl, playMode, "").execute());
                }
                return;
            }

            // user selected a specific playlist
            if (mediaId.startsWith(PREFIX_PLAYLIST)) {
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }

                playMode      = PLAY_MODE_PLAYLIST;
                playlistId    = mediaId.replace(PREFIX_PLAYLIST, "");
                playlistIndex = 0;

                fakeTrackMessage(R.string.getting_playlist_tracks, "");

                asyncTasks.add(new AsyncGetTracks(authToken, serverUrl, playMode, playlistId).execute());
                return;
            }

            // user selected a specific album
            if (mediaId.startsWith(PREFIX_ALBUM)) {
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }

                playMode      = PLAY_MODE_ALBUM;
                albumId       = mediaId.replace(PREFIX_ALBUM, "");
                playlistIndex = 0;

                fakeTrackMessage(R.string.getting_album_tracks, "");

                asyncTasks.add(new AsyncGetTracks(authToken, serverUrl, playMode, albumId).execute());
                return;
            }

            // user selected a specific song
            if (mediaId.startsWith(PREFIX_SONG)) {
                if (mediaPlayer != null) {
                    mediaPlayer.pause();
                }

                playMode = PLAY_MODE_BROWSE;
                browseId = mediaId.replace(PREFIX_SONG, "");

                asyncTasks.add(new AsyncGetTracks(authToken, serverUrl, playMode, browseId).execute());
            }
        }


        @Override
        public void onPlayFromSearch(final String query, final Bundle extras)
        {
            if (query.isEmpty()) {
                onSkipToNext();
                return;
            }

            // set state to "connecting" before starting network operations so clients can display hourglass or some other visual cue
            stateBuilder.setState(STATE_CONNECTING, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            // more feedback
            fakeTrackMessage(R.string.getting_search_results, "");

            if (!extras.containsKey("query")) {
                extras.putString("query", query);
            }

            // start search
            asyncTasks.add(new AsyncSearch(authToken, Amproid.getServerUrl(selectedAccount), extras).execute());
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
                // stopping playback serves as feedback to user that getting next track is in progress
                mediaPlayer.pause();
            }

            if ((playMode == PLAY_MODE_PLAYLIST) || (playMode == PLAY_MODE_ALBUM)) {
                // start next track immediately if there are more tracks in playlist
                playlistIndex++;
                if ((playlistIndex >= 0) && (playlistIndex < playlistTracks.size())) {
                    startTrack(playlistTracks.get(playlistIndex));
                    return;
                }

                // end of playlist: switch back to "shuffle"
                playMode = PLAY_MODE_RANDOM;
            }

            if (playMode == PLAY_MODE_BROWSE) {
                // browse is always one song only, so let's go back to our default mode, "shuffle"
                playMode = PLAY_MODE_RANDOM;
            }

            // set state to "connecting" before starting network operations so clients can display hourglass or some other visual cue
            stateBuilder.setState(STATE_CONNECTING, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            // start asynchronous task that gets a track from Ampache server
            asyncTasks.add(new AsyncGetTracks(authToken, Amproid.getServerUrl(selectedAccount), playMode, playlistId).execute());
        }


        @Override
        public void onSkipToPrevious()
        {
            // if user presses previous button after the first 2.5 seconds already played, then just do a rewind
            if ((mediaPlayer != null) && (mediaPlayer.getCurrentPosition() > 2500)) {
                mediaPlayer.seekTo(0);
                return;
            }

            // go back to previous track if not on the first track in playlist already
            if (((playMode == PLAY_MODE_PLAYLIST) || (playMode == PLAY_MODE_ALBUM)) && (playlistIndex > 0)) {
                playlistIndex--;
                if ((playlistIndex < playlistTracks.size()) && (mediaPlayer != null)) {
                    startTrack(playlistTracks.get(playlistIndex));
                }
            }

            // otherwise nothing to do
        }


        @Override
        public void onStop()
        {
            // playback is never stopped: it's either playing or paused
            // stop command actually means quit
            // so here we initiate quitting, playback is stopped in the service's onDestroy override

            quitting = true;

            // feedback to user
            fakeTrackMessage(R.string.quitting, "");

            // give a little time for the notification to update
            quitAttempts = 0;
            mainHandler.postDelayed(delayedQuit, 250);
        }


        @Override
        public void onSeekTo(long pos)
        {
            if ((mediaPlayer != null) && mediaPlayer.isPlaying() && (pos >= 0) && (pos <= mediaPlayer.getDuration())) {
                mediaPlayer.seekTo((int) pos);
            }
        }
    }


    // this receives callbacks from Android's Account settings, which effectively means the AmproidAuthenticator class in this project
    private final class AmproidAccountManagerCallback implements AccountManagerCallback<Bundle>
    {
        @Override
        public void run(AccountManagerFuture<Bundle> future)
        {
            // get the data
            Bundle bundle = null;
            try {
                bundle = future.getResult();
            }
            catch (Exception e) {
                // nothing to do here, bundle remains null
            }
            if (bundle == null) {
                return;
            }

            // data includes a pre-created intent, let's see what it intends
            if (bundle.containsKey(AccountManager.KEY_INTENT)) {
                Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);

                // authenticator wants to open the "add account" dialog
                // noinspection ConstantConditions - never null because it was checked with "containsKey"
                if (intent.getBooleanExtra("AmproidAuthenticatorActivity", false)) {
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                return;
            }

            // authenticator sends a token
            if (bundle.containsKey(KEY_AUTHTOKEN)) {
                // check if this is what we think it is
                if (!bundle.containsKey(KEY_ACCOUNT_TYPE) || (bundle.getString(KEY_ACCOUNT_TYPE, "").compareTo(getString(R.string.account_type)) != 0)) {
                    getNewAuthToken(getString(R.string.error_account_type_mismatch));
                    return;
                }

                // check for error
                if (bundle.containsKey(KEY_ERROR_MESSAGE)) {
                    // noinspection ConstantConditions - never null because it was checked with "containsKey"
                    getNewAuthToken(bundle.getString(KEY_ERROR_MESSAGE));
                    return;
                }

                // got the token
                authToken = bundle.getString(KEY_AUTHTOKEN);

                // check if its not empty - could be cached from previous error
                // noinspection ConstantConditions - never null because it was checked with "containsKey"
                if (authToken.isEmpty()) {
                    getNewAuthToken(getString(R.string.error_blank_token));
                    return;
                }

                // this checks if token is valid, which needs to be checked because AccountManager caches it
                asyncTasks.add(new AsyncValidateToken(authToken, Amproid.getServerUrl(selectedAccount)).execute());
            }
        }
    }


    // IPC receiver
    private final class AmproidBroacastReceiver extends BroadcastReceiver
    {
        @SuppressLint("ApplySharedPref")
        @Override
        public void onReceive(Context context, Intent intent)
        {
            // Android system broadcast - user unplugged headphone or something similar
            if ((intent.getAction() != null) && intent.getAction().equals("android.media.AUDIO_BECOMING_NOISY")) {
                mediaSessionCallback.onPause();
                return;
            }

            // broadcast from PresenceForever - Android Auto exited
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.auto_exited_broadcast_action))) {
                delayedStopSecsRemaining = 30;
                mainHandler.postDelayed(delayedStop, 1000);
            }

            // user selected account to use
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.account_selected_broadcast_action))) {
                // have to have extras
                Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }

                // check if this is what we think it is
                if (!extras.containsKey(KEY_ACCOUNT_TYPE)) {
                    return;
                }
                if (extras.getString(KEY_ACCOUNT_TYPE, "").compareTo(getString(R.string.account_type)) != 0) {
                    return;
                }

                // authenticator activity already saved user's account selection to preferences
                // so there's no need to extract the account name here
                // we can just start the authentication process all over again
                startAuth();

                return;
            }

            // asynchronous task finished
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.async_finished_broadcast_action))) {
                // restore normality from connecting/buffering state
                mediaSessionUpdateDurationPositionIfPlaying();

                // have to have the extras
                Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }
                if (!extras.containsKey(getString(R.string.async_finished_broadcast_type))) {
                    return;
                }

                // process depending on type
                int asyncType = extras.getInt(getString(R.string.async_finished_broadcast_type));

                // token validation
                if (asyncType == getResources().getInteger(R.integer.async_validate_token)) {
                    asyncProcessResultsValidateToken(extras);
                }
                // tracks
                else if (asyncType == getResources().getInteger(R.integer.async_get_tracks)) {
                    asyncProcessResultsGetTracks(extras);
                }
                // search
                else if (asyncType == getResources().getInteger(R.integer.async_search)) {
                    searchParameters = null;
                    asyncProcessResultsSearch(extras);
                }
                // search but result are already taken care of
                else if (asyncType == getResources().getInteger(R.integer.async_search_results_sent)) {
                    searchParameters = null;
                    if (mediaPlayer != null) {
                        mediaPlayer.metaNotify();
                    }
                }
                // album art downloaded
                else if (asyncType == getResources().getInteger(R.integer.async_image_downloader)) {
                    if ((mediaPlayer != null) && (extras.containsKey("url"))) {
                        boolean equal = false;
                        try {
                            URL imageURL = new URL(extras.getString("url"));
                            equal = imageURL.equals(mediaPlayer.getPictureUrl());
                        }
                        catch (Exception e) {
                            // nothing to do here
                        }
                        if (equal) {
                            Bitmap bitmap = extras.getParcelable("image");
                            if (bitmap != null) {
                                if ((notificationBuilder != null) && (notificationManager != null)) {
                                    notificationBuilder.setLargeIcon(bitmap);
                                    notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
                                }

                                metadataBuilder.putBitmap(METADATA_KEY_ART, bitmap);
                                mediaSession.setMetadata(metadataBuilder.build());
                            }
                        }
                    }
                }
                return;
            }

            // asynchronous task has no network connection
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.async_no_network_broadcast_action))) {
                // this is only to display a message
                long ms = intent.getLongExtra("elapsedMS", 0);
                fakeTrackMessage(R.string.error_no_network, String.format(Locale.US, "%s %ds", getString(R.string.error_network_wait), ms / 1000));
                return;
            }

            // main activity requests current audio effects settings
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.eq_request_broadcast_action))) {
                if (mediaPlayer == null) {
                    return;
                }
                if ((mediaPlayer.equalizer == null) || (mediaPlayer.loudnessEnhancer == null)) {
                    return;
                }

                // squeeze all data needed to correctly display equalizer settings into an intent and then send it out as a local broadcast a.k.a. an IPC message

                Equalizer.Settings equalizerSettings = mediaPlayer.equalizer.getProperties();

                int[] frequencies = new int[equalizerSettings.numBands];
                for (short i = 0; i < equalizerSettings.numBands; i++) {
                    frequencies[i] = mediaPlayer.equalizer.getCenterFreq(i);
                }

                Intent eqIntent = new Intent();
                eqIntent.putExtra(getString(R.string.eq_broadcast_key_settings), equalizerSettings.toString());
                eqIntent.putExtra(getString(R.string.eq_broadcast_key_min), mediaPlayer.equalizer.getBandLevelRange()[0]);
                eqIntent.putExtra(getString(R.string.eq_broadcast_key_max), mediaPlayer.equalizer.getBandLevelRange()[1]);
                eqIntent.putExtra(getString(R.string.eq_broadcast_key_freqs), frequencies);
                eqIntent.putExtra(getString(R.string.eq_broadcast_key_loudness_gain), Math.round(mediaPlayer.loudnessEnhancer.getTargetGain()));

                sendLocalBroadcast(R.string.eq_values_broadcast_action, eqIntent);

                return;
            }

            // main activity sends updated equalizer settings
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.eq_apply_broadcast_action))) {
                if (mediaPlayer == null) {
                    return;
                }
                if ((mediaPlayer.equalizer == null) || (mediaPlayer.loudnessEnhancer == null)) {
                    return;
                }

                // get data from incoming intent
                String equalizerSettingsString = intent.getStringExtra(getString(R.string.eq_broadcast_key_settings));
                loudnessGainSetting = intent.getIntExtra(getString(R.string.eq_broadcast_key_loudness_gain), LOUDNESS_GAIN_DEFAULT);

                if (equalizerSettingsString != null) {
                    try {
                        // convert data from string to actual settings, this throws exception if string is invalid
                        equalizerSettings = new Equalizer.Settings(equalizerSettingsString);

                        // save the settings in string format
                        SharedPreferences        preferences       = getSharedPreferences(getString(R.string.eq_preferences), Context.MODE_PRIVATE);
                        SharedPreferences.Editor preferencesEditor = preferences.edit();
                        preferencesEditor.putString(getString(R.string.eq_settings_preference), equalizerSettings.toString());
                        preferencesEditor.putInt(getString(R.string.eq_loudness_gain_preference), loudnessGainSetting);
                        preferencesEditor.commit();
                    }
                    catch (Exception e) {
                        // nothing to do here, equalizerSettings remains unchanged, null is handled in AmproidMediaPlayer's setEffects
                    }
                }

                // apply new settings
                mediaPlayer.setEffects();
            }

            // main activity requests playlist / album
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.playlist_request_broadcast_action))) {
                if (((playMode != PLAY_MODE_ALBUM) && (playMode != PLAY_MODE_PLAYLIST)) || (playlistTracks.size() < 1)) {
                    return;
                }
                Intent playlistIntent = new Intent();
                playlistIntent.putExtra(getString(R.string.playlist_broadcast_key_list), playlistTracks);
                sendLocalBroadcast(R.string.playlist_values_broadcast_action, playlistIntent);
            }

            // main activity requests skip to track in playlist / album
            if ((intent.getAction() != null) && intent.getAction().equals(getString(R.string.playlist_apply_broadcast_action))) {
                if ((playMode != PLAY_MODE_ALBUM) && (playMode != PLAY_MODE_PLAYLIST)) {
                    return;
                }

                Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }

                int index = extras.getInt(getString(R.string.playlist_broadcast_key_index), -1);
                if ((index < 0) || (index >= playlistTracks.size())) {
                    return;
                }

                playlistIndex = index;

                startTrack(playlistTracks.get(playlistIndex));
            }
        }
    }


    // simple subclass of MediaPlayer so that it also keeps mediaSession state up to date
    private final class AmproidMediaPlayer extends MediaPlayer
    {
        private static final int EQ_PRIORITY                = 9;
        private static final int SESSION_POS_UPDATE_SECONDS = 5;

        private static final int FADE_DURATION       = 6000;
        private static final int FADE_FREQUENCY      = 40;
        private static final int FADE_DIRECTION_NONE = 0;
        private static final int FADE_DIRECTION_IN   = 1;
        private static final int FADE_DIRECTION_OUT  = 2;

        Equalizer        equalizer        = null;
        LoudnessEnhancer loudnessEnhancer = null;

        private Track   track;
        private boolean sought        = false;
        private boolean erred         = false;
        private int     errorResource = R.string.error_error;

        private Timer positionTimer;

        private Timer fadeTimer     = null;
        private int   fadeDirection = FADE_DIRECTION_NONE;
        private float fadeValue     = 0;


        // constructor
        AmproidMediaPlayer(Track track, final boolean autoStart)
        {
            super();

            this.track = track;

            // lazy timer to do things related to position
            positionTimer = new Timer();
            positionTimer.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    if (!isPlaying()) {
                        return;
                    }

                    int position;
                    int duration;
                    try {
                        position = getCurrentPosition();
                        duration = getDuration();
                    }
                    catch (Exception e) {
                        return;
                    }
                    if ((position < 0) || (duration < 0)) {
                        return;
                    }

                    if ((position / 1000) % SESSION_POS_UPDATE_SECONDS == 0) {
                        mediaSessionUpdateDurationPositionIfPlaying();
                    }

                    if (AmproidMediaPlayer.this.track.isDoFade() && !sought && (fadeDirection == FADE_DIRECTION_NONE) && (position >= (duration - FADE_DURATION - 1000))) {
                        fadeDirection = FADE_DIRECTION_OUT;
                        fadeValue     = 1.0f;
                        startFade();
                    }
                }
            }, 2500, 1000);

            // set up some listeners to handle MediaPlayer events

            setOnPreparedListener(new OnPreparedListener()
            {
                @Override
                public void onPrepared(MediaPlayer mp)
                {
                    if (autoStart) {
                        // start play immediately
                        start();
                    } else {
                        // clear connecting/buffering state
                        stateBuilder.setState(STATE_STOPPED, mediaPlayer.getCurrentPosition(), 1);
                        mediaSession.setPlaybackState(stateBuilder.build());
                    }

                    // also get the art
                    asyncTasks.add(new AsyncImageDownloader(AmproidMediaPlayer.this.track.getPictureUrl()).execute());
                }
            });

            setOnCompletionListener(new OnCompletionListener()
            {
                @Override
                public void onCompletion(MediaPlayer mp)
                {
                    // make sure it actually played
                    if (getCurrentPosition() <= 0) {
                        // clear connecting/buffering state
                        stateBuilder.setState(STATE_STOPPED, mediaPlayer.getCurrentPosition(), 1);
                        mediaSession.setPlaybackState(stateBuilder.build());

                        fakeTrackMessage(R.string.error_error, getString(erred ? errorResource : R.string.error_play_error));
                        return;
                    }

                    mediaSessionCallback.onSkipToNext();
                }
            });

            setOnErrorListener(new OnErrorListener()
            {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra)
                {
                    int whatResource;
                    int extraResource;

                    switch (what) {
                        case MEDIA_ERROR_IO:
                            whatResource = R.string.media_error_io_str;
                            break;
                        case MEDIA_ERROR_MALFORMED:
                            whatResource = R.string.media_error_malformed_str;
                            break;
                        case MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                            whatResource = R.string.media_error_not_valid_for_progressive_playback_str;
                            break;
                        case MEDIA_ERROR_SERVER_DIED:
                            whatResource = R.string.media_error_server_died_str;
                            break;
                        case MEDIA_ERROR_TIMED_OUT:
                            whatResource = R.string.media_error_timed_out_str;
                            break;
                        case MEDIA_ERROR_UNSUPPORTED:
                            whatResource = R.string.media_error_unsupported_str;
                            break;
                        default:
                            whatResource = R.string.media_error_unknown_str;
                    }

                    switch (extra) {
                        case MEDIA_ERROR_IO:
                            extraResource = R.string.media_error_io_str;
                            break;
                        case MEDIA_ERROR_MALFORMED:
                            extraResource = R.string.media_error_malformed_str;
                            break;
                        case MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                            extraResource = R.string.media_error_not_valid_for_progressive_playback_str;
                            break;
                        case MEDIA_ERROR_SERVER_DIED:
                            extraResource = R.string.media_error_server_died_str;
                            break;
                        case MEDIA_ERROR_TIMED_OUT:
                            extraResource = R.string.media_error_timed_out_str;
                            break;
                        case MEDIA_ERROR_UNSUPPORTED:
                            extraResource = R.string.media_error_unsupported_str;
                            break;
                        default:
                            extraResource = R.string.media_error_unknown_str;
                    }

                    erred         = true;
                    errorResource = extraResource == R.string.media_error_unknown_str ? whatResource : extraResource;

                    return false;
                }
            });

            // initial volume
            if (track.isDoFade()) {
                synchronized (this) {
                    fadeValue     = 0.0f;
                    fadeDirection = FADE_DIRECTION_IN;
                }
            } else {
                synchronized (this) {
                    fadeValue     = 1.0f;
                    fadeDirection = FADE_DIRECTION_NONE;
                }
            }
            setVolume(fadeValue, fadeValue);

            // set data source with additional validation of the URL
            try {
                setDataSource(AmproidService.this, Uri.parse(track.getUrl().toString()));
            }
            catch (Exception e) {
                fakeTrackMessage(R.string.error_set_data_source_error, e.getMessage());

                erred         = true;
                errorResource = R.string.error_set_data_source_error;

                return;
            }

            // this will trigger the OnPreparedListener that was set up in the constructor
            prepareAsync();

            // update the notification and the media session's metadata
            metaNotify();
        }


        @Override
        public void prepareAsync()
        {
            // set state to "buffering" so clients can display some other visual cue
            stateBuilder.setState(STATE_BUFFERING, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            try {
                super.prepareAsync();
            }
            catch (Exception e) {
                fakeTrackMessage(getString(R.string.error_prepare_error), (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

                erred         = true;
                errorResource = R.string.error_prepare_error;
            }
        }


        @Override
        public void start()
        {
            if (quitting) {
                return;
            }

            // update the notification and the media session's metadata (just in case there was an error message before)
            metaNotify();

            // save play state (this is most important for playlist mode, so that next time user starts this app, playback re-starts where it left off)
            savePlayMode();

            // start playing
            try {
                super.start();
            }
            catch (Exception e) {
                fakeTrackMessage(R.string.error_play_error, (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

                erred         = true;
                errorResource = R.string.error_play_error;

                return;
            }

            // add the effects
            if ((equalizer == null) || (loudnessEnhancer == null)) {
                // create effects
                // note there are multiple attempts because developer console reported IllegalStateException on getAudioSessionId even though the Android API documentation says it can be called in any state
                int audioSessionId = 0;
                int attempts       = 0;
                while ((audioSessionId == 0) && (attempts < 5)) {
                    try {
                        audioSessionId = getAudioSessionId();
                        if (audioSessionId != 0) {
                            if (equalizer == null) {
                                equalizer = new Equalizer(EQ_PRIORITY, audioSessionId);
                            }
                            if (loudnessEnhancer == null) {
                                loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                            }
                        }
                    }
                    catch (Exception e) {
                        SystemClock.sleep(50);
                    }
                    attempts++;
                }

                // apply settings and enable effects (fade direction was already set in constructor)
                if (fadeDirection == FADE_DIRECTION_NONE) {
                    // must be done immediately to avoid changes in sound after playback has already started
                    setEffects();
                } else {
                    // must be done after a delay, otherwise fade-in will have a short but very much audible burst before sound is silenced
                    new Timer().schedule(new TimerTask()
                    {
                        @Override
                        public void run()
                        {
                            setEffects();
                        }
                    }, 80);
                }
            }

            // start with fade-in if needed
            if (fadeDirection == FADE_DIRECTION_IN) {
                startFade();
            }

            mainHandler.postDelayed(delayedMediaSessionUpdateDurationPositionIfPlaying, 250);
        }


        @Override
        public void stop()
        {
            // set correct state
            stateBuilder.setState(STATE_STOPPED, 0, 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            // stop playing
            try {
                super.stop();
            }
            catch (Exception e) {
                fakeTrackMessage(R.string.error_stop_error, (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

                erred         = true;
                errorResource = R.string.error_stop_error;
            }
        }


        @Override
        public void pause()
        {
            // set correct state
            stateBuilder.setState(STATE_PAUSED, getCurrentPosition(), 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            try {
                super.pause();
            }
            catch (Exception e) {
                fakeTrackMessage(R.string.error_pause_error, (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

                erred         = true;
                errorResource = R.string.error_pause_error;
            }
        }


        @Override
        public void seekTo(int msec)
        {
            if (quitting) {
                return;
            }

            // set correct state
            stateBuilder.setState(isPlaying() ? STATE_PLAYING : STATE_PAUSED, msec, isPlaying() ? 1 : 0);
            mediaSession.setPlaybackState(stateBuilder.build());

            try {
                super.seekTo(msec);
                sought = true;
            }
            catch (Exception e) {
                fakeTrackMessage(R.string.error_error, (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

                erred         = true;
                errorResource = R.string.error_error;
            }
        }


        @Override
        public void release()
        {
            positionTimer.cancel();
            positionTimer.purge();
            positionTimer = null;

            if (fadeTimer != null) {
                fadeTimer.cancel();
                fadeTimer.purge();
                fadeTimer = null;
            }

            if (equalizer != null) {
                equalizer.release();
                equalizer = null;
            }
            if (loudnessEnhancer != null) {
                loudnessEnhancer.release();
                loudnessEnhancer = null;
            }

            super.release();
        }


        URL getPictureUrl()
        {
            if (track == null) {
                return null;
            }
            return track.getPictureUrl();
        }


        Track getTrack()
        {
            return track;
        }


        // update notification and media browser with current track's metadata
        void metaNotify()
        {
            if (track == null) {
                return;
            }

            String title = track.getTitle();
            if ((playMode == PLAY_MODE_PLAYLIST) || (playMode == PLAY_MODE_ALBUM)) {
                title = String.format(Locale.US, "%d/%d: %s", playlistIndex + 1, playlistTracks.size(), title);
            }

            if ((notificationBuilder != null) && (notificationManager != null)) {
                notificationBuilder.setContentTitle(title);
                notificationBuilder.setContentText(track.getArtist());
                notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
            }

            metadataBuilder.putString(METADATA_KEY_TITLE, title);
            metadataBuilder.putString(METADATA_KEY_ALBUM, track.getAlbum());
            metadataBuilder.putString(METADATA_KEY_ARTIST, track.getArtist());
            metadataBuilder.putLong(METADATA_KEY_DURATION, -1);
            mediaSession.setMetadata(metadataBuilder.build());
        }


        // helper to apply effect settings
        void setEffects()
        {
            if ((equalizer != null) && (equalizerSettings != null) && (equalizerSettings.numBands == equalizer.getNumberOfBands())) {

                short minLevel = equalizer.getBandLevelRange()[0];
                short maxLevel = equalizer.getBandLevelRange()[1];

                for (int i = 0; i < equalizerSettings.numBands; i++) {
                    short level = equalizerSettings.bandLevels[i];

                    if (level < minLevel) {
                        level = minLevel;
                    }
                    if (level > maxLevel) {
                        level = maxLevel;
                    }

                    equalizer.setBandLevel((short) i, level);
                }

                equalizer.setEnabled(true);
            }

            if (loudnessEnhancer != null) {
                loudnessEnhancer.setTargetGain(loudnessGainSetting);
                loudnessEnhancer.setEnabled(true);
            }
        }


        boolean wasError()
        {
            return erred;
        }


        private void startFade()
        {
            if (track == null) {
                return;
            }

            if (fadeTimer == null) {
                fadeTimer = new Timer();

                fadeTimer.schedule(new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        if (fadeDirection == FADE_DIRECTION_NONE) {
                            return;
                        }

                        if (isPlaying()) {
                            float step = FADE_DURATION;
                            step = (fadeDirection == FADE_DIRECTION_IN ? 1f : -1f) / (step / FADE_FREQUENCY);
                            fadeValue += step;
                        }

                        setVolume(Math.max(Math.min(fadeValue, 1), 0), Math.max(Math.min(fadeValue, 1), 0));

                        if ((fadeValue > 1) || (fadeValue < 0)) {
                            fadeTimer.cancel();
                            fadeTimer.purge();
                            fadeTimer = null;

                            fadeDirection = FADE_DIRECTION_NONE;
                        }
                    }
                }, FADE_FREQUENCY, FADE_FREQUENCY);
            }
        }
    }
}
