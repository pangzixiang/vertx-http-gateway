package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.EventHandler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.*;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
class VertxHttpGatewayConnectorMainVerticle extends AbstractVerticle {
    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;
    private final EventHandler eventHandler;

    private static final String EVENT_BUS_STATUS_CHECK = "status-check-" + UUID.randomUUID();

    private Long timerId;

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

                MessageConsumer<Object> messageConsumerForStatusCheck = getVertx().eventBus().consumer(EVENT_BUS_STATUS_CHECK).handler(message -> {
                    boolean isHealthy = (boolean) message.body();
                    if (!isHealthy) {
                        ws.close();
                    } else {
                        log.trace("connection is healthy");
                    }
                });

                ws.closeHandler(unused -> {
                    if (timerId != null) {
                        getVertx().cancelTimer(timerId);
                    }
                    messageConsumerForStatusCheck.unregister();
                    if (getVertx().deploymentIDs().contains(deploymentID())) {
                        log.debug("Trigger reconnection...");
                        register(registerClient, options, vertxHttpGatewayConnectorHandler);
                    }
                });

                getVertx().cancelTimer(id);
                log.debug("Succeeded to register to vertx http gateway [{}:{}{}]!", options.getHost(), options.getPort(), options.getURI());
                promise.complete();

                eventHandler.afterEstablishConnection(ws);

                ws.pongHandler(buffer -> {
                    log.trace("Received pong from server");
                    if (timerId != null) {
                        getVertx().cancelTimer(timerId);
                    }
                    getVertx().eventBus().send(EVENT_BUS_STATUS_CHECK, true);
                });

                getVertx().setPeriodic(0, 10000, l -> {
                    log.trace("Send ping to server");

                    ws.writePing(Buffer.buffer("ping")).onSuccess(unused -> {
                        timerId = getVertx().setTimer(5000, l2 -> {
                            log.error("Failed to get pong from server within 5s");
                            getVertx().eventBus().send(EVENT_BUS_STATUS_CHECK, false);
                            getVertx().cancelTimer(l);
                        });
                    }).onFailure(throwable -> {
                        log.error("Failed to send ping to server", throwable);
                        getVertx().eventBus().send(EVENT_BUS_STATUS_CHECK, false);
                        getVertx().cancelTimer(l);
                    });
                });

                vertxHttpGatewayConnectorHandler.handle(ws);
            } catch (Throwable throwable) {
                log.debug("Failed to register to vertx http gateway [{}:{}{}]! (error={})", options.getHost(), options.getPort(), options.getURI(), throwable.getMessage());
            }
        });
        return promise.future();
    }


}
