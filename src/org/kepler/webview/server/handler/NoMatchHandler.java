/*
 *
 * Copyright (c) 2015 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-23 22:42:39 -0700 (Wed, 23 Aug 2017) $' 
 * '$Revision: 1375 $'
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
package org.kepler.webview.server.handler;

import java.io.File;
import java.net.HttpURLConnection;

import org.kepler.webview.server.WebViewConfiguration;
import org.kepler.webview.server.WebViewServer;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

public class NoMatchHandler extends BaseHandler {
    
    public NoMatchHandler(WebViewServer server) {
        super(server);
        
        _allowWorkflowDownloads = WebViewConfiguration.getHttpServerAllowWorkflowDownloads();
    }

    @Override
    public void handle(RoutingContext context) {
        
        long timestamp = System.currentTimeMillis();
        
        HttpServerRequest req = context.request();
        String normalizedPath = context.normalisedPath();
        
        String path = WebViewServer.findFile(normalizedPath);
        boolean isDir = false;
        if(path != null) {
            isDir = new File(path).isDirectory();
            if(!isDir && (_allowWorkflowDownloads || !path.endsWith(".kar"))) {
                context.response().sendFile(path);
                _server.log(req, HttpURLConnection.HTTP_OK, timestamp, new File(path).length());
                return;
            }
        }
        
        int error;
        String message;
        if(isDir) {
            File indexFile = new File(path, "index.html");
            if(indexFile.exists()) {
                context.response().sendFile(indexFile.getAbsolutePath());
                _server.log(req, HttpURLConnection.HTTP_OK, timestamp, new File(path).length());
                return;
            }
            System.err.println("Unhandled http request (directory) for: " + normalizedPath);
            error = HttpURLConnection.HTTP_FORBIDDEN;
            message = "File " + normalizedPath + ": permission denied.";                
        } else if(path != null && path.endsWith(".kar") && !_allowWorkflowDownloads) {
            System.err.println("Unhandled http request (workflow permission denied) for: " + normalizedPath);
            error = HttpURLConnection.HTTP_FORBIDDEN;            
            message = "File " + normalizedPath + ": permission denied.";
        } else {
            System.err.println("Unhandled http request (file not found) for: " + normalizedPath);
            error = HttpURLConnection.HTTP_NOT_FOUND;
            message = "File " + normalizedPath + " not found.";
        }
        
        context.response().headers().set("Content-Type", "text/html");
        // NOTE: always return not found since we do not want to disclose
        // the presence of directories.
        context.response().setChunked(true)
            .write("<html>\n<body>\n<h2>" + message + "</h2>\n</body>\n</html>")
            .setStatusCode(HttpURLConnection.HTTP_NOT_FOUND)
            .end();
        _server.log(req, error, timestamp);            
    }

    /** If true, allow downloads for workflow files. */
    private boolean _allowWorkflowDownloads;
}
