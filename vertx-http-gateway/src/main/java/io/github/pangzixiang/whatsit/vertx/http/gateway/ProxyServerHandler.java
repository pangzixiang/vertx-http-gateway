package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunk;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunkType;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.RequestMessageInfoChunkBody;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.ResponseMessageInfoChunkBody;
import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.EventHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.UUID;

@Slf4j
class ProxyServerHandler extends AbstractVerticle implements Handler<RoutingContext> {

    private static final String INSTANCE_UUID = UUID.randomUUID().toString();

    private final VertxHttpGatewayOptions vertxHttpGatewayOptions;
    private final EventHandler eventHandler;

    ProxyServerHandler(VertxHttpGatewayOptions vertxHttpGatewayOptions, EventHandler eventHandler) {
        this.vertxHttpGatewayOptions = vertxHttpGatewayOptions;
        this.eventHandler = eventHandler;
    }

    @Override
    public void handle(RoutingContext routingContext) {
        String base = routingContext.pathParam("base");

        ServiceRegistrationInfo serviceRegistrationInfo = (ServiceRegistrationInfo) GatewayUtils.getConnectorInfoMap(getVertx()).get(base);
        if (serviceRegistrationInfo != null && !serviceRegistrationInfo.getServiceRegistrationInstances().isEmpty()) {
            try {
                ServiceRegistrationInstance serviceRegistrationInstance = Future.await(resolveTargetServer(routingContext.request(), serviceRegistrationInfo));
                Long requestId = Future.await(GatewayUtils.generateRequestId(getVertx()));
                Future.await(eventHandler.beforeProxyRequest(requestId, routingContext.request(), serviceRegistrationInstance));
                if (isWebsocket(routingContext.request().version(), routingContext.request().method(), routingContext.request().headers())) {
                    handleProxyWebsocketRequest(routingContext, serviceRegistrationInstance, requestId);
                } else {
                    handleProxyHttpRequest(routingContext, serviceRegistrationInstance, requestId);
                }
            } catch (Throwable throwable) {
                routingContext.fail(throwable);
            }
        } else {
            routingContext.fail(HttpResponseStatus.NOT_FOUND.code());
        }
    }

    private void handleProxyWebsocketRequest(RoutingContext routingContext, ServiceRegistrationInstance serviceRegistrationInstance, long requestId) {
        log.debug("Start to proxy websocket request [{} {} {}] from {} to {}:{} in instance [{}] (requestId={})", routingContext.request().version(), routingContext.request().method(), routingContext.request().uri(), routingContext.request().remoteAddress(),
                serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort(), serviceRegistrationInstance.getInstanceId(), requestId);
        Buffer firstChunk = MessageChunk.build(MessageChunkType.INFO, requestId, buildFirstProxyRequestChunkBody(routingContext.request()));
        getVertx().eventBus().send(serviceRegistrationInstance.getEventBusAddress(), firstChunk);

        try {
            ServerWebSocket serverWebSocket = Future.await(routingContext.request().toWebSocket());
            MessageConsumer<Object> requestConsumer = getVertx().eventBus().consumer(getProxyRequestEventBusAddress(requestId)).handler(message -> {
                Buffer chunk = (Buffer) message.body();
                MessageChunk messageChunk = new MessageChunk(chunk);
                byte chunkType = messageChunk.getChunkType();

                if (chunkType == MessageChunkType.BODY.getFlag()) {
                    Buffer chunkBody = messageChunk.getChunkBody();
                    if (chunkBody.getByte(0) == (byte) 0) {
                        Future.await(serverWebSocket.writeTextMessage(chunkBody.getString(1, chunkBody.length())));
                    } else {
                        Future.await(serverWebSocket.writeBinaryMessage(chunkBody.getBuffer(1, chunkBody.length())));
                    }
                }

                if (chunkType == MessageChunkType.CLOSED.getFlag()) {
                    Future.await(serverWebSocket.close());
                }

            });

            serverWebSocket.textMessageHandler(textMessage -> {
                Buffer bodyChunk = MessageChunk.build(MessageChunkType.BODY, requestId, Buffer.buffer().appendByte((byte) 0).appendString(textMessage));
                getVertx().eventBus().send(serviceRegistrationInstance.getEventBusAddress(), bodyChunk);
            });

            serverWebSocket.binaryMessageHandler(bufferMessage -> {
                Buffer bodyChunk = MessageChunk.build(MessageChunkType.BODY, requestId, Buffer.buffer().appendByte((byte) 1).appendBuffer(bufferMessage));
                getVertx().eventBus().send(serviceRegistrationInstance.getEventBusAddress(), bodyChunk);
            });

            serverWebSocket.closeHandler(unused -> {
                Future.await(requestConsumer.unregister());
                Buffer endChunk = MessageChunk.build(MessageChunkType.CLOSED, requestId);
                getVertx().eventBus().send(serviceRegistrationInstance.getEventBusAddress(), endChunk);
            });
        } catch (Throwable throwable) {
            routingContext.fail(throwable);
        }
    }

    private void handleProxyHttpRequest(RoutingContext routingContext, ServiceRegistrationInstance serviceRegistrationInstance, long requestId) {
        log.debug("Start to proxy http request [{} {} {}] from {} to {}:{} in instance [{}] (requestId={})", routingContext.request().version(), routingContext.request().method(), routingContext.request().uri(), routingContext.request().remoteAddress(),
                serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort(), serviceRegistrationInstance.getInstanceId(), requestId);

        long timeoutChecker = getVertx().setTimer(vertxHttpGatewayOptions.getProxyTimeout(), l -> {
            log.debug("Failed to proxy request [{} {} {}] from {} to {}:{} in instance [{}] (requestId={}) due to no message from connector", routingContext.request().version(), routingContext.request().method(), routingContext.request().uri(), routingContext.request().remoteAddress(),
                    serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort(), serviceRegistrationInstance.getInstanceId(), requestId);
            routingContext.fail(HttpResponseStatus.GATEWAY_TIMEOUT.code());
        });

        MessageConsumer<Object> requestConsumer = getVertx().eventBus().consumer(getProxyRequestEventBusAddress(requestId)).handler(message -> {
            Buffer chunk = (Buffer) message.body();

            MessageChunk messageChunk = new MessageChunk(chunk);

            byte chunkType = messageChunk.getChunkType();
            if (Objects.equals(chunkType, MessageChunkType.INFO.getFlag())) {
                getVertx().cancelTimer(timeoutChecker);
                String responseInfoChunkBody = messageChunk.getChunkBody().toString();
                ResponseMessageInfoChunkBody responseMessageInfoChunkBody = new ResponseMessageInfoChunkBody(responseInfoChunkBody);
                routingContext.response().setStatusCode(responseMessageInfoChunkBody.getStatusCode()).setStatusMessage(responseMessageInfoChunkBody.getStatusMessage());

                if (routingContext.request().version().equals(HttpVersion.HTTP_1_1) &&
                        Objects.equals(responseMessageInfoChunkBody.getHeaders().get(HttpHeaderNames.TRANSFER_ENCODING), HttpHeaderValues.CHUNKED.toString())) {
                    routingContext.response().setChunked(true);
                }

                MultiMap finalResponseHeaders = Future.await(eventHandler.processProxyResponseHeaders(responseMessageInfoChunkBody.getHeaders().remove(HttpHeaderNames.TRANSFER_ENCODING)));
                // set response headers
                routingContext.response().headers().addAll(finalResponseHeaders);
            }

            if (Objects.equals(chunkType, MessageChunkType.BODY.getFlag())) {
                Future.await(routingContext.response().write(messageChunk.getChunkBody()));
            }

            if (Objects.equals(chunkType, MessageChunkType.ERROR.getFlag())) {
                log.debug("Failed to proxy request [{} {} {}] from {} to {}:{} in instance [{}] (requestId={}) due to {}", routingContext.request().version(), routingContext.request().method(), routingContext.request().uri(), routingContext.request().remoteAddress(),
                        serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort(), serviceRegistrationInstance.getInstanceId(), requestId, messageChunk.getChunkBody());
                Future.await(routingContext.response().setStatusCode(HttpResponseStatus.BAD_GATEWAY.code()).end("Failed to proxy request due to target server error [%s] (requestId=%s)".formatted(messageChunk.getChunkBody(), requestId)));
            }

            if (Objects.equals(chunkType, MessageChunkType.ENDING.getFlag())) {
                Future.await(routingContext.response().end());
                log.debug("Succeeded to proxy request [{} {} {}] from {} to {}:{} in instance [{}] (requestId={})", routingContext.request().version(), routingContext.request().method(), routingContext.request().uri(), routingContext.request().remoteAddress(),
                        serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort(), serviceRegistrationInstance.getInstanceId(), requestId);
            }
        });

        routingContext.response().endHandler(unused -> {
            getVertx().eventBus().send(serviceRegistrationInstance.getEventBusAddress(), MessageChunk.build(MessageChunkType.CLOSED, requestId));
            getVertx().cancelTimer(timeoutChecker);
            Future.await(requestConsumer.unregister());
            eventHandler.afterProxyRequest(requestId, routingContext.request(), serviceRegistrationInstance);
        });

        Buffer firstChunk = MessageChunk.build(MessageChunkType.INFO, requestId, buildFirstProxyRequestChunkBody(routingContext.request()));
        getVertx().eventBus().send(serviceRegistrationInstance.getEventBusAddress(), firstChunk);

        routingContext.request().handler(bodyBuffer -> {
            Buffer bodyChunk = MessageChunk.build(MessageChunkType.BODY, requestId, bodyBuffer);
            getVertx().eventBus().send(serviceRegistrationInstance.getEventBusAddress(), bodyChunk);
        });

        routingContext.request().endHandler(unused -> {
            Buffer endChunk = MessageChunk.build(MessageChunkType.ENDING, requestId);
            getVertx().eventBus().send(serviceRegistrationInstance.getEventBusAddress(), endChunk);
        });
    }

    private String buildFirstProxyRequestChunkBody(HttpServerRequest request) {
        HttpMethod requestMethod = request.method();
        String requestUri = request.uri();
        MultiMap requestHeaders = request.headers();
        return RequestMessageInfoChunkBody.build(request.version(), requestMethod, requestUri, requestHeaders);
    }

    private Future<ServiceRegistrationInstance> resolveTargetServer(HttpServerRequest httpServerRequest, ServiceRegistrationInfo serviceRegistrationInfo) {
        return vertxHttpGatewayOptions.getLoadBalanceAlgorithm().handle(getVertx(), httpServerRequest, serviceRegistrationInfo);
    }

    public static String getProxyRequestEventBusAddress(long requestId) {
        return INSTANCE_UUID + "." + "proxy-request." + requestId;
    }

    private boolean isWebsocket(HttpVersion httpVersion, HttpMethod httpMethod, MultiMap headers) {
        return httpVersion.equals(HttpVersion.HTTP_1_1) &&
                httpMethod.equals(HttpMethod.GET) &&
                headers.contains(HttpHeaders.CONNECTION, HttpHeaders.UPGRADE, true);
    }
}
