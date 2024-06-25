package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.DefaultEventHandler;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.EventHandler;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.ext.healthchecks.HealthChecks;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class VertxHttpGatewayConnector {
    private final Vertx vertx;
    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;

    private EventHandler eventHandler;

    private static final String CLOSE_EVENT_BUS_ID = UUID.randomUUID().toString();

    static final String RESTART_EVENT_BUS_ID = UUID.randomUUID().toString();

    private final AtomicBoolean isReconnection = new AtomicBoolean(false);

    private final HealthChecks healthChecks;

    private MessageConsumer<Object> closeMessageConsumer;

    public VertxHttpGatewayConnector(Vertx vertx, VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions) {
        this(vertx, vertxHttpGatewayConnectorOptions, HealthChecks.create(vertx));
    }

    public VertxHttpGatewayConnector(Vertx vertx, VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions, HealthChecks healthChecks) {
        this.vertx = vertx;
        this.vertxHttpGatewayConnectorOptions = vertxHttpGatewayConnectorOptions;
        this.eventHandler = new DefaultEventHandler();
        this.healthChecks = healthChecks;
        vertx.eventBus().consumer(RESTART_EVENT_BUS_ID).handler(msg -> {
            if (isReconnection.compareAndSet(false, true)) {
                this.close().compose(unused -> this.connect()).onComplete(result -> {
                    if (result.succeeded() && isReconnection.compareAndSet(true, false)) {
                        log.info("Connector restart successfully!");
                    } else {
                        log.info("Connector restart failed", result.cause());
                        vertx.eventBus().send(RESTART_EVENT_BUS_ID, null);
                    }
                });
            }
        }).completionHandler(unused -> log.debug("Succeeded to register restart event bus [{}]", RESTART_EVENT_BUS_ID));
    }

    public VertxHttpGatewayConnector withEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
        return this;
    }

    public final HealthChecks getHealthChecks() {
        return this.healthChecks;
    }

    public Future<Void> connect() {
        List<Future<Void>> startFutures = new ArrayList<>();
        vertxHttpGatewayConnectorOptions.getRegisterURIs().forEach(registerURI -> startFutures.add(startConnectorVerticle(registerURI)));
        return Future.all(startFutures).mapEmpty();
    }

    private Future<Void> startConnectorVerticle(URI registerURI) {
        Promise<Void> promise = Promise.promise();
        vertx.deployVerticle(() -> new VertxHttpGatewayConnectorMainVerticle(vertxHttpGatewayConnectorOptions, registerURI, eventHandler, healthChecks), new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD).setInstances(vertxHttpGatewayConnectorOptions.getInstance())).onSuccess(id -> {
            this.closeMessageConsumer = vertx.eventBus().consumer(CLOSE_EVENT_BUS_ID).handler(message -> {
                eventHandler.beforeDisconnect();
                vertx.undeploy(id).onComplete(result -> {
                    if (result.succeeded()) {
                        message.reply(result.succeeded());
                    } else {
                        message.fail(-1, result.cause().getMessage());
                    }
                    eventHandler.afterDisconnect(result.succeeded(), result.cause());
                });
            });

            this.closeMessageConsumer.completionHandler(result -> {
                if (result.succeeded()) {
                    log.debug("Succeeded to register shutdown eventbus [{}]", CLOSE_EVENT_BUS_ID);
                    promise.complete();
                } else {
                    String err = "Failed to register shutdown eventbus";
                    log.debug(err, result.cause());
                    promise.fail(err);
                }
            });
        }).onFailure(throwable -> {
            log.debug("Failed to deploy vertx http gateway connector main verticle", throwable);
            promise.fail(throwable);
        });

        return promise.future();
    }

    public Future<Void> close() {
        return vertx.eventBus().request(CLOSE_EVENT_BUS_ID, null).compose(message -> {
            this.closeMessageConsumer.unregister();
            if (Boolean.TRUE.equals(message.body())) {
                return Future.succeededFuture();
            } else {
                return Future.failedFuture("Failed to close connector");
            }
        }, Future::failedFuture);
    }

}
