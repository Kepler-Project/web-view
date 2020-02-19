/*
 * Copyright (c) 2017 The Regents of the University of California.
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

import java.net.HttpURLConnection;
import org.kepler.webview.server.WebViewConfiguration;
import org.kepler.webview.server.WebViewServer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/** Handler for logins.
 * 
 * @author Daniel Crawl
 * @version $Id: LoginHandler.java 1375 2017-08-24 05:42:39Z crawl $
 */
public class LoginHandler extends BaseHandler {

    public LoginHandler(WebViewServer server) {
        super(server);
        _metadataFileName = WebViewConfiguration.getHttpServerMetadataFileName();
        _fileSystem = _server.getVertx().fileSystem();
    }

    @Override
    public void handle(RoutingContext context) {

        final long timestamp = System.currentTimeMillis();
        
        /*
        System.out.println("logged in " + 
            context.user().principal().getString("username") + " " +
            context.session().id());
         */
        
        // get the last modified time of the metadata file.
        _fileSystem.props(_metadataFileName, propsResult -> {
            if(propsResult.succeeded()) {
                // see if file was modified since we last read it
                if(propsResult.result().lastModifiedTime() > _metadataLastModifiedTime || _metadataJson == null) {
                    _metadataLastModifiedTime = propsResult.result().lastModifiedTime();
                    
                    // read metadata file
                    _fileSystem.readFile(_metadataFileName, readResult -> {
                        if(readResult.succeeded()) {
                            // update the cached json
                            try {
                                
                                JsonArray newMetadata = readResult.result().toJsonArray();
                                for(int i = 0; i < newMetadata.size(); i++) {
                                    if(newMetadata.getJsonObject(i).containsKey("enable") &&
                                        !newMetadata.getJsonObject(i).getBoolean("enable").booleanValue()) {

                                        newMetadata.remove(i);
                                        
                                        i--;
                                    }
                                }
                                _metadataJson = newMetadata;
                            } catch(Throwable t) {
                                context.response()
                                    .putHeader("Content-Type", "text/plain")
                                    .setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                                    .setStatusMessage("Could not access metadata.")
                                    .end();
                                System.err.println("Error reading metadata file " +
                                    _metadataFileName + ": " + t.getMessage());
                                _server.log(context.request(), context.user(), HttpURLConnection.HTTP_INTERNAL_ERROR, timestamp);
                                return;
                            }
                            // send metadata json in response
                            _sendMetadata(context, timestamp);
                        } else {
                            context.response()
                                .putHeader("Content-Type", "text/plain")
                                .setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                                .setStatusMessage("Could not access metadata.")
                                .end();
                            System.err.println("Could not read metadata " +
                                    _metadataFileName + ": " + readResult.cause().getMessage());
                            _server.log(context.request(), context.user(), HttpURLConnection.HTTP_INTERNAL_ERROR, timestamp);
                        }
                    });
                } else {
                    // send cached metadata json in response 
                    _sendMetadata(context, timestamp);
                }                
            } else {
                context.response()
                    .putHeader("Content-Type", "text/plain")
                    .setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                    .setStatusMessage("Could not access metadata.")
                    .end();
                System.err.println("Could not get properties for metadata " +
                    _metadataFileName + ": " + propsResult.cause().getMessage());
                _server.log(context.request(), context.user(), HttpURLConnection.HTTP_INTERNAL_ERROR, timestamp);
            }                
        });
    }

    /** Send metadata JSON in response.
     * @param context The vertx routing context.
     * @param timestamp The timestamp of the request.
     */
    private void _sendMetadata(RoutingContext context, long timestamp) {
        
        // construct the response json
        
        JsonObject principal = context.user().principal();
        //Set<String> userGroups = AuthUtilities.getGroups(context.user());        
        
        JsonObject returnJson = new JsonObject().mergeIn(principal);
        
        // add the username
        /*
        returnJson.put("username", principal.getString("username"));
        returnJson.put("fullname", principal.getString("fullname"));
        
        // add the groups
        JsonArray groupsArray = new JsonArray();
        for(String groupName : userGroups) {
            groupsArray.add(groupName);
        }
        returnJson.put("groups", groupsArray);
        */
        
        boolean isAdmin = false;
        for(Object g: returnJson.getJsonArray("groups")) {
            if(g.equals("admin")) {
                isAdmin = true;
                break;
            }
        }

        // add metadata

        if(isAdmin) {
            returnJson.put("metadata", _metadataJson.copy());
        } else { 
            JsonArray returnMetadataJson = new JsonArray();
            for(int i = 0; i < _metadataJson.size(); i++) {
                JsonObject m = _metadataJson.getJsonObject(i);
                boolean inGroup = false;
                if(!m.containsKey("groups")) {
                    inGroup = true;
                } else {
                    JsonArray groupsList = m.getJsonArray("groups");
                    for(int j = 0; j < groupsList.size(); j++) {
                        for(Object g: returnJson.getJsonArray("groups")) {
                            if(g.equals(groupsList.getString(j))) {
                                inGroup = true;
                                break;
                            }
                        }
                    }
                }
                
                if(inGroup) {
                    returnMetadataJson.add(m.copy());
                }
            };
                        
            returnJson.put("metadata", returnMetadataJson);
        }
        
        // remove specific fields before returning to client.
        for(int i = 0; i < returnJson.getJsonArray("metadata").size(); i++) {
            JsonObject obj = returnJson.getJsonArray("metadata").getJsonObject(i);
            for(String key: _fieldsToRemove) {
                if(obj.containsKey(key)) {
                    obj.remove(key);
                }
            }
        }
               
        //System.out.println(returnJson.encodePrettily());
        
        context.response().putHeader("Content-Type", "application/json")
            .end(returnJson.encode());
        _server.log(context.request(), context.user(), HttpURLConnection.HTTP_OK, timestamp);
    }
    
    /** Vertx file system. */
    private FileSystem _fileSystem;
    
    /** Metadata file name. */
    private String _metadataFileName;
    
    /** Last modified time of metadata file. */
    private long _metadataLastModifiedTime = -1;
    
    /** Metadata JSON cached from reading metadata file. */
    private JsonArray _metadataJson;
    
    /** Names of fields to remove from metadata json before returning to the client. */
    private static String[] _fieldsToRemove = {"groups", "private"};
}
