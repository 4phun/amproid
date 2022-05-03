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


import static com.pppphun.amproid.shared.Amproid.NETWORK_CONNECT_TIMEOUT;
import static com.pppphun.amproid.shared.Amproid.NETWORK_READ_TIMEOUT;
import static com.pppphun.amproid.shared.Amproid.bundleGetString;

import android.os.Bundle;

import com.pppphun.amproid.shared.Amproid;

import org.jetbrains.annotations.NotNull;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;
import java.util.function.Predicate;


public class AmpacheAPICaller
{
    public static final int SEARCH_RESULTS_SONGS         = 1;
    public static final int SEARCH_RESULTS_ALBUMS        = 2;
    public static final int SEARCH_RESULTS_ARTIST_ALBUMS = 3;
    public static final int SEARCH_RESULTS_ARTISTS       = 4;
    public static final int SEARCH_RESULTS_PLAYLISTS     = 5;
    public static final int SEARCH_RESULTS_ADVANCED      = 6;
    public static final int SEARCH_RESULTS_TAGS          = 7;

    private static final int    SEARCH_MAX_SONGS   = 100;
    private static final int    SEARCH_MAX_ALBUMS  = 36;
    private static final int    SEARCH_MAX_ARTISTS = 10;
    private static final int    SEARCH_MAX_TAGS    = 12;
    private static final int    MIN_API_VERSION    = 400001;
    private static final String API_PATH           = "/server/xml.server.php";


    public enum GetTracksIdType
    {
        GET_TRACKS_ID_TYPE_NONE,
        GET_TRACKS_ID_TYPE_SONG,
        GET_TRACKS_ID_TYPE_ARTIST,
        GET_TRACKS_ID_TYPE_ALBUM,
        GET_TRACKS_ID_TYPE_PLAYLIST,
        GET_TRACKS_ID_TYPE_GENRE,
        GET_TRACKS_ID_TYPE_FLAGGED,
        GET_TRACKS_ID_TYPE_RECENTLY_PLAYED,
        GET_TRACKS_ID_TYPE_ANCIENTLY_PLAYED,
        GET_TRACKS_ID_TYPE_NEVER_PLAYED,
        GET_TRACKS_ID_TYPE_RANDOM_RECENTLY_ADDED
    }


    private URL     baseUrl;
    private String  errorMessage     = "";
    private boolean loginShouldRetry = true;


    public AmpacheAPICaller(String url)
    {
        try {
            baseUrl = new URL(url);
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            baseUrl      = null;
        }
    }


    public Vector<HashMap<String, String>> getAlbum(String token, String id)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_blank_token);
            return new Vector<>();
        }

        errorMessage = "";

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "album");
        queryString.addNameValue("auth", token);
        queryString.addNameValue("filter", id);

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("name");
        tagsNeeded.add("art");

        return blockingTransactionMulti(callUrl, "album", tagsNeeded);
    }


    public Vector<HashMap<String, String>> getAlbums(String token, String artistId)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_blank_token);
            return new Vector<>();
        }

        int apiVersion = pingForVersion();

        errorMessage = "";

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "artist_albums");
        queryString.addNameValue("auth", token);
        queryString.addNameValue("filter", artistId);
        if ((apiVersion != 424000) && (apiVersion != 425000)) {
            queryString.addNameValue("limit", "none");
        }

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("name");
        tagsNeeded.add("art");

        return blockingTransactionMulti(callUrl, "album", tagsNeeded);
    }


    public Vector<HashMap<String, String>> getArtist(String token, String id)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_blank_token);
            return new Vector<>();
        }

        errorMessage = "";

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "artist");
        queryString.addNameValue("auth", token);
        queryString.addNameValue("filter", id);

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("name");
        tagsNeeded.add("art");

        return blockingTransactionMulti(callUrl, "artist", tagsNeeded);
    }


    public String getErrorMessage()
    {
        return errorMessage;
    }


    public Vector<HashMap<String, String>> getLiveStreams(String token)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_blank_token);
            return new Vector<>();
        }

        int apiVersion = pingForVersion();

        errorMessage = "";

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "get_indexes");
        queryString.addNameValue("auth", token);
        if ((apiVersion != 424000) && (apiVersion != 425000)) {
            queryString.addNameValue("limit", "none");
        }
        queryString.addNameValue("type", "live_stream");

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("name");
        tagsNeeded.add("url");

        return blockingTransactionMulti(callUrl, "live_stream", tagsNeeded);
    }


    public Vector<HashMap<String, String>> getPlaylists(String token)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_blank_token);
            return new Vector<>();
        }

        int apiVersion = pingForVersion();

        errorMessage = "";

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "playlists");
        queryString.addNameValue("auth", token);
        if ((apiVersion != 424000) && (apiVersion != 425000)) {
            queryString.addNameValue("limit", "none");
        }

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("name");
        tagsNeeded.add("art");

        return blockingTransactionMulti(callUrl, "playlist", tagsNeeded);
    }


    public Vector<HashMap<String, String>> getRecentAlbums(String token)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_blank_token);
            return new Vector<>();
        }

        errorMessage = "";

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "stats");
        queryString.addNameValue("auth", token);
        queryString.addNameValue("type", "album");
        queryString.addNameValue("filter", "newest");
        queryString.addNameValue("limit", "15");

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("name");
        tagsNeeded.add("art");
        tagsNeeded.add("songcount");

        return blockingTransactionMulti(callUrl, "album", tagsNeeded);
    }


    public Vector<Track> getTracks(String token, int count, String id, GetTracksIdType idType)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_blank_token);
            return new Vector<>();
        }

        int apiVersion = pingForVersion();

        errorMessage = "";

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("auth", token);

        // count = 0 means no limit
        if (count == 0) {
            if ((apiVersion != 424000) && (apiVersion != 425000)) {
                queryString.addNameValue("limit", "none");
            }
        }
        else {
            queryString.addNameValue("limit", String.valueOf(count));
        }

        if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_ARTIST) {
            queryString.addNameValue("action", "playlist_generate");
            queryString.addNameValue("artist", id);
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_ALBUM) {
            queryString.addNameValue("action", "album_songs");
            queryString.addNameValue("filter", id);
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_PLAYLIST) {
            queryString.addNameValue("action", "playlist_songs");
            queryString.addNameValue("filter", id);
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_GENRE) {
            queryString.addNameValue("action", "advanced_search");
            queryString.addNameValue("type", "song");
            queryString.addNameValue("random", "1");

            String[] tags = id.split(String.valueOf((char) 255));
            for (int i = 0; i < tags.length; i++) {
                queryString.addNameValue(String.format(Locale.US, "rule_%d", i + 1), "tag");
                queryString.addNameValue(String.format(Locale.US, "rule_%d_operator", i + 1), "4");
                queryString.addNameValue(String.format(Locale.US, "rule_%d_input", i + 1), tags[i]);
            }
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_SONG) {
            queryString.addNameValue("action", "song");
            queryString.addNameValue("filter", id);
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_FLAGGED) {
            if (apiVersion < 5000000) {
                queryString.addNameValue("action", "playlist_generate");
                queryString.addNameValue("mode", "random");
                queryString.addNameValue("flag", "1");
            }
            else {
                queryString.addNameValue("action", "advanced_search");
                queryString.addNameValue("random", "1");
                queryString.addNameValue("rule_1", "favorite");
                queryString.addNameValue("rule_1_operator", "0");
                queryString.addNameValue("rule_1_input", "%");
            }
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_RECENTLY_PLAYED) {
            queryString.addNameValue("action", "advanced_search");
            queryString.addNameValue("type", "song");
            queryString.addNameValue("random", "1");
            queryString.addNameValue("rule_1", "myplayed");
            queryString.addNameValue("rule_1_operator", "0");
            queryString.addNameValue("rule_1_input", "true");
            queryString.addNameValue("rule_2", "last_play");
            queryString.addNameValue("rule_2_operator", "1");
            queryString.addNameValue("rule_2_input", "7");
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_ANCIENTLY_PLAYED) {
            queryString.addNameValue("action", "advanced_search");
            queryString.addNameValue("type", "song");
            queryString.addNameValue("random", "1");
            queryString.addNameValue("rule_1", "myplayed");
            queryString.addNameValue("rule_1_operator", "0");
            queryString.addNameValue("rule_1_input", "true");
            queryString.addNameValue("rule_2", "last_play");
            queryString.addNameValue("rule_2_operator", "0");
            queryString.addNameValue("rule_2_input", "30");
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_NEVER_PLAYED) {
            queryString.addNameValue("action", "advanced_search");
            queryString.addNameValue("type", "song");
            queryString.addNameValue("random", "1");
            queryString.addNameValue("rule_1", "myplayed");
            queryString.addNameValue("rule_1_operator", "1");
            queryString.addNameValue("rule_1_input", "true");
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_RANDOM_RECENTLY_ADDED) {
            // note here id should contain the number of recent tracks to select from (how far to go back)
            queryString.addNameValue("action", "advanced_search");
            queryString.addNameValue("type", "song");
            queryString.addNameValue("random", "1");
            queryString.addNameValue("rule_1", "recent_added");
            queryString.addNameValue("rule_1_operator", "0");
            queryString.addNameValue("rule_1_input", id);
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_NONE) {
            queryString.addNameValue("action", "playlist_generate");
        }

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        String tagTag = "tag";
        if (apiVersion >= 5000000) {
            tagTag = "genre";
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("title");
        tagsNeeded.add("album");
        tagsNeeded.add("artist");
        tagsNeeded.add("url");
        tagsNeeded.add("art");
        tagsNeeded.add(tagTag);

        Vector<HashMap<String, String>> results = blockingTransactionMulti(callUrl, "song", tagsNeeded);

        if (!errorMessage.isEmpty()) {
            return new Vector<>();
        }

        String        localErrorMessage = "";
        Vector<Track> returnValue       = new Vector<>();
        for (HashMap<String, String> result : results) {
            URL url;
            try {
                url = new URL(result.get("url"));
            }
            catch (Exception e) {
                localErrorMessage = e.getMessage();
                continue;
            }

            URL pictureURL = null;
            try {
                pictureURL = new URL(result.get("art"));
            }
            catch (Exception ignored) {
            }

            Track track = new Track();
            track.setId(result.get("id"));
            track.setAlbumId(result.get("album_id"));
            track.setArtistId(result.get("artist_id"));
            track.setUrl(url);
            track.setPictureUrl(pictureURL);
            track.setTitle(result.get("title"));
            track.setAlbum(result.get("album"));
            track.setArtist(result.get("artist"));

            if (result.containsKey(tagTag)) {
                track.addTags(result.get(tagTag));
            }

            returnValue.add(track);
        }

        if (returnValue.isEmpty()) {
            errorMessage = localErrorMessage;
        }

        return returnValue;
    }


    public String handshake(String user, String psw)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return "";
        }
        if (user.isEmpty() || psw.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_credentials);
            return "";
        }

        errorMessage     = "";
        loginShouldRetry = true;

        String timeStr      = String.valueOf(System.currentTimeMillis() / 1000);
        String psw256       = toHexSHA256(psw);
        String handshake256 = toHexSHA256(timeStr + psw256);
        if (!errorMessage.isEmpty()) {
            return "";
        }

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "handshake");
        queryString.addNameValue("auth", handshake256);
        queryString.addNameValue("timestamp", timeStr);
        queryString.addNameValue("user", user);

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return "";
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("error");
        tagsNeeded.add("auth");
        tagsNeeded.add("api");

        HashMap<String, String> results = blockingTransaction(callUrl, tagsNeeded);

        if (!errorMessage.isEmpty()) {
            return "";
        }

        if (results.containsKey("error")) {
            errorMessage     = results.get("error");
            loginShouldRetry = false;
            return "";
        }

        if (results.isEmpty() || !results.containsKey("auth") || !results.containsKey("api")) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_response);
            return "";
        }

        int apiVersion = apiVersionFromString(results.get("api"));
        if (apiVersion < MIN_API_VERSION) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_unsupported_api, apiVersion, MIN_API_VERSION);
            loginShouldRetry = false;
            return "";
        }

        return results.get("auth");
    }


    public boolean isLoginShouldRetry()
    {
        return loginShouldRetry;
    }


    public HashMap<Integer, Vector<HashMap<String, String>>> search(String token, Bundle searchParameters)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return new HashMap<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_blank_token);
            return new HashMap<>();
        }

        String query  = bundleGetString(searchParameters, "query");
        String artist = bundleGetString(searchParameters, "android.intent.extra.artist");
        String album  = bundleGetString(searchParameters, "android.intent.extra.album");
        String title  = bundleGetString(searchParameters, "android.intent.extra.title");

        errorMessage = "";

        Vector<String> titleTagNeeded = new Vector<>();
        titleTagNeeded.add("title");
        titleTagNeeded.add("album");
        titleTagNeeded.add("artist");
        titleTagNeeded.add("art");

        Vector<String> nameTagNeeded = new Vector<>();
        nameTagNeeded.add("name");
        nameTagNeeded.add("art");

        URL url;

        Vector<HashMap<String, String>> advanced = new Vector<>();
        if ((title.length() > 0) && (artist.length() > 0)) {
            QueryStringBuilder advancedQueryString = new QueryStringBuilder();
            advancedQueryString.addNameValue("action", "advanced_search");
            advancedQueryString.addNameValue("auth", token);
            advancedQueryString.addNameValue("type", "song");
            advancedQueryString.addNameValue("random", "1");
            advancedQueryString.addNameValue("limit", String.valueOf(SEARCH_MAX_SONGS));
            advancedQueryString.addNameValue("rule_1", "title");
            advancedQueryString.addNameValue("rule_1_operator", "0");
            advancedQueryString.addNameValue("rule_1_input", title);
            advancedQueryString.addNameValue("rule_2", "artist");
            advancedQueryString.addNameValue("rule_2_operator", "0");
            advancedQueryString.addNameValue("rule_2_input", artist);
            if (album.length() > 0) {
                advancedQueryString.addNameValue("rule_3", "album");
                advancedQueryString.addNameValue("rule_3_operator", "0");
                advancedQueryString.addNameValue("rule_3_input", album);
            }

            try {
                url = new URL(baseUrl.toString() + API_PATH + "?" + advancedQueryString.getQueryString());
            }
            catch (Exception e) {
                errorMessage = e.getMessage();
                return new HashMap<>();
            }

            advanced.addAll(blockingTransactionMulti(url, "song", titleTagNeeded));

            if ((advanced.size() < 1) && (album.length() > 0)) {
                advancedQueryString.removeName("rule_3");
                advancedQueryString.removeName("rule_3_operator");
                advancedQueryString.removeName("rule_3_input");

                try {
                    url = new URL(baseUrl.toString() + API_PATH + "?" + advancedQueryString.getQueryString());
                }
                catch (Exception e) {
                    errorMessage = e.getMessage();
                    return new HashMap<>();
                }

                advanced.addAll(blockingTransactionMulti(url, "song", titleTagNeeded));
            }
        }

        QueryStringBuilder artistQueryString = new QueryStringBuilder();
        artistQueryString.addNameValue("action", "artists");
        artistQueryString.addNameValue("auth", token);
        artistQueryString.addNameValue("filter", artist.isEmpty() ? query : artist);
        artistQueryString.addNameValue("limit", String.valueOf(SEARCH_MAX_ARTISTS));

        try {
            url = new URL(baseUrl.toString() + API_PATH + "?" + artistQueryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new HashMap<>();
        }

        Vector<HashMap<String, String>> artists = blockingTransactionMulti(url, "artist", nameTagNeeded);

        nameTagNeeded.add("artist");
        nameTagNeeded.add("art");

        QueryStringBuilder albumsQueryString = new QueryStringBuilder();
        albumsQueryString.addNameValue("action", "albums");
        albumsQueryString.addNameValue("auth", token);
        albumsQueryString.addNameValue("filter", album.isEmpty() ? query : album);
        albumsQueryString.addNameValue("limit", String.valueOf(SEARCH_MAX_ALBUMS));

        try {
            url = new URL(baseUrl.toString() + API_PATH + "?" + albumsQueryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new HashMap<>();
        }

        Vector<HashMap<String, String>> albums        = blockingTransactionMulti(url, "album", nameTagNeeded);
        Vector<HashMap<String, String>> artist_albums = new Vector<>();

        for (HashMap<String, String> foundArtist : artists) {
            if (!foundArtist.containsKey("id")) {
                continue;
            }

            QueryStringBuilder artistAlbumsQueryString = new QueryStringBuilder();
            artistAlbumsQueryString.addNameValue("action", "artist_albums");
            artistAlbumsQueryString.addNameValue("auth", token);
            artistAlbumsQueryString.addNameValue("filter", foundArtist.get("id"));
            artistAlbumsQueryString.addNameValue("limit", String.valueOf(SEARCH_MAX_ALBUMS));

            try {
                url = new URL(baseUrl.toString() + API_PATH + "?" + artistAlbumsQueryString.getQueryString());
            }
            catch (Exception e) {
                errorMessage = e.getMessage();
                return new HashMap<>();
            }

            artist_albums.addAll(blockingTransactionMulti(url, "album", nameTagNeeded));
        }

        QueryStringBuilder songsQueryString = new QueryStringBuilder();
        songsQueryString.addNameValue("action", "songs");
        songsQueryString.addNameValue("auth", token);
        songsQueryString.addNameValue("filter", title.isEmpty() ? query : title);
        songsQueryString.addNameValue("limit", String.valueOf(SEARCH_MAX_SONGS));

        try {
            url = new URL(baseUrl.toString() + API_PATH + "?" + songsQueryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new HashMap<>();
        }

        Vector<HashMap<String, String>> songs = blockingTransactionMulti(url, "song", titleTagNeeded);

        if ((songs.size() < 1) && (albums.size() < 1) && (artist_albums.size() < 1) && (artists.size() < 1)) {
            Vector<String> toCheck = new Vector<>();

            String[] pieces = query.split("\\s");
            for (String piece : pieces) {
                if ((piece.length() > 3) && (!toCheck.contains(piece))) {
                    toCheck.add(piece);
                }
            }
            pieces = title.split("\\s");
            for (String piece : pieces) {
                if ((piece.length() > 3) && (!toCheck.contains(piece))) {
                    toCheck.add(piece);
                }
            }

            toCheck.sort(new Comparator<String>()
            {
                @Override
                public int compare(String o1, String o2)
                {
                    if (o1.length() == o2.length()) {
                        return o1.compareTo(o2);
                    }

                    return Integer.compare(o2.length(), o1.length());
                }
            });

            for (String check : toCheck) {
                QueryStringBuilder checkQueryString = new QueryStringBuilder();
                checkQueryString.addNameValue("action", "songs");
                checkQueryString.addNameValue("auth", token);
                checkQueryString.addNameValue("filter", check);
                checkQueryString.addNameValue("limit", String.valueOf(SEARCH_MAX_SONGS));

                try {
                    url = new URL(baseUrl.toString() + API_PATH + "?" + checkQueryString.getQueryString());
                }
                catch (Exception e) {
                    errorMessage = e.getMessage();
                    return new HashMap<>();
                }

                songs.addAll(blockingTransactionMulti(url, "song", titleTagNeeded));

                if (songs.size() > 0) {
                    break;
                }
            }
        }

        int apiVersion = pingForVersion();

        QueryStringBuilder tagsQueryString = new QueryStringBuilder();
        tagsQueryString.addNameValue("action", apiVersion >= 5000000 ? "genres" : "tags");
        tagsQueryString.addNameValue("auth", token);
        tagsQueryString.addNameValue("filter", query);
        tagsQueryString.addNameValue("limit", String.valueOf(SEARCH_MAX_TAGS));

        try {
            url = new URL(baseUrl.toString() + API_PATH + "?" + tagsQueryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new HashMap<>();
        }

        Vector<HashMap<String, String>> tags = blockingTransactionMulti(url, apiVersion >= 5000000 ? "genre" : "tag", nameTagNeeded);
        try {
            for (HashMap<String, String> tag : tags) {
                tag.replace("id", URLEncoder.encode(tag.get("name"), StandardCharsets.UTF_8.toString()));
            }
        }
        catch (Exception e) {
            // in case UTF-8 is not supported, pretty much never happens with min SDK version being what it is
            tags.clear();
        }

        HashMap<Integer, Vector<HashMap<String, String>>> returnValue = new HashMap<>();
        returnValue.put(SEARCH_RESULTS_SONGS, songs);
        returnValue.put(SEARCH_RESULTS_ALBUMS, albums);
        returnValue.put(SEARCH_RESULTS_ARTIST_ALBUMS, artist_albums);
        returnValue.put(SEARCH_RESULTS_ARTISTS, artists);
        returnValue.put(SEARCH_RESULTS_ADVANCED, advanced);
        returnValue.put(SEARCH_RESULTS_TAGS, tags);

        return returnValue;
    }


    public boolean tokenTest(String token)
    {
        if (baseUrl == null) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_invalid_server_url);
            return false;
        }
        if (token.isEmpty()) {
            setErrorMessage(com.pppphun.amproid.shared.R.string.error_blank_token);
            return false;
        }

        errorMessage = "";

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "playlist_generate");
        queryString.addNameValue("auth", token);
        queryString.addNameValue("limit", "1");
        queryString.addNameValue("format", "index");

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return false;
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("error");
        tagsNeeded.add("total_count");

        HashMap<String, String> results = blockingTransaction(callUrl, tagsNeeded);

        if (!errorMessage.isEmpty()) {
            return false;
        }

        return !results.containsKey("error") && results.containsKey("total_count");
    }


    private int apiVersionFromString(String apiVersionString)
    {
        if (apiVersionString == null) {
            apiVersionString = "0";
        }

        int apiVersion = 0;

        try {
            // old version format: integer
            apiVersion = Integer.parseInt(apiVersionString);

            // under some circumstances, 5.0.0 sends 500000
            if ((apiVersion >= 500000) && (apiVersion < 1000000)) {
                apiVersion *= 10;
            }
        }
        catch (Exception e) {
            // new version format introduced at 5.0.0: string major.minor.patch
            String[] versionParts = apiVersionString.split("\\.");
            if (versionParts.length == 3) {
                int majorApiVersion = 0;
                int minorApiVersion = 0;
                int patchApiVersion = 0;

                try {
                    majorApiVersion = Integer.parseInt(versionParts[0]);
                    minorApiVersion = Integer.parseInt(versionParts[1]);
                    patchApiVersion = Integer.parseInt(versionParts[2]);
                }
                catch (Exception ignored) {
                }

                apiVersion = majorApiVersion * 1000000 + minorApiVersion * 1000 + patchApiVersion;
            }

            // string version must be minimum 5.0.0
            if (apiVersion < 5000000) {
                apiVersion = 0;
            }
        }

        return apiVersion;
    }


    private HashMap<String, String> blockingTransaction(@NotNull URL url, @NotNull Vector<String> tags)
    {
        HashMap<String, String> results = new HashMap<>();

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setConnectTimeout(NETWORK_CONNECT_TIMEOUT);
            connection.setReadTimeout(NETWORK_READ_TIMEOUT);
            connection.connect();
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return results;
        }

        try {
            XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
            xmlPullParserFactory.setNamespaceAware(false);

            XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
            xmlPullParser.setInput(new BufferedReader(new InputStreamReader(connection.getInputStream())));

            String currentElement = "";

            int xmlState = xmlPullParser.getEventType();
            while (xmlState != XmlPullParser.END_DOCUMENT) {
                if (xmlState == XmlPullParser.START_TAG) {
                    currentElement = xmlPullParser.getName();
                }
                else if (xmlState == XmlPullParser.END_TAG) {
                    currentElement = "";
                }
                else if (xmlState == XmlPullParser.TEXT) {
                    if (tags.contains(currentElement)) {
                        results.put(currentElement, xmlPullParser.getText());
                    }
                    else if ((currentElement.compareTo("error") == 0) || (currentElement.compareTo("errorMessage") == 0)) {
                        String errorText = "";
                        try {
                            errorText = xmlPullParser.getText().trim();
                        }
                        catch (Exception ignored) {
                        }
                        if (errorText.length() > 0) {
                            errorMessage = errorText;
                            break;
                        }
                    }
                }
                xmlState = xmlPullParser.next();
            }
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
        }


        return results;
    }


    private Vector<HashMap<String, String>> blockingTransactionMulti(@NotNull URL url, @NotNull String repeatingTag, @NotNull Vector<String> subTags)
    {
        Vector<HashMap<String, String>> results = new Vector<>();

        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.setConnectTimeout(NETWORK_CONNECT_TIMEOUT);
            connection.setReadTimeout(NETWORK_READ_TIMEOUT);
            connection.connect();
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return results;
        }

        HashMap<String, String> subResults = null;

        try {
            XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
            xmlPullParserFactory.setNamespaceAware(false);

            XmlPullParser xmlPullParser = xmlPullParserFactory.newPullParser();
            xmlPullParser.setInput(connection.getInputStream(), null);

            String currentElement   = "";
            String currentErrorCode = "";

            int xmlState = xmlPullParser.getEventType();
            while (xmlState != XmlPullParser.END_DOCUMENT) {
                if (xmlState == XmlPullParser.START_TAG) {
                    currentElement = xmlPullParser.getName();

                    String id = "";

                    int i = 0;
                    while (i < xmlPullParser.getAttributeCount()) {
                        if (xmlPullParser.getAttributeName(i).compareTo("id") == 0) {
                            id = String.valueOf(xmlPullParser.getAttributeValue(i));
                            break;
                        }
                        else if ((currentElement.compareTo("error") == 0) && ((xmlPullParser.getAttributeName(i).compareTo("code") == 0) || (xmlPullParser.getAttributeName(i).compareTo("errorCode") == 0))) {
                            currentErrorCode = String.valueOf(xmlPullParser.getAttributeValue(i));
                            break;
                        }
                        i++;
                    }

                    if (currentElement.compareTo(repeatingTag) == 0) {
                        subResults = new HashMap<>();
                        results.add(subResults);

                        if (!id.isEmpty()) {
                            subResults.put("id", id);
                        }
                    }
                    else if (subTags.contains(currentElement)) {
                        if (!id.isEmpty() && (subResults != null)) {
                            subResults.put(currentElement + "_id", id);
                        }
                    }
                }
                else if (xmlState == XmlPullParser.END_TAG) {
                    if (currentElement.compareTo("error") == 0) {
                        currentErrorCode = "";
                    }
                    currentElement = "";
                }
                else if (xmlState == XmlPullParser.TEXT) {
                    if (subTags.contains(currentElement) && (subResults != null)) {
                        if (((currentElement.compareTo("tag") == 0) || (currentElement.compareTo("genre") == 0)) && (subResults.containsKey(currentElement))) {
                            String tags = subResults.get(currentElement);
                            tags = tags + (char) 255 + xmlPullParser.getText();
                            subResults.put(currentElement, tags);
                        }
                        else {
                            subResults.put(currentElement, xmlPullParser.getText());
                        }
                    }
                    else if ((currentElement.compareTo("error") == 0) || (currentElement.compareTo("errorMessage") == 0)) {
                        String errorText = "";
                        try {
                            errorText = xmlPullParser.getText().trim();
                        }
                        catch (Exception ignored) {
                        }
                        if (errorText.length() > 0) {
                            // do not error on "not found", an empty set will be returned
                            if ((currentErrorCode.compareTo("4704") != 0) && (currentErrorCode.compareTo("404") != 0)) {
                                errorMessage = errorText;
                                break;
                            }
                        }
                    }
                }
                xmlState = xmlPullParser.next();
            }
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
        }

        return results;
    }


    private int pingForVersion()
    {
        if (baseUrl == null) {
            return 0;
        }

        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "ping");

        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            return 0;
        }

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("version");

        HashMap<String, String> results = blockingTransaction(callUrl, tagsNeeded);

        return apiVersionFromString(results.get("version"));
    }


    private void setErrorMessage(int stringResource, Object... args)
    {
        errorMessage = String.format(Amproid.getAppContext().getString(stringResource), args);
    }


    private String toHexSHA256(@NotNull String input)
    {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return "";
        }

        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));

        StringBuilder stringBuilder = new StringBuilder();
        for (byte b : hash) {
            stringBuilder.append(Integer.toHexString((b & 0xFF) | 0x100).substring(1, 3));
        }

        return stringBuilder.toString();
    }


    private static final class QueryStringBuilder
    {
        private final ArrayList<String> queryString = new ArrayList<>();


        void addNameValue(String name, String value)
        {
            queryString.add(name + "=" + value);
        }


        String getQueryString()
        {
            String returnValue = "";
            for (String part : queryString) {
                if (!returnValue.isEmpty()) {
                    returnValue = returnValue.concat("&");
                }
                returnValue = returnValue.concat(part);
            }

            return returnValue;
        }


        void removeName(String name)
        {
            queryString.removeIf(new Predicate<String>()
            {
                @Override
                public boolean test(String s)
                {
                    return s.startsWith(name + "=");
                }
            });
        }
    }
}


