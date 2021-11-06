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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;

import com.pppphun.amproid.shared.Amproid;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;
import java.util.Vector;


public class GetRecommendationsThread extends ThreadCancellable
{
    private final String authToken;
    private final String url;

    private final Handler resultsHandler;


    GetRecommendationsThread(String authToken, String url, Handler resultsHandler)
    {
        this.authToken      = authToken;
        this.url            = url;
        this.resultsHandler = resultsHandler;
    }


    @Override
    public void run()
    {
        if ((authToken == null) || authToken.isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_recommendations, R.string.error_blank_token);
            return;
        }
        if ((url == null) || url.isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_recommendations, R.string.error_invalid_server_url);
            return;
        }

        long                     checkStart       = System.currentTimeMillis();
        Amproid.ConnectionStatus connectionStatus = Amproid.getConnectionStatus();
        while (!isCancelled() && ((connectionStatus == CONNECTION_UNKNOWN) || (connectionStatus == CONNECTION_NONE))) {
            Bundle arguments = new Bundle();
            arguments.putLong("elapsedMS", System.currentTimeMillis() - checkStart);
            Amproid.sendMessage(resultsHandler, R.string.msg_async_no_network, arguments);

            SystemClock.sleep(1000);

            connectionStatus = Amproid.getConnectionStatus();
        }

        PlaylistsCache playlistsCache = new PlaylistsCache(authToken, url, null);

        final TypedArray        recommendationsModes = Amproid.getAppContext().getResources().obtainTypedArray(R.array.recommendations_modes);
        final SharedPreferences preferences          = Amproid.getAppContext().getSharedPreferences(Amproid.getAppContext().getString(R.string.options_preferences), Context.MODE_PRIVATE);
        String                  forYouModeSetting    = preferences.getString(Amproid.getAppContext().getString(R.string.recommendations_mode_preference), recommendationsModes.getString(0));

        int forYouIndex = 0;
        for (int i = 0; i < recommendationsModes.length(); i++) {
            if (forYouModeSetting.compareTo(recommendationsModes.getString(i)) == 0) {
                forYouIndex = i;
                break;
            }
        }

        recommendationsModes.recycle();

        // default values = Balanced
        int trackCountFav         = 1;
        int trackCountRecent      = 1;
        int trackCountAncient     = 1;
        int trackCountNeverPlayed = 1;

        if (forYouIndex == 0) {
            // Adventurous
            trackCountRecent      = 0;
            trackCountAncient     = 2;
            trackCountNeverPlayed = 2;
        }
        else if (forYouIndex == 2) {
            // Disciplined
            trackCountFav         = 3;
            trackCountRecent      = 2;
            trackCountAncient     = 1;
            trackCountNeverPlayed = 0;
        }

        int trackCountBase = trackCountFav + trackCountRecent + trackCountAncient + trackCountNeverPlayed;

        AmpacheAPICaller ampacheAPICaller = new AmpacheAPICaller(url);
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_recommendations, ampacheAPICaller.getErrorMessage());
            return;
        }

        Vector<Track> favTracks = new Vector<>(ampacheAPICaller.getTracks(authToken, trackCountBase, null, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_FLAGGED));
        if (isCancelled()) {
            return;
        }
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_recommendations, ampacheAPICaller.getErrorMessage());
            return;
        }

        Vector<Track> recentTracks = new Vector<>(ampacheAPICaller.getTracks(authToken, trackCountBase, null, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_RECENTLY_PLAYED));
        if (isCancelled()) {
            return;
        }
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_recommendations, ampacheAPICaller.getErrorMessage());
            return;
        }

        Vector<Track> ancientTracks = new Vector<>(ampacheAPICaller.getTracks(authToken, trackCountBase, null, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_ANCIENTLY_PLAYED));
        if (isCancelled()) {
            return;
        }
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_recommendations, ampacheAPICaller.getErrorMessage());
            return;
        }

        Vector<Track> neverPlayedTracks = new Vector<>(ampacheAPICaller.getTracks(authToken, trackCountBase, null, AmpacheAPICaller.GetTracksIdType.GET_TRACKS_ID_TYPE_NEVER_PLAYED));
        if (isCancelled()) {
            return;
        }
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_recommendations, ampacheAPICaller.getErrorMessage());
            return;
        }

        Vector<Track> tracks = new Vector<>();

        int i = 0;
        while ((favTracks.size() > 0) && (i < trackCountFav)) {
            tracks.add(favTracks.elementAt(0));
            favTracks.remove(0);
            i++;
        }
        i = 0;
        while ((recentTracks.size() > 0) && (i < trackCountRecent)) {
            tracks.add(recentTracks.elementAt(0));
            recentTracks.remove(0);
            i++;
        }
        i = 0;
        while ((ancientTracks.size() > 0) && (i < trackCountAncient)) {
            tracks.add(ancientTracks.elementAt(0));
            ancientTracks.remove(0);
            i++;
        }
        i = 0;
        while ((neverPlayedTracks.size() > 0) && (i < trackCountNeverPlayed)) {
            tracks.add(neverPlayedTracks.elementAt(0));
            neverPlayedTracks.remove(0);
            i++;
        }

        if (forYouIndex == 0) {
            while ((tracks.size() < trackCountBase) && (neverPlayedTracks.size() > 0)) {
                tracks.add(neverPlayedTracks.elementAt(0));
                neverPlayedTracks.remove(0);
            }
            while ((tracks.size() < trackCountBase) && (ancientTracks.size() > 0)) {
                tracks.add(ancientTracks.elementAt(0));
                ancientTracks.remove(0);
            }
        }
        else if (forYouIndex == 2) {
            while ((tracks.size() < trackCountBase) && (favTracks.size() > 0)) {
                tracks.add(favTracks.elementAt(0));
                favTracks.remove(0);
            }
            while ((tracks.size() < trackCountBase) && (recentTracks.size() > 0)) {
                tracks.add(recentTracks.elementAt(0));
                recentTracks.remove(0);
            }
        }
        else {
            while ((tracks.size() < trackCountBase) && (recentTracks.size() > 0)) {
                tracks.add(recentTracks.elementAt(0));
                recentTracks.remove(0);
            }
            while ((tracks.size() < trackCountBase) && (ancientTracks.size() > 0)) {
                tracks.add(ancientTracks.elementAt(0));
                ancientTracks.remove(0);
            }
        }

        Vector<HashMap<String, String>> cachedPlaylists = playlistsCache.getPlaylists();

        Vector<HashMap<String, String>> artists   = new Vector<>();
        Vector<HashMap<String, String>> albums    = new Vector<>();
        Vector<HashMap<String, String>> playlists = new Vector<>();

        Vector<String> artistsUnique = new Vector<>();
        Vector<String> albumsUnique  = new Vector<>();

        HashMap<String, Integer> tagsCounter = new HashMap<>();

        Random randomizer = new Random();

        for (Track track : tracks) {
            if (!artistsUnique.contains(track.getArtistId())) {
                artistsUnique.add(track.getArtistId());

                artists.addAll(ampacheAPICaller.getArtist(authToken, track.getArtistId()));
                if (isCancelled()) {
                    return;
                }

                for (HashMap<String, String> playlist : cachedPlaylists) {
                    if (Amproid.stringContains(track.getArtist(), playlist.get("name"))) {
                        playlists.add(playlist);
                    }
                }

                Vector<HashMap<String, String>> artistAlbums = new Vector<>(ampacheAPICaller.getAlbums(authToken, track.getArtistId()));
                if (isCancelled()) {
                    return;
                }
                if (artistAlbums.size() > 0) {
                    int ind = randomizer.nextInt(artistAlbums.size());
                    int cnt = 0;
                    while ((ind < artistAlbums.size()) && (cnt < 2)) {
                        albumsUnique.add(artistAlbums.get(ind).get("id"));
                        albums.add(artistAlbums.get(ind));
                        ind++;
                        cnt++;
                    }
                }
            }
            if (!albumsUnique.contains(track.getAlbumId())) {
                albumsUnique.add(track.getAlbumId());
                albums.addAll(ampacheAPICaller.getAlbum(authToken, track.getAlbumId()));
                if (isCancelled()) {
                    return;
                }
            }

            for (String tag : track.getTagsFiltered()) {
                if (tagsCounter.containsKey(tag)) {
                    Integer current = tagsCounter.get(tag);
                    if (current == null) {
                        current = 1;
                    }
                    tagsCounter.replace(tag, current + 1);
                }
                else {
                    tagsCounter.put(tag, 1);
                }
            }
        }

        Vector<String> genres = new Vector<>();

        if (tagsCounter.size() > 0) {
            Vector<String> sortTags = new Vector<>(tagsCounter.keySet());
            sortTags.sort(new Comparator<String>()
            {
                @Override
                public int compare(String o1, String o2)
                {
                    Integer rank1 = tagsCounter.get(o1);
                    Integer rank2 = tagsCounter.get(o2);

                    if (rank1 == null) {
                        rank1 = 0;
                    }
                    if (rank2 == null) {
                        rank2 = 0;
                    }

                    return Integer.compare(rank2, rank1);
                }
            });

            Integer maxRank = tagsCounter.get(sortTags.get(0));
            if (maxRank != null) {
                i = 0;
                while ((i < sortTags.size()) && (i < 12)) {
                    genres.add(sortTags.get(i));
                    i++;
                }

                i = 0;
                while ((i < sortTags.size()) && (i < 3)) {
                    Integer currentRank = tagsCounter.get(sortTags.get(i));
                    if ((currentRank == null) || (currentRank < maxRank)) {
                        break;
                    }

                    String[] tagParts = sortTags.get(i).split("\\s+");
                    for (String tagPart : tagParts) {
                        for (HashMap<String, String> playlist : cachedPlaylists) {
                            if (Amproid.stringContains(tagPart, playlist.get("name")) && !playlists.contains(playlist)) {
                                playlists.add(playlist);
                            }
                        }
                    }
                    i++;
                }
            }
        }

        if (isCancelled()) {
            return;
        }

        Bundle arguments = new Bundle();
        arguments.putSerializable("tracks", tracks);
        arguments.putSerializable("artists", artists);
        arguments.putSerializable("albums", albums);
        arguments.putSerializable("playlists", playlists);
        arguments.putSerializable("genres", genres);
        Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_get_recommendations, arguments);
    }
}
