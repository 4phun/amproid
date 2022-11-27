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
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.util.Timer;
import java.util.TimerTask;


public class NowPlayingFragment extends Fragment
{
    private Timer   positionTimer    = null;
    private boolean increasePosition = false;


    public NowPlayingFragment()
    {
        super(R.layout.fragment_now_playing);
    }


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        try {
            Transition transition = TransitionInflater.from(requireContext()).inflateTransition(R.transition.fragment_change);
            setEnterTransition(transition);
            setExitTransition(transition);
        }
        catch (Exception ignored) {
        }
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(view, savedInstanceState);

        TextView  title    = view.findViewById(R.id.title);
        ImageView art      = view.findViewById(R.id.art);
        SeekBar   position = view.findViewById(R.id.positionIndicator);

        if (title != null) {
            title.setClickable(true);
            title.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    FragmentActivity activity = getActivity();
                    if (activity != null) {
                        ((AmproidMainActivity) activity).showComingUpTracks();
                    }
                }
            });
        }

        if (art != null) {
            view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener()
            {
                @Override
                public void onGlobalLayout()
                {
                    if ((view.getHeight() > 0) && (view.getWidth() > 0)) {
                        int imageSize = Math.min(view.getHeight(), view.getWidth());

                        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) art.getLayoutParams();
                        params.width  = imageSize;
                        params.height = imageSize;
                        if (imageSize == view.getHeight()) {
                            params.leftMargin = Math.round(getResources().getDimension(R.dimen.distance_from_edge));
                        }
                        art.setLayoutParams(params);

                        view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                }
            });
        }

        if (position != null) {
            position.setClickable(true);

            position.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
            {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
                {
                    if (fromUser) {
                        FragmentActivity activity = getActivity();
                        if (activity != null) {
                            ((AmproidMainActivity) activity).mediaSeek((long) progress * 1000L);
                        }
                    }
                }


                @Override
                public void onStartTrackingTouch(SeekBar seekBar)
                {
                }


                @Override
                public void onStopTrackingTouch(SeekBar seekBar)
                {
                }
            });
        }
    }


    @Override
    public void onStart()
    {
        super.onStart();

        if (positionTimer == null) {
            positionTimer = new Timer();
        }
        positionTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                boolean inc;
                synchronized (this) {
                    inc = increasePosition;
                }

                if (inc) {
                    SeekBar position = null;
                    try {
                        position = requireView().findViewById(R.id.positionIndicator);
                    }
                    catch (Exception ignored) {
                    }

                    if (position != null) {
                        int pos = position.getProgress();
                        if (pos < position.getMax()) {
                            position.setProgress(pos + 1);
                        }
                    }
                }
            }
        }, 100, 1000);
    }


    @Override
    public void onStop()
    {
        super.onStop();

        positionTimer.cancel();
        positionTimer.purge();
        positionTimer = null;
    }


    public void setIncreasePosition(boolean increasePosition)
    {
        this.increasePosition = increasePosition;
    }
}
