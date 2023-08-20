package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.vertx.core.http.HttpClientOptions;
import lombok.Getter;

import java.util.concurrent.TimeUnit;

@Getter
public class VertxHttpGatewayConnectorOptions {
    private final String listenerServerHost;
    private final int listenerServerPort;
    private final String serviceName;
    private final int servicePort;
    private String serviceHost;
    private String listenerServerRegisterPath;
    private HttpClientOptions registerClientOptions;
    private HttpClientOptions proxyClientOptions;
    private int instance;
    private long connectionRetryIntervalInMillis;

    private static final HttpClientOptions DEFAULT_PROXY_CLIENT_OPTIONS = new HttpClientOptions();

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, String listenerServerHost, int listenerServerPort, String serviceHost, String listenerServerRegisterPath, HttpClientOptions registerClientOptions, HttpClientOptions proxyClientOptions, int instance, long connectionRetryIntervalInMillis) {
        this.serviceName = serviceName;
        this.servicePort = servicePort;
        this.listenerServerHost = listenerServerHost;
        this.listenerServerPort = listenerServerPort;
        this.serviceHost = serviceHost;
        this.listenerServerRegisterPath = listenerServerRegisterPath;
        this.registerClientOptions = registerClientOptions;
        this.proxyClientOptions = proxyClientOptions;
        this.instance = instance;
        this.connectionRetryIntervalInMillis = connectionRetryIntervalInMillis;
    }

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, String listenerServerHost, int listenerServerPort) {
        this(serviceName, servicePort, listenerServerHost, listenerServerPort, "localhost", "/register", new HttpClientOptions(), DEFAULT_PROXY_CLIENT_OPTIONS, 2, TimeUnit.SECONDS.toMillis(2));
    }

    public VertxHttpGatewayConnectorOptions setServiceHost(String serviceHost) {
        this.serviceHost = serviceHost;
        return this;
    }

    public VertxHttpGatewayConnectorOptions setListenerServerRegisterPath(String listenerServerRegisterPath) {
        this.listenerServerRegisterPath = listenerServerRegisterPath;
        return this;
    }

    public VertxHttpGatewayConnectorOptions setProxyClientOptions(HttpClientOptions proxyClientOptions) {
        this.proxyClientOptions = proxyClientOptions;
        return this;
    }

    public VertxHttpGatewayConnectorOptions setRegisterClientOptions(HttpClientOptions registerClientOptions) {
        this.registerClientOptions = registerClientOptions;
        return this;
    }

    public VertxHttpGatewayConnectorOptions setInstance(int instance) {
        this.instance = instance;
        return this;
    }

    public VertxHttpGatewayConnectorOptions setConnectionRetryIntervalInMillis(long connectionRetryIntervalInMillis) {
        this.connectionRetryIntervalInMillis = connectionRetryIntervalInMillis;
        return this;
    }
}
