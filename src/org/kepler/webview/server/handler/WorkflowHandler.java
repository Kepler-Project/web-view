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
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.kepler.webview.actor.WebViewable;
import org.kepler.webview.server.WebViewId;
import org.kepler.webview.server.WebViewServer;
import org.kepler.webview.server.WebViewableUtilities;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import ptolemy.kernel.CompositeEntity;
import ptolemy.kernel.Entity;
import ptolemy.kernel.util.IllegalActionException;
import ptolemy.kernel.util.NamedObj;
import ptolemy.kernel.util.StringAttribute;
import ptolemy.util.MessageHandler;

public class WorkflowHandler extends BaseHandler {
    
    public WorkflowHandler(WebViewServer server) {
        super(server);
    }

    @Override
    public void handle(RoutingContext context) {

        final long timestamp = System.currentTimeMillis();
        
        HttpServerRequest req = context.request();       
        HttpServerResponse response = context.response();
        response.headers().set("Content-Type", "text/html");
        response.setChunked(true);

        String id = req.params().get("param0");
        //System.out.println("wf name is: " + name);
        NamedObj model = WebViewId.getNamedObj(id);
        if (model == null) {
            response.write("<html>\n<body>\n<h2>Workflow " +
                    id + " not found.</h2>\n</body>\n</html>")
              .setStatusCode(HttpURLConnection.HTTP_NOT_FOUND).end();
            System.err.println("Unhandled http request (workflow not found) for: " +
                req.path());
            _server.log(req, context.user(), HttpURLConnection.HTTP_NOT_FOUND, timestamp);
            return;
        }
        
        // TODO make sure is top level composite actor.
      
        String wfStartPath = WebViewServer.findFile(_WF_START);
        if(wfStartPath == null) {
            response.write("<html>\n<body>\n<h2>wf-start.html " +
                    " not found.</h2>\n</body>\n</html>")
               .setStatusCode(HttpURLConnection.HTTP_NOT_FOUND).end();
             System.err.println("Unhandled http request (wf-start.html not found) for: " +
               req.path()); 
             _server.log(req, context.user(), HttpURLConnection.HTTP_NOT_FOUND, timestamp);
             return;
        }
        
        String wfEndPath = WebViewServer.findFile(_WF_END);
        if(wfEndPath == null) {
            response.write("<html>\n<body>\n<h2>wf-end.html " +
                    " not found.</h2>\n</body>\n</html>")
               .setStatusCode(HttpURLConnection.HTTP_NOT_FOUND).end();
             System.err.println("Unhandled http request (wf-end.html not found) for: " +
               req.path());   
             _server.log(req, context.user(), HttpURLConnection.HTTP_NOT_FOUND, timestamp);
             return;
        }
        
        StringBuilder buf = new StringBuilder();
        
        try {
            
            buf.append(FileUtils.readFileToString(new File(wfStartPath)));
        
            Set<String> idsSeen = new HashSet<String>();
            List<WebViewable> webViews = _getWebViewables(model);
            for (WebViewable webView : webViews) {
                

                String webViewId;
                try {
                    webViewId = WebViewId.getId((NamedObj)webView);
                } catch (IllegalActionException e) {
                    System.err.println("Error getting id for " + webView.getFullName() + ": " + e.getMessage());
                    // TODO do not try to add position for the webviewable below
                    continue;
                }
                
                System.out.println("found actor: " + webView.getName() + ", id = " + webViewId);

                // since ids may be re-used, only add one iframe for each id.                
                if(!idsSeen.contains(webViewId)) {                
                    String title = WebViewableUtilities.getTitle(webView);
    
                    _addIFrame(buf, webViewId, title);
                    
                    idsSeen.add(webViewId);
                }
            }
                        
            String end = FileUtils.readFileToString(new File(wfEndPath));
            // TODO get rid of ugliness below
            end = end.replaceAll("URL", 
                    "'ws://'"
                     + " + window.location.hostname"
                     + " + ':'"
                     + " + window.location.port"
                     + " + '/ws/"
                     + id
                     + "'");
            
            StringBuilder positionBuf = new StringBuilder();
            for(WebViewable webView : webViews) {
                
                try {
                    // since ids may be re-used, only set the position
                    // for an id once.
                    if(idsSeen.remove(WebViewId.getId((NamedObj)webView))) {
                        try {
                            _addPosition(positionBuf, webView);
                        } catch (IllegalActionException e) {
                            System.err.println("Error setting posibiotn for " + webView.getFullName() + ": " + e.getMessage());
                        }
                    }
                } catch (IllegalActionException e) {
                    System.err.println("Error getting id for " + webView.getFullName() + ": " + e.getMessage());
                    // TODO do not try to add position for the webviewable below
                    continue;
                }
            }
            
            end = end.replace("POSITION_ACTORS", positionBuf.toString());
            
            buf.append(end);
            response.write(buf.toString());
            
        } catch(IOException e) {
            MessageHandler.error("Error reading file.", e);
        }
        response.setStatusCode(HttpURLConnection.HTTP_OK)
            .end();

        _server.log(req, context.user(), HttpURLConnection.HTTP_OK, timestamp);

    }

    private static void _addIFrame(StringBuilder buf, String id, String title) {
        buf.append("  <div id=\"")
            .append(id)
            .append("\" class=\"actor\" title=\"")
            .append(title)
            .append("\">\n")
            .append("    <iframe id=\"iframe-")
            .append(id)
            .append("\" src=\"/wf/")
            .append(id)
            .append("\"></iframe>\n")
            .append("  </div>\n");
    }
    
    private static void _addPosition(StringBuilder buf, WebViewable obj) throws IllegalActionException {
    
        String id = WebViewId.getId((NamedObj)obj);

        int topOffset = 1;
        int leftOffset = 1;

        String topOffsetStr = null;
        String leftOffsetStr = null;
        Number width = null;
        Number height = null;
        
        StringAttribute posAttribute = (StringAttribute) obj.getAttribute("_webWindowProperties");
        if(posAttribute != null) {
            String value = null;
            value = posAttribute.getValueAsString();
            
            if(value != null && !value.trim().isEmpty()) {
                JsonObject json = new JsonObject(value);
                if(json.containsKey("top")) {
                    topOffsetStr = "top+" + json.getInteger("top").toString();
                }
                if(json.containsKey("left")) {
                    leftOffsetStr = "left+" + json.getInteger("left").toString();
                }
                if(json.containsKey("width")) {
                    width = json.getInteger("width");
                }
                if(json.containsKey("height")) {
                    height = json.getInteger("height");
                }
            }
        }
        
        if(topOffsetStr == null) {                    
            topOffsetStr = topOffset * 10 + "%";
            topOffset++;
        }
        
        if(leftOffsetStr == null) {
            leftOffsetStr = leftOffset * 10 + "%";
            leftOffset++;
        }
                         
        // FIXME do dashes need to be escaped??
        
        buf.append("    $('#")
            .append(id)
            // NOTE: must be 'left top', not 'top left'
            .append("').dialog('option', 'position', { my: 'left top', at: '")
            .append(leftOffsetStr)
            .append(" ")
            .append(topOffsetStr)
            // NOTE: of: is required; if not specified position is not set.
            .append("', of: window});\n");
        
        if(width != null && height != null) {
            buf.append("    $('#")
                .append(id)
                .append("').dialog('option', 'width', ")
                .append(width)
                .append(")\n")
                .append("      .dialog('option', 'height', ")
                .append(height)
                .append(");\n")
                .append("    $('#iframe-")
                .append(id)
                .append("').load(function() {\n")
                .append("      resize('")
                .append("iframe-")
                .append(id)
                .append("', ")
                .append(width)
                .append(", ")
                .append(height)
                // TODO remove constant
                .append("- 10);\n")
                .append("    });\n\n");
        }
    }
    
    /** Get all the WebViewables in a NamedObj. */
    private static List<WebViewable> _getWebViewables(NamedObj namedObj) {
        
        // add attributes
        List<WebViewable> webViews = namedObj.attributeList(WebViewable.class);
        for(NamedObj subNamedObj : namedObj.attributeList(NamedObj.class)) {
            webViews.addAll(_getWebViewables(subNamedObj));
        }
        
        // add actors
        if(namedObj instanceof CompositeEntity) {
            webViews.addAll(((CompositeEntity)namedObj).entityList(WebViewable.class));
            for(Entity<?> entity : ((CompositeEntity)namedObj).entityList(Entity.class)) {
                webViews.addAll(_getWebViewables(entity));
            }
        }
        return webViews;
    }
    
    private static final String _WF_START = "web-view" +
            File.separator + "wf-start.html";
    
    private static final String _WF_END = "web-view" +
            File.separator + "wf-end.html";
}
