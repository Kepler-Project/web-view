package org.kepler.webview.server.auth;

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

public class DrupalUser extends AbstractUser {

    public DrupalUser() {}

    public DrupalUser(JsonObject loginJson, String groupsField, String fullNameField, String role) {
        _loginJson = loginJson;
        _role = role;

        _principal = new JsonObject().put("username", loginJson.getJsonObject("user").getString("name"));
        _principal.put("fullname", _getDrupalField(loginJson, fullNameField, loginJson.getJsonObject("user").getString("name")));
        _principal.put("groups", _getDrupalField(loginJson, groupsField, null));
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
        Boolean found = Boolean.FALSE;
        for(Object value: _loginJson.getJsonObject("user").getJsonObject("roles").getMap().values()) {
            if(value.equals(_role)) {
                found = Boolean.TRUE;
                break;
            }
        };
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
