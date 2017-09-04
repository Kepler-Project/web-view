/*
 * Copyright (c) 2017 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-24 12:45:52 -0700 (Thu, 24 Aug 2017) $' 
 * '$Revision: 1388 $'
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

import org.kepler.util.WorkflowRun;
import org.kepler.webview.server.WebViewServer;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/** Handler to get information about all workflow runs.
 * 
 * @author Daniel Crawl
 * @version $Id: RunsHandler.java 1388 2017-08-24 19:45:52Z crawl $
 * 
 */
public class RunsHandler extends ProvenanceHandler {

    public RunsHandler(WebViewServer server) {
        super(server);
    }

    @Override
    public void handle(RoutingContext context) {
        
        /*
        System.out.println("in runs handler");
        for(Entry<String, String> e : request.params()) {            
            System.out.println(e.getKey() + " -> " + e.getValue());
        }
        */

        _server.getVertx().<JsonObject>executeBlocking(future -> {
            try {
                JsonArray jsonArray = new JsonArray();
                for(WorkflowRun run : _queryable.getWorkflowRunsForUser(context.user().principal().getString("username"))) {
                   jsonArray.add(new JsonObject().put("id", run.getExecLSID().toString())
                           .put("start", run.getStartTimeISO8601())
                           .put("status", run.getType())
                           .put("workflowName", run.getWorkflowName()));
                }
                future.complete(new JsonObject().put("runs", jsonArray));

            } catch(Exception e) {
                future.fail(e);
            }
        }, false, result -> {
            if(result.succeeded()) {
                _sendResponseWithSuccessText(context.request(), "application/json",
                    result.result().encode());
            } else {
                _sendResponseWithError(context.request(), "Could not get runs: " + result.cause().getMessage());
            }
        });   
    }
}
