package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnector;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnectorOptions;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class LocalDevHttpsClientTest {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);
        router.get("/test-https/get").handler(rc -> rc.response().end("ok"));

        router.get("/test-https/sse").handler(rc -> {
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

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(0)
                .onSuccess(httpServer -> {
                    log.info("Succeeded to start test server in port {}", httpServer.actualPort());
                    HttpClientOptions registerClientOptions = new HttpClientOptions()
                            .setSsl(true).setTrustAll(true)
                            ;
                    VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions =
                            new VertxHttpGatewayConnectorOptions("test-https", httpServer.actualPort(), "localhost", 9090)
                                    .setRegisterClientOptions(registerClientOptions);

                    VertxHttpGatewayConnector vertxHttpGatewayConnector = new VertxHttpGatewayConnector(vertx, vertxHttpGatewayConnectorOptions);
                    vertxHttpGatewayConnector.connect();
                }).onFailure(throwable -> log.error("Failed to start test server", throwable));
    }
}
