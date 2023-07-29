package io.github.pangzixiang.whatsit.vertx.http.gateway.handler;

import io.github.pangzixiang.whatsit.vertx.http.gateway.ServiceRegistrationInstance;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

public interface EventHandler {
    Future<Void> beforeEstablishConnection(RoutingContext routingContext);

    Future<Void> afterEstablishConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance);

    Future<Void> beforeRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance);

    Future<Void> afterRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance);

    Future<Void> beforeProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance);
    Future<Void> afterProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance);
}
