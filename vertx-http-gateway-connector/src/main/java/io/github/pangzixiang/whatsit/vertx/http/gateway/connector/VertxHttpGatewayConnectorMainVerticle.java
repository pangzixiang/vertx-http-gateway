package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.EventHandler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
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
    public void start() throws Exception {
        WebSocketClient registerClient = getVertx().createWebSocketClient(vertxHttpGatewayConnectorOptions.getRegisterClientOptions());

        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions();
        webSocketConnectOptions.setHost(vertxHttpGatewayConnectorOptions.getListenerServerHost());
        webSocketConnectOptions.setPort(vertxHttpGatewayConnectorOptions.getListenerServerPort());
        webSocketConnectOptions.setURI(vertxHttpGatewayConnectorOptions.getListenerServerRegisterPath() + "?serviceName=%s&servicePort=%s&instance=%s".formatted(vertxHttpGatewayConnectorOptions.getServiceName(), vertxHttpGatewayConnectorOptions.getServicePort(), hashCode()));

        VertxHttpGatewayConnectorHandler vertxHttpGatewayConnectorHandler = new VertxHttpGatewayConnectorHandler(vertxHttpGatewayConnectorOptions, String.valueOf(hashCode()), eventHandler);

        Future.await(getVertx().deployVerticle(vertxHttpGatewayConnectorHandler, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)));

        Future.await(register(registerClient, webSocketConnectOptions, vertxHttpGatewayConnectorHandler));

        log.debug("Succeeded to start vertx http gateway connector");
    }

    private Future<Void> register(WebSocketClient registerClient, WebSocketConnectOptions webSocketConnectOptions, VertxHttpGatewayConnectorHandler vertxHttpGatewayConnectorHandler) {
        Promise<Void> promise = Promise.promise();
        getVertx().setPeriodic(0, vertxHttpGatewayConnectorOptions.getConnectionRetryIntervalInMillis(), id -> {
            WebSocketConnectOptions options;
            try {
                options = Future.await(eventHandler.beforeEstablishConnection(webSocketConnectOptions));
                log.debug("Start to register to vertx http gateway [{}:{}{}]", options.getHost(), options.getPort(), options.getURI());
            } catch (Throwable throwable) {
                getVertx().cancelTimer(id);
                promise.fail(throwable);
                return;
            }
            try {
                WebSocket ws = Future.await(registerClient.connect(options));
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
            } catch (Throwable throwable) {
                log.debug("Failed to register to vertx http gateway [{}:{}{}]! (error={})", options.getHost(), options.getPort(), options.getURI(), throwable.getMessage());
            }
        });
        return promise.future();
    }


}
