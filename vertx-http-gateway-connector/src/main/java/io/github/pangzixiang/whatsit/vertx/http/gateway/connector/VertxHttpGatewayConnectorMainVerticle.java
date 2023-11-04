package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.EventHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class VertxHttpGatewayConnectorMainVerticle extends AbstractVerticle {
    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;
    private final EventHandler eventHandler;

    private Long waitPongTask;

    public VertxHttpGatewayConnectorMainVerticle(VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions, EventHandler eventHandler) {
        this.vertxHttpGatewayConnectorOptions = vertxHttpGatewayConnectorOptions;
        this.eventHandler = eventHandler;
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        HttpClient registerClient = getVertx().createHttpClient(vertxHttpGatewayConnectorOptions.getRegisterClientOptions());

        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions();
        webSocketConnectOptions.setHost(vertxHttpGatewayConnectorOptions.getListenerServerHost());
        webSocketConnectOptions.setPort(vertxHttpGatewayConnectorOptions.getListenerServerPort());
        webSocketConnectOptions.setURI(vertxHttpGatewayConnectorOptions.getListenerServerRegisterPath() + "?serviceName=%s&servicePort=%s&instance=%s".formatted(vertxHttpGatewayConnectorOptions.getServiceName(), vertxHttpGatewayConnectorOptions.getServicePort(), hashCode()));

        VertxHttpGatewayConnectorHandler vertxHttpGatewayConnectorHandler = new VertxHttpGatewayConnectorHandler(vertxHttpGatewayConnectorOptions, String.valueOf(hashCode()), eventHandler);

        Future<String> deployFuture = getVertx().deployVerticle(vertxHttpGatewayConnectorHandler);

        deployFuture.compose(unused -> register(registerClient, webSocketConnectOptions, vertxHttpGatewayConnectorHandler)).onSuccess(unused2 -> {
            log.debug("Succeeded to start vertx http gateway connector");
            startPromise.complete();
        }).onFailure(throwable -> {
            log.debug("Failed to start vertx http gateway connector", throwable);
            startPromise.fail(throwable);
        });
    }

    private Future<Void> register(HttpClient registerClient, WebSocketConnectOptions webSocketConnectOptions, VertxHttpGatewayConnectorHandler vertxHttpGatewayConnectorHandler) {
        Promise<Void> promise = Promise.promise();
        getVertx().setPeriodic(0, vertxHttpGatewayConnectorOptions.getConnectionRetryIntervalInMillis(), id -> {
            eventHandler.beforeEstablishConnection(webSocketConnectOptions).onSuccess(options -> {
                log.debug("Start to register to vertx http gateway [{}:{}{}]", options.getHost(), options.getPort(), options.getURI());
                registerClient.webSocket(options)
                        .onSuccess(ws -> {
                            getVertx().cancelTimer(id);
                            log.debug("Succeeded to register to vertx http gateway [{}:{}{}]!", options.getHost(), options.getPort(), options.getURI());
                            promise.complete();
                            eventHandler.afterEstablishConnection(ws);

                            ws.pongHandler(buffer -> {
                                log.trace("Received pong from server");
                                if (waitPongTask != null) {
                                    getVertx().cancelTimer(waitPongTask);
                                }
                            });

                            long pingTaskId = getVertx().setPeriodic(0, 5000, l -> {
                                log.trace("Send ping to server");
                                waitPongTask = getVertx().setTimer(3000, l2 -> {
                                    log.error("Failed to get pong from server, will reconnect...");
                                    ws.close();
                                });
                                ws.writePing(Buffer.buffer("ping")).onFailure(throwable -> {
                                    log.error("Failed to send ping to server", throwable);
                                    ws.close();
                                });
                            });
                            ws.closeHandler(unused -> {
                                getVertx().cancelTimer(pingTaskId);
                                if (getVertx().deploymentIDs().contains(deploymentID())) {
                                    register(registerClient, options, vertxHttpGatewayConnectorHandler);
                                }
                            });
                            vertxHttpGatewayConnectorHandler.handle(ws);
                        })
                        .onFailure(throwable -> {
                            log.debug("Failed to register to vertx http gateway [{}:{}{}]! (error={})", options.getHost(), options.getPort(), options.getURI(), throwable.getMessage());
                        });
            }).onFailure(throwable -> {
                getVertx().cancelTimer(id);
               promise.fail(throwable);
            });
        });
        return promise.future();
    }


}
