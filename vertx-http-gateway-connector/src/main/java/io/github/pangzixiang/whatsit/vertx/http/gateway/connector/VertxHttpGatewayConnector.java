package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.DefaultEventHandler;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.EventHandler;
import io.vertx.core.*;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class VertxHttpGatewayConnector {
    private final Vertx vertx;
    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;

    private EventHandler eventHandler;

    public VertxHttpGatewayConnector(Vertx vertx, VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions) {
        this.vertx = vertx;
        this.vertxHttpGatewayConnectorOptions = vertxHttpGatewayConnectorOptions;
        this.eventHandler = new DefaultEventHandler();
    }

    public VertxHttpGatewayConnector withEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
        return this;
    }

    private static final String CLOSE_EVENT_BUS_ID = UUID.randomUUID().toString();

    public Future<Void> connect() {
        Promise<Void> promise = Promise.promise();
        vertx.deployVerticle(() -> new VertxHttpGatewayConnectorMainVerticle(vertxHttpGatewayConnectorOptions, eventHandler), new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD).setInstances(vertxHttpGatewayConnectorOptions.getInstance())).onSuccess(id -> {
            vertx.eventBus().consumer(CLOSE_EVENT_BUS_ID).handler(message -> {
                eventHandler.beforeDisconnect();
                vertx.undeploy(id).onComplete(result -> {
                    message.reply(result.succeeded());
                    eventHandler.afterDisconnect(result.succeeded(), result.cause());
                });
            }).completionHandler(result -> {
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
           if (Boolean.TRUE.equals(message.body())) {
               return Future.succeededFuture();
           } else {
               return Future.failedFuture("Failed to close connector");
           }
        });
    }

}
