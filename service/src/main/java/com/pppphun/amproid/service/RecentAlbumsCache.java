/*
 * This file is part of Amproid
 *
 * Copyright (c) 2023. Peter Papp
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
import static com.pppphun.amproid.shared.Amproid.NEW_TOKEN_REASON_CACHE;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;

import androidx.annotation.NonNull;
import androidx.media.MediaBrowserServiceCompat;

import com.pppphun.amproid.shared.Amproid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;


public class RecentAlbumsCache
{
    private final Context context = Amproid.getAppContext();

    private       String  authToken;
    private final String  url;
    private final Handler amproidServiceHandler;

    private Vector<HashMap<String, String>> albums = new Vector<>();

    private boolean valid        = false;
    private boolean sendValidMsg = false;

    private GetRecentAlbumsThread                                                getRecentAlbumsThread = null;
    private MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend          = null;

    private final Handler recentlyAddedCacheHandler = new Handler(Looper.getMainLooper())
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
                if (!arguments.containsKey(context.getString(R.string.msg_action))) {
                    return;
                }

                String action = arguments.getString(context.getString(R.string.msg_action));
                if (action.equals(context.getString(R.string.msg_action_async_finished))) {
                    int asyncType = arguments.getInt(context.getString(R.string.msg_async_finished_type));
                    if (asyncType == context.getResources().getInteger(R.integer.async_get_recent_albums)) {
                        synchronized (this) {
                            getRecentAlbumsThread = null;
                        }

                        String errorMessage = arguments.getString(Amproid.getAppContext().getString(R.string.msg_error_message), "");
                        if (!errorMessage.isEmpty()) {
                            if (amproidServiceHandler != null) {
                                Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.recent_albums_now_valid, errorMessage, NEW_TOKEN_REASON_CACHE);
                            }
                            return;
                        }

                        @SuppressWarnings("unchecked")
                        Vector<HashMap<String, String>> recentAlbums = (Vector<HashMap<String, String>>) arguments.getSerializable("recentAlbums");
                        if (recentAlbums != null) {
                            synchronized (this) {
                                albums = recentAlbums;
                                valid  = true;
                            }
                            sendResultToSend();
                            if ((amproidServiceHandler != null) && sendValidMsg) {
                                sendValidMsg = false;
                                Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.recent_albums_now_valid, (Bundle) null);
                            }
                        }
                    }
                }
            }
        }
    };


    public RecentAlbumsCache(String authToken, String url, Handler amproidServiceHandler)
    {
        this.authToken             = authToken;
        this.url                   = url;
        this.amproidServiceHandler = amproidServiceHandler;

        downloadRecentlyAdded();
    }


    public void setAuthToken(String authToken)
    {
        this.authToken = authToken;
    }


    public void getRecentlyAdded(MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend)
    {
        boolean proceed;
        synchronized (this) {
            proceed = (this.resultToSend == null);
        }
        if (!proceed) {
            return;
        }

        synchronized (this) {
            this.resultToSend = resultToSend;
        }

        if (isValid()) {
            sendResultToSend();
            return;
        }

        downloadRecentlyAdded();
    }


    public boolean isValid()
    {
        boolean returnValue;
        synchronized (this) {
            returnValue = valid;
        }
        return returnValue;
    }


    public void refreshRecentlyAdded()
    {
        sendValidMsg = true;
        downloadRecentlyAdded();
    }


    private void downloadRecentlyAdded()
    {
        synchronized (this) {
            if (getRecentAlbumsThread == null) {
                getRecentAlbumsThread = new GetRecentAlbumsThread(authToken, url, recentlyAddedCacheHandler);
                getRecentAlbumsThread.start();
            }
        }
    }


    private void sendResultToSend()
    {
        boolean proceed;
        synchronized (this) {
            proceed = (resultToSend != null);
        }
        if (!proceed) {
            return;
        }

        Vector<HashMap<String, String>> recentAlbums;
        synchronized (this) {
            recentAlbums = Amproid.deepCopyItems(this.albums);
        }

        ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

        for (HashMap<String, String> album : recentAlbums) {
            if (!album.containsKey("id") || !album.containsKey("name")) {
                continue;
            }
            results.add(
                    new MediaBrowserCompat.MediaItem(
                            new MediaDescriptionCompat.Builder()
                                    .setMediaId(PREFIX_ALBUM + album.get("id"))
                                    .setTitle(album.get("name"))
                                    .setIconUri(Uri.parse(album.get("art")))
                                    .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
            );
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

        synchronized (this) {
            resultToSend = null;
        }
    }

}
