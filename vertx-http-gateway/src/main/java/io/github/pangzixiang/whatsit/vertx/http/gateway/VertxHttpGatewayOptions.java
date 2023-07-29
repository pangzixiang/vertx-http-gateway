package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.algorithm.LoadBalanceAlgorithm;
import io.github.pangzixiang.whatsit.vertx.http.gateway.algorithm.RoundRobin;
import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.EventHandler;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.shareddata.Shareable;
import io.vertx.ext.web.RoutingContext;
import lombok.Getter;

import java.util.concurrent.TimeUnit;

@Getter
public class VertxHttpGatewayOptions implements Shareable {
    private int proxyServerPort;
    private int listenerServerPort;
    private int proxyServerInstance;
    private int listenerServerInstance;
    private String listenerServerRegisterPath;
    private LoadBalanceAlgorithm loadBalanceAlgorithm;
    private long proxyTimeout;
    private EventHandler eventHandler;

    public VertxHttpGatewayOptions() {
        this.proxyServerPort = 8080;
        this.listenerServerPort = 9090;
        this.proxyServerInstance = 2;
        this.listenerServerInstance = 2;
        this.listenerServerRegisterPath = "/register";
        this.loadBalanceAlgorithm = new RoundRobin();
        this.proxyTimeout = TimeUnit.SECONDS.toMillis(15);
        this.eventHandler = new DefaultEventHandler();
    }

    public VertxHttpGatewayOptions setProxyServerPort(int proxyServerPort) {
        this.proxyServerPort = proxyServerPort;
        return this;
    }

    public VertxHttpGatewayOptions setListenerServerPort(int listenerServerPort) {
        this.listenerServerPort = listenerServerPort;
        return this;
    }

    public VertxHttpGatewayOptions setProxyServerInstance(int proxyServerInstance) {
        this.proxyServerInstance = proxyServerInstance;
        return this;
    }

    public VertxHttpGatewayOptions setListenerServerInstance(int listenerServerInstance) {
        this.listenerServerInstance = listenerServerInstance;
        return this;
    }

    public VertxHttpGatewayOptions setListenerServerRegisterPath(String listenerServerRegisterPath) {
        this.listenerServerRegisterPath = listenerServerRegisterPath;
        return this;
    }

    public VertxHttpGatewayOptions setLoadBalanceAlgorithm(LoadBalanceAlgorithm loadBalanceAlgorithm) {
        this.loadBalanceAlgorithm = loadBalanceAlgorithm;
        return this;
    }

    public VertxHttpGatewayOptions setProxyTimeout(int proxyTimeout) {
        this.proxyTimeout = proxyTimeout;
        return this;
    }

    public VertxHttpGatewayOptions setEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
        return this;
    }

    private static class DefaultEventHandler implements EventHandler {
        @Override
        public Future<Void> beforeEstablishConnection(RoutingContext routingContext) {
            return Future.succeededFuture();
        }

        @Override
        public Future<Void> afterEstablishConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
            return Future.succeededFuture();
        }

        @Override
        public Future<Void> beforeRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
            return Future.succeededFuture();
        }

        @Override
        public Future<Void> afterRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
            return Future.succeededFuture();
        }

        @Override
        public Future<Void> beforeProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance) {
            return Future.succeededFuture();
        }

        @Override
        public Future<Void> afterProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance) {
            return Future.succeededFuture();
        }
    }
}
