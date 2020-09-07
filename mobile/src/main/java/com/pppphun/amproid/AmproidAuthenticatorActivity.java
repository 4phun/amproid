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

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import java.net.MalformedURLException;
import java.net.URL;

import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;


public class AmproidAuthenticatorActivity extends AccountAuthenticatorActivity
{
    @SuppressWarnings ("rawtypes")
    private ArrayAdapter accountItems = null;   // accounts in account selector drop-down


    // initialize activity
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // get and check data passed in from whatever started this activity
        final Bundle extras = getIntent().getExtras();
        if ((extras == null) || !extras.getString(KEY_ACCOUNT_TYPE, "").equals(getString(R.string.account_type))) {
            Toast.makeText(getApplicationContext(), R.string.error_account_type_mismatch, Toast.LENGTH_SHORT).show();
            finish();
        }

        // build UI from layout XML
        setContentView(R.layout.activity_authenticator);

        // clear text warning only on Pie and up
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
            findViewById(R.id.clear_text_warning).setVisibility(View.GONE);
        }

        // handle add and edit buttons
        findViewById(R.id.add_account_button).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // display "add account" dialog, see below
                addAccountDialog();
            }
        });
        findViewById(R.id.manage_account_button).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // display Android's Accounts activity
                startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS));
            }
        });

        // handle "select account" button
        findViewById(R.id.select_account_button).setOnClickListener(new View.OnClickListener()
        {
            @SuppressLint ("ApplySharedPref")
            @Override
            public void onClick(View v)
            {
                // get and check selected account name from drop-down
                Spinner accountSelector = findViewById(R.id.account_selector);
                String  selectedAccount = (String) accountSelector.getSelectedItem();
                if ((selectedAccount == null) || selectedAccount.isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.error_no_account_selection, Toast.LENGTH_SHORT).show();
                    return;
                }

                // save to preferences
                SharedPreferences        preferences       = getSharedPreferences(getString(R.string.account_preferences), Context.MODE_PRIVATE);
                SharedPreferences.Editor preferencesEditor = preferences.edit();
                preferencesEditor.putString(getString(R.string.account_selected_preference), selectedAccount);
                preferencesEditor.commit();

                // create intent to report results
                final Intent intent = new Intent();
                intent.putExtra(KEY_ACCOUNT_NAME, selectedAccount);
                intent.putExtra(KEY_ACCOUNT_TYPE, getString(R.string.account_type));

                // this is for Android's built-in "Accounts" settings
                setAccountAuthenticatorResult(intent.getExtras());
                setResult(RESULT_OK, intent);

                // send broadcast too, this will be caught by the main Amproid service
                // this is necessary because services can not do "startActivityForResult", also they must start activities with FLAG_ACTIVITY_NEW_TASK flag on
                Amproid.sendLocalBroadcast(R.string.account_selected_broadcast_action, intent);

                // OK now we're done for good
                finish();
            }
        });

        // handle "cancel" button
        findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                // just go away, nothing else to do here
                finish();
            }
        });

        // create and attach adapter for accounts in account selector drop-down
        accountItems = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item);
        Spinner accountSelector = findViewById(R.id.account_selector);
        accountSelector.setAdapter(accountItems);

        // add accounts to the drop-down
        accountsToSpinner();

        // auto-start "add account" dialog if there are no existing accounts or if specifically requested
        if ((accountItems.getCount() == 0) || ((extras != null) && (extras.getBoolean("add_new", false)))) {
            addAccountDialog();
        }
    }


    @Override
    protected void onRestart()
    {
        super.onRestart();

        // have to refresh accounts
        accountItems.clear();
        accountsToSpinner();
    }


    private void accountsToSpinner()
    {
        // get a reference to the spinner
        Spinner accountSelector = findViewById(R.id.account_selector);

        // get existing accounts
        AccountManager accountManager = AccountManager.get(this);
        Account[]      accounts       = accountManager.getAccountsByType(getString(R.string.account_type));

        // get last used account's name
        SharedPreferences preferences         = getSharedPreferences(getString(R.string.account_preferences), Context.MODE_PRIVATE);
        String            selectedAccountName = preferences.getString(getString(R.string.account_selected_preference), null);

        // add existing accounts to adapter created above
        int index         = 0;
        int selectedIndex = -1;
        for(Account account : accounts) {
            // noinspection unchecked - OK java why on Earth adding a String to ArrayAdapter<String> is unchecked?
            accountItems.add(account.name);

            if (account.name.equals(selectedAccountName)) {
                selectedIndex = index;
            }

            index++;
        }

        // select the selected, if any
        if (selectedIndex >= 0) {
            accountSelector.setSelection(selectedIndex);
        }
    }


    // add and edit account dialog
    @SuppressLint ("InflateParams")
    private void addAccountDialog()
    {
        // create dialog instance
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.account_add_dialog_title);

        // build UI from layout XML
        View view = getLayoutInflater().inflate(R.layout.dialog_add_account, null);
        builder.setView(view);

        // find the input fields in UI
        final TextInputEditText urlEditor  = view.findViewById(R.id.url_input);
        final TextInputEditText userEditor = view.findViewById(R.id.user_input);
        final TextInputEditText pswEditor  = view.findViewById(R.id.psw_input);

        // this is going to be needed multiple times
        final AccountManager accountManager = AccountManager.get(this);

        // handle "OK" button
        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                // get and check user entered data
                String url  = urlEditor.getText() == null ? "" : urlEditor.getText().toString();
                String user = userEditor.getText() == null ? "" : userEditor.getText().toString();
                String psw  = pswEditor.getText() == null ? "" : pswEditor.getText().toString();
                if (url.isEmpty() || user.isEmpty() || psw.isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.error_invalid_user_input, Toast.LENGTH_SHORT).show();
                    return;
                }

                // validate url
                // TODO make this more strict
                URL urlInstance;
                try {
                    urlInstance = new URL(url);
                }
                catch (MalformedURLException e) {
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                String host = urlInstance.getHost();
                if (host.isEmpty() || urlInstance.getProtocol().isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.error_invalid_user_input, Toast.LENGTH_SHORT).show();
                    return;
                }

                // build account name, this will be displayed by Android's built-in "Accounts" settings
                String accountName = user + "@" + host;

                // create new account instance
                Account account = new Account(accountName, getString(R.string.account_type));

                // build data to be stored with account
                Bundle extra = new Bundle();
                extra.putString("url", urlInstance.toString());
                extra.putString("user", user);

                // save new account to Android's built-in "Accounts" settings
                accountManager.addAccountExplicitly(account, psw, extra);

                // add new account to the account selector drop-down
                // noinspection unchecked - OK java why on Earth adding a String to ArrayAdapter<String> is unchecked?
                accountItems.add(accountName);

                // make it the selection
                Spinner accountSelector = findViewById(R.id.account_selector);
                accountSelector.setSelection(accountItems.getCount() - 1);
            }
        });

        // handle "Cancel" button
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                // nothing much to do here
                dialog.cancel();
            }
        });

        // dialog is now built completely, let's display it
        builder.show();
    }
}
