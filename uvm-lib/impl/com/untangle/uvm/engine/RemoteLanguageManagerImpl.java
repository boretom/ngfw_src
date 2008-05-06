/*
 * $HeadURL: svn://chef/branch/prod/web-ui/work/src/uvm-lib/impl/com/untangle/uvm/engine/RemoteLanguageManagerImpl.java $
 * Copyright (c) 2003-2007 Untangle, Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * AS-IS and WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, TITLE, or
 * NONINFRINGEMENT.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package com.untangle.uvm.engine;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.fileupload.FileItem;
import org.apache.log4j.Logger;
import org.hibernate.Query;
import org.hibernate.Session;

import com.untangle.uvm.LanguageInfo;
import com.untangle.uvm.LanguageSettings;
import com.untangle.uvm.RemoteLanguageManager;
import com.untangle.uvm.UvmException;
import com.untangle.uvm.util.DeletingDataSaver;
import com.untangle.uvm.util.TransactionWork;

/**
 * Implementation of RemoteLanguageManagerImpl.
 *
 * @author <a href="mailto:cmatei@untangle.com">Catalin Matei</a>
 * @version 1.0
 */
class RemoteLanguageManagerImpl implements RemoteLanguageManager
{
    private static final String LANGUAGES_DIR;
    private static final String DEFAULT_LANGUAGE = "en";
	private static final int BUFFER = 2048; 

    private final Logger logger = Logger.getLogger(getClass());

    private final UvmContextImpl uvmContext;
    private LanguageSettings settings;

    RemoteLanguageManagerImpl(UvmContextImpl uvmContext) {
    	this.uvmContext = uvmContext;    	
    	
        TransactionWork tw = new TransactionWork()
        {
            public boolean doWork(Session s)
            {
                Query q = s.createQuery("from LanguageSettings");
                settings = (LanguageSettings)q.uniqueResult();

                if (null == settings) {
                	settings = new LanguageSettings();
                	settings.setLanguage(DEFAULT_LANGUAGE);
                    s.save(settings);
                }
                
                return true;
            }
        };
        uvmContext.runTransaction(tw);
    }

    // public methods ---------------------------------------------------------

	public LanguageSettings getLanguageSettings() {
		return settings;
	}

	public void setLanguageSettings(LanguageSettings settings) {
        /* delete whatever is in the db, and just make a fresh settings object */
		LanguageSettings copy = new LanguageSettings();
        settings.copy(copy);
        saveSettings(copy);
        this.settings = copy;
	}
	
    public void uploadLanguagePack(FileItem item) throws UvmException {
	    try {
	        BufferedOutputStream dest = null;
			ZipEntry entry = null;
			
	        // Open the ZIP file
		    InputStream uploadedStream = item.getInputStream();
		    ZipInputStream zis = new ZipInputStream(uploadedStream);
			while ((entry = zis.getNextEntry()) != null) {
				int count;
				byte data[] = new byte[BUFFER];
				// write the files to the disk
				FileOutputStream fos = new FileOutputStream(LANGUAGES_DIR + File.separator + entry.getName());
				dest = new BufferedOutputStream(fos, BUFFER);
				while ((count = zis.read(data, 0, BUFFER)) != -1) {
					dest.write(data, 0, count);
				}
				dest.flush();
				dest.close();
			}
			zis.close();		    	
		    uploadedStream.close();
	    } catch (IOException e) {
	    	logger.error(e);
			throw new UvmException("Upload Language Pack Failed");
	    }
    }
	
    public List<LanguageInfo> getLanguagesList() {
    	List<LanguageInfo> languages = new ArrayList<LanguageInfo>();
    	languages.add(new LanguageInfo("en", "English"));
    	languages.add(new LanguageInfo("fr", "French"));
    	languages.add(new LanguageInfo("ro", "Romanian"));
    	return languages;
    }
    
    // private methods --------------------------------------------------------
    private void saveSettings(LanguageSettings settings) {
        DeletingDataSaver<LanguageSettings> saver = 
            new DeletingDataSaver<LanguageSettings>(uvmContext,"LanguageSettings");
        this.settings = saver.saveData(settings);
    }
    
    static {
//        SKINS_DIR = System.getProperty("bunnicula.skins.dir");
    	LANGUAGES_DIR = "/tmp";
    }
}
