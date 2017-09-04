/*
 *
 * Copyright (c) 2015 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-23 23:13:47 -0700 (Wed, 23 Aug 2017) $' 
 * '$Revision: 1382 $'
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

import org.kepler.webview.server.WebViewServer;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/** Handler base class.
 * 
 *  @author Daniel Crawl
 *  @version $Id: BaseHandler.java 1382 2017-08-24 06:13:47Z crawl $
 *  
 */
public abstract class BaseHandler implements Handler<RoutingContext> {

    public BaseHandler(WebViewServer server) {
        _server = server;
    }

    /** Do nothing. */
    @Override
    public void handle(RoutingContext context) {
        
    }

    /** Send a file with the response.
     * @param request The http request.
     * @param file The file to send.
     * @param delete If true, delete the file once response is sent. 
     */
    protected void _sendResponseWithFile(HttpServerRequest request, File file, boolean delete) {       
        if(delete) {            
            request.response().sendFile(file.getAbsolutePath(), res -> {
                // delete file after sent
                if(!file.delete()) {
                    System.err.println("WARNING: unable to delete " + file.getAbsolutePath());
                }                        
            });
        } else {
            request.response().sendFile(file.getAbsolutePath());            
        }
    }
    
    /** Send an error response.
     * @param request The http request.
     * @param errorStr The error text. 
     */
    protected void _sendResponseWithError(HttpServerRequest request, String errorStr) {
        request.response()
            .putHeader("Content-Type", "application/json")
            .setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
            .end(new JsonObject().put("error", "Error: " + errorStr).encode());
    }
    
    /** Send an success response with text.
     * @param request The http request.
     * @param contentType The value for Content-Type.
     * @param text The success text.
     */
    protected void _sendResponseWithSuccessText(HttpServerRequest request,
        String contentType, String text) {        
        request.response()
            .putHeader("Content-Type", contentType)
            .setStatusCode(HttpURLConnection.HTTP_OK)
            .end(text);        
    }

    protected WebViewServer _server;
}
