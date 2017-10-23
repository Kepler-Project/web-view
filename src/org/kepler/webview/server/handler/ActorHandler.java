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

import java.io.BufferedReader;
import java.io.File;
import java.io.StringReader;
import java.net.HttpURLConnection;

import org.apache.commons.io.FileUtils;
import org.kepler.webview.actor.WebViewable;
import org.kepler.webview.server.WebViewId;
import org.kepler.webview.server.WebViewServer;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import ptolemy.kernel.util.NamedObj;
import ptolemy.util.MessageHandler;

public class ActorHandler extends BaseHandler {
        
    public ActorHandler(WebViewServer server) {
        super(server);
    }

    @Override
    public void handle(RoutingContext context) {

        final long timestamp = System.currentTimeMillis();
        
        HttpServerRequest req = context.request();
        HttpServerResponse response = context.response();
        response.headers().set("Content-Type", "text/html");
        response.setChunked(true);
        
        String actorEndPath = WebViewServer.findFile(_ACTOR_END_HTML);
        if(actorEndPath == null) {
            response.write("<html>\n<body>\n<h2>actor-end.html " +
                   " not found.</h2>\n</body>\n</html>")
              .setStatusCode(HttpURLConnection.HTTP_NOT_FOUND).end();
            System.err.println("Unhandled http request (actor-end.html not found) for: " +
              context.normalisedPath());
            _server.log(req, context.user(), HttpURLConnection.HTTP_NOT_FOUND, timestamp);
            return;
        }

        String actorId = req.params().get("param0");        
        //System.out.println("wf name is: " + name +
            //" actor name is " + actorName);
        NamedObj namedObj = WebViewId.getNamedObj(actorId);
        if (namedObj == null) {
            response.write("<html>\n<body>\n<h2>Id " +
                    actorId + " not found.</h2>\n</body>\n</html>")
              .setStatusCode(HttpURLConnection.HTTP_NOT_FOUND).end();
            _server.log(req, context.user(), HttpURLConnection.HTTP_NOT_FOUND, timestamp);
            System.err.println("Unhandled http request (id not found) for: " +
                context.normalisedPath());
            return;
        }       
        
        String name = namedObj.getFullName();
        
        //System.out.println("name = " + name);
        if(!(namedObj instanceof WebViewable)) {
            response.write("<html>\n<body>\n<h2>NamedObj " +
                    name + " is not a WebViewable.</h2>\n</body>\n</html>")
              .setStatusCode(HttpURLConnection.HTTP_NOT_FOUND).end();
            _server.log(req, context.user(), HttpURLConnection.HTTP_NOT_FOUND, timestamp);
            System.err.println("Unhandled http request (namedobj is not a webviewable) for: " +
                context.normalisedPath());
            return;
        }
        
        WebViewable webView = (WebViewable) namedObj;
                
        String htmlStr;
        try {
            htmlStr = webView.getHTML();
            if(htmlStr == null) {
                MessageHandler.error("Could not read HTML for " + webView.getFullName());
            } else {
                StringBuilder buf = new StringBuilder();
                try(StringReader stringReader = new StringReader(htmlStr);
                        BufferedReader reader = new BufferedReader(stringReader);) {
                    String line = reader.readLine();
                    while(line != null) {
                        if(line.trim().equals("</body>")) {
                            String end = FileUtils.readFileToString(new File(actorEndPath));
                            end = end.replaceAll("PATH", "\"" + actorId + "\"");
                            buf.append(end);
                        }
                        buf.append(line).append('\n');
                        line = reader.readLine();
                    }
                }                          
                response.write(buf.toString());
            }
        } catch (Exception e) {
            MessageHandler.error("Error reading HTML in WebView actor.", e);
        }
        response.setStatusCode(HttpURLConnection.HTTP_OK).end();
        _server.log(req, context.user(), HttpURLConnection.HTTP_OK, timestamp);
    }
    
    private final static String _ACTOR_END_HTML = "web-view" +
            File.separator + "actor-end.html";
}
