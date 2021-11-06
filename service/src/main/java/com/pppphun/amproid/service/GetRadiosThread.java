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


import static com.pppphun.amproid.service.AmproidService.PREFIX_RADIO;
import static com.pppphun.amproid.shared.Amproid.ConnectionStatus.CONNECTION_NONE;
import static com.pppphun.amproid.shared.Amproid.ConnectionStatus.CONNECTION_UNKNOWN;

import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.media.MediaBrowserServiceCompat;

import com.pppphun.amproid.shared.Amproid;

import org.apache.commons.text.StringEscapeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;


public class GetRadiosThread extends ThreadCancellable
{
    private final String authToken;
    private final String url;

    private final Handler                                                              amproidServiceHandler;
    private final MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend;


    GetRadiosThread(String authToken, String url, Handler amproidServiceHandler, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend)
    {
        this.authToken             = authToken;
        this.url                   = url;
        this.amproidServiceHandler = amproidServiceHandler;
        this.resultToSend          = resultToSend;
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

        Vector<HashMap<String, String>> radios = ampacheAPICaller.getLiveStreams(authToken);
        if (isCancelled()) {
            return;
        }
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, ampacheAPICaller.getErrorMessage());
            return;
        }

        if (resultToSend != null) {
            ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

            for (HashMap<String, String> radio : radios) {
                if (!radio.containsKey("url") || !radio.containsKey("name")) {
                    continue;
                }

                String name = StringEscapeUtils.unescapeHtml4(radio.get("name"));
                String url  = radio.get("url");

                results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                        .setMediaId(PREFIX_RADIO + url)
                        .setTitle(name)
                        .build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            }

            resultToSend.sendResult(results);
            return;
        }

        if (isCancelled()) {
            return;
        }

        Bundle arguments = new Bundle();
        arguments.putSerializable("radios", radios);
        Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.async_get_radios, arguments);
    }
}
