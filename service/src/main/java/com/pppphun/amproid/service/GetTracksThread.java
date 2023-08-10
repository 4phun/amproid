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


import static com.pppphun.amproid.service.AmproidService.PLAY_MODE_ALBUM;
import static com.pppphun.amproid.service.AmproidService.PLAY_MODE_ARTIST;
import static com.pppphun.amproid.service.AmproidService.PLAY_MODE_BROWSE;
import static com.pppphun.amproid.service.AmproidService.PLAY_MODE_GENRE;
import static com.pppphun.amproid.service.AmproidService.PLAY_MODE_PLAYLIST;
import static com.pppphun.amproid.service.AmproidService.PLAY_MODE_RADIO;
import static com.pppphun.amproid.service.AmproidService.PLAY_MODE_RANDOM;
import static com.pppphun.amproid.service.AmproidService.PLAY_MODE_RANDOM_RECENT;
import static com.pppphun.amproid.shared.Amproid.ConnectionStatus.CONNECTION_NONE;
import static com.pppphun.amproid.shared.Amproid.ConnectionStatus.CONNECTION_UNKNOWN;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

import com.pppphun.amproid.shared.Amproid;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Vector;


public class GetTracksThread extends ThreadCancellable
{
    private final String authToken;
    private final String url;
    private final int    playMode;
    private final String ampacheId;
    private       String randomGenres;
    private       int    randomGenresRemaining;

    private final Handler amproidServiceHandler;


    GetTracksThread(String authToken, String url, int playMode, String ampacheId, String randomGenres, int randomGenresRemaining, Handler amproidServiceHandler)
    {
        this.authToken             = authToken;
        this.url                   = url;
        this.playMode              = playMode;
        this.ampacheId             = ampacheId;
        this.randomGenres          = randomGenres;
        this.randomGenresRemaining = randomGenresRemaining;

        this.amproidServiceHandler = amproidServiceHandler;
    }


    @Override
    public void run()
    {
        if ((authToken == null) || authToken.isEmpty()) {
            Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, R.string.error_blank_token);
            return;
        }
        if ((url == null) || url.isEmpty()) {
            Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, R.string.error_invalid_server_url);
            return;
        }

        long                     checkStart       = System.currentTimeMillis();
        Amproid.ConnectionStatus connectionStatus = Amproid.getConnectionStatus();
        while (!isCancelled() && ((connectionStatus == CONNECTION_UNKNOWN) || (connectionStatus == CONNECTION_NONE))) {
            Bundle arguments = new Bundle();
            arguments.putLong("elapsedMS", System.currentTimeMillis() - checkStart);
            Amproid.sendMessage(amproidServiceHandler, R.string.msg_async_no_network, arguments);

            SystemClock.sleep(1000);

            connectionStatus = Amproid.getConnectionStatus();
        }

        AmpacheAPICaller ampacheAPICaller = new AmpacheAPICaller(url);
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, ampacheAPICaller.getErrorMessage());
            return;
        }

        boolean       favorites     = false;
        boolean       recentlyAdded = false;
        Vector<Track> tracks        = new Vector<>();
        String        queueTitle    = null;
        boolean       multiBrowse   = false;
        if (playMode == PLAY_MODE_RANDOM) {
            if ((randomGenresRemaining > 0) && (randomGenres.length() > 0)) {
                tracks.addAll(ampacheAPICaller.getTracks(authToken, 1, randomGenres, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_GENRE));

                randomGenresRemaining--;
                if (randomGenresRemaining == 0) {
                    randomGenresRemaining--;
                    randomGenres = "";
                }
            }
            else if (randomGenresRemaining < 0) {
                Random randomizer = new Random();
                if (randomizer.nextInt(100) < 66) {
                    tracks.addAll(ampacheAPICaller.getTracks(authToken, 1, null, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_FLAGGED));
                    favorites = true;
                }
                else {
                    tracks.addAll(ampacheAPICaller.getTracks(authToken, 1, String.valueOf(Amproid.getRecentSongCount()), AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_RANDOM_RECENTLY_ADDED));
                    recentlyAdded = true;
                }

                randomGenresRemaining = 0;
            }
            else {
                tracks.addAll(ampacheAPICaller.getTracks(authToken, 1, null, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_NONE));

                if (tracks.size() > 0) {
                    Random randomizer = new Random();

                    randomGenres = "";
                    Vector<String> trackGenres = tracks.get(0).getTagsFiltered();
                    for (String trackGenre : trackGenres) {
                        try {
                            randomGenres = (randomGenres.length() > 0 ? randomGenres + (char) 255 : "") + URLEncoder.encode(trackGenre, StandardCharsets.UTF_8.toString());
                        }
                        catch (Exception ignored) {
                            // in case UTF-8 is not supported, we just don't care: worst case is all songs will be random (not genre driven)
                        }
                    }
                    randomGenresRemaining = randomizer.nextInt(3) + 1;
                }
            }
        }
        else if (playMode == PLAY_MODE_PLAYLIST) {
            tracks.addAll(ampacheAPICaller.getTracks(authToken, 0, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_PLAYLIST));
            try {
                queueTitle = ampacheAPICaller.getPlaylist(authToken, ampacheId).get(0).get("name");
            }
            catch (Exception ignored) {
            }
        }
        else if (playMode == PLAY_MODE_ARTIST) {
            tracks.addAll(ampacheAPICaller.getTracks(authToken, 7, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_ARTIST));
            try {
                queueTitle = ampacheAPICaller.getArtist(authToken, ampacheId).get(0).get("name");
            }
            catch (Exception ignored) {
            }
        }
        else if (playMode == PLAY_MODE_ALBUM) {
            tracks.addAll(ampacheAPICaller.getTracks(authToken, 0, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_ALBUM));
            try {
                queueTitle = ampacheAPICaller.getAlbum(authToken, ampacheId).get(0).get("name");
            }
            catch (Exception ignored) {
            }
        }
        else if (playMode == PLAY_MODE_BROWSE) {
            String[] trackIds = ampacheId.split(",");
            multiBrowse = (trackIds.length > 1);
            for (String trackId: trackIds) {
                tracks.addAll(ampacheAPICaller.getTracks(authToken, 1, trackId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_SONG));
                if (isCancelled() || !ampacheAPICaller.getErrorMessage().isEmpty()) {
                    break;
                }
            }
        }
        else if (playMode == PLAY_MODE_GENRE) {
            tracks.addAll(ampacheAPICaller.getTracks(authToken, 7, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_GENRE));

            String[] tags = ampacheId.split(String.valueOf((char) 255));
            for (int i = 0; i < tags.length; i++) {
                tags[i] = tags[i].replaceAll("\\+", " ");
            }
            queueTitle = String.join(", ", tags);
        }
        else if (playMode == PLAY_MODE_RANDOM_RECENT) {
            // note here id should contain the number of recent tracks to select from (how far to go back)
            tracks.addAll(ampacheAPICaller.getTracks(authToken, 7, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_RANDOM_RECENTLY_ADDED));
            queueTitle = "Shuffle new";
        }
        else if (playMode == PLAY_MODE_RADIO) {
            tracks.addAll(ampacheAPICaller.getTracks(authToken, 1, ampacheId, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_RADIO));
        }

        if (isCancelled()) {
            return;
        }

        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, ampacheAPICaller.getErrorMessage());
            return;
        }

        Bundle arguments = new Bundle();
        arguments.putSerializable("tracks", tracks);
        arguments.putString("ampacheId", ampacheId);
        if (playMode == PLAY_MODE_RANDOM) {
            arguments.putString("randomGenres", randomGenres);
            arguments.putInt("randomGenresRemaining", randomGenresRemaining);
            arguments.putBoolean("favorites", favorites);
            arguments.putBoolean("recentlyAdded", recentlyAdded);
        }
        if ((queueTitle != null) && (queueTitle.length() > 0)) {
            arguments.putString("queueTitle", queueTitle);
        }
        if (multiBrowse) {
            arguments.putBoolean("multiBrowse", true);
        }
        Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, arguments);
    }
}
