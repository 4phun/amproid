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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;


public class AmpacheAPICaller
{
    public static final int SEARCH_RESULTS_SONGS         = 1;
    public static final int SEARCH_RESULTS_ALBUMS        = 2;
    public static final int SEARCH_RESULTS_ARTIST_ALBUMS = 3;
    public static final int SEARCH_RESULTS_ARTISTS       = 4;
    public static final int SEARCH_RESULTS_PLAYLISTS     = 5;

    private static final int    SEARCH_MAX_SONGS   = 100;
    private static final int    SEARCH_MAX_ALBUMS  = 36;
    private static final int    SEARCH_MAX_ARTISTS = 10;
    private static final int    MIN_API_VERSION    = 400001;
    private static final String API_PATH           = "/server/xml.server.php";


    public enum GetTracksIdType
    {
        GET_TRACKS_ID_TYPE_NONE,
        GET_TRACKS_ID_TYPE_SONG,
        GET_TRACKS_ID_TYPE_ARTIST,
        GET_TRACKS_ID_TYPE_ALBUM,
        GET_TRACKS_ID_TYPE_PLAYLIST,
        GET_TRACKS_ID_TYPE_FLAGGED,
        GET_TRACKS_ID_TYPE_RECENTLY_PLAYED,
        GET_TRACKS_ID_TYPE_ANCIENTLY_PLAYED,
        GET_TRACKS_ID_TYPE_NEVER_PLAYED
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
        queryString.addNameValue("action", "get_indexes");
        queryString.addNameValue("auth", token);
        if ((apiVersion != 424000) && (apiVersion != 425000)) {
            queryString.addNameValue("limit", "none");
        }
        queryString.addNameValue("type", "playlist");
        if (apiVersion >= 5000000) {
            queryString.addNameValue("include", "0");
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
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_SONG) {
            queryString.addNameValue("action", "song");
            queryString.addNameValue("filter", id);
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_FLAGGED) {
            queryString.addNameValue("action", "playlist_generate");
            queryString.addNameValue("flag", "1");
        }
        else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_RECENTLY_PLAYED) {
            queryString.addNameValue("action", "advanced_search");
            queryString.addNameValue("type", "song");
            queryString.addNameValue("random", "1");
            queryString.addNameValue("rule_1", "myplayed");
            queryString.addNameValue("rule_1_operator", "0");
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

        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("title");
        tagsNeeded.add("album");
        tagsNeeded.add("artist");
        tagsNeeded.add("url");
        tagsNeeded.add("art");
        tagsNeeded.add("tag");

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

            if (result.containsKey("tag")) {
                track.addTags(result.get("tag"));
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
        queryString.addNameValue("version", String.valueOf(MIN_API_VERSION));
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

        String query  = searchParameters.getString("query");
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

        HashMap<Integer, Vector<HashMap<String, String>>> returnValue = new HashMap<>();
        returnValue.put(SEARCH_RESULTS_SONGS, songs);
        returnValue.put(SEARCH_RESULTS_ALBUMS, albums);
        returnValue.put(SEARCH_RESULTS_ARTIST_ALBUMS, artist_albums);
        returnValue.put(SEARCH_RESULTS_ARTISTS, artists);

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

            String currentElement = "";

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
                    currentElement = "";
                }
                else if (xmlState == XmlPullParser.TEXT) {
                    if (subTags.contains(currentElement) && (subResults != null)) {
                        if ((currentElement.compareTo("tag") == 0) && (subResults.containsKey(currentElement))) {
                            String tags = subResults.get(currentElement);
                            tags = tags + "," + xmlPullParser.getText();
                            subResults.put(currentElement, tags);
                        }
                        else {
                            subResults.put(currentElement, xmlPullParser.getText());
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
    }
}


