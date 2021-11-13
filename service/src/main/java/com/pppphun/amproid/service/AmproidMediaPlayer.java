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


import android.media.MediaPlayer;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.session.PlaybackStateCompat;

import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;


final class AmproidMediaPlayer extends MediaPlayer
{
    private static final int EQ_PRIORITY   = 9;
    private static final int MAX_ATTEMPTS  = 5;
    private static final int ATTEMPT_DELAY = 50;

    private static final int FADE_DURATION       = 6000;
    private static final int FADE_FREQUENCY      = 40;
    private static final int FADE_DIRECTION_NONE = 0;
    private static final int FADE_DIRECTION_IN   = 1;
    private static final int FADE_DIRECTION_OUT  = 2;

    Equalizer        equalizer        = null;
    LoudnessEnhancer loudnessEnhancer = null;

    private final AmproidService amproidService;
    private final Track          track;
    private       boolean        prepared      = false;
    private       boolean        sought        = false;
    private       boolean        erred         = false;
    private       int            errorResource = R.string.error_error;
    private       boolean        errorReAuth   = false;

    private Timer positionTimer;

    private int effectsCreateAttempts = 0;
    private int equalizerAttempts     = 0;
    private int loudnessAttempts      = 0;

    private final Handler playerHandler = new Handler(Looper.getMainLooper());

    private Timer fadeTimer     = null;
    private int   fadeDirection = FADE_DIRECTION_NONE;
    private float fadeValue     = 0;

    private final Runnable setEqualizer = new Runnable()
    {
        @Override
        public void run()
        {
            if ((equalizerAttempts < MAX_ATTEMPTS) && (equalizer != null) && (amproidService.equalizerSettings != null) && (amproidService.equalizerSettings.numBands == equalizer.getNumberOfBands())) {
                equalizerAttempts++;

                short minLevel = equalizer.getBandLevelRange()[0];
                short maxLevel = equalizer.getBandLevelRange()[1];

                for (int i = 0; i < amproidService.equalizerSettings.numBands; i++) {
                    short level = amproidService.equalizerSettings.bandLevels[i];

                    if (level < minLevel) {
                        level = minLevel;
                    }
                    if (level > maxLevel) {
                        level = maxLevel;
                    }

                    try {
                        equalizer.setBandLevel((short) i, level);
                    }
                    catch (Exception e) {
                        playerHandler.postDelayed(setEqualizer, ATTEMPT_DELAY);
                        return;
                    }
                }

                try {
                    equalizer.setEnabled(true);
                }
                catch (Exception e) {
                    playerHandler.postDelayed(setEqualizer, ATTEMPT_DELAY);
                }
            }
        }
    };

    private final Runnable setLoudnessEnhancer = new Runnable()
    {
        @Override
        public void run()
        {
            if ((loudnessAttempts < MAX_ATTEMPTS) && (loudnessEnhancer != null)) {
                loudnessAttempts++;

                try {
                    loudnessEnhancer.setTargetGain(track.isRadio() ? Math.max(0, amproidService.loudnessGainSetting - 600) : amproidService.loudnessGainSetting);
                    loudnessEnhancer.setEnabled(true);
                }
                catch (Exception e) {
                    playerHandler.postDelayed(setLoudnessEnhancer, ATTEMPT_DELAY);
                }
            }
        }
    };

    private final Runnable effectCreate = new Runnable()
    {
        @Override
        public void run()
        {
            // multiple attempts because developer console reported IllegalStateException on getAudioSessionId even though this is called after onPrepared
            if ((effectsCreateAttempts < MAX_ATTEMPTS) && ((equalizer == null) || (loudnessEnhancer == null))) {
                effectsCreateAttempts++;

                try {
                    int audioSessionId = getAudioSessionId();
                    if (audioSessionId != 0) {
                        if (equalizer == null) {
                            equalizer = new Equalizer(EQ_PRIORITY, audioSessionId);
                        }
                        if (loudnessEnhancer == null) {
                            loudnessEnhancer = new LoudnessEnhancer(audioSessionId);
                        }
                    }
                }
                catch (Exception e) {
                    playerHandler.postDelayed(effectCreate, ATTEMPT_DELAY);
                    return;
                }

                if (fadeDirection == FADE_DIRECTION_NONE) {
                    setEffects();
                }
                else {
                    // must be done after a delay, otherwise fade-in will have a short but very much audible burst before sound is silenced
                    new Timer().schedule(new TimerTask()
                    {
                        @Override
                        public void run()
                        {
                            setEffects();
                        }
                    }, 80);
                }
            }
        }
    };


    AmproidMediaPlayer(AmproidService amproidService, Track track, final boolean autoStart)
    {
        super();
        this.amproidService = amproidService;
        this.track          = track;

        positionTimer = new Timer();
        positionTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                if (!isPlaying()) {
                    return;
                }

                int position;
                int duration;
                try {
                    position = getCurrentPosition();
                    duration = getDuration();
                }
                catch (Exception e) {
                    return;
                }
                if ((position < 0) || (duration < 0)) {
                    return;
                }

                if (AmproidMediaPlayer.this.track.isDoFade() && !sought && (fadeDirection == FADE_DIRECTION_NONE) && (position >= (duration - FADE_DURATION - 1000))) {
                    synchronized (this) {
                        fadeDirection = FADE_DIRECTION_OUT;
                        fadeValue     = 1.0f;
                    }
                    startFade();
                }
            }
        }, 2500, 1000);

        setOnPreparedListener(new OnPreparedListener()
        {
            @Override
            public void onPrepared(MediaPlayer mp)
            {
                prepared = true;

                if (autoStart) {
                    start();
                }
                else {
                    amproidService.stateUpdate(PlaybackStateCompat.STATE_STOPPED, getCurrentPosition());
                }

                if (AmproidMediaPlayer.this.track.getPictureUrl() != null) {
                    amproidService.downloadPicture(AmproidMediaPlayer.this.track.getPictureUrl());
                }
            }
        });

        setOnCompletionListener(new OnCompletionListener()
        {
            @Override
            public void onCompletion(MediaPlayer mp)
            {
                // make sure it actually played
                int currentPosition = 0;
                try {
                    currentPosition = getCurrentPosition();
                }
                catch (Exception ignored) {
                }
                if (currentPosition <= 0) {
                    amproidService.stateUpdate(PlaybackStateCompat.STATE_STOPPED, 0);
                    if (errorReAuth) {
                        amproidService.getNewAuthToken(amproidService.getString(errorResource));
                    }
                    else {
                        amproidService.fakeTrackMessage(R.string.error_play_error, amproidService.getString(R.string.error_error));
                    }

                    return;
                }

                amproidService.skipToNext();
            }
        });

        setOnErrorListener(new OnErrorListener()
        {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra)
            {
                int whatResource;
                int extraResource;

                switch (what) {
                    case MEDIA_ERROR_IO:
                        whatResource = R.string.media_error_io_str;
                        break;
                    case MEDIA_ERROR_MALFORMED:
                        whatResource = R.string.media_error_malformed_str;
                        break;
                    case MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                        whatResource = R.string.media_error_not_valid_for_progressive_playback_str;
                        break;
                    case MEDIA_ERROR_SERVER_DIED:
                        whatResource = R.string.media_error_server_died_str;
                        break;
                    case MEDIA_ERROR_TIMED_OUT:
                        whatResource = R.string.media_error_timed_out_str;
                        break;
                    case MEDIA_ERROR_UNSUPPORTED:
                        whatResource = R.string.media_error_unsupported_str;
                        break;
                    default:
                        whatResource = R.string.media_error_unknown_str;
                }

                switch (extra) {
                    case MEDIA_ERROR_IO:
                        extraResource = R.string.media_error_io_str;
                        break;
                    case MEDIA_ERROR_MALFORMED:
                        extraResource = R.string.media_error_malformed_str;
                        break;
                    case MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                        extraResource = R.string.media_error_not_valid_for_progressive_playback_str;
                        break;
                    case MEDIA_ERROR_SERVER_DIED:
                        extraResource = R.string.media_error_server_died_str;
                        break;
                    case -2147483648:   // MEDIA_ERROR_SYSTEM ("session expired": the media player can not play the server's error message)
                        extraResource = R.string.media_error_system;
                        errorReAuth = true;
                        break;
                    case MEDIA_ERROR_TIMED_OUT:
                        extraResource = R.string.media_error_timed_out_str;
                        break;
                    case MEDIA_ERROR_UNSUPPORTED:
                        extraResource = R.string.media_error_unsupported_str;
                        break;
                    default:
                        extraResource = R.string.media_error_unknown_str;
                }

                erred         = true;
                errorResource = extraResource == R.string.media_error_unknown_str ? whatResource : extraResource;

                return false;
            }
        });

        if (track.isDoFade()) {
            synchronized (this) {
                fadeValue     = 0.0f;
                fadeDirection = FADE_DIRECTION_IN;
            }
        }
        else {
            synchronized (this) {
                fadeValue     = 1.0f;
                fadeDirection = FADE_DIRECTION_NONE;
            }
        }
        setVolume(fadeValue, fadeValue);

        try {
            setDataSource(amproidService, Uri.parse(track.getUrl().toString()));
        }
        catch (Exception e) {
            amproidService.fakeTrackMessage(R.string.error_set_data_source_error, e.getMessage());

            erred         = true;
            errorResource = R.string.error_set_data_source_error;

            return;
        }

        // this will trigger the OnPreparedListener that was set up in the constructor
        prepareAsync();

        amproidService.genuineTrackMessage(track);
    }


    @Override
    public void prepareAsync()
    {
        amproidService.stateUpdate(PlaybackStateCompat.STATE_BUFFERING, 0);

        try {
            super.prepareAsync();
        }
        catch (Exception e) {
            amproidService.fakeTrackMessage(amproidService.getString(R.string.error_prepare_error), (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

            erred         = true;
            errorResource = R.string.error_prepare_error;
        }
    }


    @Override
    public void start()
    {
        if (!prepared) {
            return;
        }

        effectsCreateAttempts = 0;
        equalizerAttempts     = 0;
        loudnessAttempts      = 0;

        amproidService.genuineTrackMessage(track);
        amproidService.savePlayMode();

        try {
            super.start();
        }
        catch (Exception e) {
            amproidService.fakeTrackMessage(R.string.error_play_error, (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

            erred         = true;
            errorResource = R.string.error_play_error;

            return;
        }

        playerHandler.post(effectCreate);

        if (fadeDirection == FADE_DIRECTION_IN) {
            startFade();
        }

        amproidService.mediaSessionUpdateDurationPosition();
    }


    @Override
    public void stop()
    {
        amproidService.stateUpdate(PlaybackStateCompat.STATE_STOPPED, 0);

        prepared = false;

        try {
            super.stop();
        }
        catch (Exception e) {
            amproidService.fakeTrackMessage(R.string.error_stop_error, (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

            erred         = true;
            errorResource = R.string.error_stop_error;
        }
    }


    @Override
    public void pause()
    {
        amproidService.stateUpdate(PlaybackStateCompat.STATE_PAUSED, getCurrentPosition());

        try {
            super.pause();
        }
        catch (Exception e) {
            amproidService.fakeTrackMessage(R.string.error_pause_error, (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

            erred         = true;
            errorResource = R.string.error_pause_error;
        }
    }


    @Override
    public void seekTo(int millisecond)
    {
        amproidService.stateUpdate(isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED, millisecond);

        try {
            super.seekTo(millisecond);
            sought = true;
        }
        catch (Exception e) {
            amproidService.fakeTrackMessage(R.string.error_error, (e.getMessage() == null) || e.getMessage().isEmpty() ? e.getClass().toString() : e.getMessage());

            erred         = true;
            errorResource = R.string.error_error;
        }
    }


    @Override
    public void release()
    {
        positionTimer.cancel();
        positionTimer.purge();
        positionTimer = null;

        if (fadeTimer != null) {
            fadeTimer.cancel();
            fadeTimer.purge();
            fadeTimer = null;
        }

        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        if (loudnessEnhancer != null) {
            loudnessEnhancer.release();
            loudnessEnhancer = null;
        }

        super.release();
    }


    URL getPictureUrl()
    {
        if (track == null) {
            return null;
        }
        return track.getPictureUrl();
    }


    Track getTrack()
    {
        return track;
    }


    boolean isRadio()
    {
        return track.isRadio();
    }


    void setEffects()
    {
        playerHandler.post(setEqualizer);
        playerHandler.post(setLoudnessEnhancer);
    }


    boolean wasError()
    {
        return erred;
    }


    private void startFade()
    {
        if (track == null) {
            return;
        }

        if (fadeTimer == null) {
            fadeTimer = new Timer();

            fadeTimer.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    if (fadeDirection == FADE_DIRECTION_NONE) {
                        return;
                    }

                    if (isPlaying()) {
                        float step = FADE_DURATION;
                        step = (fadeDirection == FADE_DIRECTION_IN ? 1f : -1f) / (step / FADE_FREQUENCY);
                        fadeValue += step;
                    }

                    setVolume(Math.max(Math.min(fadeValue, 1), 0), Math.max(Math.min(fadeValue, 1), 0));

                    if ((fadeValue > 1) || (fadeValue < 0)) {
                        fadeTimer.cancel();
                        fadeTimer.purge();
                        fadeTimer = null;

                        fadeDirection = FADE_DIRECTION_NONE;
                    }
                }
            }, FADE_FREQUENCY, FADE_FREQUENCY);
        }
    }
}
