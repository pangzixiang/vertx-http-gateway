package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.WebSocketClientOptions;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Getter
public class VertxHttpGatewayConnectorOptions {
    private final List<URI> registerURIs;
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

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, List<URI> registerURIs, String serviceHost, WebSocketClientOptions registerClientOptions, HttpClientOptions proxyClientOptions, WebSocketClientOptions websocketProxyClientOptions, int instance, long connectionRetryIntervalInMillis) {
        if (registerURIs.stream().anyMatch(uri -> !uri.getScheme().startsWith("ws"))) {
            throw new IllegalArgumentException("registerURIs must start with 'ws' or 'wss'");
        }
        this.serviceName = serviceName;
        this.servicePort = servicePort;
        this.registerURIs = registerURIs;
        this.serviceHost = serviceHost;
        this.registerClientOptions = registerClientOptions.setSsl(registerURIs.stream().anyMatch(uri -> uri.getScheme().startsWith("wss")));
        this.proxyClientOptions = proxyClientOptions;
        this.websocketProxyClientOptions = websocketProxyClientOptions;
        this.instance = instance;
        this.connectionRetryIntervalInMillis = connectionRetryIntervalInMillis;
        this.basePathConvert = basePath -> basePath;
    }

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, String listenerServerHost, int listenerServerPort, String serviceHost, String listenerServerRegisterPath, WebSocketClientOptions registerClientOptions, HttpClientOptions proxyClientOptions, WebSocketClientOptions websocketProxyClientOptions, int instance, long connectionRetryIntervalInMillis) {
        this(serviceName, servicePort, List.of(URI.create("%s://%s%s".formatted(registerClientOptions.isSsl()? "wss" : "ws", listenerServerPort > 0 ? listenerServerHost + ":" + listenerServerPort : listenerServerHost, StringUtils.isNotBlank(listenerServerRegisterPath) ? listenerServerRegisterPath : ""))), serviceHost, registerClientOptions, proxyClientOptions, websocketProxyClientOptions, instance, connectionRetryIntervalInMillis);
    }

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, String listenerServerHost, int listenerServerPort) {
        this(serviceName, servicePort, listenerServerHost, listenerServerPort, "localhost", "/register", new WebSocketClientOptions(), DEFAULT_PROXY_CLIENT_OPTIONS, new WebSocketClientOptions(), 2, TimeUnit.SECONDS.toMillis(5));
    }

    public VertxHttpGatewayConnectorOptions(String serviceName, int servicePort, List<URI> registerURIs) {
        this(serviceName, servicePort, registerURIs, "localhost", new WebSocketClientOptions(), DEFAULT_PROXY_CLIENT_OPTIONS, new WebSocketClientOptions(), 2, TimeUnit.SECONDS.toMillis(5));
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
