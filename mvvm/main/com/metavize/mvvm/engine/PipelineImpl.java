/*
 * Copyright (c) 2005 Metavize Inc.
 * All rights reserved.
 *
 * This software is the confidential and proprietary information of
 * Metavize Inc. ("Confidential Information").  You shall
 * not disclose such Confidential Information.
 *
 * $Id$
 */

package com.metavize.mvvm.engine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.metavize.mvvm.tapi.Fitting;
import com.metavize.mvvm.tapi.MPipe;
import com.metavize.mvvm.tapi.Pipeline;
import com.metavize.mvvm.tran.PipelineInfo;

class PipelineImpl implements Pipeline
{
    private static final File BUNNICULA_TMP
        = new File(System.getProperty("bunnicula.tmp.dir"));

    private final List<MPipe> mPipes;
    private final List<Fitting> fittings;
    private final PipelineInfo info;
    private final String sessionPrefix;

    private final Map objects = new ConcurrentHashMap();
    private final List<File> files = new LinkedList<File>();

    private int id = 0;

    // constructors -----------------------------------------------------------

    PipelineImpl(List<MPipe> mPipes, List<Fitting> fittings, PipelineInfo info)
    {
        this.mPipes = new ArrayList<MPipe>(mPipes);
        this.fittings = new ArrayList<Fitting>(fittings);
        this.info = info;
        this.sessionPrefix = "sess-" + info.getSessionId() + "-";
    }

    // object registry methods ------------------------------------------------

    /**
     * Add object to registry. The object will remain in the token
     * manager as long as the key is held onto.
     *
     * @param object object to add.
     * @return the key.
     */
    public Long attach(Object o)
    {
        Long key;
        synchronized (objects) {
            key = new Long(++id);
        }
        objects.put(key, o);
        return key;
    }

    /**
     * Get object, by key..
     *
     * @param key object's key.
     * @return the object.
     */
    public Object getAttachment(Long key)
    {
        return objects.get(key);
    }

    /**
     * Retrieve and remove an object from the pipeline.
     *
     * @param key key of the object.
     * @return the object.
     */
    public Object detach(Long key)
    {
        return objects.remove(key);
    }

    public Fitting getClientFitting(MPipe mPipe)
    {
        int i = 0;
        for (MPipe mp : mPipes) {
            if (mp == mPipe) {
                return fittings.get(i);
            }
            i++;
        }
        throw new IllegalArgumentException("mPipe not in pipeline: " + mPipe);
    }

    public Fitting getServerFitting(MPipe mPipe)
    {
        int i = 1;
        for (MPipe mp : mPipes) {
            if (mp == mPipe) {
                return fittings.get(mPipes.size() == i ? 0 : i);
            }
            i++;
        }
        throw new IllegalArgumentException("mPipe not in pipeline: " + mPipe);
    }

    public File mktemp() throws IOException
    {
        File f = File.createTempFile(sessionPrefix, null, BUNNICULA_TMP);
        synchronized (files) {
            files.add(f);
        }
        return f;
    }

    // accessors --------------------------------------------------------------

    PipelineInfo getInfo()
    {
        return info;
    }

    // package protected methods ----------------------------------------------

    void destroy()
    {
        for (File f : files) {
            f.delete();
        }
    }
}
