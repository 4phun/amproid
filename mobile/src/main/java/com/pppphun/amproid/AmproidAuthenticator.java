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

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;
import static android.accounts.AccountManager.KEY_AUTHTOKEN;
import static android.accounts.AccountManager.KEY_ERROR_CODE;
import static android.accounts.AccountManager.KEY_ERROR_MESSAGE;


public class AmproidAuthenticator extends AbstractAccountAuthenticator
{
    private final Context context;


    AmproidAuthenticator(Context context)
    {
        super(context);
        this.context = context;
    }


    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType)
    {
        return null;
    }


    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options)
    {
        Bundle returnValue = new Bundle();

        // create intent to open the authenticator activity, see also AmproidService's AmproidAccountManagerCallback class
        final Intent intent = new Intent(context, AmproidAuthenticatorActivity.class);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        intent.putExtra(KEY_ACCOUNT_TYPE, accountType);
        intent.putExtra("AmproidAuthenticatorActivity", true);
        intent.putExtra("add_new", true);

        returnValue.putParcelable(AccountManager.KEY_INTENT, intent);

        return returnValue;
    }


    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options)
    {
        return null;
    }


    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options)
    {
        Bundle returnValue = new Bundle();

        // just to make sure we're on the right page
        if (account.type.compareTo(context.getString(R.string.account_type)) != 0) {
            returnValue.putString(KEY_ERROR_CODE, "0001");
            returnValue.putString(KEY_ERROR_MESSAGE, context.getString(R.string.error_account_type_mismatch));
            return returnValue;
        }

        // get credentials
        String url  = null;
        String user = "";
        String psw  = "";

        AccountManager accountManager = AccountManager.get(context);
        if (accountManager != null) {
            try {
                url  = accountManager.getUserData(account, "url");
                user = accountManager.getUserData(account, "user");
                psw  = accountManager.getPassword(account);
            }
            catch (Exception e) {
                // nothing much can be done here
            }
        }

        String token = "";

        // send request to server and get response
        AmpacheAPICaller ampacheAPICaller = new AmpacheAPICaller(url);
        if (ampacheAPICaller.getErrorMessage().isEmpty()) {
            token = ampacheAPICaller.handshake(user, psw);
        }

        // add error message to return value if there is one
        if (!ampacheAPICaller.getErrorMessage().isEmpty()) {
            returnValue.putString(KEY_ERROR_CODE, "0001");
            returnValue.putString(KEY_ERROR_MESSAGE, ampacheAPICaller.getErrorMessage());
        }

        // add data to return value
        returnValue.putString(KEY_ACCOUNT_NAME, account.name);
        returnValue.putString(KEY_ACCOUNT_TYPE, account.type);
        returnValue.putString(KEY_AUTHTOKEN, token);

        return returnValue;
    }


    @Override
    public String getAuthTokenLabel(String authTokenType)
    {
        return null;
    }


    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options)
    {
        return null;
    }


    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features)
    {
        return null;
    }

}
