package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunk;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunkType;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.RequestMessageInfoChunkBody;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.ResponseMessageInfoChunkBody;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.AbstractVerticle;
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

    private final String instanceId;

    public VertxHttpGatewayConnectorHandler(VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions, String instanceId) {
        this.vertxHttpGatewayConnectorOptions = vertxHttpGatewayConnectorOptions;
        this.instanceId = instanceId;
    }

    @Override
    public void start() throws Exception {
        proxyClient = getVertx().createHttpClient(vertxHttpGatewayConnectorOptions.getProxyClientOptions());
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

                if (isWebsocket(httpVersion, httpMethod, headers)) {
                    handleProxyWebsocket(webSocket, uri, headers, requestId);
                } else {
                    handleProxyHttpRequest(webSocket, httpMethod, uri, headers, requestId);
                }
            } else {
                getVertx().eventBus().send(getProxyRequestEventbusAddress(requestId), buffer);
            }
        });
    }

    private void handleProxyWebsocket(WebSocket webSocket, String uri, MultiMap headers, long requestId) {
        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions();
        webSocketConnectOptions.setHost(vertxHttpGatewayConnectorOptions.getServiceHost());
        webSocketConnectOptions.setPort(vertxHttpGatewayConnectorOptions.getServicePort());
        webSocketConnectOptions.setURI(uri);
        webSocketConnectOptions.setHeaders(headers.remove(HttpHeaderNames.SEC_WEBSOCKET_EXTENSIONS));
        proxyClient.webSocket(webSocketConnectOptions).onSuccess(ws -> {
            webSocket.resume();
            MessageConsumer<Object> consumer = getVertx().eventBus().consumer(getProxyRequestEventbusAddress(requestId)).handler(message -> {
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

            ws.textMessageHandler(textBody -> {
                webSocket.write(MessageChunk.build(MessageChunkType.BODY, requestId, Buffer.buffer().appendByte((byte) 0).appendString(textBody)));
            });

            ws.binaryMessageHandler(bodyBuffer -> {
                webSocket.write(MessageChunk.build(MessageChunkType.BODY, requestId, Buffer.buffer().appendByte((byte) 1).appendBuffer(bodyBuffer)));
            });

            ws.closeHandler(unused -> {
                consumer.unregister();
                webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.CLOSED, requestId));
            });
        }).onFailure(throwable -> {
            log.error("Failed to establish websocket connection for {}:{}{}", vertxHttpGatewayConnectorOptions.getServiceHost(),
                    vertxHttpGatewayConnectorOptions.getServicePort(), uri, throwable);
            webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.ERROR, requestId, throwable.getMessage()));
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

    private boolean isWebsocket(HttpVersion httpVersion, HttpMethod httpMethod, MultiMap headers) {
        return httpVersion.equals(HttpVersion.HTTP_1_1) &&
                httpMethod.equals(HttpMethod.GET) &&
                headers.contains(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE, true);
    }

    private void handleProxyHttpRequest(WebSocket webSocket, HttpMethod httpMethod, String uri, MultiMap headers, long requestId) {
        proxyClient.request(httpMethod, vertxHttpGatewayConnectorOptions.getServicePort(), vertxHttpGatewayConnectorOptions.getServiceHost(), uri).onSuccess(httpClientRequest -> {
            httpClientRequest.headers().addAll(headers);

            MessageConsumer<Object> consumer = getVertx().eventBus().consumer(getProxyRequestEventbusAddress(requestId)).handler(message -> {
                Buffer chunkMessage = (Buffer) message.body();
                MessageChunk requestChunkMessage = new MessageChunk(chunkMessage);
                byte type = requestChunkMessage.getChunkType();
                if (type == MessageChunkType.BODY.getFlag()) {
                    httpClientRequest.write(requestChunkMessage.getChunkBody());
                }

                if (type == MessageChunkType.ENDING.getFlag()) {
                    httpClientRequest.end();
                }

                if (type == MessageChunkType.CLOSED.getFlag()) {
                    // gracefully close the request connection
                    httpClientRequest.connection().shutdown(500L);
                }
            });

            consumer.completionHandler(unused -> webSocket.resume());

            httpClientRequest.connection().closeHandler(unused -> {
                if (consumer.isRegistered()) {
                    consumer.unregister();
                }
            });

            httpClientRequest.response().onSuccess(httpClientResponse -> {
                Buffer firstChunk = MessageChunk.build(MessageChunkType.INFO, requestId, buildFirstResponseChunkBody(httpClientResponse));
                webSocket.writeBinaryMessage(firstChunk);

                httpClientResponse.handler(bodyBuffer -> {
                    webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.BODY, requestId, bodyBuffer));
                });

                httpClientResponse.endHandler(unused -> {
                    webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.ENDING, requestId));
                });
            }).onFailure(throwable -> {
                log.error("Failed to receive response for {} {}:{}{}", httpMethod, vertxHttpGatewayConnectorOptions.getServiceHost(),
                        vertxHttpGatewayConnectorOptions.getServicePort(), uri, throwable);
                webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.ERROR, requestId, throwable.getMessage()));
            });
        }).onFailure(throwable -> {
            log.error("Failed to send request for {} {}:{}{}", httpMethod, vertxHttpGatewayConnectorOptions.getServiceHost(),
                    vertxHttpGatewayConnectorOptions.getServicePort(), uri, throwable);
            webSocket.writeBinaryMessage(MessageChunk.build(MessageChunkType.ERROR, requestId, throwable.getMessage()));
            webSocket.resume();
        });
    }
}
