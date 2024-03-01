package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import lombok.Getter;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Getter
public class VertxHttpGatewayConnectorOptions {
    private final String listenerServerHost;
    private final int listenerServerPort;
    private final String serviceName;
    private final int servicePort;
    private String serviceHost;
    private String listenerServerRegisterPath;
    private WebSocketClientOptions registerClientOptions;
    private HttpClientOptions proxyClientOptions;
    private WebSocketClientOptions websocketProxyClientOptions;
    private int instance;
    private long connectionRetryIntervalInMillis;
    private Function<String, String> basePathConvert;

    private static final HttpClientOptions DEFAULT_PROXY_CLIENT_OPTIONS = new HttpClientOptions();

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, String listenerServerHost, int listenerServerPort, String serviceHost, String listenerServerRegisterPath, WebSocketClientOptions registerClientOptions, HttpClientOptions proxyClientOptions, WebSocketClientOptions websocketProxyClientOptions, int instance, long connectionRetryIntervalInMillis) {
        this.serviceName = serviceName;
        this.servicePort = servicePort;
        this.listenerServerHost = listenerServerHost;
        this.listenerServerPort = listenerServerPort;
        this.serviceHost = serviceHost;
        this.listenerServerRegisterPath = listenerServerRegisterPath;
        this.registerClientOptions = registerClientOptions;
        this.proxyClientOptions = proxyClientOptions;
        this.websocketProxyClientOptions = websocketProxyClientOptions;
        this.instance = instance;
        this.connectionRetryIntervalInMillis = connectionRetryIntervalInMillis;
        this.basePathConvert = basePath -> basePath;
    }

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, String listenerServerHost, int listenerServerPort) {
        this(serviceName, servicePort, listenerServerHost, listenerServerPort, "localhost", "/register", new WebSocketClientOptions(), DEFAULT_PROXY_CLIENT_OPTIONS, new WebSocketClientOptions(), 2, TimeUnit.SECONDS.toMillis(5));
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

    public VertxHttpGatewayConnectorOptions setWebsocketProxyClientOptions(WebSocketClientOptions websocketProxyClientOptions) {
        this.websocketProxyClientOptions = websocketProxyClientOptions;
        return this;
    }

    public VertxHttpGatewayConnectorOptions setRegisterClientOptions(WebSocketClientOptions registerClientOptions) {
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

    public VertxHttpGatewayConnectorOptions setBasePathConvert(Function<String, String> basePathConvert) {
        this.basePathConvert = basePathConvert;
        return this;
    }
}
