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

import android.content.Intent;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static com.pppphun.amproid.Amproid.ConnectionStatus.CONNECTION_NONE;
import static com.pppphun.amproid.Amproid.ConnectionStatus.CONNECTION_UNKNOWN;
import static com.pppphun.amproid.AmproidService.PLAY_MODE_ALBUM;
import static com.pppphun.amproid.AmproidService.PLAY_MODE_BROWSE;
import static com.pppphun.amproid.AmproidService.PLAY_MODE_PLAYLIST;
import static com.pppphun.amproid.AmproidService.PLAY_MODE_RANDOM;
import static com.pppphun.amproid.AmproidService.PREFIX_ARTIST;
import static com.pppphun.amproid.AmproidService.PREFIX_SONG;


public class AsyncGetTracks extends AsyncTask<Void, Void, Vector<Track>>
{
    private String authToken;
    private String url;
    private int    playMode;
    private String ampacheId;
    private String artistAmpacheId;

    private MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend;

    private String errorMessage = "";


    AsyncGetTracks(String authToken, String url, int playMode, String ampacheId, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend, String artistAmpacheId)
    {
        this.authToken       = authToken;
        this.url             = url;
        this.playMode        = playMode;
        this.ampacheId       = ampacheId;
        this.resultToSend    = resultToSend;
        this.artistAmpacheId = artistAmpacheId;
    }


    AsyncGetTracks(String authToken, String url, int playMode, String ampacheId)
    {
        this.authToken = authToken;
        this.url       = url;
        this.ampacheId = ampacheId;
        this.playMode  = playMode;

        // resultToSend passed in only if this task is started from onLoadChildren (and there only to get songs for selected album)
        resultToSend    = null;
        artistAmpacheId = null;
    }


    @Override
    protected final Vector<Track> doInBackground(Void... params)
    {
        // just to be on the safe side
        if ((authToken == null) || authToken.isEmpty()) {
            errorMessage = Amproid.getAppContext().getString(R.string.error_blank_token);
            return new Vector<>();
        }
        if ((url == null) || url.isEmpty()) {
            errorMessage = Amproid.getAppContext().getString(R.string.error_invalid_server_url);
            return new Vector<>();
        }

        // can't do anything without network connection
        long                     checkStart       = System.currentTimeMillis();
        Amproid.ConnectionStatus connectionStatus = Amproid.getConnectionStatus();
        while (!isCancelled() && ((connectionStatus == CONNECTION_UNKNOWN) || (connectionStatus == CONNECTION_NONE))) {
            Intent intent = new Intent();
            intent.putExtra("elapsedMS", System.currentTimeMillis() - checkStart);
            Amproid.sendLocalBroadcast(R.string.async_no_network_broadcast_action, intent);

            SystemClock.sleep(1000);

            connectionStatus = Amproid.getConnectionStatus();
        }

        // instantiate Ampache API interface - this handles network operations and XML parsing
        AmpacheAPICaller ampacheAPICaller = new AmpacheAPICaller(url);
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            errorMessage = ampacheAPICaller.getErrorMessage();
            return new Vector<>();
        }

        // get track(s) according to current play mode
        Vector<Track> tracks = new Vector<>();
        if (resultToSend == null) {
            if (playMode == PLAY_MODE_RANDOM) {
                // get one track to be played as next in "shuffle"
                tracks.addAll(ampacheAPICaller.getTracks(authToken, 1, null, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_NONE));
            }
            else if (playMode == PLAY_MODE_PLAYLIST) {
                // get all tracks in a playlist, 0 count means unlimited
                tracks.addAll(ampacheAPICaller.getTracks(authToken, 0, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_PLAYLIST));
            }
            else if (playMode == PLAY_MODE_BROWSE) {
                // get one track that the user selected in browse
                tracks.addAll(ampacheAPICaller.getTracks(authToken, 1, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_SONG));
            }
            else if (playMode == PLAY_MODE_ALBUM) {
                // get all tracks of album, 0 count means unlimited
                tracks.addAll(ampacheAPICaller.getTracks(authToken, 0, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_ALBUM));
            }
        }
        else {
            // this is for onLoadChildren, get tracks of album, 0 count means unlimited
            tracks.addAll(ampacheAPICaller.getTracks(authToken, 0, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_ALBUM));
        }

        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            errorMessage = ampacheAPICaller.getErrorMessage();
            return new Vector<>();
        }

        return tracks;
    }


    @Override
    protected void onPostExecute(Vector<Track> tracks)
    {
        // called from onLoadChildren
        if (resultToSend != null) {
            ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

            // @formatter:off

            // add root a.k.a. link to "home" as the first element
            results.add(
                    new MediaBrowserCompat.MediaItem(
                            new MediaDescriptionCompat.Builder()
                                    .setMediaId(Amproid.getAppContext().getString(R.string.item_root_id))
                                    .setTitle(Amproid.getAppContext().getString(R.string.item_root_desc))
                                    .build(),
                            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                    )
            );

            if (!errorMessage.isEmpty()) {
                // add another link to "home" with the error message
                results.add(
                        new MediaBrowserCompat.MediaItem(
                                new MediaDescriptionCompat.Builder()
                                        .setMediaId(Amproid.getAppContext().getString(R.string.item_root_id))
                                        .setTitle(errorMessage).build(),
                                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                        )
                );
            }

            // add "up one level"
            if ((artistAmpacheId != null) && !artistAmpacheId.isEmpty()) {
            results.add(
                    new MediaBrowserCompat.MediaItem(
                            new MediaDescriptionCompat.Builder()
                                    .setMediaId(PREFIX_ARTIST + artistAmpacheId)
                                    .setTitle(Amproid.getAppContext().getString(R.string.up_one_level_songs))
                                    .build(),
                            MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                    )
            );
            }

            // @formatter:on

            // add all resulting tracks
            int defective = 0;
            for(Track track : tracks) {
                // just to be on the safe side
                if (track.isInvalid()) {
                    defective++;
                    continue;
                }

                // @formatter:off
                results.add(
                        new MediaBrowserCompat.MediaItem(
                                new MediaDescriptionCompat.Builder()
                                        .setMediaId(PREFIX_SONG + track.getId())
                                        .setTitle(track.getTitle())
                                        .build(),
                                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                        )
                );
                // @formatter:on
            }

            // let everyone know if there was a problem
            if (defective > 0) {
                // yet another link to home
                // @formatter:off
                results.add(
                        new MediaBrowserCompat.MediaItem(
                                new MediaDescriptionCompat.Builder()
                                        .setMediaId(Amproid.getAppContext().getString(R.string.item_root_id))
                                        .setTitle(String.format(Amproid.getAppContext().getString(R.string.error_defective_tracks), defective))
                                        .build(),
                                MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
                        )
                );
                // @formatter:on
            }

            // send it back to the media browser service and we're done
            resultToSend.sendResult(results);

            // for AmproidService to update position
            Amproid.sendLocalBroadcast(R.string.async_finished_broadcast_action, null);

            return;
        }

        // not called from onLoadChildren, send IPC with results to our service

        final Intent intent = new Intent();
        intent.putExtra(Amproid.getAppContext().getString(R.string.async_finished_broadcast_type), Amproid.getAppContext().getResources().getInteger(R.integer.async_get_tracks));
        intent.putExtra("tracks", tracks);
        intent.putExtra("errorMessage", errorMessage);
        Amproid.sendLocalBroadcast(R.string.async_finished_broadcast_action, intent);
    }
}
