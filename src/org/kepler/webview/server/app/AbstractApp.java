package org.kepler.webview.server.app;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class AbstractApp implements App {

    @Override
    public Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub
    }

    protected void _booleanResponse(Handler<AsyncResult<JsonArray>> handler, String key, boolean val) {
        handler.handle(Future.succeededFuture(new JsonArray().add(new JsonObject().put(key, val))));        
    }

    protected void _stringResponse(Handler<AsyncResult<JsonArray>> handler, String key, String val) {
        handler.handle(Future.succeededFuture(new JsonArray().add(new JsonObject().put(key, val))));        
    }

}
