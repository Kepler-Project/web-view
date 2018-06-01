/*
 *
 * Copyright (c) 2015 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-08-23 22:42:39 -0700 (Wed, 23 Aug 2017) $' 
 * '$Revision: 1375 $'
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

import java.io.File;
import java.net.HttpURLConnection;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.kepler.webview.server.WebViewConfiguration;
import org.kepler.webview.server.WebViewServer;

import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

public class NoMatchHandler extends BaseHandler {
    
    public NoMatchHandler(WebViewServer server) {
        super(server);
        
        _allowWorkflowDownloads = WebViewConfiguration.getHttpServerAllowWorkflowDownloads();
        
        _dirsToIndex = WebViewConfiguration.getHttpServerDirectoriesToIndex();
        
        for(String dir: new HashSet<String>(_dirsToIndex)) {
            if(!dir.startsWith(File.separator)) {
                _dirsToIndex.remove(dir);
                System.err.println("WARNING: directory to index will be ignored since it is not an absolute path: " + dir);
            }
        }
        
        _fileSystem = WebViewServer.vertx().fileSystem();
        
        _rootDir = WebViewConfiguration.getHttpServerRootDir();
        if(_rootDir == null) {
            _rootDir = "";
        }

    }

    @Override
    public void handle(RoutingContext context) {
        
        long timestamp = System.currentTimeMillis();
        
        HttpServerRequest req = context.request();
        String normalizedPath = context.normalisedPath();
        
        String path = WebViewServer.findFile(normalizedPath);
        boolean isDir = false;
        if(path != null) {
            isDir = new File(path).isDirectory();
            if(!isDir && (_allowWorkflowDownloads || !path.endsWith(".kar"))) {
                context.response().sendFile(path);
                _server.log(req, context.user(), HttpURLConnection.HTTP_OK, timestamp, new File(path).length());
                return;
            }
        }
        
        int error;
        String message;
        if(isDir) {
            File indexFile = new File(path, "index.html");
            if(indexFile.exists()) {
                context.response().sendFile(indexFile.getAbsolutePath());
                _server.log(req, context.user(), HttpURLConnection.HTTP_OK, timestamp, new File(path).length());
                return;
            } else {
                // remove trailing / from path
                while(path.endsWith("/")) {
                    path.substring(0, path.length()-1);
                }
                for(String dir : _dirsToIndex) {
                    if(path.substring(_rootDir.length() + 1).startsWith(dir)) {
                        _sendDirectoryIndex(context, path, timestamp);
                        return;
                    }
                }
            }
            System.err.println("Unhandled http request (directory) for: " + normalizedPath);
            error = HttpURLConnection.HTTP_FORBIDDEN;
            message = "File " + normalizedPath + ": permission denied.";                
        } else if(path != null && path.endsWith(".kar") && !_allowWorkflowDownloads) {
            System.err.println("Unhandled http request (workflow permission denied) for: " + normalizedPath);
            error = HttpURLConnection.HTTP_FORBIDDEN;            
            message = "File " + normalizedPath + ": permission denied.";
        } else {
            System.err.println("Unhandled http request (file not found) for: " + normalizedPath);
            error = HttpURLConnection.HTTP_NOT_FOUND;
            message = "File " + normalizedPath + " not found.";
        }
        
        context.response().headers().set("Content-Type", "text/html");
        // NOTE: always return not found since we do not want to disclose
        // the presence of directories.
        context.response().setChunked(true)
            .write("<html>\n<body>\n<h2>" + message + "</h2>\n</body>\n</html>")
            .setStatusCode(HttpURLConnection.HTTP_NOT_FOUND)
            .end();
        _server.log(req, context.user(), error, timestamp);            
    }

    /** Send a directory index.
     * @param context The routing context
     * @param path The directory index to send
     * @param timestamp The request timestamp 
     */
    private void _sendDirectoryIndex(RoutingContext context, String path, long timestamp) {
        HttpServerRequest req = context.request();
        String normalizedPath = context.normalisedPath();
        
        String accept = req.headers().get("accept");
        if(accept.contains("application/json") || accept.contains("text/javascript")) {
            _fileSystem.readDir(path, readResult -> {
                if(readResult.failed()) {
                    System.err.println("Error reading directory " + path + ": " + readResult.cause());
                    _sendResponseWithError(req, readResult.cause().toString());
                    _server.log(req, context.user(), HttpURLConnection.HTTP_INTERNAL_ERROR, timestamp, new File(path).length());
                    return;
                }
                
                JsonArray array = new JsonArray();
                for(String file: readResult.result()) {
                    JsonObject json = new JsonObject()
                        .put("name", file.substring(_rootDir.length() + 1));
                    FileProps props = _fileSystem.lpropsBlocking(file);
                    if(!props.isDirectory()) {
                        json.put("lastModified", props.lastModifiedTime());
                    }
                    array.add(json);
                }
                
                _sendResponseWithSuccessJson(req, new JsonObject().put(normalizedPath.substring(1), array));
                _server.log(req, context.user(), HttpURLConnection.HTTP_OK, timestamp, new File(path).length());
            });
        } else {
            _fileSystem.readDir(path, readResult -> {
                if(readResult.failed()) {
                    System.err.println("Error reading directory " + path + ": " + readResult.cause());
                    context.response()
                        .putHeader("Content-Type", "text/html")
                        .write("<html>\n<body>\n<h2>Error reading directory.</h2>\n</body>\n</html>")
                        .setStatusCode(HttpURLConnection.HTTP_INTERNAL_ERROR)
                        .end();
                    _server.log(req, context.user(), HttpURLConnection.HTTP_INTERNAL_ERROR, timestamp, new File(path).length());
                    return;
                }
                StringBuilder buf = new StringBuilder();
                buf.append("<meta content='text/html;charset=utf-8' http-equiv='Content-Type'>");
                buf.append("<html><body><h2>Index of ");
                buf.append(normalizedPath);
                buf.append("</h2><ul>");
                
                List<String> files = readResult.result();
                Collections.sort(files);
                
                for(String file: files) {
                    String f = file.substring(_rootDir.length() + 1 + normalizedPath.length());
                    buf.append("<li><a href='");
                    buf.append(normalizedPath);
                    buf.append("/");
                    buf.append(f);
                    buf.append("'>");
                    buf.append(f);
                    FileProps props = _fileSystem.propsBlocking(file);
                    if(props.isDirectory()) {
                        buf.append("/");
                    } else {
                        buf.append(", ");
                        buf.append(_timestampFormat.format(props.lastModifiedTime()));
                    }
                    buf.append("</a></li>");
                }

                buf.append("<li><a href='");
                buf.append(normalizedPath.substring(0, normalizedPath.lastIndexOf('/')));
                buf.append("'>Parent directory");
                buf.append("</a></li>");
                
                buf.append("</ul></body></html>");
                _sendResponseWithSuccessText(req, "text/html", buf.toString());
                _server.log(req, context.user(), HttpURLConnection.HTTP_OK, timestamp, new File(path).length());
             });            
        }
        
    }
    
    /** If true, allow downloads for workflow files. */
    private boolean _allowWorkflowDownloads;
    
    /** Set of directories that can be indexed. */
    private Set<String> _dirsToIndex;    
    
    /** Vertx file system. */
    private FileSystem _fileSystem;
    
    /** Root directory of http server. */
    private String _rootDir;
    
    /** Timestamp format for last modified times. */
    private SimpleDateFormat _timestampFormat = new SimpleDateFormat("HH:mm:ss z yyyy.MM.dd");
}
