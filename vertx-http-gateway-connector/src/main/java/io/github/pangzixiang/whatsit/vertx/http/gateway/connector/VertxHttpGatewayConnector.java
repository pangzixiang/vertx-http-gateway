package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class VertxHttpGatewayConnector {
    private final Vertx vertx;
    private final VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions;

    public Future<Void> connect() {
        return vertx.deployVerticle(() -> new VertxHttpGatewayConnectorMainVerticle(vertxHttpGatewayConnectorOptions),
                new DeploymentOptions().setInstances(vertxHttpGatewayConnectorOptions.getInstance())).mapEmpty();
    }

}
