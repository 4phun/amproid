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

package com.pppphun.amproid;


import android.widget.Toast;

import com.pppphun.amproid.service.AmproidService;


public class AmproidServiceBinderCallback implements AmproidService.IAmproidServiceBinderCallback
{
    private final AmproidMainActivity mainActivity;


    public AmproidServiceBinderCallback(AmproidMainActivity mainActivity)
    {
        this.mainActivity = mainActivity;
    }


    @Override
    public void quitNow()
    {
        mainActivity.finish();
    }


    @Override
    public void showToast(int stringResource)
    {
        Toast.makeText(mainActivity, mainActivity.getString(stringResource), Toast.LENGTH_LONG).show();
    }


    @Override
    public void startAuthenticatorActivity()
    {
        mainActivity.startAuthenticatorActivity();
    }
}
