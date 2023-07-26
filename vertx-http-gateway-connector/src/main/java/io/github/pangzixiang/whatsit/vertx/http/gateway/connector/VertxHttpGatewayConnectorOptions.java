package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.vertx.core.http.HttpClientOptions;
import lombok.Getter;

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

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, String listenerServerHost, int listenerServerPort, String serviceHost, String listenerServerRegisterPath, HttpClientOptions registerClientOptions, HttpClientOptions proxyClientOptions, int instance) {
        this.serviceName = serviceName;
        this.servicePort = servicePort;
        this.listenerServerHost = listenerServerHost;
        this.listenerServerPort = listenerServerPort;
        this.serviceHost = serviceHost;
        this.listenerServerRegisterPath = listenerServerRegisterPath;
        this.registerClientOptions = registerClientOptions;
        this.proxyClientOptions = proxyClientOptions;
        this.instance = instance;
    }

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, String listenerServerHost, int listenerServerPort) {
        this(serviceName, servicePort, listenerServerHost, listenerServerPort, "localhost", "/register?serviceName=" + serviceName + "&servicePort=" + servicePort, new HttpClientOptions(), new HttpClientOptions(), 2);
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
}
