package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.vertx.core.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
public class VertxHttpGatewayMainVerticle extends AbstractVerticle {

    private final VertxHttpGatewayOptions vertxHttpGatewayOptions;

    public VertxHttpGatewayMainVerticle() {
        this.vertxHttpGatewayOptions = new VertxHttpGatewayOptions();
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Future<String> listenerServerFuture = getVertx().deployVerticle(() -> new ListenerServerVerticle(vertxHttpGatewayOptions),
                new DeploymentOptions().setInstances(vertxHttpGatewayOptions.getListenerServerInstance()));
        Future<String> proxyServerFuture = getVertx().deployVerticle(() -> new ProxyServerVerticle(vertxHttpGatewayOptions),
                new DeploymentOptions().setInstances(vertxHttpGatewayOptions.getProxyServerInstance()));

        Future.all(listenerServerFuture, proxyServerFuture).onSuccess(unused -> startPromise.complete()).onFailure(startPromise::fail);
    }
}
