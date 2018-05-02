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

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.httpclient.util.HttpURLConnection;
import org.kepler.objectmanager.lsid.KeplerLSID;
import org.kepler.provenance.manager.ProvenanceManager;
import org.kepler.webview.server.WebViewConfiguration;
import org.kepler.webview.server.WebViewServer;
import org.kepler.webview.server.app.AbstractApp;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.client.WebClient;

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
        } else if(inputs.containsKey("runs") && inputs.getJsonArray("runs").size() < 1) {
            handler.handle(Future.failedFuture("Missing runs."));
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
                            
                            URI roURI;
                            try {
                                roURI = new URI(location);
                            } catch(Exception e) {
                                handler.handle(Future.failedFuture(new Exception("Error parsing RO URI: " + location)));
                                return;
                            }

                            //System.out.println(location);                            
                            _annotateRO(roURI, inputs, annotateHandler -> {
                               if(annotateHandler.failed() || !inputs.containsKey("runs")) {
                                   handler.handle(annotateHandler);
                               } else {
                                   _addRunsToRO(roURI, inputs, handler);
                               }                                       
                            });
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
    
    private void _addRunToRO(URI roURI, JsonArray runs, int index, Handler<AsyncResult<JsonArray>> handler) {
        // export the run as an ro
        
        KeplerLSID runLSID = null;
        try {
            runLSID = new KeplerLSID(runs.getString(index));
        } catch(Exception e) {
            handler.handle(Future.failedFuture("Invalid run LSID: " + runs.getString(index)));
            return;
        }
        
        File roBundleFile = null;
        boolean error = false;
        try {
            roBundleFile = File.createTempFile("webviewROBundle", ".zip");
            ProvenanceManager.exportROBundle(roBundleFile.getAbsolutePath(), runLSID);
        } catch (IOException e) {
            handler.handle(Future.failedFuture("Error creating temporary file: " + e.getMessage()));
            error = true;
            return;
        } catch (Exception e) {
            handler.handle(Future.failedFuture("Error creating robundle zip file: " + e.getMessage()));
            error = true;
            return;
        } finally {
            if(error && roBundleFile != null && !roBundleFile.delete()) {
                System.err.println("WARNING: unable to delete " + roBundleFile.getAbsolutePath());    
            }
        }
        
        final File roBundleFileFinal = roBundleFile;
        
        FileSystem fs = WebViewServer.vertx().fileSystem();
        fs.open(roBundleFile.getAbsolutePath(), new OpenOptions(), fileRes -> {
            if(fileRes.failed()) {
                handler.handle(Future.failedFuture("Error reading robundle zip file: " + fileRes.cause().getMessage()));
                if(!roBundleFileFinal.delete()) {
                    System.err.println("WARNING: unable to delete " + roBundleFileFinal.getAbsolutePath());    
                }
                return;
            } else {
                
                ReadStream<Buffer> stream = fileRes.result();
                
                // upload to rohub
                WebClient.create(WebViewServer.vertx())                        
                    .post(roURI.getHost(), roURI.getPath())
                    .putHeader("Content-type", "application/octect-stream")
                    .putHeader("Accept", "text/turtle")
                    .putHeader("Slug", "ro_bundle" + index + ".zip")
                    .putHeader("Authorization", "Bearer " + _authStr)
                    .sendStream(stream, response -> {

                        // delete the bundle .zip
                        if(!roBundleFileFinal.delete()) {
                            System.err.println("WARNING: unable to delete " + roBundleFileFinal.getAbsolutePath());    
                        }
                        
                        if(response.failed()) {
                            handler.handle(Future.failedFuture("Error adding run bundle to RO: " + response.cause().getMessage()));
                        } else {
                        
                            // if more runs, process them
                            if(index + 1 < runs.size()) {
                                _addRunToRO(roURI, runs, index + 1, handler);
                            } else {
                            
                            //int status = response.statusCode();
                            //System.out.println("annotation status: " + status);
                                _stringResponse(handler, "location", roURI.toString());
                            /*
                            response.bodyHandler(b -> {
                               System.out.println(b); 
                            });
                            */
                            }
                        }
                    });
                                                
            }
        });
        
    }
    
    private void _addRunsToRO(URI roURI, JsonObject inputs, Handler<AsyncResult<JsonArray>> handler) {
        JsonArray runs = inputs.getJsonArray("runs");
        _addRunToRO(roURI, runs, 0, handler);               
    }
    
    private void _annotateRO(URI roURI, JsonObject inputs, Handler<AsyncResult<JsonArray>> handler) {

        String title = inputs.getString("title");
        String description = inputs.getString("description");
        
        
        HttpClientRequest request = WebViewServer.vertx()
            .createHttpClient()
            .post(roURI.getHost(), roURI.getPath(), response -> {
                //int status = response.statusCode();
                //System.out.println("annotation status: " + status);
                _stringResponse(handler, "location", roURI.toString());
                /*
                response.bodyHandler(b -> {
                   System.out.println(b); 
                });
                */
        });
        
        String body = "<?xml version=\"1.0\"?>\n" + 
                "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" xmlns:prov=\"http://www.w3.org/ns/prov#\" xmlns:dct=\"http://purl.org/dc/terms/\" xmlns:pav=\"http://purl.org/pav/\" xmlns:foaf=\"http://xmlns.com/foaf/0.1/\" xmlns:skos=\"http://www.w3.org/2004/02/skos/core#\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:odrs=\"http://schema.theodi.org/odrs#\" xmlns:roes=\"http://w3id.org/ro/earth-science#\" xmlns:xsd=\"http://www.w3.org/2001/XMLSchema#\" xmlns:roterms=\"http://purl.org/wf4ever/roterms#\">\n" + 
                "  <rdf:Description rdf:about=\"" + roURI.toString() + "\">\n" + 
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
            .set("Link", "<" + roURI.toString() + ">; rel=\"http://purl.org/ao/annotatesResource\"");
        request.setFollowRedirects(true).write(body).end();
        
        //System.out.println(request.headers().get("Link"));
    }
    
                        
    private String _authStr;
    private URI _roHubURI;
    private int _roHubPort;
}
