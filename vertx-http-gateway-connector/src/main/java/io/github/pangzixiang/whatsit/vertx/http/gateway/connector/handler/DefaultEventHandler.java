package io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.ProxyRequestContext;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;

public class DefaultEventHandler implements EventHandler {
    @Override
    public Future<WebSocketConnectOptions> beforeEstablishConnection(WebSocketConnectOptions webSocketConnectOptions) {
        return Future.succeededFuture(webSocketConnectOptions);
    }

    @Override
    public void afterEstablishConnection(WebSocket webSocket) {

    }

    @Override
    public void beforeDisconnect() {

    }

    @Override
    public void afterDisconnect(boolean succeeded, Throwable cause) {

    }

    @Override
    public Future<ProxyRequestContext> beforeProxyRequest(ProxyRequestContext proxyRequestContext) {
        return Future.succeededFuture(proxyRequestContext);
    }

    @Override
    public Future<MultiMap> processProxyResponseHeaders(MultiMap responseHeaders) {
        return Future.succeededFuture(responseHeaders);
    }

    @Override
    public void afterProxyRequest(ProxyRequestContext proxyRequestContext) {

    }
}
