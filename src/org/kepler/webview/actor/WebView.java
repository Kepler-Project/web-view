/*
 * Copyright (c) 2015-2017 The Regents of the University of California.
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

package org.kepler.webview.actor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.kepler.webview.data.TokenConverter;

import ptolemy.actor.TypedAtomicActor;
import ptolemy.actor.TypedIOPort;
import ptolemy.data.Token;
import ptolemy.data.type.BaseType;
import ptolemy.data.type.Type;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.Workspace;

public class WebView extends TypedAtomicActor {

    /** Create a new WebView in a container with the specified name. */
    public WebView(CompositeEntity container, String name)
            throws IllegalActionException, NameDuplicationException {
        super(container, name);

        webView = new WebViewAttribute(this, "webView");
        webView.title.setToken(name);
    }
    
    /** Clone the actor into the specified workspace. 
     *  @param workspace The workspace for the new object.
     *  @return A new actor.
     *  @exception CloneNotSupportedException If a derived class contains
     *   an attribute that cannot be cloned.
     */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        WebView newObject = (WebView) super.clone(workspace);
        newObject._outputQueues = new HashMap<TypedIOPort,BlockingQueue<Data>>();
        newObject._tokenConverter = new TokenConverter();
        return newObject;
    }
    
    public void dataReceived(String name, Object value) {
        TypedIOPort port = (TypedIOPort) getPort(name);
        if(port != null) {
            BlockingQueue<Data> queue = _outputQueues.get(port);
            if(queue == null) {
                System.err.println("WARNING: dropping received data for " +
                    port.getFullName() +
                    "; is workflow running?");
            } else {
                queue.add(new Data(value.toString()));
            }
        } else {
            System.err.println("WARNING: dropping received data since no output port called " + name);
        }
    }

    @Override
    public void fire() throws IllegalActionException {
        
        super.fire();
        
        // read all connected input ports
        for(TypedIOPort port: inputPortList()) {
            if(port.numberOfSources() > 0) {
                /*Token token =*/ port.get(0);
            }
        }
                        
        // read from client for each output port
        for(TypedIOPort port: outputPortList()) {
            BlockingQueue<Data> queue = _outputQueues.get(port);
            Data data;
            try {
                data = queue.take();
            } catch (InterruptedException e) {
                throw new IllegalActionException(this, e, "Error waiting for output.");
            }
            if(data != DATA_STOP) {
                Token token = _convertOutputToToken(data.value, port.getType());
                port.broadcast(token);
            }
        }
    }

    /** Perform initializations during workflow start up. */
    @Override
    public void preinitialize() throws IllegalActionException {
        
        super.preinitialize();
        
        _outputQueues.clear();
        for(TypedIOPort port: outputPortList()) {
            _outputQueues.put(port, new LinkedBlockingQueue<Data>());
            
            // set default output type to be string; can be overridden
            // in derived classes.
            if(port.getType().equals(BaseType.UNKNOWN)) {
                port.setTypeEquals(BaseType.STRING);
            }
        }        
    }
    
    @Override
    public void stop() {
        super.stop();
        //System.out.println("stop");
        for(TypedIOPort port: outputPortList()) {
            BlockingQueue<Data> queue = _outputQueues.get(port);
            queue.add(DATA_STOP);
        }
    }
        
    ///////////////////////////////////////////////////////////////////
    ////                         public fields                     ////

    public WebViewAttribute webView;
    
    ///////////////////////////////////////////////////////////////////
    ////                         protected methods                 ////
    
    protected Token _convertOutputToToken(String data, Type type) throws IllegalActionException {
        return _tokenConverter.convertToToken(data, type);
    }
    
    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////
             
    /** A class to encapsulate data received from the web clients. */
    private static class Data {
        public Data(String v) {
            this.value = v;
        }
        public String value;
    }
    
    private Map<TypedIOPort,BlockingQueue<Data>> _outputQueues =
        new HashMap<TypedIOPort,BlockingQueue<Data>>();
    
    /** A specific Data instance used when the workflow is stopped. */
    private final static Data DATA_STOP = new Data("");
        
    private TokenConverter _tokenConverter = new TokenConverter();
}