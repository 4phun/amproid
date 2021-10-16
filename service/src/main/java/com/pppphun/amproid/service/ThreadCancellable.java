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


import java.util.concurrent.locks.ReentrantLock;


public class ThreadCancellable extends Thread
{
    private       boolean       cancelled     = false;
    private final ReentrantLock cancelledLock = new ReentrantLock();


    public void cancel()
    {
        cancelledLock.lock();
        try {
            cancelled = true;
        }
        finally {
            cancelledLock.unlock();
        }
    }


    protected boolean isCancelled()
    {
        boolean returnValue;

        cancelledLock.lock();
        try {
            returnValue = cancelled;
        }
        finally {
            cancelledLock.unlock();
        }

        return returnValue;
    }
}
