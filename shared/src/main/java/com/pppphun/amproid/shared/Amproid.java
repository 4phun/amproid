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

package com.pppphun.amproid.shared;


import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.WindowMetrics;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Vector;


public class Amproid extends Application
{
    public static final int     NETWORK_CONNECT_TIMEOUT       = 25000;
    public static final int     NETWORK_READ_TIMEOUT          = 90000;
    public static final int     NEW_TOKEN_REASON_NONE         = 0;
    public static final int     NEW_TOKEN_REASON_CACHE        = 1;
    public static final int     DEFAULT_RECENT_SONG_COUNT     = 150;
    public static final boolean DEFAULT_SHOW_SHUFFLE_IN_TITLE = false;

    public enum ScreenSizeDimension
    {
        SCREEN_SIZE_WIDTH, SCREEN_SIZE_HEIGHT
    }

    public enum ConnectionStatus
    {
        CONNECTION_UNKNOWN, CONNECTION_NONE, CONNECTION_EXIST
    }

    private static Context appContext = null;


    @NotNull
    public static String bundleGetString(Bundle bundle, String key)
    {
        if ((bundle == null) || !bundle.containsKey(key)) {
            return "";
        }

        String returnValue = bundle.getString(key);
        if (returnValue == null) {
            return "";
        }

        return returnValue;
    }


    public static Vector<HashMap<String, String>> deepCopyItems(Vector<HashMap<String, String>> items)
    {
        Vector<HashMap<String, String>> deepCopy = new Vector<>();

        for (HashMap<String, String> item : items) {
            HashMap<String, String> copy = new HashMap<>();
            for (String key : item.keySet()) {
                copy.put(key, item.get(key));
            }
            deepCopy.add(copy);
        }

        return deepCopy;
    }


    public static HashMap<Integer, Vector<HashMap<String, String>>> deepCopyItems(HashMap<Integer, Vector<HashMap<String, String>>> items)
    {
        HashMap<Integer, Vector<HashMap<String, String>>> deepCopy = new HashMap<>();

        for (Integer topKey : items.keySet()) {
            Vector<HashMap<String, String>> vector = items.get(topKey);
            if (vector != null) {
                Vector<HashMap<String, String>> vectorCopy = new Vector<>();
                for (HashMap<String, String> item : vector) {
                    HashMap<String, String> copy = new HashMap<>();
                    for (String key : item.keySet()) {
                        copy.put(key, item.get(key));
                    }
                    vectorCopy.add(copy);
                }
                @SuppressWarnings("UnnecessaryUnboxing")
                int topKeyCopy = topKey.intValue();
                deepCopy.put(topKeyCopy, vectorCopy);
            }
        }

        return deepCopy;
    }


    public static Context getAppContext()
    {
        return appContext;
    }


    public static ConnectionStatus getConnectionStatus()
    {
        // TODO use registerDefaultNetworkCallback instead of NetworkInfo

        ConnectivityManager connectivityManager = (ConnectivityManager) appContext.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return ConnectionStatus.CONNECTION_UNKNOWN;
        }

        ConnectionStatus returnValue = ConnectionStatus.CONNECTION_NONE;

        Network[] networks = connectivityManager.getAllNetworks();
        for (Network network : networks) {
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);

            if ((networkInfo != null) && networkInfo.isConnected()) {
                returnValue = ConnectionStatus.CONNECTION_EXIST;
                break;
            }
        }

        return returnValue;
    }


    public static int getRecentSongCount()
    {
        SharedPreferences preferences = Amproid.getAppContext().getSharedPreferences(Amproid.getAppContext().getString(R.string.options_preferences), Context.MODE_PRIVATE);
        return preferences.getInt(Amproid.getAppContext().getString(R.string.random_count_preference), DEFAULT_RECENT_SONG_COUNT);
    }


    public static String getServerUrl(Account account)
    {
        if (account == null) {
            return null;
        }

        AccountManager accountManager = AccountManager.get(appContext);
        if (accountManager == null) {
            return null;
        }

        String serverUrl = null;
        try {
            serverUrl = accountManager.getUserData(account, "url");
        }
        catch (Exception ignored) {
        }

        return serverUrl;
    }


    public static Vector<String> loadRecentSearches()
    {
        Vector<String>    searches    = new Vector<>();
        SharedPreferences preferences = appContext.getSharedPreferences(appContext.getString(R.string.search_preferences), Context.MODE_PRIVATE);

        int key = 0;
        while (preferences.contains(Integer.toString(key))) {
            searches.add(preferences.getString(Integer.toString(key), ""));
            key++;
        }

        return searches;
    }


    @SuppressLint("ApplySharedPref")
    public static void saveRecentSearches(Vector<String> searches)
    {
        SharedPreferences        preferences       = appContext.getSharedPreferences(appContext.getString(R.string.search_preferences), Context.MODE_PRIVATE);
        SharedPreferences.Editor preferencesEditor = preferences.edit();

        int key = 0;
        while (key < searches.size()) {
            preferencesEditor.putString(Integer.toString(key), searches.get(key));
            key++;
        }
        while (preferences.contains(Integer.toString(key))) {
            preferencesEditor.remove(Integer.toString(key));
            key++;
        }

        preferencesEditor.commit();
    }


    public static int screenSize(Activity activity, ScreenSizeDimension screenSizeDimension)
    {
        try {
            WindowMetrics windowMetrics = activity.getWindowManager().getCurrentWindowMetrics();
            if (screenSizeDimension == ScreenSizeDimension.SCREEN_SIZE_WIDTH) {
                return windowMetrics.getBounds().width();
            }
            return windowMetrics.getBounds().height();
        }
        catch (Exception ignored) {
        }
        return 0;
    }


    public static void sendMessage(Handler handler, int actionStringResource, int asyncFinishedTypeResource, Bundle arguments)
    {
        if (arguments == null) {
            arguments = new Bundle();
        }

        arguments.putString(appContext.getString(R.string.msg_action), appContext.getString(actionStringResource));
        if (asyncFinishedTypeResource != 0) {
            arguments.putInt(appContext.getString(R.string.msg_async_finished_type), appContext.getResources().getInteger(asyncFinishedTypeResource));
        }

        Message msg = Message.obtain(handler);
        msg.obj = arguments;
        handler.dispatchMessage(msg);
    }


    public static void sendMessage(Handler handler, int actionStringResource, Bundle arguments)
    {
        sendMessage(handler, actionStringResource, 0, arguments);
    }


    public static void sendMessage(Handler handler, int actionStringResource)
    {
        sendMessage(handler, actionStringResource, 0, (Bundle) null);
    }


    public static void sendMessage(Handler handler, int actionStringResource, int asyncFinishedTypeResource, String errorMessage)
    {
        Bundle arguments = new Bundle();
        arguments.putString(appContext.getString(R.string.msg_error_message), errorMessage);

        sendMessage(handler, actionStringResource, asyncFinishedTypeResource, arguments);
    }


    public static void sendMessage(Handler handler, int actionStringResource, int asyncFinishedTypeResource, String errorMessage, int newTokenReason)
    {
        Bundle arguments = new Bundle();
        arguments.putString(appContext.getString(R.string.msg_error_message), errorMessage);
        arguments.putInt(appContext.getString(R.string.msg_new_token_reason), newTokenReason);

        sendMessage(handler, actionStringResource, asyncFinishedTypeResource, arguments);
    }


    public static void sendMessage(Handler handler, int actionStringResource, int asyncFinishedTypeResource, int errorMessageResource)
    {
        sendMessage(handler, actionStringResource, asyncFinishedTypeResource, appContext.getString(errorMessageResource));
    }


    public static boolean stringContains(String needle, String haystack)
    {
        if ((needle == null) || needle.isEmpty() || (haystack == null) || haystack.isEmpty()) {
            return false;
        }

        needle   = needle.toLowerCase(Locale.ROOT);
        haystack = haystack.toLowerCase(Locale.ROOT);

        needle   = needle.replaceAll("\\W", "");
        haystack = haystack.replaceAll("\\W", "");

        return haystack.contains(needle);
    }


    @Override
    public void onCreate()
    {
        super.onCreate();
        appContext = getApplicationContext();
    }
}
