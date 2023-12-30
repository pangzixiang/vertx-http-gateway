package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.EventHandler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import lombok.extern.slf4j.Slf4j;

import static io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnector.RESTART_EVENT_BUS_ID;

@Slf4j
class VertxHttpGatewayConnectorMainVerticle extends AbstractVerticle {
    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;
    private final EventHandler eventHandler;

    private WebSocketClient registerClient;
    private Long timerId;

    public VertxHttpGatewayConnectorMainVerticle(VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions, EventHandler eventHandler) {
        this.vertxHttpGatewayConnectorOptions = vertxHttpGatewayConnectorOptions;
        this.eventHandler = eventHandler;
    }

    @Override
    public void start() throws Exception {
        registerClient = getVertx().createWebSocketClient(vertxHttpGatewayConnectorOptions.getRegisterClientOptions());

        WebSocketConnectOptions webSocketConnectOptions = new WebSocketConnectOptions();
        webSocketConnectOptions.setHost(vertxHttpGatewayConnectorOptions.getListenerServerHost());
        webSocketConnectOptions.setPort(vertxHttpGatewayConnectorOptions.getListenerServerPort());
        webSocketConnectOptions.setURI(vertxHttpGatewayConnectorOptions.getListenerServerRegisterPath() + "?serviceName=%s&servicePort=%s&instance=%s".formatted(vertxHttpGatewayConnectorOptions.getServiceName(), vertxHttpGatewayConnectorOptions.getServicePort(), hashCode()));

        VertxHttpGatewayConnectorHandler vertxHttpGatewayConnectorHandler = new VertxHttpGatewayConnectorHandler(vertxHttpGatewayConnectorOptions, String.valueOf(hashCode()), eventHandler);

        Future.await(getVertx().deployVerticle(vertxHttpGatewayConnectorHandler, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)));

        Future.await(register(webSocketConnectOptions, vertxHttpGatewayConnectorHandler));

        log.debug("Succeeded to start vertx http gateway connector");
    }

    private Future<Void> register(WebSocketConnectOptions webSocketConnectOptions, VertxHttpGatewayConnectorHandler vertxHttpGatewayConnectorHandler) {
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

                ws.closeHandler(unused -> {
                    VertxHttpGatewayConnector.setConnectorHealthy(true, false);
                    if (timerId != null) {
                        getVertx().cancelTimer(timerId);
                    }
                    if (getVertx().deploymentIDs().contains(deploymentID())) {
                        log.debug("Trigger reconnection...");
                        getVertx().eventBus().send(RESTART_EVENT_BUS_ID, null);
                    }
                });

                getVertx().cancelTimer(id);
                log.debug("Succeeded to register to vertx http gateway [{}:{}{}]!", options.getHost(), options.getPort(), options.getURI());
                VertxHttpGatewayConnector.setConnectorHealthy(false, true);
                promise.complete();

                eventHandler.afterEstablishConnection(ws);

                ws.pongHandler(buffer -> {
                    log.trace("Received pong from server");
                    if (timerId != null) {
                        getVertx().cancelTimer(timerId);
                    }
                    log.trace("Connector is healthy!");
                    VertxHttpGatewayConnector.setConnectorHealthy(false, true);
                });

                getVertx().setPeriodic(0, 6000, l -> {
                    log.trace("Send ping to server");
                    ws.writePing(Buffer.buffer("ping")).onSuccess(unused -> {
                        timerId = getVertx().setTimer(3000, l2 -> {
                            getVertx().cancelTimer(l);
                            log.error("Failed to get pong from server within 5s");
                            ws.close();
                        });
                    }).onFailure(throwable -> {
                        getVertx().cancelTimer(l);
                        log.error("Failed to send ping to server", throwable);
                        ws.close();
                    });
                });

                vertxHttpGatewayConnectorHandler.handle(ws);
            } catch (Throwable throwable) {
                log.debug("Failed to register to vertx http gateway [{}:{}{}]! (error={})", options.getHost(), options.getPort(), options.getURI(), throwable.getMessage());
            }
        });
        return promise.future();
    }


    @Override
    public void stop() throws Exception {
        super.stop();
        log.debug("{} undeploy", this);
    }
}
