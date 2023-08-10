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


import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;

import com.pppphun.amproid.shared.Amproid;

import java.net.URL;
import java.util.Arrays;
import java.util.Vector;


public class Track
{
    public enum ShuffleSource
    {
        SHUFFLE_SOURCE_NONE,
        SHUFFLE_SOURCE_SHUFFLE,
        SHUFFLE_SOURCE_FAVORIE,
        SHUFFLE_SOURCE_RECENTLY_ADDED
    }

    private String        id;
    private String        albumId;
    private String        artistId;
    private URL           url;
    private URL           pictureUrl;
    private String        title;
    private String        album;
    private String        artist;
    private boolean       radio;
    private ShuffleSource shuffleSource;

    private final Vector<String> tags;


    public Track()
    {
        id            = "";
        url           = null;
        pictureUrl    = null;
        title         = "Unknown";
        album         = "Unknown";
        artist        = "Unknown";
        radio         = false;
        tags          = new Vector<>();
        shuffleSource = ShuffleSource.SHUFFLE_SOURCE_NONE;
    }


    public void addTags(String twofiftyfiveSeparatedTags)
    {
        if (twofiftyfiveSeparatedTags == null) {
            return;
        }

        String[] tags = twofiftyfiveSeparatedTags.split(String.valueOf((char) 255));
        this.tags.addAll(Arrays.asList(tags));
    }


    public String getAlbum()
    {
        return album;
    }


    public void setAlbum(String album)
    {
        this.album = album;
    }


    public String getAlbumId()
    {
        return albumId;
    }


    public void setAlbumId(String albumId)
    {
        this.albumId = albumId;
    }


    public String getArtist()
    {
        return artist;
    }


    public void setArtist(String artist)
    {
        this.artist = artist;
    }


    public String getArtistId()
    {
        return artistId;
    }


    public void setArtistId(String artistId)
    {
        this.artistId = artistId;
    }


    public String getId()
    {
        return id;
    }


    public void setId(String id)
    {
        this.id = id;
    }


    public URL getPictureUrl()
    {
        return pictureUrl;
    }


    public void setPictureUrl(URL pictureUrl)
    {
        this.pictureUrl = pictureUrl;
    }


    public Vector<String> getTagsFiltered()
    {
        Vector<String> filteredTags = new Vector<>();

        for (String tag : tags) {
            if (getFadeTags().contains(tag)) {
                continue;
            }
            filteredTags.add(tag);
        }

        return filteredTags;
    }


    public String getTitle()
    {
        String title = this.title;

        SharedPreferences preferences = Amproid.getAppContext().getSharedPreferences(Amproid.getAppContext().getString(R.string.options_preferences), Context.MODE_PRIVATE);
        if (preferences.getBoolean(Amproid.getAppContext().getString(R.string.show_shuffle_in_title_preference), Amproid.DEFAULT_SHOW_SHUFFLE_IN_TITLE)) {
            if (shuffleSource == ShuffleSource.SHUFFLE_SOURCE_FAVORIE) {
                title = "\uD83D\uDC9F " +title;
            }
            else if (shuffleSource == ShuffleSource.SHUFFLE_SOURCE_RECENTLY_ADDED) {
                title = "\uD83C\uDD95 " + title;
            }
            else if (shuffleSource == ShuffleSource.SHUFFLE_SOURCE_SHUFFLE) {
                title = "\uD83D\uDD00 " + title;
            }
        }

        return title;
    }


    public void setTitle(String title)
    {
        this.title = title;
    }


    public URL getUrl()
    {
        return url;
    }


    public void setUrl(URL url)
    {
        this.url = url;
    }


    public boolean isDoFade()
    {
        if (isRadio()) {
            return true;
        }

        boolean doFade = false;
        for (String tag : tags) {
            if (getFadeTags().contains(tag)) {
                doFade = true;
                break;
            }
        }
        return doFade;
    }


    public boolean isInvalid()
    {
        return (url == null) || (id.isEmpty());
    }


    public boolean isRadio()
    {
        return radio;
    }


    public void setRadio(boolean radio)
    {
        this.radio = radio;
    }


    public void setShuffleSource(ShuffleSource shuffleSource)
    {
        this.shuffleSource = shuffleSource;
    }


    private Vector<String> getFadeTags()
    {
        TypedArray fadeTags = Amproid.getAppContext().getResources().obtainTypedArray(R.array.fade_tags);

        Vector<String> returnValue = new Vector<>();
        for (int i = 0; i < fadeTags.length(); i++) {
            returnValue.add(fadeTags.getString(i));
        }

        fadeTags.recycle();
        return returnValue;
    }
}
