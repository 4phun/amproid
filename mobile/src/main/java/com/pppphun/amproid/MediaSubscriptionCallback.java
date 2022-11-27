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


import android.support.v4.media.MediaBrowserCompat;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;

import java.util.List;
import java.util.Objects;
import java.util.Vector;


final class MediaSubscriptionCallback extends MediaBrowserCompat.SubscriptionCallback
{
    private final AmproidMainActivity amproidMainActivity;


    public MediaSubscriptionCallback(AmproidMainActivity amproidMainActivity)
    {
        this.amproidMainActivity = amproidMainActivity;
    }


    @Override
    public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children)
    {
        try {
            amproidMainActivity.findViewById(R.id.loading).setVisibility(View.GONE);
        }
        catch (Exception ignored) {
        }

        if (parentId.equals(amproidMainActivity.getMediaBrowser().getRoot())) {
            TabLayout tabs = amproidMainActivity.findViewById(R.id.tabs);
            if (tabs != null) {
                int addedCount = 0;
                for (int i = 0; i < children.size(); i++) {
                    String title = amproidMainActivity.getString(R.string.unknown);
                    try {
                        title = Objects.requireNonNull(children.get(i).getDescription().getTitle()).toString();
                    }
                    catch (Exception ignored) {
                    }
                    String id = children.get(i).getDescription().getMediaId();

                    boolean found = false;
                    int     j     = 0;
                    while (!found && (j < tabs.getTabCount())) {
                        String tag = null;
                        try {
                            tag = (String) Objects.requireNonNull(tabs.getTabAt(j)).getTag();
                        }
                        catch (Exception ignored) {
                        }

                        if ((tag != null) && tag.equals(id)) {
                            found = true;
                        }
                        else {
                            j++;
                        }
                    }
                    if (!found) {
                        tabs.addTab(tabs.newTab().setText(title).setTag(id));
                        addedCount++;
                    }
                }
                if (addedCount == 1) {
                    tabs.selectTab(tabs.getTabAt(tabs.getTabCount() - 1));
                }

                Vector<Integer> toBeRemoved = new Vector<>();
                for (int i = 0; i < tabs.getTabCount(); i++) {
                    String tag = null;
                    try {
                        tag = (String) Objects.requireNonNull(tabs.getTabAt(i)).getTag();
                    }
                    catch (Exception ignored) {
                    }
                    if ((tag == null) || tag.equals(amproidMainActivity.getString(R.string.fragment_tag_now_playing))) {
                        continue;
                    }

                    boolean found = false;
                    int     j     = 0;
                    while (!found && (j < children.size())) {
                        String id = children.get(j).getDescription().getMediaId();
                        if ((id != null) && id.equals(tag)) {
                            found = true;
                        }
                        else {
                            j++;
                        }
                    }
                    if (!found) {
                        toBeRemoved.add(i);
                    }
                }
                for (Integer index: toBeRemoved) {
                    tabs.removeTabAt(index);
                }
            }
        }
        else {
            Fragment mediaBrowser = amproidMainActivity.getSupportFragmentManager().findFragmentByTag(amproidMainActivity.getString(R.string.fragment_tag_media_browser));
            if ((mediaBrowser != null) && mediaBrowser.isVisible()) {
                ((MediaBrowserFragment)mediaBrowser).setMediaItems(children);
            }
        }
    }
}
