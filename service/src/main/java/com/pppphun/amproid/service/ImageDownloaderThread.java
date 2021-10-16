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


import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;

import com.pppphun.amproid.shared.Amproid;

import java.net.HttpURLConnection;
import java.net.URL;


final class ImageDownloaderThread extends ThreadCancellable
{
    private final URL     url;
    private final Handler resultsHandler;
    private final int     maxSize;
    private final long    identifier;


    ImageDownloaderThread(URL url, Handler resultsHandler)
    {
        this.url            = url;
        this.resultsHandler = resultsHandler;

        maxSize    = 0;
        identifier = 0;
    }


    ImageDownloaderThread(URL url, Handler resultsHandler, int maxSize, long identifier)
    {
        this.url            = url;
        this.resultsHandler = resultsHandler;
        this.maxSize        = maxSize;
        this.identifier     = identifier;
    }


    public long getIdentifier()
    {
        return identifier;
    }


    @Override
    public void run()
    {
        Bitmap bitmap = null;
        try {
            if (maxSize == 0) {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);

                connection.connect();
                bitmap = BitmapFactory.decodeStream(connection.getInputStream());
                connection.disconnect();
            }
            else {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);

                BitmapFactory.Options bitmapFactoryOptions = new BitmapFactory.Options();
                bitmapFactoryOptions.inJustDecodeBounds = true;

                connection.connect();
                BitmapFactory.decodeStream(connection.getInputStream(), null, bitmapFactoryOptions);
                connection.disconnect();

                int halfHeight   = bitmapFactoryOptions.outHeight / 2;
                int halfWidth    = bitmapFactoryOptions.outWidth / 2;
                int inSampleSize = 1;

                while (((halfHeight / inSampleSize) >= maxSize) || (halfWidth / inSampleSize) >= maxSize) {
                    inSampleSize *= 2;
                }

                connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);

                bitmapFactoryOptions.inJustDecodeBounds = false;
                bitmapFactoryOptions.inSampleSize       = inSampleSize;

                connection.connect();
                bitmap = BitmapFactory.decodeStream(connection.getInputStream(), null, bitmapFactoryOptions);
                connection.disconnect();

            }

        }
        catch (Exception ignored) {
        }

        if (isCancelled() || (bitmap == null)) {
            return;
        }

        Bundle arguments = new Bundle();
        arguments.putParcelable("image", bitmap);
        arguments.putString("url", url.toString());
        Amproid.sendMessage(resultsHandler, R.string.msg_action_async_finished, R.integer.async_image_downloader, arguments);
    }
}