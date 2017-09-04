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

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.kepler.webview.actor.WebViewAttribute;

import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.StringAttribute;
import ptolemy.kernel.util.Workspace;

public class WebViewId extends StringAttribute {

    /** Create a new WebViewId in the specified container. This constructor is private
     *  since only WebViewId.getId() is allowed to create instances of this class.
     */
    private WebViewId(NamedObj container) throws IllegalActionException, NameDuplicationException {
        super(container, NAME);

        // set a default value for the id.
        _setExpression(null, false);

        // set visibility to EXPERT since normally users do not change the id.
        setVisibility(Settable.EXPERT);
    }
    
    /** Clone the WebViewId into the specified workspace. */
    @Override
    public Object clone(Workspace workspace) throws CloneNotSupportedException {
        WebViewId newObject = (WebViewId) super.clone(workspace);
        // if the id was not set manually, generate a new id for the clone.
        if(!newObject._setManually) {
            try {
                _setExpression(null, false);
            } catch (IllegalActionException e) {
                throw new CloneNotSupportedException("Error setting expression to new webview id: " + e.getMessage());
            }
        }
        return newObject;
    }
    
    /** Get the web view id for a NamedObj. If the NamedObj does not have a web view id,
     *  then a new one is created.
     */
    public static String getId(NamedObj namedObj) throws IllegalActionException {
        WebViewId id = (WebViewId) namedObj.getAttribute(NAME);
        if(id == null) {
            try {
                id = new WebViewId(namedObj);
            } catch(NameDuplicationException e) {
                throw new IllegalActionException(namedObj, e, "Error creating WebViewId.");
            }
        }
        
        String idStr;
        
        NamedObj toplevel = namedObj.toplevel();
        if(toplevel == namedObj) {
            idStr = id.getExpression();
        } else {
            // TODO
            idStr = getId(toplevel) + "-" + id.getExpression();
        }
        
        // TODO need to delete NamedObjs from map when they are removed from workflow.
        _idMap.put(idStr, new WeakReference<NamedObj>(namedObj));
        
        return idStr;
    }
    
    /** Get the NamedObj for a specific id. */
    public static NamedObj getNamedObj(String id) {
        return _idMap.get(id).get();
    }
    
    /** Remove all the ids inside of the a workflow. */
    public static void removeWorkflow(NamedObj model) {
        
        synchronized(_idMap) {
            Map<String,WeakReference<NamedObj>> mapCopy = new HashMap<String,WeakReference<NamedObj>>(_idMap);
            for(Map.Entry<String, WeakReference<NamedObj>> entry : mapCopy.entrySet()) {
                final NamedObj namedObj = entry.getValue().get();
                if(namedObj == null || namedObj == model || namedObj.toplevel() == model) {
                    _idMap.remove(entry.getKey());
                    if(namedObj != null && (namedObj instanceof WebViewAttribute)) {
                        ((WebViewAttribute)namedObj).unregisterHandler();
                    }
                }
            }
        }
    }
    
    /** Override the base class to remove an existing WebViewId in the
     *  container. This can occur since a WebViewId is created during
     *  the cloning process.
     */
    @Override
    public void setContainer(NamedObj container) throws IllegalActionException, NameDuplicationException {
        
        if(container != null) {
            // see if the container already has an id.
            // this can occur during cloning since WebViewAttribute.setContainer()
            // can create an id during registering the handle.
            WebViewId containerId = (WebViewId) container.getAttribute(NAME);
            if(containerId != null) {
                // copy the values
                _setExpression(containerId.getExpression(), containerId._setManually);
                // remove the existing one.
                containerId.setContainer(null);
            }
        }
        
        super.setContainer(container);
    }
    
    /** Set the value of the id. */
    @Override
    public void setExpression(String value) throws IllegalActionException {
        _setExpression(value, true);
    }
    
    /** Set the value of the ID.
     *  @param value The value of the id. If null or empty, a new id is generated and setManually
     *  is ignored and assumed to be false.
     *  @param setManually If true, then this object is set persistent so that it is saved in the model.
     */
    private void _setExpression(String value, boolean setManually) throws IllegalActionException {
        
        if(value == null || value.trim().isEmpty()) {
            value = String.valueOf(_counter.incrementAndGet());
            setManually = false;
        }
        
        super.setExpression(value);
        _setManually = setManually;        
        setPersistent(setManually);
        //System.out.println("_setExpression to " + value + " done manually = " + setManually);
     }
    
    private boolean _setManually = false;
        
    private static final String NAME = "_webViewId";
    
    private static final AtomicInteger _counter = new AtomicInteger(0);
    
    private static final Map<String,WeakReference<NamedObj>> _idMap =
            Collections.synchronizedMap(new HashMap<String,WeakReference<NamedObj>>());
}
