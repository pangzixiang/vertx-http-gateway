package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunk;
import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunkType;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class ListenerServerHandler extends AbstractVerticle implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.request().toWebSocket().onSuccess(serverWebSocket -> {
            String serviceName = routingContext.queryParams().get("serviceName");
            String servicePort = routingContext.queryParams().get("servicePort");
            String instance = routingContext.queryParams().get("instance");

            ServiceRegistrationInstance serviceRegistrationInstance = ServiceRegistrationInstance.builder()
                    .remoteAddress(serverWebSocket.remoteAddress().hostAddress())
                    .remotePort(servicePort)
                    .instanceId(instance)
                    .build();

            this.addConnector(serviceName, serviceRegistrationInstance).onSuccess(unused -> {
                serverWebSocket.frameHandler(frame -> {
                    Buffer chunk = frame.binaryData();
                    MessageChunk messageChunk = new MessageChunk(chunk);
                    byte requestId = messageChunk.getRequestId();
                    getVertx().eventBus().send(ProxyServerHandler.getProxyRequestEventBusAddress(requestId), chunk);
                });

                MessageConsumer<Object> messageConsumer = getVertx().eventBus().consumer(serviceRegistrationInstance.getEventBusAddress()).handler(msg -> {
                    Buffer chunk = (Buffer) msg.body();
                    MessageChunk messageChunk = new MessageChunk(chunk);
                    byte chunkType = messageChunk.getChunkType();
                    if (chunkType == MessageChunkType.INFO.getFlag()) {
                        serverWebSocket.writeFrame(WebSocketFrame.binaryFrame(chunk, false));
                    }
                    if (chunkType == MessageChunkType.BODY.getFlag()) {
                        serverWebSocket.writeFrame(WebSocketFrame.continuationFrame(chunk, false));
                    }
                    if (chunkType == MessageChunkType.ENDING.getFlag()) {
                        serverWebSocket.writeFrame(WebSocketFrame.continuationFrame(chunk, true));
                    }
                });

                serverWebSocket.closeHandler(unused2 -> {
                    messageConsumer.unregister();
                    this.removeConnector(serviceName, serviceRegistrationInstance);
                });
            }).onFailure(throwable -> serverWebSocket.reject());
        }).onFailure(throwable -> {
            routingContext.fail(HttpResponseStatus.BAD_REQUEST.code());
        });
    }

    private Future<Void> addConnector(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
        return GatewayUtils.getConnectorInfoMapLock(getVertx()).onSuccess(lock -> {
            ServiceRegistrationInfo serviceRegistrationInfo = (ServiceRegistrationInfo) GatewayUtils.getConnectorInfoMap(getVertx()).getOrDefault(serviceName, ServiceRegistrationInfo.builder().basePath("/" + serviceName).build());
            serviceRegistrationInfo.addTargetServer(serviceRegistrationInstance);
            GatewayUtils.getConnectorInfoMap(getVertx()).putIfAbsent(serviceName, serviceRegistrationInfo);
            lock.release();
        }).onFailure(throwable -> log.error("Failed to get Lock to add connector info", throwable)).mapEmpty();
    }

    private Future<Void> removeConnector(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
        return GatewayUtils.getConnectorInfoMapLock(getVertx()).onSuccess(lock -> {
            ServiceRegistrationInfo serviceRegistrationInfo = (ServiceRegistrationInfo) GatewayUtils.getConnectorInfoMap(getVertx()).get(serviceName);
            if (serviceRegistrationInfo != null) {
                serviceRegistrationInfo.removeTargetServer(serviceRegistrationInstance);
                log.debug("Remove connection [{}] from {}:{} due to connection closed", serviceRegistrationInstance.getInstanceId(), serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort());
            }
            lock.release();
        }).onFailure(throwable -> log.error("Failed to get Lock to add connector info", throwable)).mapEmpty();
    }
}
