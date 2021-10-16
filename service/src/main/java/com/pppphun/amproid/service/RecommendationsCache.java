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
import static com.pppphun.amproid.service.AmproidService.PREFIX_ARTIST;
import static com.pppphun.amproid.service.AmproidService.PREFIX_PLAYLIST;

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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;


public class RecommendationsCache
{
    private final Context context = Amproid.getAppContext();

    private final String  authToken;
    private final String  url;
    private final Handler amproidServiceHandler;

    private Vector<HashMap<String, String>> artists   = new Vector<>();
    private Vector<HashMap<String, String>> albums    = new Vector<>();
    private Vector<HashMap<String, String>> playlists = new Vector<>();

    private boolean valid        = false;
    private boolean sendValidMsg = false;

    private GetRecommendationsThread                                             getRecommendationsThread = null;
    private MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend             = null;

    private final Handler recommendationsCacheHandler = new Handler(Looper.getMainLooper())
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
                    if (asyncType == context.getResources().getInteger(R.integer.async_get_recommendations)) {
                        synchronized (this) {
                            getRecommendationsThread = null;
                        }

                        String errorMessage = arguments.getString("errorMessage", "");
                        if (errorMessage.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            Vector<HashMap<String, String>> artists = (Vector<HashMap<String, String>>) arguments.getSerializable("artists");
                            @SuppressWarnings("unchecked")
                            Vector<HashMap<String, String>> albums = (Vector<HashMap<String, String>>) arguments.getSerializable("albums");
                            @SuppressWarnings("unchecked")
                            Vector<HashMap<String, String>> playlists = (Vector<HashMap<String, String>>) arguments.getSerializable("playlists");
                            if ((artists != null) && (albums != null) && (playlists != null)) {
                                synchronized (this) {
                                    RecommendationsCache.this.artists   = artists;
                                    RecommendationsCache.this.albums    = albums;
                                    RecommendationsCache.this.playlists = playlists;
                                    valid                               = true;
                                }
                                sendResultToSend();
                                if ((amproidServiceHandler != null) && sendValidMsg) {
                                    sendValidMsg = false;
                                    Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.recommendations_now_valid, (Bundle) null);
                                }
                            }
                        }
                    }
                }
            }
        }
    };


    public RecommendationsCache(String authToken, String url, Handler amproidServiceHandler)
    {
        this.authToken             = authToken;
        this.url                   = url;
        this.amproidServiceHandler = amproidServiceHandler;

        downloadRecommendations();
    }


    public void getRecommendations(MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend)
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

        downloadRecommendations();
    }


    public boolean isValid()
    {
        boolean returnValue;
        synchronized (this) {
            returnValue = valid;
        }
        return returnValue;
    }


    public void refreshRecommendations()
    {
        sendValidMsg = true;
        downloadRecommendations();
    }


    private void downloadRecommendations()
    {
        synchronized (this) {
            if (getRecommendationsThread == null) {
                getRecommendationsThread = new GetRecommendationsThread(authToken, url, recommendationsCacheHandler);
                getRecommendationsThread.start();
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

        Vector<HashMap<String, String>> artists;
        Vector<HashMap<String, String>> albums;
        Vector<HashMap<String, String>> playlists;
        synchronized (this) {
            artists   = Amproid.deepCopyItems(this.artists);
            albums    = Amproid.deepCopyItems(this.albums);
            playlists = Amproid.deepCopyItems(this.playlists);
        }

        ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

        for (HashMap<String, String> artist : artists) {
            if (!artist.containsKey("id") || !artist.containsKey("name")) {
                continue;
            }
            results.add(
                    new MediaBrowserCompat.MediaItem(
                            new MediaDescriptionCompat.Builder()
                                    .setMediaId(PREFIX_ARTIST + artist.get("id"))
                                    .setTitle(artist.get("name"))
                                    .setSubtitle(Amproid.getAppContext().getString(R.string.subtitle_artist))
                                    .setIconUri(Uri.parse(artist.get("art")))
                                    .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
            );
        }
        for (HashMap<String, String> album : albums) {
            if (!album.containsKey("id") || !album.containsKey("name")) {
                continue;
            }
            results.add(
                    new MediaBrowserCompat.MediaItem(
                            new MediaDescriptionCompat.Builder()
                                    .setMediaId(PREFIX_ALBUM + album.get("id"))
                                    .setTitle(album.get("name"))
                                    .setSubtitle(Amproid.getAppContext().getString(R.string.subtitle_album))
                                    .setIconUri(Uri.parse(album.get("art")))
                                    .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
            );
        }
        for (HashMap<String, String> playlist : playlists) {
            if (!playlist.containsKey("id") || !playlist.containsKey("name")) {
                continue;
            }
            results.add(
                    new MediaBrowserCompat.MediaItem(
                            new MediaDescriptionCompat.Builder()
                                    .setMediaId(PREFIX_PLAYLIST + playlist.get("id"))
                                    .setTitle(playlist.get("name"))
                                    .setSubtitle(Amproid.getAppContext().getString(R.string.subtitle_playlist))
                                    .build(),
                            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
                    )
            );
        }

        results.sort(new Comparator<MediaBrowserCompat.MediaItem>()
        {
            @Override
            public int compare(MediaBrowserCompat.MediaItem o1, MediaBrowserCompat.MediaItem o2)
            {
                int result = 0;
                try {
                    result = o1.getDescription().getTitle().toString().compareTo(o2.getDescription().getTitle().toString());
                }
                catch (Exception ignored) {
                }
                return result;
            }
        });

        resultToSend.sendResult(results);

        synchronized (this) {
            resultToSend = null;
        }
    }
}
