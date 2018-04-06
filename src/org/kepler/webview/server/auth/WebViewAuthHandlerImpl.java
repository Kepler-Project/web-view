/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package org.kepler.webview.server.auth;

import java.util.Base64;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.handler.impl.BasicAuthHandlerImpl;

/** Basic authentication handler that uses "WebView" as the scheme
 * instead of "Basic" so that 401 responses are not caught by the browser. 
 * 
 * Copied from vertx BasicAuthHandlerImpl.
 * 
 * @author <a href="http://pmlopes@gmail.com">Paulo Lopes</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebViewAuthHandlerImpl extends BasicAuthHandlerImpl {


  public WebViewAuthHandlerImpl(AuthProvider authProvider, String realm) {
    super(authProvider, realm);
  }

  @Override
  public void handle(RoutingContext context) {
    User user = context.user();
    if (user != null) {
      // Already authenticated in, just authorize
      authorizeUser(user, context);
    } else {
      HttpServerRequest request = context.request();
      String authorization = request.headers().get(HttpHeaders.AUTHORIZATION);

      //System.out.println(authorization);
      
      if (authorization == null) {
        handle401(context, "Basic");
      } else {
        String suser;
        String spass;
        String sscheme;

        try {
          String[] parts = authorization.split(" ");
          sscheme = parts[0];
          String decoded = new String(Base64.getDecoder().decode(parts[1]));
          int colonIdx = decoded.indexOf(":");
          if(colonIdx!=-1) {
              suser = decoded.substring(0,colonIdx);
              spass = decoded.substring(colonIdx+1);
          } else {
              suser = decoded;
              spass = null;
          }
        } catch (ArrayIndexOutOfBoundsException e) {
          handle401(context, "Basic");
          return;
        } catch (IllegalArgumentException | NullPointerException e) {
          // IllegalArgumentException includes PatternSyntaxException
          context.fail(e);
          return;
        }

        // handle either "WebView" or "Basic" scheme
        if (!"WebView".equals(sscheme) && !"Basic".equals(sscheme)) {
          context.fail(400);
        } else {
          JsonObject authInfo = new JsonObject().put("username", suser).put("password", spass);
          authProvider.authenticate(authInfo, res -> {
            if (res.succeeded()) {
              User authenticated = res.result();
              context.setUser(authenticated);
              Session session = context.session();
              if (session != null) {
                // the user has upgraded from unauthenticated to authenticated
                // session should be upgraded as recommended by owasp
                session.regenerateId();
              }
              authorizeUser(authenticated, context);
            } else {
              // if not able to authenticate, send 401 with same scheme as in request
              handle401(context, sscheme);
            }
          });
        }
      }
    }
  }

  private void authorizeUser(final User user, final RoutingContext context) {
    this.authorize(user, authZ -> {
        if (authZ.failed()) {
            this.processException(context, authZ.cause());
            return;
        }
        context.next();
    });
  }

  private void handle401(RoutingContext context, String scheme) {
    context.response().putHeader("WWW-Authenticate", scheme + " realm=\"" + realm + "\"")
        .putHeader("Content-type", "text/plain");
    context.fail(401);
  }
}
