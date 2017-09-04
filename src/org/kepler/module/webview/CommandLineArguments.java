/*
 * Copyright (c) 2016-2017 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-29 15:27:08 -0700 (Tue, 29 Aug 2017) $' 
 * '$Revision: 1392 $'
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

import org.kepler.CommandLineArgument;
import org.kepler.Kepler;
import org.kepler.module.ModuleCommandLineArguments;
import org.kepler.webview.server.WebViewConfiguration;

/** Module initializer that adds command line arguments for the web view module.
 * 
 *  @author Daniel Crawl
 *  @version $Id: CommandLineArguments.java 1392 2017-08-29 22:27:08Z crawl $
 */
public class CommandLineArguments implements ModuleCommandLineArguments {

    @Override
    public void initializeCommandLineArguments() {

      CommandLineArgument.add(WebViewConfiguration.WEBVIEW_START_SERVER_FLAG,
          "Start web-view server.");

      CommandLineArgument.add(WebViewConfiguration.WEBVIEW_SERVER_INSTANCES_FLAG,
          1,
          "Number of web-view server instances.",
          WebViewConfiguration.DEFAULT_WEBVIEW_SERVER_INSTANCES);

      CommandLineArgument.add(WebViewConfiguration.WEBVIEW_SERVER_PORT_FLAG,
          1,
          "Web-view server port number.",
          WebViewConfiguration.DEFAULT_WEBVIEW_SERVER_PORT);

      CommandLineArgument.add(WebViewConfiguration.WEBVIEW_SERVER_ROOT_DIR_FLAG,
          1,
          "Web-view server root directory.",
          WebViewConfiguration.DEFAULT_WEBVIEW_SERVER_ROOT_DIR);
      
      CommandLineArgument.add(WebViewConfiguration.WEBVIEW_SERVER_DAEMON_FLAG,
          "Run web-view server as daemon.")
          .setAction(Shutdown.class.getName(), "waitForShutdown");
      
      if(WebViewConfiguration.shouldStartHttpServerAsDaemon()) {
          // NOTE: need to call setRunApplication here instead of Initialize,
          // since Initialize is after the application is starting.
          Kepler.setRunApplication(Shutdown.class.getName(), "waitForShutdown");
      }

    }
}