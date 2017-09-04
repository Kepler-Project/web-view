/*
 * Copyright (c) 2017 The Regents of the University of California.
 * All rights reserved.
 *
 * '$Author: crawl $'
 * '$Date: 2017-07-04 14:25:38 -0700 (Tue, 04 Jul 2017) $' 
 * '$Revision: 1257 $'
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

import java.net.HttpURLConnection;

import org.kepler.webview.server.WebViewServer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

/** AuthProvider that authenticates against a Drupal site.
 * 
 * @author Daniel Crawl
 * @version $Id: DrupalAuth.java 1257 2017-07-04 21:25:38Z crawl $
 * 
 */
public class DrupalAuth implements AuthProvider {

    /** Create a new DrupalAuth.
     *  @param host The hostname of the drupal site.
     *  @param service The name of the Services REST service.
     *  @param role The name of the role required by authorized users.
     *  @param groupsField The name of the user field containing the group name(s).
     */
    public DrupalAuth(String host, String service, String role, String groupsField,
        String fullNameField) {

        _loginUrl = "?q=" + service + "/user/login.json";
        _logoutUrl = "?q=" + service + "/user/logout.json";
        _role = role;
        _groupsField = "field_" + groupsField;
        _fullNameField = "field_" + fullNameField;
        
        _client = WebViewServer.vertx().createHttpClient(
            new HttpClientOptions().setDefaultHost(host)
                .setDefaultPort(443).setSsl(true).setTrustAll(true));//.setLogActivity(true));
    }
    
    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> handler) {
        
        //System.out.println("authenticating for " + authInfo.getString("username"));
        
        String username = authInfo.getString("username");
        if (username == null) {
            handler.handle(Future.failedFuture("authInfo must contain username in 'username' field"));
        } else {
            String password = authInfo.getString("password");
            if (password == null) {
                handler.handle(Future.failedFuture("authInfo must contain password in 'password' field"));
            } else {
                //System.out.println(_sessionTokenUrl);
                _getCSRFToken(null, loginTokenRes -> {
                    if(loginTokenRes.failed()) {
                        handler.handle(Future.failedFuture(loginTokenRes.cause().getMessage()));
                    } else {
                        //String body = "{username=\"" + username + "\",password=\"" + password + "\"}";
                        String body = new JsonObject().put("username", username).put("password", password).encode();
                        //System.out.println(body);
                        _client.post(_loginUrl, loginResponse -> {
                            //System.out.println("login status " + loginResponse.statusCode());
                            //System.out.println("login message " + loginResponse.statusMessage());
                            if(loginResponse.statusCode() != HttpURLConnection.HTTP_OK) {
                                String message = loginResponse.statusMessage();
                                if(message.indexOf(": ") > -1) {
                                    message = message.substring(message.indexOf(": ") + 2);
                                }
                                handler.handle(Future.failedFuture(message));
                            } else {
                                loginResponse.handler(loginBuffer -> {
                                    JsonObject loginJson = loginBuffer.toJsonObject();
                                    User user = new DrupalUser(loginJson, _groupsField, _fullNameField, _role);
                                    handler.handle(Future.succeededFuture(user));

                                    String sessionCookieStr = loginJson.getString("session_name") + "=" + loginJson.getString("sessid");                                    
                                    _getCSRFToken(sessionCookieStr, logoutTokenRes -> {
                                        if(logoutTokenRes.failed()) {
                                            System.err.println("WARNING: could not get CSRF token for logout: " +
                                                loginTokenRes.cause().getMessage());
                                        } else {
                                            _client.post(_logoutUrl, logoutResponse -> {
                                                //System.out.println("logout: " + logoutResponse.statusMessage());
                                                if(logoutResponse.statusCode() != HttpURLConnection.HTTP_OK) {
                                                    System.err.println("WARNING: could not log out: " + logoutResponse.statusMessage());
                                                    System.out.println("Cookie" + loginJson.getString("session_name") + "=" + loginJson.getString("sessid"));
                                                }
                                            }).putHeader("X-CSRF-Token", logoutTokenRes.result())
                                                .putHeader("Cookie", sessionCookieStr)
                                                .putHeader("Content-Type", "application/json")
                                                .putHeader("Content-Length", String.valueOf(0))
                                                .end();
                                        }
                                    });
                                });
                            }
                        }).putHeader("X-CSRF-Token", loginTokenRes.result())
                            .putHeader("Content-Type", "application/json")
                            .putHeader("Content-Length", String.valueOf(body.length()))
                            .write(body)
                            .end();
                    }
                });
            }
        }

    }
        
    private void _getCSRFToken(String sessionCookieStr, Handler<AsyncResult<String>> handler) {
        HttpClientRequest request = _client.get(_sessionTokenUrl, response -> {
            if(response.statusCode() != HttpURLConnection.HTTP_OK) {
                handler.handle(Future.failedFuture("Failed to get session token while authenticating."));
            } else {
                response.handler(buffer -> {
                    handler.handle(Future.succeededFuture(buffer.toString()));
                });
            }
        });
        
        if(sessionCookieStr != null) {
            request.putHeader("Cookie", sessionCookieStr);
        }
        
        request.end();        
    }
    
    private static class DrupalUser extends AbstractUser {
        
        public DrupalUser(JsonObject loginJson, String groupsField, String fullNameField, String role) {
            
            _loginJson = loginJson;
            _role = role;
            
            _principal = new JsonObject().put("username", loginJson.getJsonObject("user").getString("name"));
            _principal.put("fullname", _getDrupalField(loginJson, fullNameField, loginJson.getJsonObject("user").getString("name")));
            _principal.put("groups", _getDrupalField(loginJson, groupsField, null));            

            //System.out.println(fullNameField);
            
            //System.out.println(_loginJson.encodePrettily());            
            //System.out.println("principal: " + _principal.encodePrettily());            
        }
        
        @Override
        public JsonObject principal() {
            return _principal;
        }

        @Override
        public void setAuthProvider(AuthProvider provider) {
        }

        @Override
        protected void doIsPermitted(String authority, Handler<AsyncResult<Boolean>> handler) {
            //System.out.println("loginJson: " + loginJson);
            Boolean found = Boolean.FALSE;
            for(Object value: _loginJson.getJsonObject("user").getJsonObject("roles").getMap().values()) {
                //System.out.println("user has role: " + value);
                if(value.equals(_role)) {
                    found = Boolean.TRUE;
                    break;
                }
            };
            //System.out.println("doIsPermitted: " + found);
            handler.handle(Future.succeededFuture(found));
        }
        
        private static String _getDrupalField(JsonObject loginJson, String fieldName, String defaultValue) {
            
            if(loginJson.getJsonObject("user").containsKey(fieldName)) {
                // groupsField key may be an empty array, so check type
                // before assuming it's a JsonObject.
                Object fieldObject = loginJson.getJsonObject("user").getValue(fieldName);
                if(fieldObject instanceof JsonObject) {
                    return ((JsonObject)fieldObject)
                            .getJsonArray("und")
                            .getJsonObject(0)
                            .getString("value");
                }
            }
            return defaultValue;
        }

        private JsonObject _loginJson;
        private JsonObject _principal;
        private String _role;
    }
    
    /** HTTP client to make requests from drupal. */
    private HttpClient _client;
    
    /** REST login url */
    private String _loginUrl;
    
    /** REST logout url */
    private String _logoutUrl;
    
    /** Role user must have */
    private String _role;
    
    /** Name of drupal user field that contains the user's group name(s). */
    private String _groupsField;

    private String _fullNameField;
    
    /** REST get session token url. */
    private final static String _sessionTokenUrl = "/?q=services/session/token";

}
