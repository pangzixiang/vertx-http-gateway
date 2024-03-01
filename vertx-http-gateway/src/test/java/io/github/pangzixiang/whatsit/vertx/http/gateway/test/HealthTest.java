package io.github.pangzixiang.whatsit.vertx.http.gateway.test;

import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayMainVerticle;
import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayOptions;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static io.github.pangzixiang.whatsit.vertx.http.gateway.test.TestUtils.connectToGateway;
import static io.github.pangzixiang.whatsit.vertx.http.gateway.test.TestUtils.getFreePorts;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class HealthTest {
    private int gatewayRegisterPort;
    private VertxHttpGatewayMainVerticle vertxHttpGatewayMainVerticle;

    @BeforeAll
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
        List<Integer> ports = getFreePorts();
        gatewayRegisterPort = ports.getFirst();
        int gatewayProxyPort = ports.getLast();
        VertxHttpGatewayOptions vertxHttpGatewayOptions = new VertxHttpGatewayOptions()
                .setProxyTimeout(5000)
                .setProxyServerPort(gatewayProxyPort)
                .setListenerServerPort(gatewayRegisterPort);

        vertxHttpGatewayMainVerticle = new VertxHttpGatewayMainVerticle(vertxHttpGatewayOptions);

        vertx.deployVerticle(vertxHttpGatewayMainVerticle).onComplete(vertxTestContext.succeeding(id -> {
            vertx.eventBus().consumer("gateway-shutdown").handler(message -> vertx.undeploy(id).onComplete(unused -> message.reply("ok")));
            vertxTestContext.completeNow();
        }));
    }

    @Test
    void testGetConnectorHealth(Vertx vertx, VertxTestContext vertxTestContext) {
        Checkpoint checkpoint = vertxTestContext.checkpoint(3);
        Router router = Router.router(vertx);
        vertx.createHttpServer().requestHandler(router).listen(0)
                .compose(httpServer -> connectToGateway(vertx, httpServer.actualPort(), "test-service", gatewayRegisterPort))
                .compose(vertxHttpGatewayConnector -> vertxHttpGatewayConnector.getHealthChecks().checkStatus().map(
                        checkResult -> {
                            Assertions.assertTrue(checkResult.getUp());
                            checkpoint.flag();
                            return vertxHttpGatewayConnector;
                        }
                ))
                .compose(vertxHttpGatewayConnector -> vertx.eventBus().request("gateway-shutdown", null).map(vertxHttpGatewayConnector))
                .onComplete(vertxTestContext.succeeding(vertxHttpGatewayConnector -> {
                    vertx.setTimer(1000, l -> {
                        vertxHttpGatewayConnector.getHealthChecks().checkStatus().onComplete(vertxTestContext.succeeding(checkResult -> {
                            Assertions.assertFalse(checkResult.getUp());
                            checkpoint.flag();
                        }));
                    });

                    vertx.setTimer(2000, l -> {
                        vertx.deployVerticle(vertxHttpGatewayMainVerticle).onComplete(vertxTestContext.succeeding(unused -> {
                            vertx.setTimer(5000, l2 -> {
                                vertxHttpGatewayConnector.getHealthChecks().checkStatus().onComplete(vertxTestContext.succeeding(checkResult -> {
                                    Assertions.assertTrue(checkResult.getUp());
                                    checkpoint.flag();
                                }));
                            });
                        }));
                    });
                }));
    }

}
