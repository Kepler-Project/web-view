package org.kepler.webview.server.auth;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

public class NoneUser extends AbstractUser {

    public NoneUser() {}

    public NoneUser(String username, String group) {
        _group = group;
        _username = username;
    }

    @Override
    public JsonObject principal() {
        return new JsonObject().put("username", _username)
            .put("fullname", _username)
            .put("groups", _group);
    }

    /** Do nothing. */
    @Override
    public void setAuthProvider(AuthProvider arg0) {
    }

    @Override
    protected void doIsPermitted(String authority,
            Handler<AsyncResult<Boolean>> permittedHandler) {
        // Everything is authorized.
        permittedHandler.handle(Future.succeededFuture(Boolean.TRUE));
    }

    private String _group;
    private String _username;
}
