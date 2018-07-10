/*
 * Copyright (c) 2016 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-09-04 12:58:02 -0700 (Mon, 04 Sep 2017) $' 
 * '$Revision: 1405 $'
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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.httpclient.util.HttpURLConnection;
import org.kepler.CommandLineArgument;
import org.kepler.build.modules.Module;
import org.kepler.configuration.ConfigurationManager;
import org.kepler.configuration.ConfigurationProperty;
import org.kepler.util.DotKeplerManager;
import org.kepler.webview.server.auth.DrupalAuth;
import org.kepler.webview.server.auth.NoneAuth;
import org.kepler.webview.server.auth.SimpleAuth;

import io.vertx.ext.auth.AuthProvider;

/** Utility class to read WebView configuration.
 * 
 * @author Daniel Crawl
 * @version $Id: WebViewConfiguration.java 1405 2017-09-04 19:58:02Z crawl $
 */
public class WebViewConfiguration {

    /** This class cannot be instantiated. */
    private WebViewConfiguration() {
        
    }

    /** Returns true if the http server table of contents should be enabled. */
    public static boolean enableHttpServerTableOfContents() {        
        return _getConfigurationBoolean("server.tableOfContents.enable", false);
    }

    /** Returns true if the http server should use ssl. */
    public static boolean enableHttps() {
        return _getConfigurationBoolean("server.ssl.enable", false);
    }

    /** Returns the auth token for ROHUB. */
    public static String getROHubAuthToken() {
        return _getConfigurationString("server.roHub.authToken", null);
    }
    
    /** Returns the URI for ROHUB REST API. */
    public static String getROHubURI() {
        return _getConfigurationString("server.roHub.uri", null);       
    }
    
    /** Get the authorization group.
     *  @param username The user name.
     *  @param password The password.
     *  @return Returns null if username not found, or password does not match.
     */ 
    public static String getSimpleAuthGroup(String username, String password) {
        for(ConfigurationProperty property : _getConfigurationProperties("server.auth.entity")) {
            if(property.getProperty("user").getValue().equals(username)) {
                if(property.getProperty("password").getValue().equals(password)) {
                    return property.getProperty("group").getValue();
                }
                // found user, but incorrect password.
                return null;
            }
        }
        // did not find user.
        return null;
    }
    
    /** Get the class name of an app. */
    public static String getAppClassName(String appName) {
        for(ConfigurationProperty property : _getConfigurationProperties("server.apps.app")) {
            if(property.getProperty("name").getValue().equals(appName)) {
                return property.getProperty("class").getValue();
            }
        }
        return null;
    }
    
    /** Get config properties for an app. */
    public static List<ConfigurationProperty> getAppProperties(String appName, String confName) {

        for(ConfigurationProperty property : _getConfigurationProperties("server.apps.app")) {
            if(property.getProperty("name").getValue().equals(appName)) {
                return property.getProperties(confName);
            }
        }
        return new LinkedList<ConfigurationProperty>();
    }

    /** Get an authentication provider based on the authentication type
     *  specified in the configuration.
     */
    public static AuthProvider getAuthProvider() {
        String authType = _getConfigurationString("server.auth.type", null);
        if(authType == null || authType.equals("simple")) {
            return new SimpleAuth();
        } else if(authType.equals("none")) {
            System.err.println("WARNING: WebView server authentication disabled.");
            return new NoneAuth();
        } else if(authType.equals("drupal")) {
            return new DrupalAuth(_getConfigurationString("server.auth.drupal.host", null),
                _getConfigurationString("server.auth.drupal.service", null),
                _getConfigurationString("server.auth.drupal.role", null),
                _getConfigurationString("server.auth.drupal.groupsField", null),
                _getConfigurationString("server.auth.drupal.fullNameField", null));

        } else if(authType.equals("class")) {
            String className = _getConfigurationString("server.auth.class", null);
            if(className != null) {
                try {
                    Class<?> clazz = Class.forName(className);
                    return (AuthProvider)clazz.newInstance();
                } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                    System.err.println("ERROR: could not instantiate auth class " + className + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } else {
            System.err.println("ERROR: unknown type of authentication: " + authType);
        }
        return null;
    }

    /** Get the CORS allow origin pattern. */
    public static String getHttpServerCorsAllowOriginPattern() {
        return _getConfigurationString("server.cors.allowOrigin", null);
    }
    
    /** Returns true if the http server allows CORS. */
    public static boolean getHttpServerCorsEnabled() {
        return _getConfigurationBoolean("server.cors.enable", false);
    }

    /** Returns true if the http server allows workflows to be downloaded. */
    public static boolean getHttpServerAllowWorkflowDownloads() {
        return _getConfigurationBoolean("server.allowWorkflowDownloads", false);
    }

    public static boolean getHttpServerAppendIndexHtml() {
        return _getConfigurationBoolean("server.appendIndexHtml", false);
    }
        
    /** Get list of server directories that can be indexed. */
    public static Set<String> getHttpServerDirectoriesToIndex() {
        return _getConfigurationStrings("server.directoryIndex.dir");
    }
    
    /** Get the number of instances of http servers to start. */
    public static int getHttpServerInstances() {
        
        boolean wasParsed = CommandLineArgument.wasParsed(WEBVIEW_SERVER_INSTANCES_FLAG);
        if(wasParsed) {
            return Integer.valueOf(
                CommandLineArgument.get(WEBVIEW_SERVER_INSTANCES_FLAG).getValue())
                .intValue();
        }        

        return _getConfigurationInteger("server.instances", DEFAULT_WEBVIEW_SERVER_INSTANCES);
    }
    
    /** Get the log directory and file name for the http server. */
    public static String getHttpServerLogPath() {
        return _getConfigurationString("server.logPath",
            DotKeplerManager.getInstance().getPersistentModuleDirectory("web-view") +
            File.separator +
            "server.log");
    }

    /** Get the metadata file name. */
    public static String getHttpServerMetadataFileName() {
        String fileName = _getConfigurationString("server.metadataFile", null);
        if(fileName != null && !fileName.startsWith(File.separator)) {
            fileName = _getConfigurationModule("server.metadataFile").getConfigurationsDir() +
                File.separator + fileName;
        }
        return fileName;
    }

    /* Get the port number for the http server. */
    public static int getHttpServerPort() {
        
        boolean wasParsed = CommandLineArgument.wasParsed(WEBVIEW_SERVER_PORT_FLAG);
        if(wasParsed) {
            return Integer.valueOf(
                CommandLineArgument.get(WEBVIEW_SERVER_PORT_FLAG).getValue())
                .intValue();
        }        

        return _getConfigurationInteger("server.port", DEFAULT_WEBVIEW_SERVER_PORT);
    }

    /** Get the http redirect status code. */
    public static int getHttpServerRedirectStatus() {
        return _getConfigurationInteger("server.ssl.redirectHttp.status", HttpURLConnection.HTTP_MOVED_PERM);
    }
    
    /** Get the hostname to redirect http requests to. */
    public static String getHttpServerRedirectHostname() {
        return _getConfigurationString("server.ssl.redirectHttp.hostname", "localhost");
    }
    
    /** Get the port to redirect http requests to. */
    public static int getHttpServerRedirectPort() {
        return _getConfigurationInteger("server.ssl.redirectHttp.port", DEFAULT_WEBVIEW_SECURE_SERVER_PORT);
    }

    /** Returns the root directory for the HTTP Server. If none specified,
     *  returns null.
     */
    public static String getHttpServerRootDir() {
        
        boolean wasParsed = CommandLineArgument.wasParsed(WEBVIEW_SERVER_ROOT_DIR_FLAG);
        if(wasParsed) {
            return CommandLineArgument.get(WEBVIEW_SERVER_ROOT_DIR_FLAG).getValue();
        }        

        return _getConfigurationString("server.rootDir", null);
    }

    /** Get the timeout, in milliseconds, for the session if not accessed. */
    public static long getHttpServerSessionTimeout() {
        return _getConfigurationLong("server.sessionTimeout",
            DEFAULT_WEBVIEW_SERVER_SESSION_TIMEOUT);
    }
    
    /** Get the path regex of the http server table of contents. */
    public static String getHttpServerTableOfContentsPath() {
        return _getConfigurationString("server.tableOfContents.pathRegex", null);
    }
    
    /** Returns the number of threads for the worker pool. */
    public static int getHttpServerWorkerThreads() {
        return _getConfigurationInteger("server.workerThreads", 1);
    }

    /** Get the timeout, in milliseconds, for workflow execution.
     * Returns -1 for no timeout.
     */
    public static long getHttpServerWorkflowTimeout() {
        return _getConfigurationLong("server.workflowTimeout", -1);
    }

    /** Get the port number for the https server. */
    public static int getHttpsServerPort() {
        return _getConfigurationInteger("server.ssl.port", DEFAULT_WEBVIEW_SECURE_SERVER_PORT);
    }

    /** Get the SSL PEM Key file path. */
    public static String getHttpsPEMKeyPath() {
        return _getConfigurationString("server.ssl.key", null);
    }

    /** Get the SSL PEM Certificate file path.*/
    public static String getHttpsPEMCertPath() {
        return _getConfigurationString("server.ssl.cert", null);
    }
        
    public static boolean shouldHttpServerRedirect() {
        return _getConfigurationBoolean("server.ssl.redirectHttp.enable", false);
    }

    /** Returns true if the web view http server should start. */
    public static boolean shouldStartHttpServer() {
        
        boolean retval = CommandLineArgument.wasParsed(WEBVIEW_START_SERVER_FLAG);
                
        if(!retval) {
            retval = _getConfigurationBoolean("server.startServer", false);
        }
        
        return retval;
    }
    
    /** Returns true if the web view http server should start in daemon mode. */
    public static boolean shouldStartHttpServerAsDaemon() {
        
        boolean retval = CommandLineArgument.wasParsed(WEBVIEW_SERVER_DAEMON_FLAG);
        
        if(!retval) {
            return _getConfigurationBoolean("server.daemon", false);
        }
        
        return retval;
    }

    ///////////////////////////////////////////////////////////////////
    ////                         public variables                  ////

    /** Command line flag to specify the server port. */
    public final static String WEBVIEW_SERVER_PORT_FLAG = "-wvport";
    
    /** The default server port. */
    public final static int DEFAULT_WEBVIEW_SERVER_PORT = 9122;
    
    /** The default secure sever port. */
    public final static int DEFAULT_WEBVIEW_SECURE_SERVER_PORT = 8443;
    
    /** Command line flag to specify the root directory. */
    public final static String WEBVIEW_SERVER_ROOT_DIR_FLAG = "-wvroot";
    
    /** The default root directory. */
    public final static String DEFAULT_WEBVIEW_SERVER_ROOT_DIR = "web-view/resources/html";

    /** Command line flag to start the server. */
    public final static String WEBVIEW_START_SERVER_FLAG = "-wv";
    
    /** Command line flag to run the server in daemon mode. */
    public final static String WEBVIEW_SERVER_DAEMON_FLAG = "-wvd";

    /** Command line flag to specify the number of server instances. */
    public final static String WEBVIEW_SERVER_INSTANCES_FLAG = "-wvnum";
    
    /** The default number of server instances. */
    public final static int DEFAULT_WEBVIEW_SERVER_INSTANCES = 1;

    ///////////////////////////////////////////////////////////////////
    ////                         private methods                   ////

    /** Get a configuration property in the web-view config file. */
    private static ConfigurationProperty _getConfigurationProperty(String path) {
        return ConfigurationManager.getInstance()
                .getProperty(ConfigurationManager.getModule("web-view")).getProperty(path);
    }

    /** Get a list of configuration properties in the web-view config file. */
    private static List<ConfigurationProperty> _getConfigurationProperties(String path) {
        return ConfigurationManager.getInstance()
                .getProperties(ConfigurationManager.getModule("web-view"), path);
    }

    /** Get a configuration value in the web-view config file. Returns
     *  the default value if the path does not exist or is not set.
     */
    private static String _getConfigurationString(String path, String defaultValue) {
        ConfigurationProperty property = _getConfigurationProperty(path);
        if(property != null) {
            String value = property.getValue();
            if(value != null) {
                return value;
            }
        }
        return defaultValue;
    }
    
    /** Get set of strings from multiple configuration values with the same name. */
    private static Set<String> _getConfigurationStrings(String path) {
        Set<String> retval = new HashSet<String>();
        for(ConfigurationProperty property: _getConfigurationProperties(path)) {
            retval.add(property.getValue());
        }
        return retval;
    }
        
    /** Get a boolean configuration value in the web-view config file. */
    private static boolean _getConfigurationBoolean(String path, boolean defaultValue) {
        String value = _getConfigurationString(path, null);
        if(value != null) {
            return Boolean.valueOf(value);
        }
        return defaultValue;
    }
    
    /** Get an integer configuration value in the web-view config file. */
    private static int _getConfigurationInteger(String path, int defaultValue) {
        String value = _getConfigurationString(path, null);
        if(value != null) {
            return Integer.valueOf(value);
        }
        return defaultValue;
    }
    
    /** Get a long configuration value in the web-view config file. */
    private static long _getConfigurationLong(String path, long defaultValue) {
        String value = _getConfigurationString(path, null);
        if(value != null) {
            return Long.valueOf(value);
        }
        return defaultValue;
    }

    private static Module _getConfigurationModule(String path) {
        ConfigurationProperty property = _getConfigurationProperty(path);
        if(property != null) {
            return property.getModule();
        }
        return null;
    }

    /** Default session timeout in milliseconds */
    private static final long DEFAULT_WEBVIEW_SERVER_SESSION_TIMEOUT = 3600*1000;
    }
