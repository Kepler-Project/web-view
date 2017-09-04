/*
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

import java.net.HttpURLConnection;

import org.kepler.webview.server.WebViewId;
import org.kepler.webview.server.WebViewServer;

import io.vertx.ext.web.RoutingContext;
import ptolemy.actor.CompositeActor;
import ptolemy.kernel.util.IllegalActionException;

/** A HTTP Server handler that generates a table of contents for
 *  the WebViewServer showing all the available workflows.
 *  
 *  @author Daniel Crawl
 *  @version $Id: TableOfContentsHandler.java 1375 2017-08-24 05:42:39Z crawl $
 */
public class TableOfContentsHandler extends BaseHandler {

    public TableOfContentsHandler(WebViewServer server) {
        super(server);
    }

    @Override
    public void handle(RoutingContext context) {

        long timestamp = System.currentTimeMillis();
        
        StringBuilder buf = new StringBuilder("<html>\n<body>\n");
        
        if(WebViewServer._models.isEmpty()) {
            buf.append("<h2>No workflows loaded!<h2>\n");
        } else {
            buf.append("  <ul>\n");
            for(CompositeActor model : WebViewServer._models.values()) {
                String name = model.getName();
                String id;
                try {
                    id = WebViewId.getId(model);
                } catch (IllegalActionException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                    continue;
                }
                buf.append("    <li><a href=\"/wf/")
                    .append(id)
                    .append("\">")
                    .append(name)
                    .append("</a></li>\n");
            }
            buf.append("  </ul>\n");
        }
        buf.append("</body>\n</html>\n");
        context.response()
            // add headers to prevent this page from being cached by the browser
            .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
            .putHeader("Pragma", "no-cache")
            .putHeader("Expires", "0")
            .end(buf.toString());
        
        _server.log(context.request(), HttpURLConnection.HTTP_OK, timestamp);            
    }
}
