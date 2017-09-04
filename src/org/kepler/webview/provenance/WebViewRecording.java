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

package org.kepler.webview.provenance;

import java.util.Date;

import org.kepler.objectmanager.lsid.KeplerLSID;
import org.kepler.provenance.FireState;
import org.kepler.provenance.ProvenanceRecorder;
import org.kepler.provenance.RecordingException;
import org.kepler.provenance.SimpleFiringRecording;
import org.kepler.webview.server.WebViewableUtilities;

import ptolemy.actor.Actor;
import ptolemy.actor.FiringEvent;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.Nameable;
import ptolemy.kernel.util.NamedObj;

public class WebViewRecording extends SimpleFiringRecording<Integer> {

    public WebViewRecording() throws RecordingException {
        super();
    }

    @Override
    /** Record an actor firing at a specific time. */
    public void actorFire(FiringEvent event, Date timestamp) throws RecordingException {
        
        Actor actor = event.getActor();
        FiringEvent.FiringEventType curEventType = event.getType();
        FireState<Integer> fireState = _fireStateTable.get(actor);
        synchronized (fireState) {
            
            // get the last type of firing start
            FiringEvent.FiringEventType lastStartType = fireState.getLastStartFireType();
            if (curEventType == FiringEvent.BEFORE_ITERATE ||
                    (curEventType == FiringEvent.BEFORE_PREFIRE &&
                    lastStartType != FiringEvent.BEFORE_ITERATE)) {

                int firing = fireState.getNumberOfFirings() + 1;
                fireState.fireStart(curEventType, firing);
                
                try {
                    WebViewableUtilities.sendEvent(WebViewableUtilities.Event.FireStart,
                            (NamedObj)actor, timestamp);
                } catch (IllegalActionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            // see if current firing is end of iteration:
            else if (curEventType == FiringEvent.AFTER_ITERATE ||
                    (curEventType == FiringEvent.AFTER_POSTFIRE &&
                    lastStartType == FiringEvent.BEFORE_PREFIRE)) {
                
                if (curEventType == FiringEvent.AFTER_POSTFIRE) {
                    fireState.fireStop(FiringEvent.AFTER_PREFIRE);
                } else {
                    fireState.fireStop(curEventType);
                }
                
                try {
                    WebViewableUtilities.sendEvent(WebViewableUtilities.Event.FireEnd,
                            (NamedObj)actor, timestamp);
                } catch (IllegalActionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * An actor threw an exception.
     * 
     * @param source
     * @param throwable
     * @param executionLSID
     * @throws RecordingException
     */
    @Override
    public void executionError(Nameable source, Throwable throwable, KeplerLSID executionLSID)
            throws RecordingException {
        // TODO
    }

    /**
     * Record the starting of workflow execution at a specific time.
     * 
     * @param executionLSID
     * @throws RecordingException
     */
    @Override
    public void executionStart(KeplerLSID executionLSID, Date timestamp) throws RecordingException {
        try {
            WebViewableUtilities.sendEvent(
                    WebViewableUtilities.Event.WorkflowExecutionStart,
                    _model, timestamp);
        } catch (IllegalActionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Record the stopping of workflow execution.
     * 
     * @param executionLSID
     * @throws RecordingException
     */
    @Override
    public void executionStop(KeplerLSID executionLSID, Date timestamp) throws RecordingException {
        try {
            WebViewableUtilities.sendEvent(
                    WebViewableUtilities.Event.WorkflowExecutionEnd,
                    _model, timestamp);
        } catch (IllegalActionException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public void setContainer(ProvenanceRecorder container) {
        super.setContainer(container);
        if (_recorderContainer == null) {
            _model = null;
        } else {
            _model = _recorderContainer.toplevel();
        }
    }

    private NamedObj _model;
}
