/*
 * Copyright (c) 2017 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-29 15:27:08 -0700 (Tue, 29 Aug 2017) $' 
 * '$Revision: 1392 $'
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

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.kepler.sms.SemanticType;
import org.kepler.webview.actor.WebViewable;

import io.vertx.core.json.JsonObject;
import io.vertx.core.shareddata.LocalMap;
import ptolemy.data.StringToken;
import ptolemy.data.Token;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NamedObj;

public class WebViewableUtilities {

    private WebViewableUtilities() {
    }

    public static String getTitle(WebViewable webView) {
        String title = null;
        StringParameter titleParameter = (StringParameter) webView.getAttribute("title");
        if(titleParameter != null) {
            // TODO use getToken instead.
            title = titleParameter.getExpression();
        }
        if(title == null || title.trim().isEmpty()) {
            title = webView.getName(webView.toplevel());
        }
        return title;
    }
    
    public static void sendData(JsonObject jsonData, NamedObj source) throws IllegalActionException {

        final String actorId = WebViewId.getId(source);    

        JsonObject name = new JsonObject().put("data", jsonData);
        JsonObject actor = new JsonObject().put(actorId, name);
        JsonObject json = new JsonObject().put("actor", actor);
        
        _sendToClients(json, source);
    }
    
    public static void sendEvent(Event type, NamedObj source) throws IllegalActionException {
        sendEvent(type, source, new Date());
    }
    
    public static void sendEvent(Event type, NamedObj source, Date timestamp) throws IllegalActionException {
        _sendEvent(type, source, timestamp, null);
    }
    
    private static void _sendEvent(Event type, NamedObj source, Date timestamp,
        String clientId) throws IllegalActionException {
        
        if(timestamp == null) {
            timestamp = new Date();
        }
        
        JsonObject event = new JsonObject()
                .put("type", type._value)
                .put("ts", timestamp.getTime());
        JsonObject json = new JsonObject().put("event", event);
        
        if(type == Event.FireStart || type == Event.FireEnd ||
                type == Event.Initialize) {
            final String actorIdStr = WebViewId.getId(source);
            event.put("actor", actorIdStr);
        }
        
        _sendToClient(json, source, clientId);        
    }
    
    public static void sendEvent(Event type, NamedObj source, String id) throws IllegalActionException {
        _sendEvent(type, source, null, id);
    }

    public static void sendOptions(NamedObj source, String id) throws IllegalActionException {

        List<Parameter> parameters = new LinkedList<Parameter>();
        if(source instanceof WebViewable) {
            parameters = ((WebViewable)source).getOptions();
        } else {
            for(Parameter parameter : source.attributeList(Parameter.class)) {
                parameters.add(parameter);
            }
        }
        
        JsonObject options = new JsonObject();
        for(Parameter parameter : parameters) {
            String name = parameter.getName();
            // TODO do not include parameters containing WebViewables
            if(!name.startsWith("_")  &&
               !name.equals("html") &&
               !name.equals("htmlFile") &&
                !name.equals("title") &&
                !(parameter instanceof SemanticType)) {
                Token token = parameter.getToken();
                if(token != null) {
                    
                    JsonObject parameterJson = new JsonObject();
                    
                    if(token instanceof StringToken) {
                        parameterJson.put("value", ((StringToken)token).stringValue());
                        parameterJson.put("datatype", "string");
                    } else {
                        // TODO use put methods for numbers?
                        parameterJson.put("value", token.toString());
                        // TODO other types? records?
                        parameterJson.put("datatype", "num");
                    }
                    
                    for(Parameter subParameter : parameter.attributeList(Parameter.class)) {
                        if(!(subParameter instanceof SemanticType)) {
                            Token subToken = subParameter.getToken();
                            if(subToken != null) {
                                if(token instanceof StringToken) {
                                    parameterJson.put(subParameter.getName(), ((StringToken)subToken).stringValue());
                                } else {
                                    parameterJson.put(subParameter.getName(), subToken.toString());
                                }
                            }
                        }
                    }
                    
                    options.put(name, parameterJson);
                }
            }
        }
        
        // only send if options is not empty
        if(options.size() > 0) {            
            final String actorId = WebViewId.getId(source);    
            _sendToClient(new JsonObject().put("actor",
                new JsonObject().put(actorId,
                    new JsonObject().put("options", options))), source, id);
        }
    }

    // sent when id is changed.
    /* FIXME used any more?
    public static void sendRenameActor(String oldName, NamedObj source) throws IllegalActionException {
        
        // TODO id does not change when rename occurs.
        final String newName = WebViewId.getId(source);    

        JsonObject rename = new JsonObject()
            .putString("oldName", oldName)
            .putString("newName", newName);
        JsonObject json = new JsonObject().putObject("actor_rename", rename);

        _sendToClients(json, source);
    }
    */
    
    public static void sendTitle(String title, NamedObj source) throws IllegalActionException {

        final String actorId = WebViewId.getId(source);    
        
        JsonObject name = new JsonObject().put("title", title);
        JsonObject actor = new JsonObject().put(actorId, name);
        JsonObject json = new JsonObject().put("actor", actor);
        
        _sendToClients(json, source);
    }
    
    public enum Event {
        FireStart("fire_start"),
        FireEnd("fire_end"),
        Initialize("initialize"),
        WorkflowClosed("wf_closed"),
        WorkflowExecutionStart("wf_start"),
        WorkflowExecutionEnd("wf_end");
        
        private Event(String value) {
            _value = value;
        }
        
        private String _value;
    };

    private static void _sendToClients(JsonObject json, NamedObj source) {
        _sendToClient(json, source, null);
    }
        
    
    /** Send data to any clients of a NamedObj. */
    private static void _sendToClient(JsonObject json, NamedObj source, String clientId) {
        
        if(clientId != null) {
            WebViewServer.vertx().eventBus().publish(clientId, json.encode());
            return;
        }
        
        final NamedObj model = source.toplevel();
        
        // save in client buffer
        
        List<JsonObject> clientBuffer = WebViewServer.getClientBuffer(model);
        if(clientBuffer == null) {
            System.err.println("no buffer for " + model.getFullName() + " " + model.getClassName());
            return;
        }
        
        // synchronize on the buffer so WorkflowWebSocketHandler
        // can send any buffered data to a newly-connected client
        // before new data arrives.
        synchronized(clientBuffer) {
            
            // ignore json if actor rename event
            if(!json.containsKey("actor_rename")) {
    
                // see if json is wf_start event
                if(json.containsKey("event") && 
                    json.getJsonObject("event").getString("type").equals("wf_start")) {
                
                    // clear buffer
                    clientBuffer.clear();
                    //System.out.println("cleared buffer");
                }
             
                // save json to buffer
                //System.out.println("buffering " + json.encode());
                clientBuffer.add(json);            
            }
    
            // send to any web socket clients
            String modelId;
            try {
                modelId = WebViewId.getId(model);
            } catch (IllegalActionException e) {
                System.err.println("Unable to get id for " + model.getFullName() + ": " + e.getMessage());
                return;
            }
            LocalMap<String,Integer> map = WebViewServer.vertx().sharedData().getLocalMap("/ws/" + modelId);
            for(String id : map.keySet()) {
                //System.out.println("sending to " + id);
                WebViewServer.vertx().eventBus().publish(id, json.encode());
            }
        }        
    }
}
