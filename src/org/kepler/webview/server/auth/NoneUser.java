package org.kepler.webview.server.auth;

import java.nio.charset.StandardCharsets;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AbstractUser;
import io.vertx.ext.auth.AuthProvider;

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

    @Override
    public int readFromBuffer(int pos, Buffer buffer) {
        pos = super.readFromBuffer(pos, buffer);
        
        int groupLength = buffer.getInt(pos);
        pos += 4;
        byte[] groupBytes = buffer.getBytes(pos, pos + groupLength);
        pos += groupLength;
        _group = new String(groupBytes, StandardCharsets.UTF_8);

        int usernameLength = buffer.getInt(pos);
        pos += 4;
        byte[] usernameBytes = buffer.getBytes(pos, pos + usernameLength);
        pos += usernameLength;
        _username = new String(usernameBytes, StandardCharsets.UTF_8);

        return pos;
    }
    
    @Override
    public void writeToBuffer(Buffer buffer) {
        super.writeToBuffer(buffer);
        byte[] groupBytes = _group.getBytes(StandardCharsets.UTF_8);
        buffer.appendInt(groupBytes.length).appendBytes(groupBytes);
        byte[] userBytes = _username.getBytes(StandardCharsets.UTF_8);
        buffer.appendInt(userBytes.length).appendBytes(userBytes);        
    }
    
    private String _group;
    private String _username;
}
