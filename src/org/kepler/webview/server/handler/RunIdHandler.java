/*
 * Copyright (c) 2017 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-29 15:24:48 -0700 (Tue, 29 Aug 2017) $' 
 * '$Revision: 1391 $'
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.kepler.loader.util.Screenshot;
import org.kepler.objectmanager.lsid.KeplerLSID;
import org.kepler.provenance.QueryException;
import org.kepler.provenance.RecordPlayer;
import org.kepler.provenance.manager.ProvenanceManager;
import org.kepler.provenance.prov.ProvUtilities;
import org.kepler.util.WorkflowRun;
import org.kepler.webview.actor.WebView;
import org.kepler.webview.server.WebViewServer;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import ptolemy.actor.CompositeActor;

/** Handler to get information about a workflow run.
 * 
 * @author Daniel Crawl
 * @version $Id: RunIdHandler.java 1391 2017-08-29 22:24:48Z crawl $
 * 
 */
public class RunIdHandler extends ProvenanceHandler {

    public RunIdHandler(WebViewServer server) {
        super(server);                
        Screenshot.closeAllWhenDone(false);        
    }

    @Override
    public void handle(RoutingContext context) {
        
        HttpServerRequest request = context.request();
        String runLSID = request.getParam("id");
        final MultiMap params = request.params();
        
        //System.out.println(runId);
        
        /*
        System.out.println("in runid handler");
        for(Entry<String, String> e : params) {            
            System.out.println(e.getKey() + " -> " + e.getValue());
        } 
        */
                
        _server.getVertx().<JsonObject>executeBlocking(future -> {
            try {
                
                JsonObject responseJson = new JsonObject();                
                List<KeplerLSID> list = new LinkedList<KeplerLSID>();
                list.add(new KeplerLSID(runLSID));
                Map<KeplerLSID, WorkflowRun> runs = _queryable.getWorkflowRunsForExecutionLSIDs(list);
                
                // see if the run was found.
                if(runs.size() == 0) {
                    future.fail("Bad run id.");
                } else {                
                    final WorkflowRun run = runs.values().iterator().next();
                    
                    //System.out.println("run user " + run.getUser() + " req user " + 
                        //context.user().principal().getString("username"));
                    
                    // make sure the run belong to the user making the request
                    if(!run.getUser().equals(context.user().principal().getString("username"))) {
                        future.fail("Run belongs to different user.");
                    } else {                   
                        responseJson.put("status", run.getType())
                            .put("start", run.getStartTimeISO8601())
                            .put("workflowName", run.getWorkflowName());
                        
                        Map<Integer,String> errorMap = run.getErrorMessages();
                        if(!errorMap.isEmpty()) {
                            responseJson.put("runError", errorMap.values().iterator().next());
                        }
                                                
                        if(_getTrueFalseParameter(params, "keysValues")) {
                            _addKeysValues(responseJson, run);
                        }

                        if(_getTrueFalseParameter(params, "outputs")) {
                            _addOutputs(responseJson, run);
                        }
                        
                        if(_getTrueFalseParameter(params, "prov")) {
                            _addProv(responseJson, params, run);
                        } else if(params.contains("provFormat")) {
                           throw new Exception("Must set prov=true when specifying provFormat."); 
                        }
                        
                        future.complete(responseJson);
                    }
                }                      
                

            } catch(Exception e) {
                future.fail(e);
            }

        }, false, result -> {            
            if(result.succeeded()) {
                _sendResponseWithSuccessText(request, "application/json", result.result().encode());
            } else {
                _sendResponseWithError(request, result.cause().getMessage());
            }
        });   
    }
    
    /** Handle a request to get the workflow for a specific run. */
    public void handleBinary(RoutingContext context) {
        
        //System.out.println("run id handle binary");
        
        HttpServerRequest request = context.request();
        String runLSIDStr = request.getParam("id");        
        KeplerLSID runLSID = null;

        try {
            runLSID = new KeplerLSID(runLSIDStr);
        } catch(Exception e) {
            _sendResponseWithError(request, "Bad KeplerLSID: " + runLSIDStr);
            return;
        }

        // check permission
        try {
            String runUser = _queryable.getUserForExecution(runLSID);
            if(runUser == null) {
                _sendResponseWithError(request, "Unable to find user of run.");
                return;
            } else if(!runUser.equals(context.user().principal().getString("username"))) {
                _sendResponseWithError(request, "Unauthorized: run does not belong to user.");                
                return;
            }
        } catch(QueryException e) {
            _sendResponseWithError(request, e.getMessage());
            return;
        }

        String operationStr = request.getParam("param");
        final MultiMap params = request.params();

        try {

            if(operationStr.equals("workflow")) {
                File karFile = null;
                try {
                    karFile = File.createTempFile("webviewProvKAR", ".kar");
                    ProvenanceManager.exportWorkflow(karFile.getAbsolutePath(), runLSID);
                    _sendResponseWithFile(request, karFile, true);
                    karFile = null;
                } finally {
                    if(karFile != null && !karFile.delete()) {
                        System.err.println("WARNING: unable to delete " + karFile.getAbsolutePath());
                    }
                }
            } else if(operationStr.equals("screenshot")) {
                File karFile = null;
                try {
                    karFile = File.createTempFile("webviewProvKAR", ".kar");
                    ProvenanceManager.exportWorkflow(karFile.getAbsolutePath(), runLSID);
                    List<File> screenshots = Screenshot.makeScreenshot(
                            Arrays.asList(karFile.getAbsolutePath()),
                            "png",
                            null,
                            null, 
                            false);
                    if(screenshots.isEmpty()) {
                        _sendResponseWithError(request, "Unable to make screenshot.");
                    } else {
                        _sendResponseWithFile(request, screenshots.get(0), true);
                    }                    
                } finally {
                    if(karFile != null && !karFile.delete()) {
                        System.err.println("WARNING: unable to delete " + karFile.getAbsolutePath());
                    }
                }
            } else if(operationStr.equals("prov")) {
                String formatStr = _getProvFormat(params);
                File karFile = null;
                try {
                    karFile = File.createTempFile("webviewProvKAR", ".kar");
                    ProvenanceManager.exportProvenance(karFile.getAbsolutePath(),
                        false, Arrays.asList(runLSID), formatStr);
                    _sendResponseWithFile(request, karFile, true);
                    // file will be deleted after response sent
                    karFile = null;
                } finally {
                    if(karFile != null && !karFile.delete()) {
                        System.err.println("WARNING: unable to delete " + karFile.getAbsolutePath());
                    }
                }
            } else if(operationStr.equals("roBundle")) {
                File roBundleFile = null;
                try {
                    roBundleFile = File.createTempFile("webviewROBundle", ".zip");
                    ProvenanceManager.exportROBundle(roBundleFile.getAbsolutePath(), runLSID);
                    _sendResponseWithFile(request, roBundleFile, true);
                    // file will be deleted after response sent
                    roBundleFile = null;
                } finally {
                    if(roBundleFile != null && !roBundleFile.delete()) {
                        System.err.println("WARNING: unable to delete " + roBundleFile.getAbsolutePath());    
                    }
                }
            } else {
                _sendResponseWithError(request, "Unsupported operation: " + operationStr);
            }
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            _sendResponseWithError(request, e.getMessage());
        }
    }
    
    /** Add keys-values. */
    private void _addKeysValues(JsonObject responseJson, WorkflowRun run) throws Exception {
            
        JsonObject keysValuesJson = new JsonObject();
        responseJson.put("keysValues", keysValuesJson);
        Map<String,String> keysValues = _queryable.getAssociatedKeysValuesForExecution(run.getExecId());
        for(Map.Entry<String,String> entry: keysValues.entrySet()) {
            keysValuesJson.put(entry.getKey(), entry.getValue());
        }
    }

    /** Add workflow outputs. */
    private void _addOutputs(JsonObject responseJson, WorkflowRun run) throws Exception {

        CompositeActor model = (CompositeActor)_queryable.getWorkflowForExecution(run.getExecId());
        WebViewServer._addModel(model);
        
        try {                                
            RecordPlayer player = new RecordPlayer(_queryable);
            player.executeActor(WebView.class.getName());
            player.play(run.getExecId(), model);
            
            // build the response JSON object from the client buffers
            // for this workflow.
            JsonArray arrayJson = new JsonArray();
            List<JsonObject> outputs = WebViewServer.getClientBuffer(model);
            if(outputs != null) {
                for(JsonObject outputJson : outputs) {
                    if(outputJson.containsKey("actor")) {
                        JsonObject actorObject = outputJson.getJsonObject("actor");
                        for(String idStr : actorObject.fieldNames()) {
                            JsonObject idObject = actorObject.getJsonObject(idStr);
                            if(idObject.containsKey("data")) {
                                arrayJson.add(idObject.getJsonObject("data"));
                            }
                        }
                    }
                }
            }
            // send the successful response
            //System.out.println(arrayJson.encodePrettily());
            responseJson.put("responses", arrayJson);
            
        } finally {
            WebViewServer.removeModel(model);
        }
    }
    
    /** Add prov if prov parameter is true. */
    private void _addProv(JsonObject responseJson, MultiMap params,
            WorkflowRun run) throws Exception {

        String formatStr = _getProvFormat(params);
        
        try(ByteArrayOutputStream stream = new ByteArrayOutputStream();) {
            ProvUtilities.writeProv(stream, formatStr, run.getExecId());
            responseJson.put("prov", stream.toString());
        }
    }
    
    /** Get the PROV format from request params. */
    private String _getProvFormat(MultiMap params) {
        String formatStr = params.get("provFormat");
        if(formatStr == null) {
            return "JSON";
        }
        return formatStr.toUpperCase();
    }
        
    /** Verify that a REST parameter is either true or false.
     * @param params the REST parameters
     * @param the name of the parameter
     * @return the value of the parameter
     */
    private boolean _getTrueFalseParameter(MultiMap params, String name)
        throws Exception {
        String value = params.get(name);
        if(value == null) {
            return false;
        }
        if(value != null && !"true".equals(value) && !"false".equals(value)) {
            throw new Exception("Parameter " + name + " must be either true or false.");
        }
        return value.toLowerCase().equals("true");
    }
        
}
