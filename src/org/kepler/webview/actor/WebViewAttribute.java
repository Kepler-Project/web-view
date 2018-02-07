/*
 * Copyright (c) 2015 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-05-11 11:19:04 -0700 (Thu, 11 May 2017) $' 
 * '$Revision: 1183 $'
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
package org.kepler.webview.actor;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.kepler.provenance.ProvenanceRecorder;
import org.kepler.webview.data.TokenConverter;
import org.kepler.webview.server.WebViewId;
import org.kepler.webview.server.WebViewServer;
import org.kepler.webview.server.WebViewableUtilities;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import ptolemy.actor.ActorFiringListener;
import ptolemy.actor.CompositeActor;
import ptolemy.actor.FiringEvent;
import ptolemy.actor.FiringsRecordable;
import ptolemy.actor.IOPort;
import ptolemy.actor.IOPortEvent;
import ptolemy.actor.IOPortEventListener;
import ptolemy.actor.Initializable;
import ptolemy.actor.gui.style.TextStyle;
import ptolemy.data.Token;
import ptolemy.data.expr.FileParameter;
import ptolemy.data.expr.Parameter;
import ptolemy.data.expr.StringParameter;
import ptolemy.kernel.Entity;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.ChangeRequest;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.StringAttribute;
import ptolemy.kernel.util.Workspace;
import ptolemy.util.MessageHandler;

public class WebViewAttribute extends StringAttribute implements Initializable, WebViewable, IOPortEventListener, ActorFiringListener {

    public WebViewAttribute(NamedObj container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);

        setExpression("Click configure.");
        setVisibility(Settable.NOT_EDITABLE);
        
        title = new StringParameter(this, "title");
        title.setExpression("");
        
        htmlFile = new FileParameter(this, "htmlFile");
        // TODO set base directory?
        
        html = new StringParameter(this, "html");
        html.setToken("Click configure to edit.");
        html.setVisibility(Settable.NOT_EDITABLE);
        
        htmlCode = new StringAttribute(html, "htmlCode");
        TextStyle style = new TextStyle(htmlCode, "_style");
        style.height.setToken("100");
        
    }

    @Override
    public void addInitializable(Initializable initializable) {
        _initializables.add(initializable);        
    }

    /** React to an attribute change. */
    @Override
    public void attributeChanged(Attribute attribute) throws IllegalActionException {
        
        if (attribute == title) {
            _setTitle(title.stringValue());
        } else {
            super.attributeChanged(attribute);
        }
    }

    /** Clone the actor into the specified workspace. 
     *  @param workspace The workspace for the new object.
     *  @return A new actor.
     *  @exception CloneNotSupportedException If a derived class contains
     *   an attribute that cannot be cloned.
     */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        WebViewAttribute newObject = (WebViewAttribute) super.clone(workspace);
        newObject._consumer = null;
        newObject._initializables = new HashSet<Initializable>();
        newObject._readDataJson = new JsonObject();
        //newObject._registeredName = null;
        newObject._tokenConverter = new TokenConverter();
        return newObject;
    }

    @Override
    public void firingEvent(FiringEvent event) {
        
        if(event.getType() == FiringEvent.AFTER_ITERATE ||
                event.getType() == FiringEvent.AFTER_POSTFIRE) {
            try {
                //System.out.println("After fire event: " + event);
                WebViewableUtilities.sendData(_readDataJson, this);
            } catch (IllegalActionException e) {
                System.err.println("Error sending data: " + e.getMessage());
                e.printStackTrace(System.err);
            }      
            _readDataJson = new JsonObject();
        }        
    }

    @Override
    public String getHTML() throws IOException, IllegalActionException {
        String htmlStr = null;
        String fileStr = htmlFile.stringValue();
        
        // see if file was specified
        if(fileStr == null) {
            // return string in htmlCode
            htmlStr = htmlCode.getExpression();
        } else if(fileStr.trim().isEmpty()) {
            throw new IllegalActionException(this,
                "Either htmlFile or htmlCode must be specified.");
        } else {
            File file = new File(fileStr);
            // see if file is an absolute path
            if(file.isAbsolute()) {
                if(file.exists()) {
                    htmlStr = FileUtils.readFileToString(file);
                }
            } else {
                // read relative path from <module>/resources/html
                String path = WebViewServer.findFile(fileStr);
                if(path != null) {
                    htmlStr = FileUtils.readFileToString(new File(path));
                } else {
                    throw new IllegalActionException(this, "Could not find file " + fileStr);
                }
            }
        }
        return htmlStr;
    }

    @Override
    public List<Parameter> getOptions() {
        return getContainer().attributeList(Parameter.class);
    }

    @Override
    public Parameter getOption(String name) {
        return (Parameter) getContainer().getAttribute(name);
    }

    @Override
    public void initialize() throws IllegalActionException {
        for(Initializable initializable : _initializables) {
            initializable.initialize();
        }
    }

    @Override
    public void portEvent(IOPortEvent event) throws IllegalActionException {
        // read input ports and send to clients        
        if(event.getEventType() == IOPortEvent.GET_END) {
            Object value = _convertInputToJson(event.getToken());
            //System.out.println("Port event " + event.getPort().getName() + " value is " + value);
            _readDataJson.put(event.getPort().getName(), value);
        }
        
        if(!_firingEventsFromDirector) {
            try {
                //System.out.println("After fire event: " + event);
                WebViewableUtilities.sendData(_readDataJson, this);
            } catch (IllegalActionException e) {
                System.err.println("Error sending data: " + e.getMessage());
                e.printStackTrace(System.err);
            }      
            _readDataJson = new JsonObject();
        }        
    }

    @Override
    public void preinitialize() throws IllegalActionException {
        for(Initializable initializable : _initializables) {
            initializable.preinitialize();
        }
        
        NamedObj container = getContainer();
        if(container instanceof Entity) {
            for(Object object : ((Entity<?>)container).portList()) {
                if(object instanceof IOPort) {
                    ((IOPort)object).addIOPortEventListener(this);
                }
            }
        }

        while(container != null && !(container instanceof CompositeActor)) {
            container = container.getContainer();
        }

        if(container == null ||
            !ProvenanceRecorder.containsSupportedDirector((CompositeActor) container)) {
            _firingEventsFromDirector = false;
        } else {
            _firingEventsFromDirector = true;            
        }

    }

    @Override
    public void removeInitializable(Initializable initializable) {
        _initializables.remove(initializable);
    }

    /** Set the container. */
    @Override
    public void setContainer(NamedObj container) throws IllegalActionException, NameDuplicationException {
        
        NamedObj oldContainer = getContainer();
                
        super.setContainer(container);
                
        if(container == null) {
            unregisterHandler();
        } else {
            _registerHandler();
        }
        
        if(oldContainer != null) {
            if(oldContainer instanceof Initializable) {
             ((Initializable)oldContainer).removeInitializable(this);
            }
            
            if(oldContainer instanceof FiringsRecordable) {
                ((FiringsRecordable)oldContainer).removeActorFiringListener(this);
            }
        }
        
        if(container != null) {
            if(container instanceof Initializable) {
                ((Initializable)container).addInitializable(this);        
            }
            
            if(container instanceof FiringsRecordable) {
                ((FiringsRecordable)container).addActorFiringListener(this);
            }
        }

    }

    /* FIXME _registerHandler no longer uses name
    @Override
    public void setName(String name) throws IllegalActionException,
            NameDuplicationException {
        super.setName(name);
        _registerHandler();
    }
    */

    /** Stop listening for events from clients. This must be called when
     *  the containing model is no longer used.
     */ 
    public void unregisterHandler() {
        if(_consumer != null) {
            _consumer.unregister();
            _consumer = null;
            //System.out.println("unregistering handler for " + getFullName());
        }
    }

    @Override
    public void wrapup() throws IllegalActionException {
        for(Initializable initializable : _initializables) {
            initializable.wrapup();
        }
        
        NamedObj container = getContainer();
        if(container instanceof Entity) {
            for(Object object : ((Entity<?>)container).portList()) {
                if(object instanceof IOPort) {
                    ((IOPort)object).removeIOPortEventListener(this);
                }
            }
        }
        
        _readDataJson = new JsonObject();        
    }

    public FileParameter htmlFile;
    public StringParameter html;
    public StringAttribute htmlCode;
    public StringParameter title;

    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////

    protected void _registerHandler() throws IllegalActionException {

        // TODO check if address changed. maybe: address does not include actor name.
        unregisterHandler();

        // do not register if there is no container
        if(toplevel() == this) {
            return;
        }
                        
        EventBus bus = WebViewServer.vertx().eventBus();
        final String id = WebViewId.getId(this);
        //final String actorPath = "actor-" + id;

        /*
        if(_registeredName != null && !_registeredName.equals(id)) {
            // send rename to web clients
            WebViewableUtilities.sendRenameActor(_registeredName, this);
        }
        
        _registeredName = id;
        */
        
        String handlerAddress = "/ws/" + WebViewId.getId(toplevel());
        //System.out.println(getFullName() + ": " + id + " registering handler at " + _handlerAddress);

        _consumer = bus.consumer(handlerAddress, message -> {
            JsonObject json = message.body();
            //System.out.println(id + " recv from web: " + json);
            JsonObject actor = json.getJsonObject("actor");
            if(actor != null) {
                JsonObject pathObject = actor.getJsonObject(id);
                if(pathObject != null) {
                    JsonObject data = pathObject.getJsonObject("data");
                    if(data != null) {
                        for(Map.Entry<String, Object> entry: data) {
                            _dataReceived(entry.getKey(), entry.getValue());
                        }
                    }
                    JsonObject options = pathObject.getJsonObject("options");
                    if(options != null) {
                        for(Map.Entry<String,Object> entry: options) {
                            _optionReceived(entry.getKey(), entry.getValue());
                        }
                    }
                    JsonObject newPos = pathObject.getJsonObject("pos");
                    if(newPos != null) {
                        //System.out.println(getFullName() + " got pos: " + newPos);
                        StringAttribute position = (StringAttribute) getAttribute("_webWindowProperties");
                        try {
                            if(position == null) {
                                position = new StringAttribute(WebViewAttribute.this, "_webWindowProperties");
                                // set visibility to expert to hide in configure dialog
                                position.setVisibility(Settable.EXPERT);
                            }
                            String curStr = position.getValueAsString();
                            if(!curStr.isEmpty()) {
                                JsonObject curPos = new JsonObject(position.getValueAsString());
                                curPos.mergeIn(newPos);
                                position.setExpression(curPos.encode());
                            } else {
                                position.setExpression(newPos.encode());
                            }
                            
                        } catch(IllegalActionException | NameDuplicationException e) {
                            MessageHandler.error("Error setting window position.", e);
                        }
                    }
                }
            }
            
            JsonObject event = json.getJsonObject("event");
            if(event != null) {
                if(!event.containsKey("type")) {
                    System.err.println("ERROR: missing type for event: " + event);
                } else {
                    String type = event.getString("type");
                    try {
                        switch(type) {
                        case "conopen":
                            _connectionOpened(event.getString("id"));
                            break;
                        case "conclose":
                            _connectionClosed(event.getString("id"));
                            break;
                        default:
                            System.err.println("WARNING: unknown event type : " + type);
                            break;
                        }
                    } catch(IllegalActionException e) {
                        MessageHandler.error("Error handling " + type + " event.", e);
                    }
                }
            }
        });

    }

    protected void _connectionClosed(String id) {
        
    }
    
    protected void _connectionOpened(String id) throws IllegalActionException {
        
        // set parameters
        try {
            WebViewableUtilities.sendOptions(this, id);
        } catch (IllegalActionException e) {
            System.err.println("Error sending options: " + e.getMessage());
        }
        
        // send initialize event        
        WebViewableUtilities.sendEvent(WebViewableUtilities.Event.Initialize, this, id);
    }
    
    protected void _dataReceived(String name, Object value) {
        NamedObj container = getContainer();
        if(container instanceof WebView) {
            ((WebView)container).dataReceived(name, value);
        }
    }
    
    protected void _optionReceived(String name, final Object value) {

        final Parameter option = getOption(name);
        
        if(option == null) {
            System.err.println("WARNING: no parameter found for option received: " + name);
        } else {
            // if not in batch mode, update canvas
            String headless = System.getProperty("java.awt.headless");
            boolean usingUI = headless == null || headless.equals("false");
                            
            ChangeRequest request = new ChangeRequest(this,
                    "WebViewAttribute change request",
                    usingUI) {
                @Override
                protected void _execute() throws IllegalActionException {
                    Token oldToken = option.getToken();

                    String newValue = value.toString();
                    if (oldToken == null || !oldToken.toString().equals(newValue)) {
                        option.setToken(newValue);

                        // NOTE: If we don't call validate(), then the
                        // change will not propagate to dependents.
                        option.validate();
                    }
                }
            };
            request.setPersistent(false);
            //request.addChangeListener(this);
            requestChange(request);
        }
    }
    
    protected Object _convertInputToJson(Token token) throws IllegalActionException {
        return _tokenConverter.convertFromToken(token);
    }
    
    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////
     
    /** Set the title of the actor in the web view. */
    private void _setTitle(String titleStr) throws IllegalActionException {
                
        if(_titleStr == null || !_titleStr.equals(titleStr)) {

            // if title is empty, use actor name.
            if(titleStr.trim().isEmpty()) {
                _titleStr = getName();
            } else {
                _titleStr = titleStr;
            }
            WebViewableUtilities.sendTitle(_titleStr, this);
        }
    }

    ///////////////////////////////////////////////////////////////////
    ////                         private fields                    ////

    private MessageConsumer<JsonObject> _consumer;

    /** The title of the window in the web view. */
    private String _titleStr;

    private Set<Initializable> _initializables = new HashSet<Initializable>();
    private JsonObject _readDataJson = new JsonObject();
    
    private TokenConverter _tokenConverter = new TokenConverter();

    /** If true, director will send firing events. */
    private boolean _firingEventsFromDirector = false;
}
