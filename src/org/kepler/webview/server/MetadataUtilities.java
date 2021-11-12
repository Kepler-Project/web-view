/*
 * Copyright (c) 2019 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2018-10-22 14:40:55 -0700 (Mon, 22 Oct 2018) $' 
 * '$Revision: 2260 $'
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
package org.kepler.webview.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.kepler.webview.server.auth.AuthUtilities;

import io.vertx.core.Future;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;

/** Utilities for access metadata.
 * 
 * @author Daniel Crawl
 */
public class MetadataUtilities {

    private MetadataUtilities() {
    }
    
    public static Future<Boolean> checkPermission(User user, String name, String type) {

        Future<Boolean> future = Future.future();
        getMetadataItem(name, type).setHandler(result -> {
            if(result.failed()) {
                future.fail(result.cause().getMessage());
            } else {
                future.complete(checkPermission(user, result.result()));
            }
        });
        return future;
    }
    
    public static boolean checkPermission(User user, JsonObject metadataItem) {
        
        if(metadataItem == null) {
            return false;           
        } else {
        
            // check permissions           
            Set<String> userGroups = AuthUtilities.getGroups(user);            
            if(userGroups.contains("admin")) {
                return true;
            } else {                    
                JsonArray metadataGroups = metadataItem.getJsonArray("groups");
                if(metadataGroups == null) {
                    System.err.println("WARNING: no permission groups for " + metadataItem);
                } else {
                    for(int i = 0; i < metadataGroups.size(); i++) {
                        if(userGroups.contains(metadataGroups.getString(i))) {
                            return true;
                        }
                    }
                }
            }
            
        }
        
        return false;
    }
    
    public synchronized static Future<JsonArray> getMetadata() {
        
        Future<JsonArray> future = Future.future();
        
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
                                future.complete(_metadataJson.copy());
                            } catch(Throwable t) {
                                System.err.println("Error reading metadata file " +
                                        _metadataFileName + ": " + t);
                                future.fail(t.getMessage());
                                return;
                            }
                                                        
                        } else {
                            future.fail("Could not read metadata.");
                            System.err.println("Could not read metadata " +
                                    _metadataFileName + ": " + readResult.cause().getMessage());
                            return;
                        }
                    });
                } else {
                    future.complete(_metadataJson.copy());
                }                
            } else {
                future.fail("Could not access metadata.");
            }                
        });
        
        return future;
    }
    
    /** Get metadata item with name and type. */
    public static Future<JsonObject> getMetadataItem(String name, String type) {
        return getMetadataItem(name, type, new HashMap<>());
    }
    
    /** Get metadata item with name, type, and matching set of key-values.
     *  @param name the name
     *  @param type the type
     *  @param map key-values to match.
     */
    public static Future<JsonObject> getMetadataItem(String name, String type, 
            Map<String,String> map) {
        
        Future<JsonObject> future = Future.future();
        
        getMetadata().setHandler(result -> {
            if(result.failed()) {
                future.fail(result.cause().getMessage());
            } else {                
                // find the named item in the metadata.
                JsonArray metadata = result.result();
                for(int i = 0; i < metadata.size(); i++) {
                    JsonObject metadataItem = metadata.getJsonObject(i);
                    if(metadataItem.getString("name").equals(name) &&
                        metadataItem.getString("type").equals(type)) {
                        
                        boolean foundAll = true;
                        for(Map.Entry<String,String> entry: map.entrySet()) {
                            if(!metadataItem.containsKey(entry.getKey()) ||
                                !metadataItem.getString(entry.getKey()).equals(entry.getValue())) {
                                foundAll = false;
                                break;
                            }
                        }
                        
                        if(foundAll) {
                            future.complete(metadata.getJsonObject(i));
                            return;
                        }
                    }
                }
                future.fail("Could not find metdata name " + name + " type " + type);
            }                
        });
        
        return future;
        
    }
    
    /** Vertx file system. */
    private static FileSystem _fileSystem = WebViewServer.vertx().fileSystem();

    /** Metadata file name. */
    private static String _metadataFileName = WebViewConfiguration.getHttpServerMetadataFileName();
    
    /** Last modified time of metadata file. */
    private static long _metadataLastModifiedTime = -1;
    
    /** Metadata JSON cached from reading metadata file. */
    private static JsonArray _metadataJson;


}
