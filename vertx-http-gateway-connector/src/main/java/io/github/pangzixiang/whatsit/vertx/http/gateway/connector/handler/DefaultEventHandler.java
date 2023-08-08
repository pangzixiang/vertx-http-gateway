package io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.*;

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
    public Future<Void> beforeProxyRequest(HttpMethod requestHttpMethod, String requestUri, MultiMap requestHeaders, HttpVersion requestHttpVersion, long requestId) {
        return Future.succeededFuture();
    }

    @Override
    public void afterProxyRequest(HttpMethod requestHttpMethod, String requestUri, MultiMap requestHeaders, HttpVersion requestHttpVersion, HttpClientResponse httpClientResponse, long requestId) {

    }
}
