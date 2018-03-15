/*
 * Copyright (c) 2018 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-09-04 12:58:25 -0700 (Mon, 04 Sep 2017) $' 
 * '$Revision: 1406 $'
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
 */
package org.kepler.webview.server.app;

import java.net.URI;

import org.kepler.webview.server.WebViewServer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;

/** App to perform HTTP GET and return body. Useful for sites
 *  without CORS or JSONP.
 *
 * @author Daniel Crawl
 */
public class GetURL extends AbstractApp {

    @Override
    public void exec(User user, JsonObject inputs, Handler<AsyncResult<JsonArray>> handler)
        throws Exception {
        
        if(!inputs.containsKey("url")) {
            throw new Exception("Missing url parameter.");
        }
        
        URI uri = null;
        try {
            uri = new URI(inputs.getString("url"));
        } catch(Exception e) {
            handler.handle(Future.failedFuture("Error parsing url: " + e.getMessage()));
            return;
        }
        
        JsonObject data = new JsonObject();
        
        int port = uri.getPort();
        if(port == -1) {
            port = uri.getScheme().startsWith("https") ? 443 : 80;
        }
        
        WebViewServer.vertx().createHttpClient().get(port,
            uri.getHost(), uri.getPath(), response -> {
            data.put("status", response.statusCode());
            response.bodyHandler(body -> {
                data.put("body", body.toString());
                handler.handle(Future.succeededFuture(new JsonArray().add(data)));
            });
        }).setFollowRedirects(true).end();
    }

}
