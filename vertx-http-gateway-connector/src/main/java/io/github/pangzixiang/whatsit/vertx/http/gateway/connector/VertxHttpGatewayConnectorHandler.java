package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunk;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunkType;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.RequestMessageInfoChunkBody;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.ResponseMessageInfoChunkBody;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.EventHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class VertxHttpGatewayConnectorHandler extends AbstractVerticle implements Handler<WebSocket> {

    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;

    private HttpClient proxyClient;

    private WebSocketClient webSocketProxyClient;

    private final String instanceId;

    private final EventHandler eventHandler;

    public VertxHttpGatewayConnectorHandler(VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions, String instanceId, EventHandler eventHandler) {
        this.vertxHttpGatewayConnectorOptions = vertxHttpGatewayConnectorOptions;
        this.instanceId = instanceId;
        this.eventHandler = eventHandler;
    }

    @Override
    public void start() throws Exception {
        proxyClient = getVertx().createHttpClient(vertxHttpGatewayConnectorOptions.getProxyClientOptions());
        WebSocketClientOptions webSocketClientOptions = new WebSocketClientOptions(vertxHttpGatewayConnectorOptions.getRegisterClientOptions());
        webSocketProxyClient = getVertx().createWebSocketClient(webSocketClientOptions);
    }

    @Override
    public void handle(WebSocket webSocket) {
        webSocket.handler(buffer -> {
            MessageChunk messageChunk = new MessageChunk(buffer);
            byte chunkType = messageChunk.getChunkType();
            long requestId = messageChunk.getRequestId();
            if (chunkType == MessageChunkType.INFO.getFlag()) {
                webSocket.pause();
                String requestChunkBody = messageChunk.getChunkBody().toString();
                RequestMessageInfoChunkBody requestMessageInfoChunkBody = new RequestMessageInfoChunkBody(requestChunkBody);
                HttpMethod httpMethod = requestMessageInfoChunkBody.getHttpMethod();
                MultiMap headers = requestMessageInfoChunkBody.getHeaders();
                String uri = requestMessageInfoChunkBody.getUri();
                HttpVersion httpVersion = requestMessageInfoChunkBody.getHttpVersion();

                try {
                    ProxyRequestContext proxyRequestContext = Future.await(eventHandler.beforeProxyRequest(new ProxyRequestContext(httpMethod, uri, headers, httpVersion, requestId, null)));
                    if (isWebsocket(proxyRequestContext)) {
                        handleProxyWebsocket(webSocket, proxyRequestContext);
                    } else {
                        handleProxyHttpRequest(webSocket, proxyRequestContext);
                    }
                } catch (Throwable e) {
                    log.debug("Failed to proxy request for {} {}:{}{}", httpMethod, vertxHttpGatewayConnectorOptions.getServiceHost(),
                            vertxHttpGatewayConnectorOptions.getServicePort(), uri, e);
                    webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.ERROR, requestId, e.getMessage()));
                    webSocket.resume();
                }
            } else {
                getVertx().eventBus().send(getProxyRequestEventbusAddress(requestId), buffer);
            }
        });
    }

    private void handleProxyWebsocket(WebSocket webSocket, ProxyRequestContext proxyRequestContext) {
        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions();
        webSocketConnectOptions.setHost(vertxHttpGatewayConnectorOptions.getServiceHost());
        webSocketConnectOptions.setPort(vertxHttpGatewayConnectorOptions.getServicePort());
        webSocketConnectOptions.setURI(proxyRequestContext.getRequestUri());
        webSocketConnectOptions.setHeaders(proxyRequestContext.getRequestHeaders().remove(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));
        WebSocket ws;
        try {
            ws = Future.await(webSocketProxyClient.connect(webSocketConnectOptions));
        } catch (Throwable throwable) {
            log.debug("Failed to establish websocket connection for {}:{}{}", vertxHttpGatewayConnectorOptions.getServiceHost(),
                    vertxHttpGatewayConnectorOptions.getServicePort(), proxyRequestContext.getRequestUri(), throwable);
            webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.ERROR, proxyRequestContext.getRequestId(), throwable.getMessage()));
            webSocket.resume();
            return;
        }

        MessageConsumer<Object> consumer = getVertx().eventBus().consumer(getProxyRequestEventbusAddress(proxyRequestContext.getRequestId())).handler(message -> {
            Buffer chunkMessage = (Buffer) message.body();
            MessageChunk requestChunkMessage = new MessageChunk(chunkMessage);
            byte type = requestChunkMessage.getChunkType();
            if (type == MessageChunkType.BODY.getFlag()) {
                Buffer chunkBody = requestChunkMessage.getChunkBody();
                if (chunkBody.getByte(0) == (byte) 0) {
                    ws.writeTextMessage(chunkBody.getString(1, chunkBody.length()));
                } else {
                    ws.writeBinaryMessage(chunkBody.getBuffer(1, chunkBody.length()));
                }
            }
            if (type == MessageChunkType.CLOSED.getFlag()) {
                ws.close();
            }
        });

        consumer.completionHandler(unused -> webSocket.resume());

        ws.textMessageHandler(textBody -> {
            webSocket.write(MessageChunk.build(MessageChunkType.BODY, proxyRequestContext.getRequestId(), Buffer.buffer().appendByte((byte) 0).appendString(textBody)));
        });

        ws.binaryMessageHandler(bodyBuffer -> {
            webSocket.write(MessageChunk.build(MessageChunkType.BODY, proxyRequestContext.getRequestId(), Buffer.buffer().appendByte((byte) 1).appendBuffer(bodyBuffer)));
        });

        ws.closeHandler(unused -> {
            consumer.unregister();
            webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.CLOSED, proxyRequestContext.getRequestId()));
            eventHandler.afterProxyRequest(proxyRequestContext);
        });
    }

    private String buildFirstResponseChunkBody(HttpClientResponse httpClientResponse) {
        String statusMessage = httpClientResponse.statusMessage();
        int statusCode = httpClientResponse.statusCode();
        MultiMap responseHeaders = httpClientResponse.headers();
        return ResponseMessageInfoChunkBody.build(httpClientResponse.version(), statusMessage, statusCode, responseHeaders);
    }

    private String getProxyRequestEventbusAddress(long requestId) {
        return instanceId + "." + "proxy-request." + requestId;
    }

    private boolean isWebsocket(ProxyRequestContext proxyRequestContext) {
        return proxyRequestContext.getRequestHttpVersion().equals(HttpVersion.HTTP_1_1) &&
                proxyRequestContext.getRequestHttpMethod().equals(HttpMethod.GET) &&
                proxyRequestContext.getRequestHeaders().contains(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE, true);
    }

    private void handleProxyHttpRequest(WebSocket webSocket, ProxyRequestContext proxyRequestContext) {
        HttpClientRequest httpClientRequest;
        try {
            httpClientRequest = Future.await(proxyClient.request(proxyRequestContext.getRequestHttpMethod(), vertxHttpGatewayConnectorOptions.getServicePort(), vertxHttpGatewayConnectorOptions.getServiceHost(), vertxHttpGatewayConnectorOptions.getBasePathConvert().apply(proxyRequestContext.getRequestUri())));
        } catch (Throwable throwable) {
            log.debug("Failed to send request for {} {}:{}{}", proxyRequestContext.getRequestHttpMethod(), vertxHttpGatewayConnectorOptions.getServiceHost(),
                    vertxHttpGatewayConnectorOptions.getServicePort(), proxyRequestContext.getRequestUri(), throwable);
            webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.ERROR, proxyRequestContext.getRequestId(), throwable.getMessage()));
            webSocket.resume();
            return;
        }

        httpClientRequest.headers().addAll(proxyRequestContext.getRequestHeaders());

        MessageConsumer<Object> consumer = getVertx().eventBus().consumer(getProxyRequestEventbusAddress(proxyRequestContext.getRequestId())).handler(message -> {
            Buffer chunkMessage = (Buffer) message.body();
            MessageChunk requestChunkMessage = new MessageChunk(chunkMessage);
            byte type = requestChunkMessage.getChunkType();
            if (type == MessageChunkType.BODY.getFlag()) {
                Future.await(httpClientRequest.write(requestChunkMessage.getChunkBody()));
            }

            if (type == MessageChunkType.ENDING.getFlag()) {
                Future.await(httpClientRequest.end());

                HttpClientResponse httpClientResponse;
                try {
                    httpClientResponse = Future.await(httpClientRequest.response());
                } catch (Throwable throwable) {
                    log.debug("Failed to receive response for {} {}:{}{}", proxyRequestContext.getRequestHttpMethod(), vertxHttpGatewayConnectorOptions.getServiceHost(),
                            vertxHttpGatewayConnectorOptions.getServicePort(), proxyRequestContext.getRequestUri(), throwable);
                    webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.ERROR, proxyRequestContext.getRequestId(), throwable.getMessage()));
                    return;
                }

                Buffer firstChunk = MessageChunk.build(MessageChunkType.INFO, proxyRequestContext.getRequestId(), buildFirstResponseChunkBody(httpClientResponse));
                webSocket.writeBinaryMessage(firstChunk);

                httpClientResponse.handler(bodyBuffer -> {
                    webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.BODY, proxyRequestContext.getRequestId(), bodyBuffer));
                });

                httpClientResponse.endHandler(unused -> {
                    webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.ENDING, proxyRequestContext.getRequestId()));
                    proxyRequestContext.setHttpClientResponse(httpClientResponse);
                    eventHandler.afterProxyRequest(proxyRequestContext);
                });
            }

            if (type == MessageChunkType.CLOSED.getFlag()) {
                // gracefully close the request connection
                Future.await(httpClientRequest.connection().shutdown(500L));
            }
        });

        consumer.completionHandler(unused -> webSocket.resume());

        httpClientRequest.connection().closeHandler(unused -> {
            if (consumer.isRegistered()) {
                consumer.unregister();
            }
        });
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        log.debug("{} undeploy", this);
    }
}
