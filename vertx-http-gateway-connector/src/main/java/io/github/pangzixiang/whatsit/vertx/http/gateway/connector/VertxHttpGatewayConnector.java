package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.AllArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
public class VertxHttpGatewayConnector {
    private final Vertx vertx;
    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;
    private static final String CLOSE_EVENT_BUS_ID = UUID.randomUUID().toString();

    public Future<Void> connect() {
        return vertx.deployVerticle(() -> new VertxHttpGatewayConnectorMainVerticle(vertxHttpGatewayConnectorOptions), new DeploymentOptions().setInstances(vertxHttpGatewayConnectorOptions.getInstance())).onSuccess(id -> {
            vertx.eventBus().consumer(CLOSE_EVENT_BUS_ID).handler(message -> vertx.undeploy(id).onComplete(result -> message.reply(result.succeeded())));
        }).mapEmpty();
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
