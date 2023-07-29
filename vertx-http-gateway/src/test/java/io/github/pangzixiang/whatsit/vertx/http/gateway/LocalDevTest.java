package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnector;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnectorOptions;
import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.EventHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class LocalDevTest {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        VertxHttpGatewayOptions vertxHttpGatewayOptions = new VertxHttpGatewayOptions();
        vertxHttpGatewayOptions.setEventHandler(new EventHandler() {
            @Override
            public Future<Void> beforeEstablishConnection(RoutingContext routingContext) {
                log.info("beforeEstablishConnection");
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> afterEstablishConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("afterEstablishConnection");
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> beforeRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("beforeRemoveConnection");
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> afterRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("afterRemoveConnection");
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> beforeProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("beforeProxyRequest");
                return Future.succeededFuture();
            }

            @Override
            public Future<Void> afterProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("afterProxyRequest");
                return Future.succeededFuture();
            }
        });
        vertx.deployVerticle(new VertxHttpGatewayMainVerticle(vertxHttpGatewayOptions));

        Router router = Router.router(vertx);

        router.get("/test-service/a").handler(rc -> {
//            log.debug(rc.request().headers().toString());
            rc.response().end("a");
        });

        router.get("/test-service/sse").handler(rc -> {
            HttpServerResponse httpResponse = rc.response();
            httpResponse.putHeader(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.TEXT_EVENT_STREAM);
            httpResponse.putHeader(HttpHeaderNames.ACCEPT_CHARSET, StandardCharsets.UTF_8.name());
            httpResponse.putHeader(HttpHeaderNames.CACHE_CONTROL, HttpHeaderValues.NO_CACHE);
            httpResponse.putHeader(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            httpResponse.setChunked(true);

            long id = vertx.setPeriodic(1000, l -> {
                httpResponse.write("data: %s\n\n".formatted(System.currentTimeMillis()));
            });

            httpResponse.closeHandler(unused -> {
               log.info("connection closed");
               vertx.cancelTimer(id);
            });
        });

        router.get("/test-service").handler(rc -> {
           rc.response().sendFile("test.html");
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(0)
                .onSuccess(httpServer -> {
                    log.info("Test Service started at {}", httpServer.actualPort());
                    VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions =
                            new VertxHttpGatewayConnectorOptions("test-service", httpServer.actualPort(), "localhost", 9090);
                    VertxHttpGatewayConnector vertxHttpGatewayConnector = new VertxHttpGatewayConnector(vertx, vertxHttpGatewayConnectorOptions);
                    vertxHttpGatewayConnector.connect();

                    vertx.setTimer(5000, l -> {
                       vertxHttpGatewayConnector.close();
                    });

                }).onFailure(throwable -> log.error(throwable.getMessage(), throwable));
    }
}
