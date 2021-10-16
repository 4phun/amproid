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
import static com.pppphun.amproid.service.AmproidService.PREFIX_SONG;

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


public class SearchCache
{
    private final Context context = Amproid.getAppContext();

    private final String  authToken;
    private final String  url;
    private final Handler amproidServiceHandler;

    private HashMap<Integer, Vector<HashMap<String, String>>> searchResults = new HashMap<>();

    private boolean valid        = false;
    private boolean sendValidMsg = false;

    private SearchThread                                                         searchThread = null;
    private MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend = null;

    private final Handler searchCacheHandler = new Handler(Looper.getMainLooper())
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
                    if (asyncType == context.getResources().getInteger(R.integer.async_search)) {
                        synchronized (this) {
                            searchThread = null;
                        }

                        String errorMessage = arguments.getString("errorMessage", "");
                        if (errorMessage.isEmpty()) {
                            @SuppressWarnings("unchecked")
                            HashMap<Integer, Vector<HashMap<String, String>>> results = (HashMap<Integer, Vector<HashMap<String, String>>>) arguments.getSerializable("found");
                            if (results != null) {
                                synchronized (this) {
                                    SearchCache.this.searchResults = results;
                                    valid                          = true;
                                }
                                sendResultToSend();
                                if ((amproidServiceHandler != null) && sendValidMsg) {
                                    sendValidMsg = false;
                                    Amproid.sendMessage(amproidServiceHandler, R.string.msg_action_async_finished, R.integer.search_now_valid, (Bundle) null);
                                }
                            }
                        }
                    }
                }
            }
        }
    };


    public SearchCache(String authToken, String url, Handler amproidServiceHandler)
    {
        this.authToken             = authToken;
        this.url                   = url;
        this.amproidServiceHandler = amproidServiceHandler;

        doSearch(null);
    }


    public void getSearchResults(MediaBrowserServiceCompat.Result<List<MediaBrowserCompat.MediaItem>> resultToSend)
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

        doSearch(null);
    }


    public Vector<HashMap<String, String>> getSearchResults()
    {
        if (!isValid()) {
            return new Vector<>();
        }

        return flatterSearchResults();
    }


    public boolean isValid()
    {
        boolean returnValue;
        synchronized (this) {
            returnValue = valid;
        }
        return returnValue;
    }


    public void refreshSearch(Bundle searchParameters)
    {
        sendValidMsg = true;
        doSearch(searchParameters);
    }


    private void doSearch(Bundle searchParameters)
    {
        if (searchParameters == null) {
            Vector<String> recentSearches = Amproid.loadRecentSearches();

            searchParameters = new Bundle();
            searchParameters.putString("query", recentSearches.size() > 0 ? recentSearches.lastElement() : "BAB");
        }

        synchronized (this) {
            if (searchThread == null) {
                searchThread = new SearchThread(authToken, url, searchParameters, searchCacheHandler);
                searchThread.start();
            }
        }
    }


    private Vector<HashMap<String, String>> flatterSearchResults()
    {
        HashMap<Integer, Vector<HashMap<String, String>>> searchResults;
        synchronized (this) {
            searchResults = Amproid.deepCopyItems(this.searchResults);
        }

        Vector<HashMap<String, String>> flatteredSearchResults = new Vector<>();

        // filter out duplicates
        Vector<String> addedIds = new Vector<>();

        int[] processOrder = new int[]{
                AmpacheAPICaller.SEARCH_RESULTS_PLAYLISTS,
                AmpacheAPICaller.SEARCH_RESULTS_ARTISTS,
                AmpacheAPICaller.SEARCH_RESULTS_ALBUMS,
                AmpacheAPICaller.SEARCH_RESULTS_SONGS,
                AmpacheAPICaller.SEARCH_RESULTS_ARTIST_ALBUMS
        };
        for (int type : processOrder) {
            Vector<HashMap<String, String>> items = searchResults.get(type);
            if (items == null) {
                continue;
            }

            for (HashMap<String, String> item : items) {
                // it's not the same for songs and albums
                if (item.containsKey("name") && !item.containsKey("title")) {
                    item.put("title", item.get("name"));
                    item.remove("name");
                }
            }

            items.sort(new Comparator<HashMap<String, String>>()
            {
                @Override
                public int compare(HashMap<String, String> o1, HashMap<String, String> o2)
                {
                    String title1 = o1.get("title");
                    String title2 = o2.get("title");

                    if (title1 == null) {
                        title1 = "";
                    }
                    if (title2 == null) {
                        title2 = "";
                    }

                    return title1.compareToIgnoreCase(title2);
                }
            });

            for (HashMap<String, String> item : items) {
                if (!item.containsKey("id") || !item.containsKey("title")) {
                    continue;
                }

                String idPrefix         = PREFIX_SONG;
                int    subtitleResource = R.string.subtitle_song;
                if ((type == AmpacheAPICaller.SEARCH_RESULTS_ALBUMS) || (type == AmpacheAPICaller.SEARCH_RESULTS_ARTIST_ALBUMS)) {
                    idPrefix         = PREFIX_ALBUM;
                    subtitleResource = R.string.subtitle_album;
                }
                else if (type == AmpacheAPICaller.SEARCH_RESULTS_ARTISTS) {
                    idPrefix         = PREFIX_ARTIST;
                    subtitleResource = R.string.subtitle_artist;
                }
                else if (type == AmpacheAPICaller.SEARCH_RESULTS_PLAYLISTS) {
                    idPrefix         = PREFIX_PLAYLIST;
                    subtitleResource = R.string.subtitle_playlist;
                }

                String id = idPrefix + item.get("id");
                if (addedIds.contains(id)) {
                    continue;
                }
                item.replace("id", id);

                item.put("subtitle", context.getString(subtitleResource));

                flatteredSearchResults.add(item);
                addedIds.add(id);
            }
        }

        return flatteredSearchResults;
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

        Vector<HashMap<String, String>>         flatteredSearchResults = flatterSearchResults();
        ArrayList<MediaBrowserCompat.MediaItem> results                = new ArrayList<>();

        for (HashMap<String, String> item : flatteredSearchResults) {
            results.add(new MediaBrowserCompat.MediaItem(
                    new MediaDescriptionCompat.Builder()
                            .setMediaId(item.get("id"))
                            .setTitle(item.get("title"))
                            .setSubtitle(item.get("subtitle"))
                            .setIconUri(item.get("art") == null ? null : Uri.parse(item.get("art")))
                            .build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        }

        resultToSend.sendResult(results);

        synchronized (this) {
            resultToSend = null;
        }
    }
}
