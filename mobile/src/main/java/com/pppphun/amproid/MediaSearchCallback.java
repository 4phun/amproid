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


import android.os.Bundle;
import android.support.v4.media.MediaBrowserCompat;
import android.view.View;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;

import java.util.List;


final class MediaSearchCallback extends MediaBrowserCompat.SearchCallback
{
    private final AmproidMainActivity amproidMainActivity;


    public MediaSearchCallback(AmproidMainActivity amproidMainActivity)
    {
        this.amproidMainActivity = amproidMainActivity;
    }


    @Override
    public void onSearchResult(@NonNull String query, Bundle extras, @NonNull List<MediaBrowserCompat.MediaItem> items)
    {
        ProgressBar loading = amproidMainActivity.findViewById(R.id.loading);
        if (loading != null) {
            loading.setVisibility(View.GONE);
        }
    }
}
