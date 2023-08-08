package io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.*;

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
     * @param requestHttpMethod  the request http method
     * @param requestUri         the request uri
     * @param requestHeaders     the request headers
     * @param requestHttpVersion the request http version
     * @param requestId          the request id
     * @return the future
     */
    Future<Void> beforeProxyRequest(HttpMethod requestHttpMethod, String requestUri, MultiMap requestHeaders, HttpVersion requestHttpVersion, long requestId);

    /**
     * After proxy request.
     *
     * @param requestHttpMethod  the request http method
     * @param requestUri         the request uri
     * @param requestHeaders     the request headers
     * @param requestHttpVersion the request http version
     * @param httpClientResponse the http client response
     * @param requestId          the request id
     */
    void afterProxyRequest(HttpMethod requestHttpMethod, String requestUri, MultiMap requestHeaders, HttpVersion requestHttpVersion, HttpClientResponse httpClientResponse, long requestId);
}
