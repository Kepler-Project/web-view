/*
 *
 * Copyright (c) 2015 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-23 22:44:42 -0700 (Wed, 23 Aug 2017) $' 
 * '$Revision: 1376 $'
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

import java.util.List;

import org.kepler.webview.server.WebViewId;
import org.kepler.webview.server.WebViewServer;

import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import ptolemy.actor.TypedCompositeActor;

public class WorkflowWebSocketHandler extends WebSocketServerBaseHandler  {
    
    public WorkflowWebSocketHandler(WebViewServer server) {
        super(server);
    }
    
    @Override
    public void handle(final ServerWebSocket ws) {
        boolean handled = false;
        final String path = ws.path();
        if(path.equals(WebViewServer.WS_RUNWF_PATH)) {
            handled = true;
            
            _server.log(ws, "OPEN", WebViewServer.WS_RUNWF_PATH);
                    
            ws.closeHandler(handler -> {
                // TODO stop and close running workflow.
                _server.log(ws, "CLOSE", WebViewServer.WS_RUNWF_PATH);                                        
            }).handler(buffer -> {
                _server.log(ws, "READ", WebViewServer.WS_RUNWF_PATH, buffer);
                    
                JsonObject requestJson = new JsonObject(buffer.toString());
                
                // FIXME need to authenticate and get user object.
                _server.executeWorkflow(requestJson, null, res -> {
                    JsonObject responseJson;
                    
                    if(res.succeeded()) {
                        responseJson = res.result();
                    } else {
                        responseJson = new JsonObject().put("error", res.cause().getMessage());
                    }
                    
                    Object requestId = requestJson.getValue("reqid");
                    if(requestId != null) {
                        responseJson.put("reqid", requestId);
                    }
                    
                    ws.writeFinalTextFrame(responseJson.encode());
                    
                    _server.log(ws, "WRITE", WebViewServer.WS_RUNWF_PATH);

                });
            });
            
        } else if(path.startsWith(WebViewServer.WS_PATH)) {

            System.out.println("ws path is: " + path);

            final String id = path.substring(WebViewServer.WS_PATH.length());
            TypedCompositeActor model = (TypedCompositeActor) WebViewId.getNamedObj(id);
            
            if(model == null) {
                System.err.println("Unhandled websocket request " +
                        "(workflow not found) for: " + path);
            } else {

                handled = true;

                final List<JsonObject> buffer = WebViewServer.getClientBuffer(model);
                
                // synchronize on the buffer so we can send any buffered
                // data to the client before new data arrives.                
                synchronized(buffer) {
                    final LocalMap<String,Integer> map = _server.getVertx().sharedData().getLocalMap(path);

                    System.out.println("new connection for " + path + " id = " + ws.textHandlerID());
                    for(String id2 : map.keySet()) {
                        System.out.println("existing id: " + id2);
                    }
                    
                    map.put(ws.textHandlerID(), Integer.valueOf(1));
                    ws.closeHandler(handler -> {
                        System.out.println("closing connection for " + path + " id = " + ws.textHandlerID());
                        map.remove(ws.textHandlerID());
                        
                        // notify webviewables about closed connection
                        JsonObject jsonEvent = new JsonObject().put("event",
                            new JsonObject()
                                .put("type", "conclose")
                                .put("id", ws.textHandlerID()));
                        _server.getVertx().eventBus().publish(path, jsonEvent);
                    }).handler(b -> {
                        // TODO correct way to convert Buffer to JSON?
                        JsonObject json = new JsonObject(b.toString());
                        System.out.println("recvd for " + path + ": " + json);
                        _server.getVertx().eventBus().publish(path, json);
                    });

                    // notify webviewables about new connection
                    JsonObject jsonEvent = new JsonObject().put("event",
                        new JsonObject()
                            .put("type", "conopen")
                            .put("id", ws.textHandlerID()));
                    _server.getVertx().eventBus().publish(path, jsonEvent);

                    
                    // FIXME buffered json can contain incorrect actor
                    // paths, e.g., after a rename occurs.
                    // send any buffered data
                    /*
                    for(JsonObject json : buffer) {
                        //System.out.println("writing buffered: " + json.encode());
                        //TODO re-enable ws.writeTextFrame(json.encode()); 
                    }
                    */
                }
            }            
        }
        
        if(!handled) {
            ws.reject();
        }
    }
}
