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

import java.net.URL;


class Track
{
    private String  id;
    private URL     url;
    private URL     pictureUrl;
    private String  title;
    private String  album;
    private String  artist;
    private boolean doFade;


    Track()
    {
        id         = "";
        url        = null;
        pictureUrl = null;
        title      = "Unknown";
        album      = "Unknown";
        artist     = "Unknown";
        doFade     = false;
    }


    String getAlbum()
    {
        return album;
    }


    void setAlbum(String album)
    {
        this.album = album;
    }


    String getArtist()
    {
        return artist;
    }


    void setArtist(String artist)
    {
        this.artist = artist;
    }


    String getId()
    {
        return id;
    }


    void setId(String id)
    {
        this.id = id;
    }


    URL getPictureUrl()
    {
        return pictureUrl;
    }


    void setPictureUrl(URL pictureUrl)
    {
        this.pictureUrl = pictureUrl;
    }


    String getTitle()
    {
        return title;
    }


    void setTitle(String title)
    {
        this.title = title;
    }


    URL getUrl()
    {
        return url;
    }


    void setUrl(URL url)
    {
        this.url = url;
    }


    public boolean isDoFade()
    {
        return doFade;
    }


    public void setDoFade(boolean doFade)
    {
        this.doFade = doFade;
    }


    boolean isInvalid()
    {
        return (url == null) || (id.isEmpty());
    }

}
