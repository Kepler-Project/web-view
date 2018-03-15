/*
 * Copyright (c) 2018 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-09-04 12:58:25 -0700 (Mon, 04 Sep 2017) $' 
 * '$Revision: 1406 $'
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
 */
package org.kepler.webview.server.app.ro;

import java.net.URI;

import org.apache.commons.httpclient.util.HttpURLConnection;
import org.kepler.webview.server.WebViewConfiguration;
import org.kepler.webview.server.WebViewServer;
import org.kepler.webview.server.app.AbstractApp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;

/** Create a research object on ROHUB.
 * 
 *  @author Daniel Crawl
 */
public class CreateRO extends AbstractApp {

    public CreateRO() {
        _authStr = WebViewConfiguration.getROHubAuthToken();
        
        String roHubStr = WebViewConfiguration.getROHubURI();
        
        try {
            _roHubURI = new URI(roHubStr);
        } catch(Exception e) {
            System.err.println("Syntax error parsing ROHUB URI: " + roHubStr);
        }
        
        _roHubPort = _roHubURI.getPort();
        if(_roHubPort == -1) {
            _roHubPort = _roHubURI.getScheme().startsWith("https") ? 443 : 80;
        }

    }
    
    @Override
    public void exec(User user, JsonObject inputs, Handler<AsyncResult<JsonArray>> handler)
        throws Exception {
                       
        if(_authStr == null) {
            handler.handle(Future.failedFuture("Server not configured with ROHUB token."));
        } else if(_roHubURI == null) {
            handler.handle(Future.failedFuture("Server configured with invalid ROHUB URI."));
        } else if(!inputs.containsKey("name")) {
            handler.handle(Future.failedFuture("Must specify name."));
        } else if(!inputs.containsKey("title")) {
            handler.handle(Future.failedFuture("Must specify title."));
        } else if(!inputs.containsKey("description")) {
            handler.handle(Future.failedFuture("Must specify description."));
        } else {
            String name = inputs.getString("name");
                                    
            HttpClientRequest request = WebViewServer.vertx().createHttpClient()
                .post(_roHubPort, _roHubURI.getHost(), _roHubURI.getPath(), response -> {
                    int status = response.statusCode();
                    if(status != HttpURLConnection.HTTP_OK &&
                        status != HttpURLConnection.HTTP_CREATED) {
                        handler.handle(Future.failedFuture("Unexpected response from ROHUB: " + status));
                    } else {
                        /*
                        response.headers().forEach(a -> {
                            System.out.println(a.getKey() + " -> " + a.getValue());
                        });
                        */
                        String location = response.headers().get("Location");
                        if(location == null) {
                            handler.handle(Future.failedFuture("No research object URI in response."));
                        } else {
                            System.out.println(location);
                            _annotateRO(location, inputs, handler);
                        }
                    }
            });
            
            MultiMap headers = request.headers();
            headers.set("Content-type", "text/plain")
                .set("Accept", "text/turtle")
                .set("slug", name)
                .set("Authorization", "Bearer " + _authStr);
            request.setFollowRedirects(true).end();
        }
    }
    
    private void _annotateRO(String location, JsonObject inputs, Handler<AsyncResult<JsonArray>> handler) {

        String title = inputs.getString("title");
        String description = inputs.getString("description");
        
        URI roURI;
        try {
            roURI = new URI(location);
        } catch(Exception e) {
            handler.handle(Future.failedFuture(new Exception("Error parsing RO URI: " + location)));
            return;
        }
        
            HttpClientRequest request = WebViewServer.vertx()
                .createHttpClient()
                .post(roURI.getHost(), roURI.getPath(), response -> {
                    int status = response.statusCode();
                    System.out.println("annotation status: " + status);
                    _stringResponse(handler, "location", location);
                    /*
                    response.bodyHandler(b -> {
                       System.out.println(b); 
                    });
                    */
            });
            
            String body = "<?xml version=\"1.0\"?>\n" + 
                    "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:prov=\"http://www.w3.org/ns/prov#\" xmlns:dct=\"http://purl.org/dc/terms/\" xmlns:pav=\"http://purl.org/pav/\" xmlns:foaf=\"http://xmlns.com/foaf/0.1/\" xmlns:skos=\"http://www.w3.org/2004/02/skos/core#\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:odrs=\"http://schema.theodi.org/odrs#\" xmlns:roes=\"http://w3id.org/ro/earth-science#\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\" xmlns:roterms=\"http://purl.org/wf4ever/roterms#\">\n" + 
                    "  <rdf:Description rdf:about=\"" + location + "\">\n" + 
                    "    <dct:title>" + title + "</dct:title>\n" + 
                    "    <dct:description>" + description + "</dct:description>\n" + 
                    "  </rdf:Description>\n" + 
                    "</rdf:RDF>\n";
            
            //System.out.println(body);
            
            request.headers()
                .set("Content-type", "application/rdf+xml")
                .set("Content-length", String.valueOf(body.length()))
                .set("Slug", "title.rdf")
                .set("Authorization", "Bearer " + _authStr)
                .set("Link", "<" + location + ">; rel=\"http://purl.org/ao/annotateResource\"");
            request.setFollowRedirects(true).write(body).end();
            
            //System.out.println(request.headers().get("Link"));
    }
    
                        
    private String _authStr;
    private URI _roHubURI;
    private int _roHubPort;
}
