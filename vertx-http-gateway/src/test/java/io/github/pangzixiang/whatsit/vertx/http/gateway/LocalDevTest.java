package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.ProxyRequestContext;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnector;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnectorOptions;
import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.EventHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LocalDevTest {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        VertxHttpGatewayOptions vertxHttpGatewayOptions = new VertxHttpGatewayOptions();
        Router customRouter = Router.router(vertx);
        customRouter.route("/test").handler(rc -> rc.response().end("test"));
        customRouter.route().failureHandler(rc -> {
           if (rc.statusCode() == 404) {
               rc.response().setStatusCode(rc.statusCode()).end("<h1>Oops! NOT FOUND!</h1>");
           } else {
               rc.next();
           }
        });
        vertx.deployVerticle(new VertxHttpGatewayMainVerticle(vertxHttpGatewayOptions)
                .withCustomRouter(customRouter)
                .withEventHandler(new EventHandler() {
            @Override
            public Future<Void> beforeEstablishConnection(RoutingContext routingContext) {
                log.info("beforeEstablishConnection");
                return Future.succeededFuture();
            }

            @Override
            public void afterEstablishConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("afterEstablishConnection");
            }

            @Override
            public void beforeRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("beforeRemoveConnection");
            }

            @Override
            public void afterRemoveConnection(String serviceName, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("afterRemoveConnection");
            }

            @Override
            public Future<Void> beforeProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("beforeProxyRequest");
                return Future.succeededFuture();
            }

            @Override
            public void afterProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance) {
                log.info("afterProxyRequest");
            }
        })).onSuccess(s -> log.info("Succeeded to start Http vertx gateway")).onFailure(throwable -> log.error("Failed to start vertx http gateway", throwable));

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

        router.get("/test-service/timeout").handler(rc -> {
           vertx.setTimer(TimeUnit.SECONDS.toMillis(20), l -> {
               rc.end("test");
           });

           rc.response().closeHandler(unused -> {
               log.info("connection closed");
           });
        });

        router.get("/test-service").handler(rc -> {
           rc.response().sendFile("test.html");
        });

        router.route("/test-service/ws").handler(rc -> {
           rc.request().toWebSocket().onSuccess(ws -> {
               log.info("ws connected");
               ws.handler(buffer -> {
                   log.info("ws received [{}]", buffer);
               });

               long id = vertx.setPeriodic(0, 3000, l -> {
                  ws.writeBinaryMessage(Buffer.buffer(String.valueOf(System.currentTimeMillis())).appendByte((byte) 1));
               });

               long id2 = vertx.setPeriodic(1000, 3000, l -> {
                   ws.writeTextMessage(String.valueOf(System.currentTimeMillis()));
               });

               ws.closeHandler(unused -> {
                  log.info("ws closed");
                  vertx.cancelTimer(id);
                  vertx.cancelTimer(id2);
               });
           }).onFailure(rc::fail);
        });

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(0)
                .onSuccess(httpServer -> {
                    log.info("Test Service started at {}", httpServer.actualPort());
                    VertxHttpGatewayConnectorOptions vertxHttpGatewayConnectorOptions =
                            new VertxHttpGatewayConnectorOptions("test-service", httpServer.actualPort(), "localhost", 9090);
                    VertxHttpGatewayConnector vertxHttpGatewayConnector = new VertxHttpGatewayConnector(vertx, vertxHttpGatewayConnectorOptions).withEventHandler(new io.github.pangzixiang.whatsit.vertx.http.gateway.connector.handler.EventHandler() {
                        @Override
                        public Future<WebSocketConnectOptions> beforeEstablishConnection(WebSocketConnectOptions webSocketConnectOptions) {
                            log.info("beforeEstablishConnection {}", webSocketConnectOptions);
                            return Future.succeededFuture(webSocketConnectOptions);
                        }

                        @Override
                        public void afterEstablishConnection(WebSocket webSocket) {
                            log.info("afterEstablishConnection {}", webSocket.headers());
                        }

                        @Override
                        public void beforeDisconnect() {
                            log.info("beforeDisconnect");
                        }

                        @Override
                        public void afterDisconnect(boolean succeeded, Throwable cause) {
                            log.info("AfterDisconnect {}", succeeded, cause);
                        }

                        @Override
                        public Future<ProxyRequestContext> beforeProxyRequest(ProxyRequestContext proxyRequestContext) {
                            log.info("BeforeProxyRequest {}", proxyRequestContext);
                            return Future.succeededFuture(proxyRequestContext);
                        }

                        @Override
                        public void afterProxyRequest(ProxyRequestContext proxyRequestContext) {
                            log.info("afterProxyRequest {}",proxyRequestContext.getHttpClientResponse().headers());
                        }

                    });
                    vertxHttpGatewayConnector.connect();

//                    vertx.setTimer(5000, l -> {
//                       vertxHttpGatewayConnector.close();
//                    });

                }).onFailure(throwable -> log.error(throwable.getMessage(), throwable));
    }
}
