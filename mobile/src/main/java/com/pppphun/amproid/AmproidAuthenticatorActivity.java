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


import static android.accounts.AccountManager.KEY_ACCOUNT_NAME;
import static android.accounts.AccountManager.KEY_ACCOUNT_TYPE;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.pppphun.amproid.service.AmproidService;

import java.net.MalformedURLException;
import java.net.URL;


public class AmproidAuthenticatorActivity extends AppCompatActivity
{
    private ArrayAdapter<String> accountItems = null;

    private AccountAuthenticatorResponse accountAuthenticatorResponse = null;
    private Bundle                       accountAuthenticatorResult   = null;

    private AmproidService.AmproidServiceBinder amproidServiceBinder = null;


    private final ServiceConnection amproidServiceBindConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            amproidServiceBinder = (AmproidService.AmproidServiceBinder) service;
        }


        @Override
        public void onServiceDisconnected(ComponentName name)
        {
            amproidServiceBinder = null;
        }


        @Override
        public void onBindingDied(ComponentName name)
        {
            amproidServiceBinder = null;
        }


        @Override
        public void onNullBinding(ComponentName name)
        {
            amproidServiceBinder = null;
        }
    };


    public final void setAccountAuthenticatorResult(Bundle result)
    {
        accountAuthenticatorResult = result;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        amproidServiceBinder = null;

        final Bundle extras = getIntent().getExtras();
        if ((extras == null) || !extras.getString(KEY_ACCOUNT_TYPE, "").equals(getString(R.string.account_type))) {
            Toast.makeText(getApplicationContext(), R.string.error_account_type_mismatch, Toast.LENGTH_SHORT).show();
            finish();
        }

        accountAuthenticatorResponse = getIntent().getParcelableExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE);
        if (accountAuthenticatorResponse != null) {
            accountAuthenticatorResponse.onRequestContinued();
        }

        setContentView(R.layout.activity_authenticator);

        findViewById(R.id.add_account_button).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                addAccountDialog();
            }
        });

        findViewById(R.id.manage_account_button).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                startActivity(new Intent(Settings.ACTION_SYNC_SETTINGS));
            }
        });

        findViewById(R.id.select_account_button).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                Spinner accountSelector = findViewById(R.id.account_selector);
                String  selectedAccount = (String) accountSelector.getSelectedItem();
                if ((selectedAccount == null) || selectedAccount.isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.error_no_account_selection, Toast.LENGTH_SHORT).show();
                    return;
                }

                SharedPreferences        preferences       = getSharedPreferences(getString(R.string.account_preferences), Context.MODE_PRIVATE);
                SharedPreferences.Editor preferencesEditor = preferences.edit();
                preferencesEditor.putString(getString(R.string.account_selected_preference), selectedAccount);
                preferencesEditor.apply();

                final Intent intent = new Intent();
                intent.putExtra(KEY_ACCOUNT_NAME, selectedAccount);
                intent.putExtra(KEY_ACCOUNT_TYPE, getString(R.string.account_type));

                // this is for Android's built-in Accounts settings
                setAccountAuthenticatorResult(intent.getExtras());
                setResult(RESULT_OK, intent);

                // this is necessary because services can not do "startActivityForResult"
                if (amproidServiceBinder != null) {
                    amproidServiceBinder.getAmproidService().accountSelected();
                }
                finish();
            }
        });

        findViewById(R.id.cancel_button).setOnClickListener(new OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                finish();
            }
        });

        accountItems = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item);
        Spinner accountSelector = findViewById(R.id.account_selector);
        accountSelector.setAdapter(accountItems);

        accountsToSpinner();

        if ((accountItems.getCount() == 0) || ((extras != null) && (extras.getBoolean("add_new", false)))) {
            addAccountDialog();
        }
    }


    @Override
    protected void onRestart()
    {
        super.onRestart();

        accountItems.clear();
        accountsToSpinner();
    }


    public void finish()
    {
        if (accountAuthenticatorResponse != null) {
            if (accountAuthenticatorResult != null) {
                accountAuthenticatorResponse.onResult(accountAuthenticatorResult);
            }
            else {
                accountAuthenticatorResponse.onError(AccountManager.ERROR_CODE_CANCELED, "canceled");
            }
            accountAuthenticatorResult = null;
        }
        super.finish();
    }


    @Override
    protected void onStart()
    {
        super.onStart();

        Intent serviceIntent = new Intent("amproid.intent.action.binder");
        serviceIntent.setComponent(new ComponentName("com.pppphun.amproid", "com.pppphun.amproid.service.AmproidService"));
        bindService(serviceIntent, amproidServiceBindConnection, BIND_AUTO_CREATE);
    }


    @Override
    protected void onStop()
    {
        super.onStop();

        if (amproidServiceBinder != null) {
            try {
                unbindService(amproidServiceBindConnection);
                amproidServiceBinder = null;
            }
            catch (Exception ignored) {
            }
        }
    }


    private void accountsToSpinner()
    {
        Spinner accountSelector = findViewById(R.id.account_selector);

        AccountManager accountManager = AccountManager.get(this);
        Account[]      accounts       = accountManager.getAccountsByType(getString(R.string.account_type));

        SharedPreferences preferences         = getSharedPreferences(getString(R.string.account_preferences), Context.MODE_PRIVATE);
        String            selectedAccountName = preferences.getString(getString(R.string.account_selected_preference), null);

        int index         = 0;
        int selectedIndex = -1;
        for (Account account : accounts) {
            accountItems.add(account.name);

            if (account.name.equals(selectedAccountName)) {
                selectedIndex = index;
            }

            index++;
        }

        if (selectedIndex >= 0) {
            accountSelector.setSelection(selectedIndex);
        }
    }


    private void addAccountDialog()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.account_add_dialog_title);

        View view = getLayoutInflater().inflate(R.layout.dialog_add_account, null);
        builder.setView(view);

        final TextInputEditText urlEditor  = view.findViewById(R.id.url_input);
        final TextInputEditText userEditor = view.findViewById(R.id.user_input);
        final TextInputEditText pswEditor  = view.findViewById(R.id.psw_input);

        userEditor.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {

            }


            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                String url  = urlEditor.getText() == null ? "" : urlEditor.getText().toString();
                String user = userEditor.getText() == null ? "" : userEditor.getText().toString();
                String psw  = pswEditor.getText() == null ? "" : pswEditor.getText().toString();

                if (url.isEmpty() && (user.compareTo(getString(R.string.demo_user)) == 0) && (psw.compareTo(getString(R.string.demo_psw)) == 0)) {
                    urlEditor.setText(R.string.demo_address);
                }
            }


            @Override
            public void afterTextChanged(Editable s)
            {

            }
        });
        pswEditor.addTextChangedListener(new TextWatcher()
        {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after)
            {

            }


            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count)
            {
                String url  = urlEditor.getText() == null ? "" : urlEditor.getText().toString();
                String user = userEditor.getText() == null ? "" : userEditor.getText().toString();
                String psw  = pswEditor.getText() == null ? "" : pswEditor.getText().toString();

                if (url.isEmpty() && (user.compareTo(getString(R.string.demo_user)) == 0) && (psw.compareTo(getString(R.string.demo_psw)) == 0)) {
                    urlEditor.setText(R.string.demo_address);
                }
            }


            @Override
            public void afterTextChanged(Editable s)
            {

            }
        });

        final AccountManager accountManager = AccountManager.get(this);

        builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                String url  = urlEditor.getText() == null ? "" : urlEditor.getText().toString();
                String user = userEditor.getText() == null ? "" : userEditor.getText().toString();
                String psw  = pswEditor.getText() == null ? "" : pswEditor.getText().toString();

                if (url.isEmpty() && (user.compareTo(getString(R.string.demo_user)) == 0) && (psw.compareTo(getString(R.string.demo_psw)) == 0)) {
                    url = getString(R.string.demo_address);
                }
                if (url.isEmpty() || user.isEmpty() || psw.isEmpty()) {
                    Toast.makeText(getApplicationContext(), R.string.error_invalid_user_input, Toast.LENGTH_SHORT).show();
                    return;
                }

                URL urlInstance;
                try {
                    urlInstance = new URL(url);
                }
                catch (MalformedURLException e) {
                    Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                String host = urlInstance.getHost();
                if (host.isEmpty() || (urlInstance.getProtocol().compareTo("https") != 0)) {
                    Toast.makeText(getApplicationContext(), R.string.error_invalid_user_input, Toast.LENGTH_SHORT).show();
                    return;
                }

                // account name for Android's built-in "Accounts" settings
                String accountName = user + "@" + host;

                Account account = new Account(accountName, getString(R.string.account_type));

                Bundle extra = new Bundle();
                extra.putString("url", urlInstance.toString());
                extra.putString("user", user);

                accountManager.addAccountExplicitly(account, psw, extra);

                accountItems.add(accountName);
                Spinner accountSelector = findViewById(R.id.account_selector);
                accountSelector.setSelection(accountItems.getCount() - 1);

                Button selectButton = findViewById(R.id.select_account_button);
                selectButton.performClick();
            }
        });

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener()
        {
            @Override
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.cancel();
            }
        });

        builder.show();
    }
}
