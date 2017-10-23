/*
 * Copyright (c) 2016 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-23 23:14:28 -0700 (Wed, 23 Aug 2017) $' 
 * '$Revision: 1383 $'
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

import java.net.HttpURLConnection;
import java.util.Set;

import org.kepler.webview.server.WebViewServer;

import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

/** Handler to execute workflows or apps. 
 * 
 * @author Daniel Crawl
 * @version $Id: RunWorkflowHandler.java 1383 2017-08-24 06:14:28Z crawl $
 */
public class RunWorkflowHandler extends BaseHandler {

    public RunWorkflowHandler(WebViewServer server) {
        super(server);
    }

    public void handleRunApp(RoutingContext context) {

        final long timestamp = System.currentTimeMillis();
        final JsonObject requestJson = new JsonObject(context.getBody().toString());
        
        _server.executeApp(requestJson, context.user(), res -> {
            _handleResult(res, context, requestJson, timestamp);
        });
    }

    public void handleRunWorkflow(final RoutingContext context) {
        
        final long timestamp = System.currentTimeMillis();

        final HttpServerRequest req = context.request();

        //System.out.println("headers: " + req.headers().names());
        //System.out.println("form attrs: " + req.formAttributes().names());
        //System.out.println(req.formAttributes().get("json"));

        JsonObject reqJson = null;
        
        String contentType = req.getHeader("Content-Type");
        
        //System.out.println(contentType);
        
        if(contentType == null || contentType.startsWith("text/plain")) {
            reqJson = new JsonObject(context.getBody().toString());
        } else {
            String jsonString = req.formAttributes().get("json");
            if(jsonString != null) {
                reqJson = new JsonObject(jsonString);
            }
            // TODO part name - should be workflow
            Set<FileUpload> uploads = context.fileUploads();
            if(uploads.size() > 1) {
                context.response()
                    .setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                    .setStatusMessage("Only expected single file upload.")
                    .end(Buffer.buffer(new JsonObject().put("error", "Only expected single file upload.").toString()));
                _server.log(req, context.user(), HttpURLConnection.HTTP_BAD_REQUEST, timestamp);
                return;
            }
            if(reqJson == null) {
                reqJson = new JsonObject();
            }
            String uploadName = uploads.iterator().next().uploadedFileName();            
            reqJson.put("wf_name", uploadName.substring(uploadName.indexOf("file-uploads")));
        }          
               

        final JsonObject requestJson = reqJson;
        
        //System.out.println(requestJson);
                        
        _server.executeWorkflow(requestJson, context.user(), res -> {
            _handleResult(res, context, requestJson, timestamp);
        });        
    }
    
    
    protected void _handleResult(AsyncResult<JsonObject> res, RoutingContext context,
        JsonObject requestJson, long timestamp) {

        int httpStatus;
        String message;
        JsonObject responseJson;
        
        if(res.succeeded()) {
            responseJson = res.result();
            httpStatus = HttpURLConnection.HTTP_OK;
            message = "success.";
        } else {
            responseJson = new JsonObject().put("error", res.cause().getMessage());
            // TODO not all errors are HTTP_BAD_REQUEST.
            httpStatus = HttpURLConnection.HTTP_BAD_REQUEST;
            message = "Failed to execute.";
        }
        
        Object reqId = requestJson.getValue("reqid");
        if(reqId != null) {
            responseJson.put("reqid", reqId);
        }
        
        StringBuilder responseStr;
        Object callback = requestJson.getValue("callback");
        if(callback == null) {
            responseStr = new StringBuilder(responseJson.encode());
        } else {
            responseStr = new StringBuilder(callback.toString())
                .append("(")
                .append(responseJson.encode())
                .append(");");
        }
        
        //System.out.println(responseStr);
        
        context.response()
            .setStatusCode(httpStatus)
            .setStatusMessage(message)
            .end(Buffer.buffer(responseStr.toString()));
        _server.log(context.request(), context.user(), httpStatus, timestamp);
    }
}
