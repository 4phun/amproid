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


import static com.pppphun.amproid.service.AmproidService.PREFIX_PLAYLIST;
import static com.pppphun.amproid.shared.Amproid.NEW_TOKEN_REASON_CACHE;

import android.content.Context;
import android.content.SharedPreferences;
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


public class PlaylistsCache
{
    private final Context context = Amproid.getAppContext();

    private       String authToken;
    private final String url;
    private final Handler amproidServiceHandler;

    private Vector<HashMap<String, String>> playlists = new Vector<>();

    private boolean valid        = false;
    private boolean sendValidMsg = false;

    private GetPlaylistsThread                                                   getPlaylistsThread = null;
    private MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend       = null;

    private final Handler playlistCacheHandler = new Handler(Looper.getMainLooper())
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
                    if (asyncType == context.getResources().getInteger(R.integer.async_get_playlists)) {
                        synchronized (this) {
                            getPlaylistsThread = null;
                        }

                        String errorMessage = arguments.getString(Amproid.getAppContext().getString(R.string.msg_error_message), "");
                        if (!errorMessage.isEmpty()) {
                            if (amproidServiceHandler != null) {
                                Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.recommendations_now_valid, errorMessage, NEW_TOKEN_REASON_CACHE);
                            }
                            return;
                        }

                        @SuppressWarnings("unchecked")
                        Vector<HashMap<String, String>> playlists = (Vector<HashMap<String, String>>) arguments.getSerializable("playlists");
                        if (playlists != null) {
                            playlists.sort(new Comparator<HashMap<String, String>>()
                            {
                                @Override
                                public int compare(HashMap<String, String> o1, HashMap<String, String> o2)
                                {
                                    String n1 = o1.get("name");
                                    String n2 = o2.get("name");

                                    if (n1 == null) {
                                        n1 = "";
                                    }
                                    if (n2 == null) {
                                        n2 = "";
                                    }

                                    return n1.compareTo(n2);
                                }
                            });

                            synchronized (this) {
                                PlaylistsCache.this.playlists = playlists;
                                valid                         = true;
                            }
                            savePlaylists();
                            sendResultToSend();
                            if ((amproidServiceHandler != null) && sendValidMsg) {
                                sendValidMsg = false;
                                Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.playlists_now_valid, (Bundle) null);
                            }
                        }
                    }
                }
            }
        }
    };


    public PlaylistsCache(String authToken, String url, Handler amproidServiceHandler)
    {
        this.authToken             = authToken;
        this.url                   = url;
        this.amproidServiceHandler = amproidServiceHandler;

        loadPlaylists();
        if (!isValid()) {
            downloadPlaylists();
        }
    }


    public void setAuthToken(String authToken)
    {
        this.authToken = authToken;
    }


    public void getPlaylists(MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend)
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

        downloadPlaylists();
    }


    public Vector<HashMap<String, String>> getPlaylists()
    {
        Vector<HashMap<String, String>> playlists = new Vector<>();

        if (!isValid()) {
            return playlists;
        }

        final SharedPreferences preferences      = Amproid.getAppContext().getSharedPreferences(Amproid.getAppContext().getString(R.string.options_preferences), Context.MODE_PRIVATE);
        boolean                 hideDotPlaylists = preferences.getBoolean(Amproid.getAppContext().getString(R.string.dot_playlists_hide_preference), true);

        synchronized (this) {
            playlists = Amproid.deepCopyItems(this.playlists);
        }

        Vector<HashMap<String, String>> playlistsFiltered = new Vector<>();

        for (HashMap<String, String> playlist : playlists) {
            if (!playlist.containsKey("id") || !playlist.containsKey("name")) {
                continue;
            }

            String name = playlist.get("name");
            if (hideDotPlaylists && (name != null) && name.startsWith(".")) {
                continue;
            }

            playlistsFiltered.add(playlist);
        }

        return playlistsFiltered;
    }


    public boolean isValid()
    {
        boolean returnValue;
        synchronized (this) {
            returnValue = valid;
        }
        return returnValue;
    }


    public void refreshPlaylists()
    {
        sendValidMsg = true;
        downloadPlaylists();
    }


    private void downloadPlaylists()
    {
        synchronized (this) {
            if (getPlaylistsThread == null) {
                getPlaylistsThread = new GetPlaylistsThread(authToken, url, playlistCacheHandler);
                getPlaylistsThread.start();
            }
        }
    }


    private void loadPlaylists()
    {
        Vector<HashMap<String, String>> playlists = new Vector<>();
        boolean                         found     = false;

        SharedPreferences preferences = context.getSharedPreferences(context.getString(R.string.playlist_cache_preferences), Context.MODE_PRIVATE);

        int key = 10;
        while (preferences.contains(Integer.toString(key))) {
            found = true;

            HashMap<String, String> playlist = new HashMap<>();

            playlist.put("id", preferences.getString(Integer.toString(key), ""));
            playlist.put("name", preferences.getString(Integer.toString(key + 1), ""));
            if (preferences.contains(Integer.toString(key + 2))) {
                playlist.put("art", preferences.getString(Integer.toString(key + 2), ""));
            }

            playlists.add(playlist);

            key += 10;
        }

        playlists.sort(new Comparator<HashMap<String, String>>()
        {
            @Override
            public int compare(HashMap<String, String> o1, HashMap<String, String> o2)
            {
                String n1 = o1.get("name");
                String n2 = o2.get("name");

                if (n1 == null) {
                    n1 = "";
                }
                if (n2 == null) {
                    n2 = "";
                }

                return n1.compareTo(n2);
            }
        });

        synchronized (this) {
            valid          = found;
            this.playlists = playlists;
        }
    }


    private void savePlaylists()
    {
        Vector<HashMap<String, String>> playlists;
        synchronized (this) {
            playlists = Amproid.deepCopyItems(this.playlists);
        }

        SharedPreferences        preferences       = context.getSharedPreferences(context.getString(R.string.playlist_cache_preferences), Context.MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = preferences.edit();

        preferencesEditor.clear();

        int key = 10;
        for (HashMap<String, String> playlist : playlists) {
            preferencesEditor.putString(Integer.toString(key), playlist.get("id"));
            preferencesEditor.putString(Integer.toString(key + 1), playlist.get("name"));
            if (playlist.containsKey("art")) {
                preferencesEditor.putString(Integer.toString(key + 2), playlist.get("art"));
            }
            key += 10;
        }

        preferencesEditor.apply();
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

        final SharedPreferences preferences      = Amproid.getAppContext().getSharedPreferences(Amproid.getAppContext().getString(R.string.options_preferences), Context.MODE_PRIVATE);
        boolean                 hideDotPlaylists = preferences.getBoolean(Amproid.getAppContext().getString(R.string.dot_playlists_hide_preference), true);

        Vector<HashMap<String, String>> playlists;
        synchronized (this) {
            playlists = Amproid.deepCopyItems(this.playlists);
        }

        ArrayList<MediaBrowserCompat.MediaItem> results = new ArrayList<>();

        for (HashMap<String, String> playlist : playlists) {
            if (!playlist.containsKey("id") || !playlist.containsKey("name")) {
                continue;
            }

            String name = playlist.get("name");
            if (hideDotPlaylists && (name != null) && name.startsWith(".")) {
                continue;
            }

            results.add(new MediaBrowserCompat.MediaItem(new MediaDescriptionCompat.Builder()
                    .setMediaId(PREFIX_PLAYLIST + playlist.get("id"))
                    .setTitle(name)
                    .setIconUri((playlist.get("art") == null) || (playlist.get("id").startsWith("smart_")) ? null : Uri.parse(playlist.get("art")))
                    .build(), MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        }

        resultToSend.sendResult(results);

        synchronized (this) {
            resultToSend = null;
        }
    }
}
