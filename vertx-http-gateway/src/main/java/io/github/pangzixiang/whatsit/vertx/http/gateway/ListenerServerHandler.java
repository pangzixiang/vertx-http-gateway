package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.common.MessageChunk;
import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.EventHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.shareddata.Lock;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@AllArgsConstructor
class ListenerServerHandler extends AbstractVerticle implements Handler<RoutingContext> {

    private final EventHandler eventHandler;

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            Future.await(eventHandler.beforeEstablishConnection(routingContext));
            ServerWebSocket serverWebSocket = Future.await(routingContext.request().toWebSocket());
            String serviceName = routingContext.queryParams().get("serviceName");
            String servicePort = routingContext.queryParams().get("servicePort");
            String instance = routingContext.queryParams().get("instance");

            if (StringUtils.isAnyEmpty(serviceName, servicePort, instance)) {
                serverWebSocket.reject();
                return;
            }

            ServiceRegistrationInstance serviceRegistrationInstance = ServiceRegistrationInstance.builder()
                    .remoteAddress(serverWebSocket.remoteAddress().hostAddress())
                    .remotePort(servicePort)
                    .instanceId(instance)
                    .build();

            try {
                Future.await(this.addConnector(serviceName, serviceRegistrationInstance));
            } catch (Throwable throwable) {
                serverWebSocket.reject();
                return;
            }

            eventHandler.afterEstablishConnection(serviceName, serviceRegistrationInstance);

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
                eventHandler.beforeRemoveConnection(serviceName, serviceRegistrationInstance);
                Future.await(messageConsumer.unregister());
                try {
                    Future.await(this.removeConnector(serviceName, serviceRegistrationInstance));
                    eventHandler.afterRemoveConnection(serviceName, serviceRegistrationInstance);
                } catch (Throwable throwable) {
                    log.debug("Failed to remove connector [{} -> {}]", serviceName, serviceRegistrationInstance);
                }
            });
        } catch (Throwable throwable) {
            routingContext.fail(throwable);
        }
    }

    private Future<Void> addConnector(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
        try {
            Lock lock = Future.await(GatewayUtils.getConnectorInfoMapLock(getVertx()));
            ServiceRegistrationInfo serviceRegistrationInfo = (ServiceRegistrationInfo) GatewayUtils.getConnectorInfoMap(getVertx()).getOrDefault(serviceName, ServiceRegistrationInfo.builder().basePath("/" + serviceName).build());
            serviceRegistrationInfo.addTargetServer(serviceRegistrationInstance);
            GatewayUtils.getConnectorInfoMap(getVertx()).putIfAbsent(serviceName, serviceRegistrationInfo);
            log.debug("New instance for [{}] registered [{}]", serviceName, serviceRegistrationInstance);
            lock.release();
            return Future.succeededFuture();
        } catch (Throwable throwable) {
            log.debug("Failed to get Lock to add connector info", throwable);
            return Future.failedFuture(throwable);
        }
    }

    private Future<Void> removeConnector(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
        try {
            Lock lock = Future.await(GatewayUtils.getConnectorInfoMapLock(getVertx()));
            ServiceRegistrationInfo serviceRegistrationInfo = (ServiceRegistrationInfo) GatewayUtils.getConnectorInfoMap(getVertx()).get(serviceName);
            if (serviceRegistrationInfo != null) {
                serviceRegistrationInfo.removeTargetServer(serviceRegistrationInstance);
                log.debug("Remove connection [{}] from {}:{}", serviceRegistrationInstance.getInstanceId(), serviceRegistrationInstance.getRemoteAddress(), serviceRegistrationInstance.getRemotePort());
            }
            lock.release();
            return Future.succeededFuture();
        } catch (Throwable throwable) {
            log.debug("Failed to get Lock to add connector info", throwable);
            return Future.failedFuture(throwable);
        }
    }
}
