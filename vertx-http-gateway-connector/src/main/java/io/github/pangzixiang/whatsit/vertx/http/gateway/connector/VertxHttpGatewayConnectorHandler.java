package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunk;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunkType;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.RequestMessageInfoChunkBody;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.ResponseMessageInfoChunkBody;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
class VertxHttpGatewayConnectorHandler extends AbstractVerticle implements Handler<WebSocket> {

    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;

    private HttpClient proxyClient;

    private static final String INSTANCE_UUID = UUID.randomUUID().toString();

    public VertxHttpGatewayConnectorHandler(VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions) {
        this.vertxHttpGatewayConnectorOptions = vertxHttpGatewayConnectorOptions;
    }

    @Override
    public void start() throws Exception {
         proxyClient = getVertx().createHttpClient(vertxHttpGatewayConnectorOptions.getProxyClientOptions());
    }

    @Override
    public void handle(WebSocket webSocket) {
        webSocket.pause();
        webSocket.fetch(1);
        webSocket.frameHandler(frame -> {
            Buffer chunk = frame.binaryData();
            MessageChunk messageChunk = new MessageChunk(chunk);
            byte chunkType = messageChunk.getChunkType();
            byte requestId = messageChunk.getRequestId();
            if (chunkType == MessageChunkType.INFO.getFlag()) {
                String requestChunkBody = messageChunk.getChunkBody().toString();
                RequestMessageInfoChunkBody requestMessageInfoChunkBody = new RequestMessageInfoChunkBody(requestChunkBody);
                HttpMethod httpMethod = requestMessageInfoChunkBody.getHttpMethod();
                String uri = requestMessageInfoChunkBody.getUri();

                proxyClient.request(httpMethod, vertxHttpGatewayConnectorOptions.getServicePort(), vertxHttpGatewayConnectorOptions.getServiceHost(), uri).onSuccess(httpClientRequest -> {

                    httpClientRequest.headers().addAll(requestMessageInfoChunkBody.getHeaders());

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
                        webSocket.fetch(1);
                    });

                    consumer.completionHandler(unused -> webSocket.fetch(1));


                    httpClientRequest.response().onSuccess(httpClientResponse -> {
                        Buffer firstChunk = MessageChunk.build(MessageChunkType.INFO, requestId, buildFirstResponseChunkBody(httpClientResponse));
                        webSocket.writeFrame(WebSocketFrame.binaryFrame(firstChunk, false));

                        httpClientResponse.handler(bodyBuffer -> {
                            webSocket.writeFrame(WebSocketFrame.continuationFrame(MessageChunk.build(MessageChunkType.BODY, requestId, bodyBuffer), false));
                        });

                        httpClientResponse.endHandler(unused -> {
                            webSocket.writeFrame(WebSocketFrame.continuationFrame(MessageChunk.build(MessageChunkType.ENDING, requestId), true));
                            consumer.unregister();
                        });
                    }).onFailure(throwable -> {
                        log.error("Failed to receive response for {} {}:{}{}", httpMethod, vertxHttpGatewayConnectorOptions.getServiceHost(),
                                vertxHttpGatewayConnectorOptions.getServicePort(), uri, throwable);
                        webSocket.writeFrame(WebSocketFrame.binaryFrame(MessageChunk.build(MessageChunkType.ERROR, requestId, throwable.getMessage()), true));
                    });
                }).onFailure(throwable -> {
                    log.error("Failed to send request for {} {}:{}{}", httpMethod, vertxHttpGatewayConnectorOptions.getServiceHost(),
                            vertxHttpGatewayConnectorOptions.getServicePort(), uri, throwable);
                    webSocket.writeFrame(WebSocketFrame.binaryFrame(MessageChunk.build(MessageChunkType.ERROR, requestId, throwable.getMessage()), true));
                });
            } else {
                getVertx().eventBus().send(getProxyRequestEventbusAddress(requestId), chunk);
            }
        });
    }

    private String buildFirstResponseChunkBody(HttpClientResponse httpClientResponse) {
        String statusMessage = httpClientResponse.statusMessage();
        int statusCode = httpClientResponse.statusCode();
        MultiMap responseHeaders = httpClientResponse.headers();
        return ResponseMessageInfoChunkBody.build(statusMessage, statusCode, responseHeaders);
    }

    private static String getProxyRequestEventbusAddress(byte requestId) {
        return INSTANCE_UUID + "." + "proxy-request." + requestId;
    }
}
