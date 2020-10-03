/*
 * Copyright (c) 2020 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-09-04 12:58:02 -0700 (Mon, 04 Sep 2017) $' 
 * '$Revision: 1405 $'
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

import java.net.URI;
import java.net.URISyntaxException;

import org.kepler.webview.server.WebViewConfiguration;
import org.kepler.webview.server.WebViewServer;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.Pump;
import io.vertx.ext.web.RoutingContext;

public class AuthenticatedProxyHandler implements Handler<RoutingContext> {

    public AuthenticatedProxyHandler(String path) {
        //_path = path;
        _url = WebViewConfiguration.getHttpServerAuthenticatedProxyURL(path);
        _apiKey = WebViewConfiguration.getHttpServerAuthenticatedProxyApiKey(path);
        //System.out.println("Authenticated proxy for " + path);        
    }
    
    @Override
    public void handle(RoutingContext context) {
        
        //System.out.println("auth proxy handle for " + _url);

        URI uri;
        try {
            uri = new URI(_url);
        } catch (URISyntaxException e) {
            System.err.println("Error parsing url: " + e.getMessage());
            return;
        }
        
        //System.out.println(uri.getPort() + " " + uri.getHost() + " " +
                //uri.getPath());
        
        String uriPath;
        if(uri.getPath().trim().isEmpty()) {
            uriPath = "/";
        } else {
            uriPath = uri.getPath();
        }
        
        // see if it's a websocket.
        if(uri.getScheme().equals("ws") || uri.getScheme().equals("wss")) {            
            
            ServerWebSocket serverWS = context.request().upgrade();            
            
            HttpClientOptions options = new HttpClientOptions();
            if(uri.getScheme().equals("wss")) {
                options.setSsl(true);
            }

            int port;

            if(uri.getPort() != -1) {
                port = uri.getPort();
            } else if(uri.getScheme().equals("wss")) {
                port = 443;
            } else {
                port = 80;
            }
            
            WebViewServer.vertx()
                .createHttpClient(options)
                .websocket(port, uri.getHost(),
                uriPath, destWS -> {
                
                //System.out.println("connected");
                
                    // send the api key and username.
                    destWS.writeTextMessage(new JsonObject()
                        .put("api-key", _apiKey)
                        .put("user", context.user().principal().getString("username"))
                        .encode());
                                                   
                    /*Pump pump1 =*/ Pump.pump(destWS, serverWS).start();
                    /*Pump pump2 =*/ Pump.pump(serverWS, destWS).start();
    
                    /* TODO need to stop pumps?
                    destWS.closeHandler(h -> {
                        pump1.stop();
                        pump2.stop();
                        serverWS.close();
                    });
                    
                    serverWS.closeHandler(h -> {
                        pump1.stop();
                        pump2.stop();
                        destWS.close();                    
                    });
                    */                                
            });
            
        }       
        
    }

    private String _url;
    private String _apiKey;
}
