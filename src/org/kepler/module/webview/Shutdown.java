/*
 * Copyright (c) 2015-2017 The Regents of the University of California.
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

import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.kepler.module.ModuleShutdownable;
import org.kepler.webview.server.WebViewServer;

import ptolemy.util.MessageHandler;

/** Perform cleanup for the web-view module.
 * 
 * @author Daniel Crawl
 * @version $Id: Shutdown.java 1407 2017-09-04 19:59:35Z crawl $
 */
public class Shutdown implements ModuleShutdownable
{

    /** Perform any module-specific cleanup. */
    @Override
    public void shutdownModule() {
        
        WebViewServer.shutdown();
        
        Set<String> ids = WebViewServer.vertx().deploymentIDs();
        WebViewServer.vertx().undeploy(ids.iterator().next(),
            result -> {
                shutdownLatch.countDown();
            }            
        );        
        waitForShutdown(null);
        
    }
    
    /** Block until the servers have shutdown. 
     *  @param args The command line arguments (if any). These are present
     *  since this method is called from org.kepler.Kepler via reflection
     *  for -wvdaemon. 
     */
    public static void waitForShutdown(String[] args) {
        try {
            shutdownLatch.await();
        } catch (InterruptedException e) {
            MessageHandler.error("Interrupted waiting for shut down.", e);
        }
    }
    
    /** A count down latch to denote when the server(s) have shut down. */
    public final static CountDownLatch shutdownLatch = new CountDownLatch(1);
}