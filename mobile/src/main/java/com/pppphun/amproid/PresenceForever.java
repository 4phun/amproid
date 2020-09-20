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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;


public class PresenceForever extends Service
{

    private final static int    NOTIFICATION_ID         = 1100;
    private final static String NOTIFICATION_CHANNEL_ID = "AmproidPresenceForeverNotificationChannel";

    private final AutoBroadcastReceiver     autoBroadcastReceiver = new AutoBroadcastReceiver();
    private       NotificationManagerCompat notificationManager   = null;


    @Override
    public void onCreate()
    {
        super.onCreate();

        registerReceiver(autoBroadcastReceiver, new IntentFilter("android.app.action.ENTER_CAR_MODE"));
        registerReceiver(autoBroadcastReceiver, new IntentFilter("android.app.action.EXIT_CAR_MODE"));
        notificationManager = NotificationManagerCompat.from(this);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_HIGH);
            notificationChannel.setLockscreenVisibility(NotificationCompat.VISIBILITY_SECRET);
            notificationChannel.setSound(null, null);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID).setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, AmproidMainActivity.class), PendingIntent.FLAG_CANCEL_CURRENT)).setContentText(getString(R.string.persistence_notification)).setContentTitle(getString(R.string.app_name)).setSmallIcon(R.drawable.ic_launcher).setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher)).setOngoing(true).setPriority(Notification.PRIORITY_HIGH).setSound(null).setVisibility(NotificationCompat.VISIBILITY_SECRET);

        startForeground(NOTIFICATION_ID, notificationBuilder.build());
    }


    @Override
    public void onDestroy()
    {
        unregisterReceiver(autoBroadcastReceiver);

        if (notificationManager != null) {
            notificationManager.cancelAll();
        }

        super.onDestroy();
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent)
    {
        // let no one bind to this service
        return null;
    }


    private final class AutoBroadcastReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if ((intent.getAction() != null) && intent.getAction().equals("android.app.action.ENTER_CAR_MODE")) {
                Intent intentAmproid = new Intent(PresenceForever.this, AmproidService.class);
                intentAmproid.putExtra("PresenceForever", true);
                startService(intentAmproid);
                return;
            }
            if ((intent.getAction() != null) && intent.getAction().equals("android.app.action.EXIT_CAR_MODE")) {
                Amproid.sendLocalBroadcast(R.string.auto_exited_broadcast_action);
            }
        }
    }
}
