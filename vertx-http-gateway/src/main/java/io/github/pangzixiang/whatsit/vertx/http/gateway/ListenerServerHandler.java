package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunk;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
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
                serverWebSocket.handler(buffer -> {
                    MessageChunk messageChunk = new MessageChunk(buffer);
                    long requestId = messageChunk.getRequestId();
                    getVertx().eventBus().send(ProxyServerHandler.getProxyRequestEventBusAddress(requestId), buffer);
                });

                MessageConsumer<Object> messageConsumer = getVertx().eventBus().consumer(serviceRegistrationInstance.getEventBusAddress()).handler(msg -> {
                    Buffer chunk = (Buffer) msg.body();
                    serverWebSocket.writeBinaryMessage(chunk);
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
            log.info("New instance for [{}] registered [{}]", serviceName, serviceRegistrationInstance);
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
