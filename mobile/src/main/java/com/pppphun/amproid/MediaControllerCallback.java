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


import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ALBUM;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ART;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;
import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE;
import static android.support.v4.media.session.PlaybackStateCompat.STATE_PLAYING;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.fragment.app.Fragment;


final class MediaControllerCallback extends MediaControllerCompat.Callback
{
    private final AmproidMainActivity amproidMainActivity;


    public MediaControllerCallback(AmproidMainActivity amproidMainActivity)
    {
        this.amproidMainActivity = amproidMainActivity;
    }


    @Override
    public void onPlaybackStateChanged(PlaybackStateCompat state)
    {
        ProgressBar loading = amproidMainActivity.findViewById(R.id.loading);

        if ((state.getState() == PlaybackStateCompat.STATE_CONNECTING) || (state.getState() == PlaybackStateCompat.STATE_BUFFERING)) {
            if (loading != null) {
                loading.setVisibility(View.VISIBLE);
            }
            return;
        }

        if (loading != null) {
            loading.setVisibility(View.GONE);
        }

        Fragment nowPlaying = amproidMainActivity.getSupportFragmentManager().findFragmentByTag(amproidMainActivity.getString(R.string.fragment_tag_now_playing));
        if ((nowPlaying != null) && nowPlaying.isVisible()) {
            SeekBar position = null;
            try {
                position = nowPlaying.getView().findViewById(R.id.positionIndicator);
            }
            catch (Exception ignored) {
            }

            if (position != null) {
                synchronized (this) {
                    ((NowPlayingFragment) nowPlaying).setIncreasePosition(state.getState() == STATE_PLAYING);
                }

                int pos = (int) (state.getPosition() / 1000);
                if ((pos >= 0) && (pos <= position.getMax())) {
                    position.setProgress(pos);
                }
            }
        }
    }


    @Override
    public void onMetadataChanged(MediaMetadataCompat metadata)
    {
        Fragment nowPlaying = amproidMainActivity.getSupportFragmentManager().findFragmentByTag(amproidMainActivity.getString(R.string.fragment_tag_now_playing));
        if ((nowPlaying != null) && nowPlaying.isVisible()) {
            View view = nowPlaying.getView();
            if (view == null) {
                return;
            }

            TextView  title    = view.findViewById(R.id.title);
            TextView  artist   = view.findViewById(R.id.artist);
            TextView  album    = view.findViewById(R.id.album);
            ImageView art      = view.findViewById(R.id.art);
            SeekBar   position = view.findViewById(R.id.positionIndicator);

            if (title != null) {
                title.setText(metadata.getText(METADATA_KEY_TITLE));
            }
            if (artist != null) {
                artist.setText(metadata.getText(METADATA_KEY_ARTIST));
            }
            if (album != null) {
                album.setText(metadata.getText(METADATA_KEY_ALBUM));
            }
            if (art != null) {
                art.setImageBitmap(metadata.getBitmap(METADATA_KEY_ART));
            }
            if (position != null) {
                long duration = metadata.getLong(METADATA_KEY_DURATION);
                if (duration < 0) {
                    duration = 0;
                }

                position.setMin(0);
                position.setMax((int) (duration / 1000));
            }
        }
    }
}
