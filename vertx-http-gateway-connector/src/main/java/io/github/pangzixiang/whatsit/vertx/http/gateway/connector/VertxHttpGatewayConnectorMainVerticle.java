package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.EventHandler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnector.RESTART_EVENT_BUS_ID;

@Slf4j
class VertxHttpGatewayConnectorMainVerticle extends AbstractVerticle {
    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;
    private final EventHandler eventHandler;

    private WebSocketClient registerClient;
    private WebSocket webSocket;
    private Long timerId;
    private final HealthChecks healthChecks;
    private final AtomicBoolean isHealthy = new AtomicBoolean(false);

    public VertxHttpGatewayConnectorMainVerticle(VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions, EventHandler eventHandler, HealthChecks healthChecks) {
        this.vertxHttpGatewayConnectorOptions = vertxHttpGatewayConnectorOptions;
        this.eventHandler = eventHandler;
        this.healthChecks = healthChecks;
        this.healthChecks.register("connector-%s".formatted(hashCode()), promise -> promise.complete(isHealthy.get() ? Status.OK() : Status.KO()));
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
                this.webSocket = Future.await(registerClient.connect(options));

                this.webSocket.closeHandler(unused -> {
                    this.isHealthy.compareAndSet(true, false);
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
                this.isHealthy.compareAndSet(false, true);
                promise.complete();

                eventHandler.afterEstablishConnection(this.webSocket);

                this.webSocket.pongHandler(buffer -> {
                    log.trace("Received pong from server");
                    if (timerId != null) {
                        getVertx().cancelTimer(timerId);
                    }
                    log.trace("Connector is healthy!");
                    this.isHealthy.compareAndSet(false, true);
                });

                getVertx().setPeriodic(0, 6000, l -> {
                    log.trace("Send ping to server");
                    this.webSocket.writePing(Buffer.buffer("ping")).onSuccess(unused -> {
                        timerId = getVertx().setTimer(3000, l2 -> {
                            getVertx().cancelTimer(l);
                            log.error("Failed to get pong from server within 5s");
                            Future.await(this.webSocket.close());
                        });
                    }).onFailure(throwable -> {
                        getVertx().cancelTimer(l);
                        log.error("Failed to send ping to server", throwable);
                        Future.await(this.webSocket.close());
                    });
                });

                vertxHttpGatewayConnectorHandler.handle(this.webSocket);
            } catch (Throwable throwable) {
                log.debug("Failed to register to vertx http gateway [{}:{}{}]! (error={})", options.getHost(), options.getPort(), options.getURI(), throwable.getMessage());
            }
        });
        return promise.future();
    }


    @Override
    public void stop() throws Exception {
        super.stop();
        this.healthChecks.unregister("connector-%s".formatted(hashCode()));
        if (this.webSocket != null && !this.webSocket.isClosed()) {
            this.webSocket.close()
                    .onSuccess(unused -> log.debug("ws connection is closed"))
                    .onFailure(throwable -> log.error("Failed to close ws connection", throwable));
        }
        this.timerId = null;
        log.debug("{} undeploy", this);
    }
}
