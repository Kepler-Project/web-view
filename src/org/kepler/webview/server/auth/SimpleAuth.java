/*
 * Copyright (c) 2017 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-05-10 16:55:24 -0700 (Wed, 10 May 2017) $' 
 * '$Revision: 1181 $'
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
package org.kepler.webview.server.auth;

import org.kepler.webview.server.WebViewConfiguration;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

/** Simple AuthProvider implementation that reads passwords from configuration.xml.
 * 
 * @author Daniel Crawl
 * @version $Id: SimpleAuth.java 1181 2017-05-10 23:55:24Z crawl $
 * 
 */
public class SimpleAuth implements AuthProvider {

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> handler) {

        String username = authInfo.getString("username");
        if (username == null) {
            handler.handle(Future.failedFuture("authInfo must contain username in 'username' field"));
            return;
        }

        String password = authInfo.getString("password");
        if (password == null) {
            handler.handle(Future.failedFuture("authInfo must contain password in 'password' field"));
            return;
        }

        String group = WebViewConfiguration.getSimpleAuthGroup(username, password);
        if(group == null) {
            handler.handle(Future.failedFuture("Incorrect password."));
        } else {
            handler.handle(Future.succeededFuture(new NoneUser(username, group)));
        }
    }
}
