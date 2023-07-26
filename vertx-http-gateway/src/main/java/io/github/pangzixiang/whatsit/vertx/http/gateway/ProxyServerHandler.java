package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunk;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunkType;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.RequestMessageInfoChunkBody;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.ResponseMessageInfoChunkBody;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@AllArgsConstructor
class ProxyServerHandler extends AbstractVerticle implements Handler<RoutingContext> {

    private static final String INSTANCE_UUID = UUID.randomUUID().toString();

    private final VertxHttpGatewayOptions vertxHttpGatewayOptions;

    @Override
    public void handle(RoutingContext routingContext) {
        String base = routingContext.pathParam("base");

        ServiceRegistrationInfo serviceRegistrationInfo = (ServiceRegistrationInfo) GatewayUtils.getConnectorInfoMap(getVertx()).get(base);
        if (serviceRegistrationInfo != null && !serviceRegistrationInfo.getServiceRegistrationInstances().isEmpty()) {

            Future<ServiceRegistrationInstance> serviceRegistrationInstanceFuture = resolveTargetServer(routingContext.request(), serviceRegistrationInfo);

            serviceRegistrationInstanceFuture.onSuccess(serviceRegistrationInstance -> {
                byte requestId = GatewayUtils.generateRequestId();

                log.info("Start to proxy request [{} {}] from {} to {}:{} in instance [{}] (requestId={})", routingContext.request().method(), routingContext.request().uri(), routingContext.request().remoteAddress(),
                        serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort(), serviceRegistrationInstance.getInstanceId(), requestId);

                MessageConsumer<Object> requestConsumer = getVertx().eventBus().consumer(getProxyRequestEventBusAddress(requestId)).handler(message -> {
                    Buffer chunk = (Buffer) message.body();

                    MessageChunk messageChunk = new MessageChunk(chunk);

                    byte chunkType = messageChunk.getChunkType();
                    if (Objects.equals(chunkType, MessageChunkType.INFO.getFlag())) {
                        String responseInfoChunkBody = messageChunk.getChunkBody().toString();
                        ResponseMessageInfoChunkBody responseMessageInfoChunkBody = new ResponseMessageInfoChunkBody(responseInfoChunkBody);
                        routingContext.request().response().setStatusCode(responseMessageInfoChunkBody.getStatusCode()).setStatusMessage(responseMessageInfoChunkBody.getStatusMessage());
                        // set response headers
                        routingContext.request().response().headers().addAll(responseMessageInfoChunkBody.getHeaders());
                    }

                    if (Objects.equals(chunkType, MessageChunkType.BODY.getFlag())) {
                        routingContext.request().response().send(messageChunk.getChunkBody());
                    }

                    if (Objects.equals(chunkType, MessageChunkType.ERROR.getFlag())) {
                        log.info("Failed to proxy request [{} {}] from {} to {}:{} in instance [{}] (requestId={}) due to {}", routingContext.request().method(), routingContext.request().uri(), routingContext.request().remoteAddress(),
                                serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort(), serviceRegistrationInstance.getInstanceId(), requestId, messageChunk.getChunkBody());
                        routingContext.request().response().setStatusCode(HttpResponseStatus.BAD_GATEWAY.code()).end("Failed to proxy request due to target server error (requestId=%s)".formatted(requestId));
                    }

                    if (Objects.equals(chunkType, MessageChunkType.ENDING.getFlag())) {
                        routingContext.request().response().end();
                        log.info("Succeeded to proxy request [{} {}] from {} to {}:{} in instance [{}] (requestId={})", routingContext.request().method(), routingContext.request().uri(), routingContext.request().remoteAddress(),
                                serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort(), serviceRegistrationInstance.getInstanceId(), requestId);
                    }
                });

                long timeoutChecker = getVertx().setTimer(vertxHttpGatewayOptions.getProxyTimeout(), l -> {
                    if (!routingContext.request().response().ended() && !routingContext.request().response().isChunked() && routingContext.request().response().bytesWritten() == 0) {
                        routingContext.fail(HttpResponseStatus.GATEWAY_TIMEOUT.code());
                    }
                });

                routingContext.request().response().endHandler(unused -> {
                    getVertx().cancelTimer(timeoutChecker);
                    requestConsumer.unregister();
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
            }).onFailure(routingContext::fail);
        } else {
            routingContext.fail(HttpResponseStatus.NOT_FOUND.code());
        }
    }

    /**
     * @param request HttpServerRequest
     * @return
     * GET /test-service/test
     * Content-Type: application/json
     * [other headers]
     */
    private String buildFirstProxyRequestChunkBody(HttpServerRequest request) {
        HttpMethod requestMethod = request.method();
        String requestUri = request.uri();
        MultiMap requestHeaders = request.headers();
        return RequestMessageInfoChunkBody.build(requestMethod, requestUri, request.query(), requestHeaders);
    }

    private Future<ServiceRegistrationInstance> resolveTargetServer(HttpServerRequest httpServerRequest, ServiceRegistrationInfo serviceRegistrationInfo) {
        return vertxHttpGatewayOptions.getLoadBalanceAlgorithm().handle(getVertx(), httpServerRequest, serviceRegistrationInfo);
    }

    public static String getProxyRequestEventBusAddress(byte requestId) {
        return INSTANCE_UUID + "." + "proxy-request." + requestId;
    }
}
