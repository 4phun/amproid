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


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.media.session.MediaSessionCompat;


final class AmproidBroadcastReceiver extends BroadcastReceiver
{
    private final MediaSessionCompat.Callback mediaSessionCallback;


    // this constructor is provided for ENTER_CAR_MODE receiver defined in the manifest
    public AmproidBroadcastReceiver()
    {
        this.mediaSessionCallback = null;
    }


    public AmproidBroadcastReceiver(MediaSessionCompat.Callback mediaSessionCallback)
    {
        this.mediaSessionCallback = mediaSessionCallback;
    }


    @Override
    public void onReceive(Context context, Intent intent)
    {
        if ((intent.getAction() != null) && intent.getAction().equals("android.media.AUDIO_BECOMING_NOISY")) {
            if (mediaSessionCallback != null) {
                mediaSessionCallback.onPause();
            }
            return;
        }
        if ((intent.getAction() != null) && intent.getAction().equals("android.app.action.ENTER_CAR_MODE")) {
            context.startService(new Intent(context, AmproidService.class));
        }
    }
}
