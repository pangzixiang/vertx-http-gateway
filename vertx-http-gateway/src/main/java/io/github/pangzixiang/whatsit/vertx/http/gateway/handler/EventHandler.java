package io.github.pangzixiang.whatsit.vertx.http.gateway.handler;

import io.github.pangzixiang.whatsit.vertx.http.gateway.ServiceRegistrationInstance;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;

/**
 * The interface Event handler.
 */
public interface EventHandler {
    /**
     * Before establish connection future.
     *
     * @param routingContext the routing context
     * @return the future
     *
     * will throw the throwable to routingContext if return failure future
     *
     */
    Future<Void> beforeEstablishConnection(RoutingContext routingContext);

    /**
     * After establish connection future.
     *
     * @param serviceName                 the service name
     * @param serviceRegistrationInstance the service registration instance
     */
    void afterEstablishConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance);

    /**
     * Before remove connection future.
     *
     * @param serviceName                 the service name
     * @param serviceRegistrationInstance the service registration instance
     */
    void beforeRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance);

    /**
     * After remove connection future.
     *
     * @param serviceName                 the service name
     * @param serviceRegistrationInstance the service registration instance
     */
    void afterRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance);

    /**
     * Before proxy request future.
     *
     * @param requestId                   the request id
     * @param httpServerRequest           the http server request
     * @param serviceRegistrationInstance the service registration instance
     * @return the future
     *
     * will throw the throwable to routingContext if return failure future
     *
     */
    Future<Void> beforeProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance);

    /**
     * Change the proxy response headers
     *
     * @param responseHeaders the response headers
     * @return the changed response headers
     */
    Future<MultiMap> processProxyResponseHeaders(MultiMap responseHeaders);

    /**
     * After proxy request future.
     *
     * @param requestId                   the request id
     * @param httpServerRequest           the http server request
     * @param serviceRegistrationInstance the service registration instance
     */
    void afterProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance);
}
