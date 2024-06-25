package io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.ProxyRequestContext;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;

/**
 * The interface Event handler.
 */
public interface EventHandler {

    /**
     * Before establish connection future.
     *
     * @param webSocketConnectOptions the web socket connect options
     * @return the future
     */
    Future<WebSocketConnectOptions> beforeEstablishConnection(WebSocketConnectOptions webSocketConnectOptions);

    /**
     * After establish connection.
     *
     * @param webSocket the web socket
     */
    void afterEstablishConnection(WebSocket webSocket);

    /**
     * Before disconnect.
     */
    void beforeDisconnect();

    /**
     * After disconnect.
     *
     * @param succeeded the succeeded
     * @param cause     the cause
     */
    void afterDisconnect(boolean succeeded, Throwable cause);

    /**
     * Before proxy request future.
     *
     * @param proxyRequestContext the proxy request context
     * @return the future
     */
    Future<ProxyRequestContext> beforeProxyRequest(ProxyRequestContext proxyRequestContext);

    /**
     * Change the proxy response headers
     *
     * @param responseHeaders the response headers
     * @return the changed response headers
     */
    Future<MultiMap> processProxyResponseHeaders(MultiMap responseHeaders);

    /**
     * After proxy request.
     *
     * @param proxyRequestContext the proxy request context
     */
    void afterProxyRequest(ProxyRequestContext proxyRequestContext);
}
