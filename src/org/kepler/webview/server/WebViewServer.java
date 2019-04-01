/*
 * Copyright (c) 2015 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-09-04 12:59:35 -0700 (Mon, 04 Sep 2017) $' 
 * '$Revision: 1407 $'
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;
import org.kepler.build.modules.Module;
import org.kepler.build.modules.ModuleTree;
import org.kepler.gui.KeplerGraphFrame;
import org.kepler.gui.KeplerGraphFrame.Components;
import org.kepler.gui.KeplerGraphFrameUpdater;
import org.kepler.loader.util.ParseWorkflow;
import org.kepler.module.webview.Shutdown;
import org.kepler.objectmanager.lsid.KeplerLSID;
import org.kepler.provenance.ProvenanceRecorder;
import org.kepler.provenance.Queryable;
import org.kepler.provenance.Recording;
import org.kepler.util.ParseWorkflowUtil;
import org.kepler.webview.actor.ControlAttribute;
import org.kepler.webview.actor.ParametersAttribute;
import org.kepler.webview.server.app.App;
import org.kepler.webview.server.auth.AuthUtilities;
import org.kepler.webview.server.auth.WebViewAuthHandlerImpl;
import org.kepler.webview.server.handler.ActorHandler;
import org.kepler.webview.server.handler.LoginHandler;
import org.kepler.webview.server.handler.NoMatchHandler;
import org.kepler.webview.server.handler.RunIdHandler;
import org.kepler.webview.server.handler.RunWorkflowHandler;
import org.kepler.webview.server.handler.RunsHandler;
import org.kepler.webview.server.handler.TableOfContentsHandler;
import org.kepler.webview.server.handler.WorkflowHandler;
import org.kepler.webview.server.handler.WorkflowWebSocketHandler;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.handler.UserSessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import io.vertx.ext.web.sstore.SessionStore;
import ptolemy.actor.CompositeActor;
import ptolemy.actor.ExecutionListener;
import ptolemy.actor.Manager;
import ptolemy.actor.gui.ConfigurationApplication;
import ptolemy.kernel.util.Attribute;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.Settable;
import ptolemy.kernel.util.Workspace;
import ptolemy.moml.MoMLParser;
import ptolemy.moml.filter.BackwardCompatibility;
import ptolemy.util.MessageHandler;

public class WebViewServer extends AbstractVerticle {
    
    /** Execute an app.
     *  @param json The JSON object with the workflow name and any parameters.
     *  @param handler Asynchronous handler to receive the a JSON object with any results.
     */
    public void executeApp(JsonObject json, User user,
        Handler<AsyncResult<JsonObject>> handler) {

        String appName = json.getString("app_name");
        //System.out.println("execute workflow " + wfName);
        
        if(appName == null || appName.trim().isEmpty()) {
            handler.handle(Future.failedFuture("No app_name specified."));
            return;
        }

        if(user == null) {
            handler.handle(Future.failedFuture("User is not authenticated"));
            return;
        }
        
        for(String key: json.fieldNames()) {
            if(!key.equals("app_name") &&
                !key.equals("app_param") &&
                !key.equals("prov") && 
                !key.equals("reqid") &&
                !key.equals("sync")) {
                System.err.println("WARNING: unknown property in request: " + key);
            }
        }
        
        // the following will block, e.g., waiting on the workspace lock,
        // so execute in blocking threads.
        _vertx.<JsonObject>executeBlocking(future -> {

            //System.out.println("executeBlocking " + wfName);
            
            boolean runSynchronously = false;
            App app = null;
            boolean running = false;
            
            // see if app is already loaded
            try {
                app = _getApp(appName);
                
                if(app == null) {
                    throw new Exception("App not found.");
                }
                     
                JsonObject appParams;
                if(json.containsKey("app_param")) {
                    try {
                        appParams = json.getJsonObject("app_param");
                    } catch(ClassCastException e) {
                        throw new Exception("app_param must be a json object.");
                    }                
                } else {
                    appParams = new JsonObject();
                }
                                        
                runSynchronously = json.getBoolean("sync", false);
                if(!runSynchronously) {
                    throw new Exception("Asynchronous execution not supported for apps.");
                }
                
                if(runSynchronously) {                                                                                                                
                    // execute the application
                    running = true;
                    final App appCopy = app;
                    app.exec(user, appParams, appHandler -> {

                        if(appHandler.failed()) {
                            future.fail(appHandler.cause());
                        } else {
                            future.complete(new JsonObject().put("responses", appHandler.result()));                            
                        }
                        appCopy.close();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                future.fail("Error executing app: " + e.getMessage());
                return;
            } finally {
                if(!running) {
                    app.close();                   
                }
            }
                        
        // set ordered to false to allow parallel executions,
        // and use handler for result.    
        }, false, handler);
    }
    
    /** Execute a workflow.
     *  @param json The JSON object with the workflow name and any parameters.
     *  @param handler Asynchronous handler to receive the a JSON object with any results.
     */
    public void executeWorkflow(JsonObject json, User user,
        Handler<AsyncResult<JsonObject>> handler) {

        String wfName = json.getString("wf_name");
        //System.out.println("execute workflow " + wfName);
        
        if(wfName == null || wfName.trim().isEmpty()) {
            handler.handle(Future.failedFuture("No wf_name specified."));
            return;
        }

        if(user == null) {
            handler.handle(Future.failedFuture("User is not authenticated"));
            return;
        }
        
        // TODO correct way to convert Buffer to JSON?
        //System.out.println("recvd: " + json);
       
        // the following will block, e.g., waiting on the workspace lock,
        // so execute in blocking threads.
        _vertx.<JsonObject>executeBlocking(future -> {

            //System.out.println("executeBlocking " + wfName);
            
            boolean runSynchronously = false;
            ProvenanceRecorder recorder = null;
            CompositeActor model = null;
            
            // see if workflow is already loaded
            try {
                model = _getModel(wfName);

                if(model == null) {
                    throw new Exception("Workflow not found.");
                }
                                    
                try {
                    _setModelParameters(model, json, user);
                } catch (IllegalActionException e) {
                    throw new Exception("Error setting parameters: " + e.getMessage());
                }
                
                boolean recordProvenance = json.getBoolean("prov", true);
                runSynchronously = json.getBoolean("sync", false);
                
                // cannot run async without provenance
                if(!runSynchronously && !recordProvenance) {
                    throw new Exception("Cannot execute workflow asynchronously without recording provenance.");
                }
                
                recorder = ProvenanceRecorder.getDefaultProvenanceRecorder(model);

                if(recordProvenance) {
                    if(recorder == null) {                        
                        if(!ProvenanceRecorder.addProvenanceRecorder(model, null, null)) {                            
                            throw new Exception("Error adding provenance recorder to workflow.");
                        }
                        recorder = ProvenanceRecorder.getDefaultProvenanceRecorder(model);
                        if(recorder == null) {
                            throw new Exception("Cannot find provenance recorder in workflow after adding one.");
                        }
                    }                    
                    
                    //System.out.println("setting provenance username = " + user.principal().getString("username"));
                    recorder.username.setToken(user.principal().getString("username"));
                } else if (recorder != null) {
                    recorder.setContainer(null);
                }
                
                // create manager and add model
                final Manager manager = new Manager(model.workspace(), "Manager");

                try {
                    model.setManager(manager);
                } catch(IllegalActionException e) {
                    throw new Exception("Error setting Manager for sub-workflow: " + e.getMessage());
                }    
                
                String[] errorMessage = new String[1];

                ExecutionListener managerListener = new ExecutionListener() {
                    
                    @Override
                    public void executionError(Manager m, Throwable throwable) {
                        System.err.println("Workflow execution error: " + throwable.getMessage());
                        throwable.printStackTrace();
                        errorMessage[0] = throwable.getMessage();
                    }

                    @Override
                    public void executionFinished(Manager m) {
                        //System.out.println("finished");
                    }

                    @Override
                    public void managerStateChanged(Manager m) {                    
                        //System.out.println("manager state changed " + m.getState().getDescription());
                    }
                };
                
                manager.addExecutionListener(managerListener);
                
                if(runSynchronously) {
                    
                    final String[] runLSIDStr = new String[1];
                    if(recordProvenance) {
                        recorder.addPiggyback(new Recording() {
                            @Override
                            public void executionStart(KeplerLSID lsid) {
                                runLSIDStr[0] = lsid.toString();
                            }
                        });
                    }

                    final boolean[] timeout = new boolean[1];
                    timeout[0] = false;
                    long timerId = vertx.setTimer(_workflowTimeout, id -> {
                        timeout[0] = true;
                        manager.stop();
                        future.fail("Execution timeout.");
                    });
                    

                    // call execute() instead of run, otherwise exceptions
                    // go to execution listener asynchronously.
                    manager.execute();
                    
                    // see if we timed-out. if so, we already sent the response,
                    // so exit.
                    if(timeout[0]) {
                        return;
                    } else if(!vertx.cancelTimer(timerId)) {
                        System.err.println("Workflow timeout Timer does not exist.");
                    } else { System.out.println("cancelled timer."); }
                    
                    // see if there is a workflow exception
                    if(errorMessage[0] != null) {
                        future.fail(errorMessage[0]);
                        return;
                    }
                                                                        
                    JsonObject responseJson = new JsonObject();

                    if(recordProvenance) {
                        responseJson.put("id", runLSIDStr[0]);  
                    }
                    
                    // build the response JSON object from the client buffers
                    // for this workflow.
                    JsonArray arrayJson = new JsonArray();
                    List<JsonObject> outputs = getClientBuffer(model);
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
                    future.complete(responseJson);
                } else { // asynchronous             
                                        
                    final CompositeActor finalModel = model;
                    recorder.addPiggyback(new Recording() {
                        @Override
                        public void executionStart(KeplerLSID lsid) {
                            //System.out.println("execution lsid is " + lsid);
                            future.complete(new JsonObject().put("id", lsid.toString()));
                        }
                                                              
                        @Override
                        public void executionStop() {
                            removeModel(finalModel);
                        }
                    });
                    
                    manager.startRun();
                }
            } catch (Exception e) {
                future.fail("Error executing workflow: " + e.getMessage());
                return;
            } finally {
                if(model != null && runSynchronously) {
                    removeModel(model);
                    model = null;
                }
            }
            
        // set ordered to false to allow parallel executions,
        // and use handler for result.    
        }, false, handler);
    }
    
    /** Search for a file. The directories searched are the root directory (if specified),
     *  and <module>/resources/html/.
     *  @param name The name of the file to search for.
     *  @return If the path is found, the absolute path of the file.
     *  Otherwise, returns null.
     */
    public static String findFile(String name) {
        
        //System.out.println("findFile: " + name);
        
        if(_appendIndexHtml && name.endsWith("/")) {
            name = name.concat("index.html");
        }
        
        // search root dir first if specified
        if(_rootDir != null) {
            String fsPath = new StringBuilder(_rootDir)
                .append(File.separator)
                .append(name)
                .toString();
            // TODO check path is not outside _rootDir
            if(_vertx.fileSystem().existsBlocking(fsPath)) {
                return fsPath;
            }
        }
        
        // not found in root dir, so search module tree.
        for(Module module : _moduleTree) {
            String fsPath = new StringBuilder(module.getResourcesDir().getAbsolutePath())
                .append(File.separator)
                .append("html")
                .append(File.separator)
                .append(name).toString();
            // TODO check path is not outside of resources/html dir
            //System.out.println("checking " + fsPath);
            if(_vertx.fileSystem().existsBlocking(fsPath)) {
                return fsPath;
            }
        }
        
        // not found anywhere
        return null;
    }
    
    /** Get the client buffers for a workflow. */
    public static List<JsonObject> getClientBuffer(NamedObj model) {
        return _clientBuffers.get(model);
    }

    /** Called during initialization. */
    public static void initialize() {
                
        int workerPoolSize = WebViewConfiguration.getHttpServerWorkerThreads();
        VertxOptions options = new VertxOptions()
                .setWorkerPoolSize(workerPoolSize)
                .setMaxWorkerExecuteTime(Long.MAX_VALUE);
        
        _vertx = Vertx.vertx(options);
        
        String rootDir = WebViewConfiguration.getHttpServerRootDir();
        if(rootDir != null &&
            !rootDir.equals(WebViewConfiguration.DEFAULT_WEBVIEW_SERVER_ROOT_DIR)) {
            _rootDir = rootDir;
            System.out.println("Web view root dir is " + _rootDir);
        }
        
        _appendIndexHtml = WebViewConfiguration.getHttpServerAppendIndexHtml();
        
        _workflowTimeout = WebViewConfiguration.getHttpServerWorkflowTimeout();
        
        // register as an updater to get window open and close events
        KeplerGraphFrame.addUpdater(_updater);
       
        // start logging thread
        _loggingThread = new Thread(new Runnable() {
            @Override
            public void run() {
                String logPath = WebViewConfiguration.getHttpServerLogPath();
                System.out.println("Web-view logging to " + logPath);
                try(FileWriter writer = new FileWriter(logPath, true)) {
                    String message;
                    try {
                        while((message = _loggingQueue.take()) != STOP_LOGGING_THREAD_MESSAGE) {
                            writer.write(message);
                            writer.write('\n');
                            writer.flush();
                        }
                    } catch (InterruptedException e) {
                        MessageHandler.error("Interrupted while waiting in logging thread.", e);
                    }
                } catch (IOException e) {
                    MessageHandler.error("Error trying to write to web-view server log " +
                            logPath, e);
                }
            }
        });
        _loggingThread.start();   
        
        
        boolean daemon = WebViewConfiguration.shouldStartHttpServerAsDaemon();
        
        if(daemon) {
            System.out.println("Loading MoML filters.");
            
            // We set the list of MoMLFilters to handle Backward Compatibility.
            MoMLParser.setMoMLFilters(BackwardCompatibility.allFilters());
            
            // load the gui and cache configuration since we need the CacheManager
            // to load the KAREntryHandlers for exporting provenance kars.
            try {
                ConfigurationApplication.readConfiguration(
                    ConfigurationApplication.specToURL("ptolemy/configs/kepler/ConfigGUIAndCache.xml"));
            } catch (Exception e) {
                MessageHandler.error("Error creating Configuration.", e);
            }
        }

        if(WebViewConfiguration.shouldStartHttpServer() || daemon) {

            /* TODO still necessary?
            List<URL> list = new LinkedList<URL>();
            for(String path : System.getProperty("java.class.path").split(File.pathSeparator)) {
                try {
                    list.add(new File(path).toURI().toURL());
                } catch (MalformedURLException e) {
                    MessageHandler.error("Bad URL.", e);
                }
            }
             */
            // list.toArray(new URL[list.size()]),
            
            DeploymentOptions deploymentOptions = new DeploymentOptions()
                    .setInstances(WebViewConfiguration.getHttpServerInstances());
            
            WebViewServer.vertx().deployVerticle(
                WebViewServer.class.getName(),
                deploymentOptions,
                result -> {
                    if(result.failed()) {
                        MessageHandler.error("Failed to deploy web view server.", result.cause());
                        // TODO shut down kepler.
                    }
                }
            );
            
            for(String model: WebViewConfiguration.getPreloadModels()) {
                System.out.println("Preloading " + model);
                try {
                    if(_getModel(model) == null) {
                        System.err.println("ERROR: Unable to find model for preload: " + model); 
                    }
                } catch (Exception e) {
                    System.err.println("ERROR: Unable to preload " + model + ": " + e.getMessage());
                }
            }
            
            // TODO preload apps
                        
        } else {
            // not starting server, so set latch to 0
            Shutdown.shutdownLatch.countDown();
        }
    }
    
    /** Log an http server request.
     *  @param request The request
     *  @param user The user
     *  @param status The status
     *  @param timestamp The timestamp when the request occurred.
     */
    public void log(HttpServerRequest request, User user, int status, long timestamp) {
        log(request, user, status, timestamp, -1);
    }
    
    /** Log an http server request.
     *  @param request The request
     *  @param user The user
     *  @param status The status
     *  @param timestamp The timestamp when the request occurred.
     *  @param length The length of the file successfully sent to the client.
     */
    public void log(HttpServerRequest request, User user, int status, long timestamp, long length) {
        
        String versionStr;
        switch(request.version()) {
        case HTTP_1_1:
            versionStr = "HTTP/1.1";
            break;
        case HTTP_1_0:
            versionStr = "HTTP/1.0";
            break;
        default:
            versionStr = "-";
            break;
        }
        
        String hostString;
        SocketAddress address = request.remoteAddress();
        if(address == null) {
            hostString = "-";
        } else {
            hostString = address.host();
        }
        
        String referrer = request.headers().get("referrer");
        if(referrer == null) {
            referrer = "-";
        }
        
        String userAgent = request.headers().get("user-agent");
        if(userAgent == null) {
            userAgent = "-";
        }
        
        String userNameStr = "-";
        if(user != null) {
            userNameStr = user.principal().getString("username");
        }
                    
        // TODO how to get user-identifier and userid?
        String message = String.format("%s - %s [%s] \"%s %s %s\" %d %s \"%s\" \"%s\"",
            hostString,
            userNameStr,
            _logTimeFormat.format(new Date(timestamp)),
            request.method(),
            request.uri(),
            versionStr,
            status,
            length < 0 ? "-" : String.valueOf(length),
            referrer,
            userAgent);

        try {
            _loggingQueue.put(message);
        } catch (InterruptedException e) {
            MessageHandler.error("Interuppted adding to logging thread.", e);
        }
    }
    
    public void log(ServerWebSocket socket, String command, String path) {
        log(socket, command, path, null);
    }

    public void log(ServerWebSocket socket, String command, String path, Buffer buffer) {
        
        final long timestamp = System.currentTimeMillis();
        
        String hostString;
        SocketAddress address = socket.remoteAddress();
        if(address == null) {
            hostString = "-";
        } else {
            hostString = address.host();
        }
        
        String referrer = socket.headers().get("referrer");
        if(referrer == null) {
            referrer = "-";
        }
        
        String userAgent = socket.headers().get("user-agent");
        if(userAgent == null) {
            userAgent = "-";
        }

        String message = String.format("%s - - [%s] \"%s %s %s %s\" %s %s \"%s\" \"%s\"",
            hostString,
            _logTimeFormat.format(new Date(timestamp)),
            command,
            path,
            buffer == null ? "-" : buffer.toString(),
            "websocket",
            "-", // status
            "-", // length
            referrer,
            userAgent);

        try {
            _loggingQueue.put(message);
        } catch (InterruptedException e) {
            MessageHandler.error("Interuppted adding to logging thread.", e);
        }
    }

    /** Start the server. */
    @Override
    public void start() throws Exception {
        super.start();
        
        _auth = WebViewConfiguration.getAuthProvider();
        
        _queryable = ProvenanceRecorder.getDefaultQueryable(null);
        if(_queryable == null) {
            throw new Exception("Unable to get provenance queryable.");
        }
        
        Router router = Router.router(vertx);

        if(WebViewConfiguration.getHttpServerCorsEnabled()) {
            String allowOriginPatternStr = WebViewConfiguration.getHttpServerCorsAllowOriginPattern();
            if(allowOriginPatternStr == null || allowOriginPatternStr.trim().isEmpty()) {
                throw new Exception("Must specify allow origini pattern for CORS.");
            }
            router.route().handler(CorsHandler.create(allowOriginPatternStr)
                    .allowedMethod(HttpMethod.GET)
                    .allowedMethod(HttpMethod.POST)
                    .allowedMethod(HttpMethod.OPTIONS)
                    .allowCredentials(true)
                    .allowedHeader("Access-Control-Allow-Credentials")
                    .allowedHeader("X-PINGARUNER")
                    .allowedHeader("Content-Type")
                    .allowedHeader("authorization"));
            System.out.println("CORS enabled for " + allowOriginPatternStr);
        }
                
        
        // create cookie and session handlers
        // authenticated users are cached in the session so authentication
        // does not need to be performed for each request.
        router.route().handler(CookieHandler.create());
        
        BodyHandler bodyHandler = BodyHandler.create();
        
        if(_rootDir != null) {
            File uploadsDir = new File(_rootDir, "file-uploads");
            if(!uploadsDir.exists()) {
                if(!uploadsDir.mkdirs()) {
                    throw new Exception("Unable to create file uploads directory " + uploadsDir);
                }
            }
            bodyHandler.setUploadsDirectory(uploadsDir.getAbsolutePath());
        }
        
        // NOTE: need to install the BodyHandler before any handlers that
        // make asynchronous calls such as BasicAuthHandler.
        router.route().handler(bodyHandler);
        
        SessionStore sessionStore = LocalSessionStore.create(_vertx);
        SessionHandler sessionHandler = SessionHandler.create(sessionStore)
            .setSessionCookieName("WebViewSession")
            .setSessionTimeout(WebViewConfiguration.getHttpServerSessionTimeout());
        router.route().handler(sessionHandler);
        router.route().handler(UserSessionHandler.create(_auth));
        
        //System.out.println("session timeout = " + WebViewConfiguration.getHttpServerSessionTimeout());
        
        // use custom auth handler with custom auth scheme to prevent
        // browsers (e.g., chrome) from opening auth dialog.
        // FIXME read realm from conf file.        
        AuthHandler authenticationHandler = new WebViewAuthHandlerImpl(_auth, "WebView");
        
        Handler<RoutingContext> authorizationHandler = new Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext c) {
                if(c.user() == null) {
                    c.next();
                } else {
                    c.user().isAuthorized("login", result -> {
                        if(result.failed()) {
                            c.response()
                                .putHeader("Content-type", "text/plain")
                                .setStatusCode(HttpURLConnection.HTTP_FORBIDDEN)
                                .end(result.cause().getMessage());
                        } else {
                            c.next();
                        }
                    });
                }
            }            
        };
        
        // everything under /kepler and /app needs to be
        // authenticated and authorized.
        router.route("/kepler/*")
            .handler(authenticationHandler)
            .handler(authorizationHandler);
        router.route("/app")
            .handler(authenticationHandler)
            .handler(authorizationHandler);
        
        // TODO should these be under /kepler?
        router.getWithRegex("^/wf/(\\d+)$").handler(new WorkflowHandler(this));
        router.getWithRegex("^/wf/(\\d+(?:\\-\\d+)+)$").handler(new ActorHandler(this));
        
        RunWorkflowHandler runWorkflowHandler = new RunWorkflowHandler(this);
        router.post("/kepler/runwf").handler(runWorkflowHandler::handleRunWorkflow);
        router.post("/app").handler(runWorkflowHandler::handleRunApp);
        
        router.post("/kepler/runs").handler(new RunsHandler(this));
        
        RunIdHandler runIdHandler = new RunIdHandler(this);
        
        String runIdRegexStr = "(urn:lsid:[^:]+:[^:]+:[^:]+(?::\\d+)?)";         
        router.getWithRegex("/kepler/runs/" + runIdRegexStr + "/(\\w+)").blockingHandler(runIdHandler::handleBinary);
        router.getWithRegex("/kepler/runs/" + runIdRegexStr).handler(runIdHandler);
        
        if(WebViewConfiguration.getHttpServerMetadataFileName() != null) {
            System.out.println("Metadata file set; requests at /login");
            
            router.route("/login")
                .handler(authenticationHandler)            
                .handler(authorizationHandler);
            
            LoginHandler loginHandler = new LoginHandler(this);
            router.route("/login").handler(loginHandler);
                        
            // login session handler to check if session cookie is valid.
            /*
            router.route("/loginSession")
                .handler(authorizationHandler)
                .handler(loginSessionContext -> {
                
                System.out.println("session " + loginSessionContext.session().id());
                
                // if not valid, return 400
                if(loginSessionContext.user() == null) {
                    System.out.println("user is null");
                    loginSessionContext.response()
                        .putHeader("Content-Type", "text/plain")
                        .setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST)
                        .end();
                } else {
                    // otherwise call login handler to return metadata.
                    loginSessionContext.next();
                }
            });
            router.route("/loginSession").handler(loginHandler);
            */
            
            // logout handler to remove session and user.
            router.route("/logout").handler(authenticationHandler);
            // FIXME what about authorization handler?
            router.route("/logout").handler(logoutContext -> {
                //System.out.println("destroying session " + logoutContext.session().id());
                logoutContext.session().destroy();
                logoutContext.clearUser();
                logoutContext.response()
                    .putHeader("Content-Type", "text/plain")
                    .end();
            });
        }

        if(WebViewConfiguration.enableHttpServerTableOfContents()) {
            String path = WebViewConfiguration.getHttpServerTableOfContentsPath();
            if(path == null) {
                path = "^(?:/kepler/toc){0,1}/*$";
            }
            System.out.println("Enabling http server table of contents at " + path);
            router.getWithRegex(path).handler(new TableOfContentsHandler(this));
        }
        
        // add route that handles anything unmatched
        router.route().handler(new NoMatchHandler(this));
                
        if(WebViewConfiguration.enableHttps()) {
            
            sessionHandler.setCookieSecureFlag(true);
            
            String pemKeyStr = WebViewConfiguration.getHttpsPEMKeyPath();
            if(pemKeyStr == null) {
                throw new Exception("Must specify PEM key file for HTTPS server.");
            }
            
            String pemCertStr = WebViewConfiguration.getHttpsPEMCertPath();
            if(pemCertStr == null) {
                throw new Exception("Must specify PEM certificate file for HTTPS server.");                
            }
            
            int securePort = WebViewConfiguration.getHttpsServerPort();
            
            // FIXME what if pemCert or pemKey do not exist?
            
            HttpServerOptions options = new HttpServerOptions()
                .setSsl(true)
                .setPemKeyCertOptions(new PemKeyCertOptions()
                    .setCertPath(pemCertStr)
                    .setKeyPath(pemKeyStr))
                .setPort(securePort);

            System.out.println("Starting secure web server on port " + securePort);
            
            _secureServer = vertx.createHttpServer(options)
                    .websocketHandler(new WorkflowWebSocketHandler(this))
                    .requestHandler(router::accept)
                    .listen();
           
            if(WebViewConfiguration.shouldHttpServerRedirect()) {

                int status = WebViewConfiguration.getHttpServerRedirectStatus();
                String hostname = WebViewConfiguration.getHttpServerRedirectHostname();
                int redirectFromPort = WebViewConfiguration.getHttpServerPort();
                int redirectToPort = WebViewConfiguration.getHttpServerRedirectPort();
                
                String redirectUrl = "https://" + hostname + ":" + redirectToPort;
                
                System.out.println("Redirecting web view server port " + redirectFromPort +
                    " to " + redirectUrl + " with status " + status);
                
                _server = vertx.createHttpServer()
                    .requestHandler(req -> {
                        req.response().headers().set("Location", redirectUrl + req.path());                        
                        req.response()
                            .setStatusCode(status)
                            .end();
                    })
                    .listen(redirectFromPort);
            }

        } else {
        
            int port = WebViewConfiguration.getHttpServerPort();
            
            System.out.println("Starting web view server on port " + port);

            _server = vertx.createHttpServer()
                .websocketHandler(new WorkflowWebSocketHandler(this))
                .requestHandler(router::accept)
                .listen(port);
        }
    }
        
    public static void removeModel(NamedObj model) {

        // notify any clients that the workflow was closed.            
        try {
            WebViewableUtilities.sendEvent(
                WebViewableUtilities.Event.WorkflowClosed, model);
        } catch (IllegalActionException e) {
            MessageHandler.error("Error notifying clients that workflow closed.", e);
        }
        
        WebViewId.removeWorkflow(model);
        
        //System.gc();
        _clientBuffers.remove(model);
        
        //System.out.println("removing " + model + " " + model.hashCode());
        /*
        for(CompositeActor master : _models.values()) {
            System.out.println("master " + master + " " + master.hashCode());
        }
        */
        
        //System.out.println("remove " + model.hashCode());

        //_models.remove(model);
        
        //System.out.println("removed workflow " + model.getName());
    }
    
    /** Called during shutdown. */
    public static void shutdown() {
                
        KeplerGraphFrame.removeUpdater(_updater);
            
        for(CompositeActor model : _models.values()) {
            removeModel(model);
        }
        
        _models.clear();
        
        for(App app : _apps.values()) {
            app.close();
        }
        
        _apps.clear();
        
        // stop the logging thread
        _loggingQueue.add(STOP_LOGGING_THREAD_MESSAGE);
        try {
            _loggingThread.join(5000);
        } catch (InterruptedException e) {
            MessageHandler.error("Interrupted while waiting for logging thread to stop.", e);
        }
        _loggingThread = null;
    }
    
    /** Stop the server. */
    @Override
    public void stop() throws Exception {
        super.stop();
        
        if(_server != null) {
            //System.out.println("Stopping vertx web server.");
            _server.close();
            _server = null;
        }
        
        
        if(_secureServer != null) {
            _secureServer.close();
            _secureServer = null;
        }
    }
        
    public static Vertx vertx() {
        return _vertx;
    }

    private static class ModelUpdater implements KeplerGraphFrameUpdater {

        @Override
        public int compareTo(KeplerGraphFrameUpdater o) {
            return 1;
        }

        @Override
        public void updateFrameComponents(Components components) {
            try {
                CompositeActor model = (CompositeActor) components.getFrame().getModel();
                _addModel(model);
                _models.put(model.getName(), model);
            } catch (Exception e) {
                MessageHandler.error("Error adding model from UI.", e);
            }

        }

        /** Remove the model associated with the frame from the web server. */
        @Override
        public void dispose(KeplerGraphFrame frame) {
            CompositeActor model = (CompositeActor) frame.getModel();
            removeModel(model);
            _models.remove(model.getName());
        }
        
    }
            
    ///////////////////////////////////////////////////////////////////
    ////                         public variables                  ////
    
    public final static String WS_PATH = "/ws/";
    public final static String WS_RUNWF_PATH = "/ws-runwf";

    ///////////////////////////////////////////////////////////////////
    ////                         package-protected variables       ////


    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////

    /** Add a model to the set managed by the server. */
    public static void _addModel(CompositeActor model) throws Exception {
        
        //System.out.println("adding model " + model.getFullName());

        //_models.add(model);

        //System.out.println("put " + model.hashCode());
        _clientBuffers.put(model,  new LinkedList<JsonObject>());
        
        /*
        ProvenanceRecorder recorder = ProvenanceRecorder.getDefaultProvenanceRecorder(model);
        if(recorder != null) {
            recorder.addPiggyback(new WebViewRecording());
        }
        */

        if(model.attributeList(ControlAttribute.class).isEmpty()) {
            ControlAttribute control = new ControlAttribute(model, "_wvControl");
            control.setPersistent(false);
        }
        
        if(model.attributeList(ParametersAttribute.class).isEmpty()) {
            ParametersAttribute parameters = new ParametersAttribute(model, "_wvParameters");
            parameters.setPersistent(false);
        }
        
        /*
        WebViewAttribute timeline = (WebViewAttribute) model.getAttribute("_wfTimeline");
        if(timeline == null) {
            timeline = new WebViewAttribute(model, "_wfTimeline");
            timeline.htmlFile.setExpression("web-view/visjs/wf-timeline.html");
            timeline.title.setExpression("Workflow Execution Timeline");
        }
        timeline.setPersistent(false);
        */
    }
    
    /** Get a clone of a model. */
    private static CompositeActor _cloneModel(CompositeActor masterModel) throws CloneNotSupportedException {
        CompositeActor model = (CompositeActor)masterModel.clone(new Workspace());
        _clientBuffers.put(model,  new LinkedList<JsonObject>());
        //System.out.println("put " + model.hashCode());
        return model;
    }

    /** Convert from a JSON object value to a string representation of a Ptolemy token. */
    private static String _convertJSONToTokenString(Object value) {
        if(value instanceof JsonArray) {
            StringBuilder buf = new StringBuilder("{");
            Iterator<Object> iter = ((JsonArray)value).iterator();
            while(iter.hasNext()) {
                Object item = iter.next();
                // strings need to be surrounded by quotes in arrays
                if(item instanceof String) {
                    buf.append("\"")
                        .append(item)
                        .append("\"");
                } else {
                    buf.append(item);
                }
                if(iter.hasNext()) {
                    buf.append(",");
                }
            }
            return buf.append("}").toString();
        } else {
            // FIXME if value is a string and parameter is not in string mode,
            // will this work?
            return value.toString();
        }
    }
    
    @SuppressWarnings("resource")
    private App _getApp(String name) throws Exception {
        
        App masterApp = null;
        synchronized(_apps) {            
            // see if we've already instantiated this app
            masterApp = _apps.get(name);
            if(masterApp == null) {
            
                String className = WebViewConfiguration.getAppClassName(name);
                
                if(className == null || className.trim().isEmpty()) {
                    throw new Exception("Missing class name for app " + name);
                }
                
                try {
                    Class<?> appClass = Class.forName(className);
                    masterApp = (App) appClass.newInstance();
                    _apps.put(name, masterApp);
                } catch(ClassNotFoundException | InstantiationException
                    | IllegalAccessException e) {
                    throw new Exception("Error instantiating app " + name + ": " + e.getMessage());
                }    
            }
        }        
        return (App) masterApp.clone();
    }

    /** Get the model for a specific name. If the model is not already
     *  loaded, then use findFile() to find the model file with the
     *  given name, name.kar and name.xml. Otherwise, return a copy
     *  of the already-loaded model.
     *  @return If a model with the specified name is loaded or can be
     *  found and parsed, returns the model. Otherwise, null.
     */
    private static CompositeActor _getModel(String name) throws Exception {
                
        //System.out.println("size = " + _models.size());
        
        synchronized(_models) {
            
            // see if the model is already loaded.
            CompositeActor masterModel = _models.get(name);
            if(masterModel != null) {
                //System.out.println("found master model.");
                return _cloneModel(masterModel);
            }
                    
            // if not, find workflow file and load
            String wfFileStr = findFile(name);
            if(wfFileStr == null) {
                wfFileStr = findFile(name + ".kar");
                if(wfFileStr == null) {
                    wfFileStr = findFile(name + ".xml");
                    if(wfFileStr == null) {
                        return null;
                    }
                }
            }
            
            masterModel = (CompositeActor) ParseWorkflow.parseKAR(new File(wfFileStr), true);
            _addModel(masterModel);
            _models.put(name, masterModel);
            //System.out.println("adding master model " + name);
            
            // see if there is a configuration file for parameters
            File wfFile = new File(wfFileStr);
            File wfDir = wfFile.getParentFile();
            File wfConfFile = new File(wfDir, 
                FilenameUtils.getBaseName(wfFileStr) + ".conf");
            if(wfConfFile.exists()) {
                ParseWorkflowUtil.setParametersFromFile(masterModel, wfConfFile.getAbsolutePath());
            }
            
            return _cloneModel(masterModel);
        }
    }
        
    /** Set any parameters for a model from a JSON object. */
    private static void _setModelParameters(NamedObj model, JsonObject json,
        User user) throws IllegalActionException {
        
        // get any parameters
        JsonObject params = null;
        if(json.containsKey("wf_param")) {
            try {
                params = json.getJsonObject("wf_param");
            } catch(ClassCastException e) {
                throw new IllegalActionException("wf_param must be a json object.");
            }
        
            for(Map.Entry<String, Object> entry: params) {
                final String paramName = entry.getKey();
                //System.out.println("attempting to set parameter " + paramName);
                Attribute attribute = model.getAttribute(paramName);
                if(attribute == null || !(attribute instanceof Settable)) {
                    throw new IllegalActionException("Workflow does not have settable parameter " + paramName);
                }
    
                try {
                    String valueStr = _convertJSONToTokenString(entry.getValue());
                    ((Settable)attribute).setExpression(valueStr);
                } catch (IllegalActionException e) {
                    throw new IllegalActionException("Error settings parameter " +
                                paramName + ": " + e.getMessage());
                }
            }
        }
                

        // set user full name if present
        Attribute attribute = model.getAttribute("WebView_FullName");
        if(attribute != null) {
            if(!(attribute instanceof Settable)) {
                System.err.println("WARNING: WebView_FullName parameter is not settable.");
            } else {
                ((Settable)attribute).setExpression(user.principal().getString("fullname"));
            }
            attribute = null;
        }
        
        // set the authorization groups parameter if present        
        attribute = model.getAttribute("WebView_groups");
        if(attribute != null) {
            if(!(attribute instanceof Settable)) {
                System.err.println("WARNING: WebView_group parameter is not settable.");
            } else {
                // add quotes around each string since we use this to set the
                // WebView_groups parameter in the workflow.
                String authGroupsStr = AuthUtilities.getGroups(user).stream()
                    .map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
                //System.out.println(authGroupsStr);
                ((Settable)attribute).setExpression("{" + authGroupsStr + "}");
            }
        }

    }
        
    ///////////////////////////////////////////////////////////////////
    ////                         private variables                 ////

    private Queryable _queryable;
    
    /** Authentication provider. */
    private AuthProvider _auth;
    
    /** HTTP server listening for HTTP and WS requests. */
    private HttpServer _server;
    
    /** HTTPS server listening for HTTPS and WSS requests. */
    private HttpServer _secureServer;
        
    /** ModelUpdater used to add/remove workflows when KeplerGraphFrames
     *  are opened/closed.
     */
    private final static ModelUpdater _updater = new ModelUpdater();
    
    /** The module tree. */
    private final static ModuleTree _moduleTree = ModuleTree.instance();
    
    /** The root directory to search for files. If not set, the value is null. */
    private static String _rootDir;

    // TODO need better name
    private final static Map<NamedObj,List<JsonObject>> _clientBuffers =
            Collections.synchronizedMap(new HashMap<NamedObj,List<JsonObject>>());

    public final static Map<String,CompositeActor> _models
        = Collections.synchronizedMap(new HashMap<String,CompositeActor>());

    public final static Map<String,App> _apps
        = Collections.synchronizedMap(new HashMap<String,App>());
    
    /* Timestamp format for logging. */
    private final DateFormat _logTimeFormat = new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");
    
    private static Thread _loggingThread;
    
    private final static String STOP_LOGGING_THREAD_MESSAGE = "STOP";
    
    private final static LinkedBlockingQueue<String> _loggingQueue = new LinkedBlockingQueue<String>();
    
    /** If true, append index.html to all (unmatched) GETs ending with "/". */
    private static boolean _appendIndexHtml;
        
    private static Vertx _vertx;
    
    private static long _workflowTimeout;
}
