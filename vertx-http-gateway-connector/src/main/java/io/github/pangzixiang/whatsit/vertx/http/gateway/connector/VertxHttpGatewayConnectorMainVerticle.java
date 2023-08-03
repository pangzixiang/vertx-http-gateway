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

        deployFuture.compose(unused -> register(registerClient, webSocketConnectOptions, vertxHttpGatewayConnectorHandler)).onSuccess(unused2 -> {
            log.debug("Succeeded to start vertx http gateway connector");
            startPromise.complete();
        }).onFailure(throwable -> {
            log.error("Failed to start vertx http gateway connector", throwable);
            startPromise.fail(throwable);
        });
    }

    private Future<Void> register(HttpClient registerClient, WebSocketConnectOptions webSocketConnectOptions, VertxHttpGatewayConnectorHandler vertxHttpGatewayConnectorHandler) {
        Promise<Void> promise = Promise.promise();
        getVertx().setPeriodic(0, vertxHttpGatewayConnectorOptions.getConnectionRetryIntervalInMillis(), id -> {
            log.debug("Start to register to vertx http gateway [{}:{}{}]", webSocketConnectOptions.getHost(), webSocketConnectOptions.getPort(), webSocketConnectOptions.getURI());
            registerClient.webSocket(webSocketConnectOptions)
                    .onSuccess(ws -> {
                        getVertx().cancelTimer(id);
                        log.info("Succeeded to register to vertx http gateway [{}:{}{}]!", webSocketConnectOptions.getHost(), webSocketConnectOptions.getPort(), webSocketConnectOptions.getURI());
                        promise.complete();
                        ws.closeHandler(unused -> {
                            if (getVertx().deploymentIDs().contains(deploymentID())) {
                                register(registerClient, webSocketConnectOptions, vertxHttpGatewayConnectorHandler);
                            }
                        });
                        vertxHttpGatewayConnectorHandler.handle(ws);
                    })
                    .onFailure(throwable -> {
                        log.error("Failed to register to vertx http gateway [{}:{}{}]! (error={})", webSocketConnectOptions.getHost(), webSocketConnectOptions.getPort(), webSocketConnectOptions.getURI(), throwable.getMessage());
                    });
        });
        return promise.future();
    }


}
