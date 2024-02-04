package io.github.pangzixiang.whatsit.vertx.http.gateway.handler;

import io.github.pangzixiang.whatsit.vertx.http.gateway.ServiceRegistrationInstance;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

public class DefaultEventHandler implements EventHandler {
    @Override
    public Future<Void> beforeEstablishConnection(RoutingContext routingContext) {
        return Future.succeededFuture();
    }

    @Override
    public void afterEstablishConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {}

    @Override
    public void beforeRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {}

    @Override
    public void afterRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {}

    @Override
    public Future<Void> beforeProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance) {
        return Future.succeededFuture();
    }

    @Override
    public Future<MultiMap> processProxyResponseHeaders(MultiMap responseHeaders) {
        return Future.succeededFuture(responseHeaders);
    }

    @Override
    public void afterProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance) {}
}
