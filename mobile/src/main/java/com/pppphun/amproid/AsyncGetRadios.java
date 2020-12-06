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

import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import static com.pppphun.amproid.Amproid.ConnectionStatus.CONNECTION_NONE;
import static com.pppphun.amproid.Amproid.ConnectionStatus.CONNECTION_UNKNOWN;
import static com.pppphun.amproid.AmproidService.PREFIX_RADIO;


public class AsyncGetRadios extends AsyncTask<Void, Void, Vector<HashMap<String, String>>>
{
    private final String authToken;
    private final String url;

    private final MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend;

    private String errorMessage = "";


    AsyncGetRadios(String authToken, String url, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend)
    {
        this.authToken    = authToken;
        this.url          = url;
        this.resultToSend = resultToSend;
    }


    @Override
    protected final Vector<HashMap<String, String>> doInBackground(Void... params)
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

        // get the radio stations
        Vector<HashMap<String, String>> radios = ampacheAPICaller.getLiveStreams(authToken);
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            errorMessage = ampacheAPICaller.getErrorMessage();
            return new Vector<>();
        }

        return radios;
    }


    @Override
    protected void onPostExecute(Vector<HashMap<String, String>> radios)
    {
        ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

        // @formatter:off

        // add root a.k.a. link to "home" as the first element
        results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                .setMediaId(Amproid.getAppContext().getString(R.string.item_root_id))
                .setTitle(Amproid.getAppContext().getString(R.string.item_root_desc))
                .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));

        if (!errorMessage.isEmpty()) {
            // add another link to "home" with the error message
            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                    .setMediaId(Amproid.getAppContext().getString(R.string.item_root_id))
                    .setTitle(errorMessage)
                    .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        }

        // @formatter:on

        // add all resulting radio stations
        int defective = 0;
        for (HashMap<String, String> radio : radios) {
            // just to be on the safe side
            if (!radio.containsKey("url") || !radio.containsKey("name")) {
                defective++;
                continue;
            }

            String name =  StringEscapeUtils.unescapeHtml4(radio.get("name"));
            String url  = radio.get("url");

            // @formatter:off
            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                    .setMediaId(PREFIX_RADIO + url)
                    .setTitle(name)
                    .build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            // @formatter:on
        }

        // let everyone know if there was a problem
        if (defective > 0) {
            // yet another link to home
            // @formatter:off
            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                    .setMediaId(Amproid.getAppContext().getString(R.string.item_root_id))
                    .setTitle(String.format(Amproid.getAppContext().getString(R.string.error_defective_radios), defective))
                    .build(), MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
            // @formatter:on
        }

        // send it back to the media browser service
        resultToSend.sendResult(results);

        // also send it to AmproidService
        final Intent intent = new Intent();
        intent.putExtra(Amproid.getAppContext().getString(R.string.async_finished_broadcast_type), Amproid.getAppContext().getResources().getInteger(R.integer.async_get_radios));
        intent.putExtra("radios", radios);
        intent.putExtra("errorMessage", errorMessage);
        Amproid.sendLocalBroadcast(R.string.async_finished_broadcast_action, intent);
    }
}
