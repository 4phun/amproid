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


import static com.pppphun.amproid.shared.Amproid.ConnectionStatus.CONNECTION_NONE;
import static com.pppphun.amproid.shared.Amproid.ConnectionStatus.CONNECTION_UNKNOWN;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

import com.pppphun.amproid.shared.Amproid;

import java.util.HashMap;
import java.util.Vector;


class SearchThread extends ThreadCancellable
{
    private final String authToken;
    private final String url;
    private final Bundle searchParameters;

    private final Handler resultsHandler;


    SearchThread(String authToken, String url, Bundle searchParameters, Handler resultsHandler)
    {
        this.authToken        = authToken;
        this.url              = url;
        this.searchParameters = searchParameters;
        this.resultsHandler   = resultsHandler;
    }


    @Override
    public void run()
    {
        if ((authToken == null) || authToken.isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, R.string.error_blank_token);
            return;
        }
        if ((url == null) || url.isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, R.string.error_invalid_server_url);
            return;
        }

        long                     checkStart       = System.currentTimeMillis();
        Amproid.ConnectionStatus connectionStatus = Amproid.getConnectionStatus();
        while (!isCancelled() && ((connectionStatus == CONNECTION_UNKNOWN) || (connectionStatus == CONNECTION_NONE))) {
            Bundle arguments = new Bundle();
            arguments.putLong("elapsedMS", System.currentTimeMillis() - checkStart);
            Amproid.sendMessage(resultsHandler, R.string.async_no_network_broadcast_action, arguments);

            SystemClock.sleep(1000);

            connectionStatus = Amproid.getConnectionStatus();
        }

        AmpacheAPICaller ampacheAPICaller = new AmpacheAPICaller(url);
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, ampacheAPICaller.getErrorMessage());
            return;
        }

        HashMap<Integer, Vector<HashMap<String, String>>> found = ampacheAPICaller.search(authToken, searchParameters);
        if (isCancelled()) {
            return;
        }
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, ampacheAPICaller.getErrorMessage());
            return;
        }

        Vector<HashMap<String, String>> artists = found.get(AmpacheAPICaller.SEARCH_RESULTS_ARTISTS);
        if ((artists != null) && (artists.size() > 0)) {
            Vector<HashMap<String, String>> matchingPlaylists = new Vector<>();

            PlaylistsCache                  playlistsCache  = new PlaylistsCache(authToken, url, null);
            Vector<HashMap<String, String>> cachedPlaylists = playlistsCache.getPlaylists();

            for (HashMap<String, String> artist : artists) {
                for (HashMap<String, String> playlist : cachedPlaylists) {
                    if (Amproid.stringContains(artist.get("name"), playlist.get("name"))) {
                        matchingPlaylists.add(playlist);
                    }
                }
            }

            if (matchingPlaylists.size() > 0) {
                found.put(AmpacheAPICaller.SEARCH_RESULTS_PLAYLISTS, matchingPlaylists);
            }
        }

        Bundle arguments = new Bundle();
        arguments.putSerializable("found", found);
        arguments.putBundle("searchParameters", searchParameters);
        Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_search, arguments);
    }
}
