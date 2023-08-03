package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.algorithm.LoadBalanceAlgorithm;
import io.github.pangzixiang.whatsit.vertx.http.gateway.algorithm.RoundRobin;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.shareddata.Shareable;
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
    private HttpServerOptions listenerServerOptions;
    private HttpServerOptions proxyServerOptions;

    public VertxHttpGatewayOptions() {
        this.proxyServerPort = 8080;
        this.listenerServerPort = 9090;
        this.proxyServerInstance = 2;
        this.listenerServerInstance = 2;
        this.listenerServerRegisterPath = "/register";
        this.loadBalanceAlgorithm = new RoundRobin();
        this.proxyTimeout = TimeUnit.SECONDS.toMillis(15);
        this.listenerServerOptions = new HttpServerOptions();
        this.proxyServerOptions = new HttpServerOptions();
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

    public VertxHttpGatewayOptions setListenerServerOptions(HttpServerOptions listenerServerOptions) {
        this.listenerServerOptions = listenerServerOptions;
        return this;
    }

    public VertxHttpGatewayOptions setProxyServerOptions(HttpServerOptions proxyServerOptions) {
        this.proxyServerOptions = proxyServerOptions;
        return this;
    }
}
