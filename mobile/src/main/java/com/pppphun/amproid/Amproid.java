/*
 * This file is part of Amproid
 *
 * Copyright (c) 2020. Peter Papp
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Bundle;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.jetbrains.annotations.NotNull;


public class Amproid extends Application
{
    public enum ConnectionStatus
    {
        CONNECTION_UNKNOWN, CONNECTION_NONE, CONNECTION_EXIST
    }


    static final int NETWORK_CONNECT_TIMEOUT = 25000;
    static final int NETWORK_READ_TIMEOUT    = 90000;

    private static Context appContext = null;


    @NotNull
    static String bundleGetString(Bundle bundle, String key)
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


    static Context getAppContext()
    {
        return appContext;
    }


    static ConnectionStatus getConnectionStatus()
    {
        // Note: really should use registerDefaultNetworkCallback,
        //       but that was added in API level 24 and Amproid still supports API level 21

        ConnectivityManager connectivityManager = (ConnectivityManager) appContext.getSystemService(CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return ConnectionStatus.CONNECTION_UNKNOWN;
        }

        ConnectionStatus returnValue = ConnectionStatus.CONNECTION_NONE;

        Network[] networks = connectivityManager.getAllNetworks();
        for(Network network : networks) {
            NetworkInfo networkInfo = connectivityManager.getNetworkInfo(network);

            if ((networkInfo != null) && networkInfo.isConnected()) {
                returnValue = ConnectionStatus.CONNECTION_EXIST;
                break;
            }
        }

        return returnValue;
    }


    static String getServerUrl(Account account)
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
        catch (Exception e) {
            // nothing much can be done here
        }

        return serverUrl;
    }


    static void sendLocalBroadcast(int actionStringResource, Intent intent)
    {
        if (intent == null) {
            intent = new Intent();
        }

        intent.setAction(appContext.getString(actionStringResource));

        LocalBroadcastManager.getInstance(appContext).sendBroadcast(intent);
    }


    static void sendLocalBroadcast(int actionStringResource)
    {
        sendLocalBroadcast(actionStringResource, null);
    }


    @Override
    public void onCreate()
    {
        super.onCreate();
        appContext = getApplicationContext();
    }
}
