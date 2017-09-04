/*
 * Copyright (c) 2015 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-09-04 12:59:35 -0700 (Mon, 04 Sep 2017) $' 
 * '$Revision: 1407 $'
 * 
 * Permission is hereby granted, without written agreement and without
 * license or royalty fees, to use, copy, modify, and distribute this
 * software and its documentation for any purpose, provided that the above
 * copyright notice and the following two paragraphs appear in all copies
 * of this software.
 *
 * IN NO EVENT SHALL THE UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY
 * FOR DIRECT, INDIRECT, SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES
 * ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
 * THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 * THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
 * PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
 * CALIFORNIA HAS NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES,
 * ENHANCEMENTS, OR MODIFICATIONS.
 *
 */

package org.kepler.module.webview;

import org.kepler.module.ModuleInitializer;
import org.kepler.webview.server.WebViewServer;

/**
 * Perform initialization for the web-view module.
 * 
 * @author Daniel Crawl
 * @version $Id: Initialize.java 1407 2017-09-04 19:59:35Z crawl $
 */
public class Initialize implements ModuleInitializer {
	
	/** Perform any module-specific initializations. */
	@Override
    public void initializeModule() {
	    
	    WebViewServer.initialize();	    
	}
}