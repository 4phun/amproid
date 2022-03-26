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


import static com.pppphun.amproid.service.AmproidService.PREFIX_ALBUM;
import static com.pppphun.amproid.shared.Amproid.ConnectionStatus.CONNECTION_NONE;
import static com.pppphun.amproid.shared.Amproid.ConnectionStatus.CONNECTION_UNKNOWN;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.media.MediaBrowserServiceCompat;

import com.pppphun.amproid.shared.Amproid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;


public class GetRecentAlbumsThread extends ThreadCancellable
{
    private final String authToken;
    private final String url;

    private final Handler                                                              amproidServiceHandler;
    private final MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend;


    GetRecentAlbumsThread(String authToken, String url, Handler amproidServiceHandler, MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend)
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

        Vector<HashMap<String, String>> recentAlbums = ampacheAPICaller.getRecentAlbums(authToken);
        if (isCancelled()) {
            return;
        }
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.async_get_tracks, ampacheAPICaller.getErrorMessage());
            return;
        }

        recentAlbums.sort(new Comparator<HashMap<String, String>>()
        {
            @Override
            public int compare(HashMap<String, String> o1, HashMap<String, String> o2)
            {
                String name1 = o1.get("name");
                String name2 = o2.get("name");

                if (name1 == null) {
                    name1 = "A";
                }
                if (name2 == null) {
                    name2 = "A";
                }

                return name1.compareTo(name2);
            }
        });

        if (isCancelled()) {
            return;
        }

        if (resultToSend != null) {
            ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

            for (HashMap<String, String> recentAlbum : recentAlbums) {
                if (!recentAlbum.containsKey("id") || !recentAlbum.containsKey("name")) {
                    continue;
                }

                MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                        .setMediaId(PREFIX_ALBUM + recentAlbum.get("id"))
                        .setTitle(recentAlbum.get("name"));

                String art = recentAlbum.get("art");
                if ((art != null) && !art.isEmpty()) {
                    builder.setIconUri(Uri.parse(art));
                }

                results.add(new MediaBrowserCompat.MediaItem(builder.build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
            }

            results.add(0,
                    new MediaBrowserCompat.MediaItem(
                            new MediaDescriptionCompat.Builder()
                                    .setMediaId(Amproid.getAppContext().getString(R.string.item_random_recent_id))
                                    .setTitle(Amproid.getAppContext().getString(R.string.item_random_recent_desc))
                                    .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
            );

            resultToSend.sendResult(results);
        }

        int totalSongCount = 0;
        for (HashMap<String, String> recentAlbum : recentAlbums) {
            if (!recentAlbum.containsKey("songcount")) {
                continue;
            }

            int songCount = 0;
            try {
                songCount = Integer.parseInt(recentAlbum.get("songcount"));
            }
            catch (Exception ignored) {
            }

            totalSongCount += songCount;
        }

        if (totalSongCount > 0) {
            Bundle arguments = new Bundle();
            arguments.putSerializable("recentSongCount", totalSongCount);
            Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.async_get_recent_albums, arguments);
        }
    }
}
