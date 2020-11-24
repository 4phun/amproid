/*
 * This file is part of Amproid
 *
 * Copyright (c) 2020. Peter Papp
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
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.media.MediaBrowserServiceCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import static com.pppphun.amproid.Amproid.ConnectionStatus.CONNECTION_NONE;
import static com.pppphun.amproid.Amproid.ConnectionStatus.CONNECTION_UNKNOWN;
import static com.pppphun.amproid.AmproidService.PREFIX_ALBUM;
import static com.pppphun.amproid.AmproidService.PREFIX_SONG;


class AsyncSearch extends AsyncTask<Void, Void, HashMap<Integer, Vector<HashMap<String, String>>>>
{
    private final String authToken;
    private final String url;
    private final Bundle searchParameters;

    private final MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend;

    private String errorMessage = "";


    AsyncSearch(String authToken, String url, Bundle searchParameters, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend)
    {
        this.authToken        = authToken;
        this.url              = url;
        this.searchParameters = searchParameters;
        this.resultToSend     = resultToSend;
    }


    AsyncSearch(String authToken, String url, Bundle searchParameters)
    {
        this.authToken        = authToken;
        this.url              = url;
        this.searchParameters = searchParameters;

        resultToSend = null;
    }


    @SuppressLint("UseSparseArrays")
    @Override
    protected HashMap<Integer, Vector<HashMap<String, String>>> doInBackground(Void... voids)
    {
        // just to be on the safe side
        if ((authToken == null) || authToken.isEmpty()) {
            errorMessage = Amproid.getAppContext().getString(R.string.error_blank_token);
            return new HashMap<>();
        }
        if ((url == null) || url.isEmpty()) {
            errorMessage = Amproid.getAppContext().getString(R.string.error_invalid_server_url);
            return new HashMap<>();
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
            return new HashMap<>();
        }

        // execute search
        HashMap<Integer, Vector<HashMap<String, String>>> results = ampacheAPICaller.search(authToken, searchParameters);
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            errorMessage = ampacheAPICaller.getErrorMessage();
            return new HashMap<>();
        }

        return results;
    }


    @Override
    protected void onPostExecute(HashMap<Integer, Vector<HashMap<String, String>>> found)
    {
        // called from onSearch
        if (resultToSend != null) {
            ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

            if (!errorMessage.isEmpty()) {
                // add another link to "home" with the error message
                results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(Amproid.getAppContext().getString(R.string.item_root_id)).setTitle(errorMessage).build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
            }

            // this is to filter out duplicates
            Vector<String> addedIds = new Vector<>();

            // add all found items
            int[] processOrder = new int[]{AmpacheAPICaller.SEARCH_RESULTS_SONGS, AmpacheAPICaller.SEARCH_RESULTS_ALBUMS, AmpacheAPICaller.SEARCH_RESULTS_ARTIST_ALBUMS};
            int   defective    = 0;
            for (int type : processOrder) {
                Vector<HashMap<String, String>> items = found.get(type);
                if (items == null) {
                    items = new Vector<>();
                }

                for (HashMap<String, String> item : items) {
                    // it's not the same for songs and albums
                    if (item.containsKey("name") && !item.containsKey("title")) {
                        item.put("title", item.get("name"));
                        item.remove("name");
                    }

                    // just to be on the safe side
                    if (!item.containsKey("id") || !item.containsKey("title")) {
                        defective++;
                        continue;
                    }

                    String idPrefix    = PREFIX_SONG;
                    String titleSuffix = "";
                    if ((type == AmpacheAPICaller.SEARCH_RESULTS_ALBUMS) || (type == AmpacheAPICaller.SEARCH_RESULTS_ARTIST_ALBUMS)) {
                        idPrefix    = PREFIX_ALBUM;
                        titleSuffix = " " + Amproid.getAppContext().getString(R.string.album_suffix);
                    }

                    String id = idPrefix + item.get("id");
                    if (addedIds.contains(id)) {
                        continue;
                    }

                    results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(id).setTitle(item.get("title") + titleSuffix).build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
                    addedIds.add(id);
                }
            }

            // sort them nice 'n' neat
            results.sort(new Comparator<MediaBrowserCompat.MediaItem>()
            {
                @Override
                public int compare(MediaBrowserCompat.MediaItem o1, MediaBrowserCompat.MediaItem o2)
                {
                    return o1.getDescription().getTitle().toString().compareToIgnoreCase(o2.getDescription().getTitle().toString());
                }
            });

            // add root a.k.a. link to "home" as the first element
            results.add(0, new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(Amproid.getAppContext().getString(R.string.item_root_id)).setTitle(Amproid.getAppContext().getString(R.string.item_root_desc)).build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

            // let everyone know if there was a problem
            if (defective > 0) {
                // yet another link to home
                results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder().setMediaId(Amproid.getAppContext().getString(R.string.item_root_id)).setTitle(String.format(Amproid.getAppContext().getString(R.string.error_defective_albums), defective)).build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
            }

            // send it back to the media browser service
            resultToSend.sendResult(results);

            // also let the service know so temp message can be dismissed
            final Intent intent = new Intent();
            intent.putExtra(Amproid.getAppContext().getString(R.string.async_finished_broadcast_type), Amproid.getAppContext().getResources().getInteger(R.integer.async_search_results_sent));
            Amproid.sendLocalBroadcast(R.string.async_finished_broadcast_action, intent);

            return;
        }

        // called from onPlayFromSearch, send IPC with results to our service

        final Intent intent = new Intent();
        intent.putExtra(Amproid.getAppContext().getString(R.string.async_finished_broadcast_type), Amproid.getAppContext().getResources().getInteger(R.integer.async_search));
        intent.putExtra("found", found);
        intent.putExtra("searchParameters", searchParameters);
        intent.putExtra("errorMessage", errorMessage);
        Amproid.sendLocalBroadcast(R.string.async_finished_broadcast_action, intent);
    }
}
