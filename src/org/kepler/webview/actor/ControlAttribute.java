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

package org.kepler.webview.actor;

import ptolemy.actor.CompositeActor;
import ptolemy.actor.Manager;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NameDuplicationException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.StringAttribute;

/**
 *  TODO: notify client of errors
 *  TODO: add pause, resume?
 *  
 *  @author Daniel Crawl
 *  @version $Id: ControlAttribute.java 1392 2017-08-29 22:27:08Z crawl $
 */
public class ControlAttribute extends WebViewAttribute {

    public ControlAttribute(NamedObj container, String name) throws IllegalActionException, NameDuplicationException {
        super(container, name);
        
        htmlFile.setExpression("web-view/control.html");
        title.setExpression("Control");
        StringAttribute position = new StringAttribute(this, "_webWindowProperties");
        position.setExpression("{\"top\":0,\"left\":0,\"width\":172,\"height\":95}");
        position.setVisibility(Settable.NONE);
    }
    
    @Override
    protected void _dataReceived(String name, Object value) {
        
        //System.out.println("dr " + name + " " + value);
        
        CompositeActor toplevel;
        Manager manager;
        
        if(name.equals("event")) {
            if(!(value instanceof String)) {
                System.err.println("ERROR: expected String for event value; got " + value.getClass());
            } else {
                switch((String)value) {
                case "run":
                    
                    toplevel = (CompositeActor) toplevel();
                    manager = toplevel.getManager(); 
                    if(manager == null) {                        
                        try {
                            manager = new Manager(toplevel.workspace(), "manager");
                            toplevel.setManager(manager);
                        } catch (IllegalActionException e) {
                            System.err.print("ERROR: could not create Manager: " + e.getMessage());
                            return;
                        }
                    }
                    try {
                        manager.startRun();
                    } catch (IllegalActionException e) {
                        System.err.println("ERROR: count not start execution: " + e.getMessage());
                        return;
                    }
                    
                    break;
                case "stop":
                    toplevel = (CompositeActor) toplevel();
                    manager = toplevel.getManager(); 
                    if(manager == null) {
                        System.err.println("ERROR: cannot stop when no Manager found.");
                    } else {
                        manager.finish();
                    }
                    break;
                default:
                    System.err.println("ERROR: unexpected event: " + value);
                    break;
                }
            }
        }
    }
}
