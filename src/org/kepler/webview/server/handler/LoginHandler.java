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

import org.kepler.webview.server.MetadataUtilities;
import org.kepler.webview.server.WebViewServer;

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
    }

    @Override
    public void handle(RoutingContext context) {

        final long timestamp = System.currentTimeMillis();
        
        /*
        System.out.println("logged in " + 
            context.user().principal().getString("username") + " " +
            context.session().id());
         */
        
        MetadataUtilities.getMetadata().setHandler(res -> {
            if(res.failed()) {
                context.response()
                    .putHeader("Content-Type", "text/plain")
                    .setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                    .setStatusMessage("Could not access metadata: " + res.cause())
                    .end();
            } else {
                

                // construct the response json
                
                JsonObject principal = context.user().principal();
                
                JsonObject returnJson = new JsonObject().mergeIn(principal);                                
                
                boolean isAdmin = false;
                
                if(returnJson.containsKey("admin")) {
                    for(Object v: returnJson.getJsonArray("admin")) {
                        if("*".equals(v) ) {
                            isAdmin = true;
                        }
                    };
                }
                
                // add metadata
        
                JsonArray metadataJson = res.result();
                
                if(isAdmin) {
                    returnJson.put("metadata", metadataJson.copy());
                } else { 
                    JsonArray returnMetadataJson = new JsonArray();
                    for(int i = 0; i < metadataJson.size(); i++) {
                        JsonObject m = metadataJson.getJsonObject(i);
                        
                        // do not include type = paramset metadata entries,
                        // since these are not governed by groups.
                        if(m.containsKey("type") && "paramset".equals(m.getString("type")) ) {
                            continue;                        
                        }
                        
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
        });
        
    }
        
    /** Names of fields to remove from metadata json before returning to the client. */
    private static String[] _fieldsToRemove = {"groups", "private"};
}
