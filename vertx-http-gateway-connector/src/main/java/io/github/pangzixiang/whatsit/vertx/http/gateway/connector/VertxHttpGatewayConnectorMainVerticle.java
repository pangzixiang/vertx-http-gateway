package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
class VertxHttpGatewayConnectorMainVerticle extends AbstractVerticle {
    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        HttpClient registerClient = getVertx().createHttpClient(vertxHttpGatewayConnectorOptions.getRegisterClientOptions());

        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions();
        webSocketConnectOptions.setHost(vertxHttpGatewayConnectorOptions.getListenerServerHost());
        webSocketConnectOptions.setPort(vertxHttpGatewayConnectorOptions.getListenerServerPort());
        webSocketConnectOptions.setURI(vertxHttpGatewayConnectorOptions.getListenerServerRegisterPath() + "&instance=" + hashCode());

        VertxHttpGatewayConnectorHandler vertxHttpGatewayConnectorHandler = new VertxHttpGatewayConnectorHandler(vertxHttpGatewayConnectorOptions, String.valueOf(hashCode()));

        Future<String> deployFuture = getVertx().deployVerticle(vertxHttpGatewayConnectorHandler);

        Future<WebSocket> webSocketFuture = registerClient.webSocket(webSocketConnectOptions).onSuccess(vertxHttpGatewayConnectorHandler);

        Future.all(deployFuture, webSocketFuture).onSuccess(unused -> {
            log.info("Succeeded to register to gateway [{}:{}{}]!", webSocketConnectOptions.getHost(), webSocketConnectOptions.getPort(), webSocketConnectOptions.getURI());
            startPromise.complete();
        }).onFailure(startPromise::fail);
    }


}
