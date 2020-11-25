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

import android.annotation.SuppressLint;
import android.os.Bundle;

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

import static com.pppphun.amproid.Amproid.NETWORK_CONNECT_TIMEOUT;
import static com.pppphun.amproid.Amproid.NETWORK_READ_TIMEOUT;
import static com.pppphun.amproid.Amproid.bundleGetString;


class AmpacheAPICaller
{
    static final int SEARCH_RESULTS_SONGS         = 1;
    static final int SEARCH_RESULTS_ALBUMS        = 2;
    static final int SEARCH_RESULTS_ARTIST_ALBUMS = 3;

    private static final int    SEARCH_MAX_SONGS   = 100;
    private static final int    SEARCH_MAX_ALBUMS  = 36;
    private static final int    SEARCH_MAX_ARTISTS = 10;
    private static final int    MIN_API_VERSION    = 400001;
    private static final String API_PATH           = "/server/xml.server.php";

    private URL     baseUrl;
    private String  errorMessage     = "";
    private boolean loginShouldRetry = true;


    AmpacheAPICaller(String url)
    {
        try {
            baseUrl = new URL(url);
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            baseUrl      = null;
        }
    }


    Vector<HashMap<String, String>> getAlbums(String token, String artistId)
    {
        if (baseUrl == null) {
            setErrorMessage(R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(R.string.error_blank_token);
            return new Vector<>();
        }

        int apiVersion = pingForVersion();

        errorMessage = "";

        // build query
        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "artist_albums");
        queryString.addNameValue("auth", token);
        queryString.addNameValue("filter", artistId);
        if (apiVersion != 424000) {
            queryString.addNameValue("limit", "none");
        }

        // create URL
        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        // tags that we're interested in
        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("name");

        // make the call
        return blockingTransactionMulti(callUrl, "album", tagsNeeded);
    }


    Vector<HashMap<String, String>> getArtists(String token)
    {
        if (baseUrl == null) {
            setErrorMessage(R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(R.string.error_blank_token);
            return new Vector<>();
        }

        int apiVersion = pingForVersion();

        errorMessage = "";

        // build query
        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "artists");
        queryString.addNameValue("auth", token);
        if (apiVersion != 424000) {
            queryString.addNameValue("limit", "none");
        }

        // create URL
        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        // tags that we're interested in
        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("name");

        // make the call
        return blockingTransactionMulti(callUrl, "artist", tagsNeeded);
    }


    String getErrorMessage()
    {
        return errorMessage;
    }


    private void setErrorMessage(int stringResource)
    {
        errorMessage = Amproid.getAppContext().getString(stringResource);
    }


    boolean isLoginShouldRetry()
    {
        return loginShouldRetry;
    }


    Vector<HashMap<String, String>> getPlaylists(String token)
    {
        if (baseUrl == null) {
            setErrorMessage(R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(R.string.error_blank_token);
            return new Vector<>();
        }

        int apiVersion = pingForVersion();

        errorMessage = "";

        // build query
        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "get_indexes");
        queryString.addNameValue("auth", token);
        if (apiVersion != 424000) {
            queryString.addNameValue("limit", "none");
        }
        queryString.addNameValue("type", "playlist");
        if (apiVersion >= 5000000) {
            queryString.addNameValue("include", "0");
        }

        // create URL
        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        // tags that we're interested in
        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("name");

        // make the call
        return blockingTransactionMulti(callUrl, "playlist", tagsNeeded);
    }


    Vector<Track> getTracks(String token, int count, String id, GetTracksIdType idType)
    {
        if (baseUrl == null) {
            setErrorMessage(R.string.error_invalid_server_url);
            return new Vector<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(R.string.error_blank_token);
            return new Vector<>();
        }

        int apiVersion = pingForVersion();

        errorMessage = "";

        // build query
        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("auth", token);

        // count = 0 means no limit
        if (count == 0) {
            if (apiVersion != 424000) {
                queryString.addNameValue("limit", "none");
            }
        }
        else {
            queryString.addNameValue("limit", String.valueOf(count));
        }

        // empty id means random
        if ((id == null) || id.isEmpty() || (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_NONE)) {
            queryString.addNameValue("action", "playlist_generate");
        }
        else {
            if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_ALBUM) {
                queryString.addNameValue("action", "album_songs");
            }
            else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_PLAYLIST) {
                queryString.addNameValue("action", "playlist_songs");
            }
            else if (idType == GetTracksIdType.GET_TRACKS_ID_TYPE_SONG) {
                queryString.addNameValue("action", "song");
            }
            queryString.addNameValue("filter", id);
        }

        // create URL
        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return new Vector<>();
        }

        // tags that we're interested in
        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("title");
        tagsNeeded.add("album");
        tagsNeeded.add("artist");
        tagsNeeded.add("url");
        tagsNeeded.add("art");
        tagsNeeded.add("tag");

        // make the call
        Vector<HashMap<String, String>> results = blockingTransactionMulti(callUrl, "song", tagsNeeded);

        // network failure, probably
        if (!errorMessage.isEmpty()) {
            return new Vector<>();
        }

        // transform to java style objects
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
            catch (Exception e) {
                // nothing more to do here, pictureUrl remains null, not fatal
            }

            Track track = new Track();
            track.setId(result.get("id"));
            track.setUrl(url);
            track.setPictureUrl(pictureURL);
            track.setTitle(result.get("title"));
            track.setAlbum(result.get("album"));
            track.setArtist(result.get("artist"));

            if (result.containsKey("tag")) {
                String tagsString = result.get("tag");
                if (tagsString != null) {
                    String[] tags = tagsString.split(",");

                    boolean liveFound = false;
                    for (String tag : tags) {
                        if ((tag.compareToIgnoreCase("Live") == 0) || (tag.compareToIgnoreCase("Medley") == 0) || (tag.compareToIgnoreCase("Nonstop") == 0)) {
                            liveFound = true;
                            break;
                        }
                    }

                    if (liveFound) {
                        track.setDoFade(true);
                    }
                }
            }

            returnValue.add(track);
        }

        // make the error message global if all tracks' URL failed
        if (returnValue.isEmpty()) {
            errorMessage = localErrorMessage;
        }

        return returnValue;
    }


    String handshake(String user, String psw)
    {
        if (baseUrl == null) {
            setErrorMessage(R.string.error_invalid_server_url);
            return "";
        }
        if (user.isEmpty() || psw.isEmpty()) {
            setErrorMessage(R.string.error_invalid_credentials);
            return "";
        }

        errorMessage     = "";
        loginShouldRetry = true;

        // calculate Ampache handshake hash
        String timeStr      = String.valueOf(System.currentTimeMillis() / 1000);
        String psw256       = toHexSHA256(psw);
        String handshake256 = toHexSHA256(timeStr + psw256);
        if (!errorMessage.isEmpty()) {
            return "";
        }

        // build query
        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "handshake");
        queryString.addNameValue("auth", handshake256);
        queryString.addNameValue("timestamp", timeStr);
        queryString.addNameValue("version", String.valueOf(MIN_API_VERSION));
        queryString.addNameValue("user", user);

        // create URL
        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return "";
        }

        // tags that we're interested in
        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("error");
        tagsNeeded.add("auth");
        tagsNeeded.add("api");

        // make the call
        HashMap<String, String> results = blockingTransaction(callUrl, tagsNeeded);

        // network failure, probably
        if (!errorMessage.isEmpty()) {
            return "";
        }

        // check results
        if (results.containsKey("error")) {
            errorMessage     = results.get("error");
            loginShouldRetry = false;
            return "";
        }

        if (results.isEmpty() || !results.containsKey("auth") || !results.containsKey("api")) {
            setErrorMessage(R.string.error_invalid_server_response);
            return "";
        }

        int apiVersion = apiVersionFromString(results.get("api"));
        if (apiVersion < MIN_API_VERSION) {
            setErrorMessage(R.string.error_unsupported_api);
            if (errorMessage.contains("%")) {
                errorMessage = String.format(errorMessage, apiVersion, MIN_API_VERSION);
            }
            loginShouldRetry = false;
            return "";
        }

        return results.get("auth");
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
                catch (Exception ee) {
                    // nothing to do here
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


    @SuppressLint("UseSparseArrays")
    HashMap<Integer, Vector<HashMap<String, String>>> search(String token, Bundle searchParameters)
    {
        if (baseUrl == null) {
            setErrorMessage(R.string.error_invalid_server_url);
            return new HashMap<>();
        }
        if (token.isEmpty()) {
            setErrorMessage(R.string.error_blank_token);
            return new HashMap<>();
        }

        // get parameters passed in
        String query  = searchParameters.getString("query");
        String artist = bundleGetString(searchParameters, "android.intent.extra.artist");
        String album  = bundleGetString(searchParameters, "android.intent.extra.album");
        String title  = bundleGetString(searchParameters, "android.intent.extra.title");

        errorMessage = "";

        // tags that we're interested in - songs
        Vector<String> titleTagNeeded = new Vector<>();
        titleTagNeeded.add("title");
        titleTagNeeded.add("album");
        titleTagNeeded.add("artist");

        // tags that we're interested in - everything else (this will be adjusted as search progresses)
        Vector<String> nameTagNeeded = new Vector<>();
        nameTagNeeded.add("name");

        URL url;

        // search artists

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

        // search albums

        nameTagNeeded.add("artist");

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

        Vector<HashMap<String, String>> albums = blockingTransactionMulti(url, "album", nameTagNeeded);

        // search albums by artist

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

        // search songs

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

        // create return value
        HashMap<Integer, Vector<HashMap<String, String>>> returnValue = new HashMap<>();
        returnValue.put(SEARCH_RESULTS_SONGS, songs);
        returnValue.put(SEARCH_RESULTS_ALBUMS, albums);
        returnValue.put(SEARCH_RESULTS_ARTIST_ALBUMS, artist_albums);

        return returnValue;
    }


    boolean tokenTest(String token)
    {
        if (baseUrl == null) {
            setErrorMessage(R.string.error_invalid_server_url);
            return false;
        }
        if (token.isEmpty()) {
            setErrorMessage(R.string.error_blank_token);
            return false;
        }

        errorMessage = "";

        // build query
        QueryStringBuilder queryString = new QueryStringBuilder();
        queryString.addNameValue("action", "playlist_generate");
        queryString.addNameValue("auth", token);
        queryString.addNameValue("limit", "1");
        queryString.addNameValue("format", "index");

        // create URL
        URL callUrl;
        try {
            callUrl = new URL(baseUrl.toString() + API_PATH + "?" + queryString.getQueryString());
        }
        catch (Exception e) {
            errorMessage = e.getMessage();
            return false;
        }

        // tags that we're interested in
        Vector<String> tagsNeeded = new Vector<>();
        tagsNeeded.add("error");
        tagsNeeded.add("total_count");

        // make the call
        HashMap<String, String> results = blockingTransaction(callUrl, tagsNeeded);

        // network failure, probably
        if (!errorMessage.isEmpty()) {
            return false;
        }

        return !results.containsKey("error") && results.containsKey("total_count");
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
                    if (currentElement.compareTo(repeatingTag) == 0) {
                        subResults = new HashMap<>();
                        results.add(subResults);

                        int i = 0;
                        while (i < xmlPullParser.getAttributeCount()) {
                            if (xmlPullParser.getAttributeName(i).compareTo("id") == 0) {
                                subResults.put("id", String.valueOf(xmlPullParser.getAttributeValue(i)));
                                break;
                            }
                            i++;
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


    public enum GetTracksIdType
    {
        GET_TRACKS_ID_TYPE_NONE, GET_TRACKS_ID_TYPE_SONG, GET_TRACKS_ID_TYPE_ALBUM, GET_TRACKS_ID_TYPE_PLAYLIST
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


