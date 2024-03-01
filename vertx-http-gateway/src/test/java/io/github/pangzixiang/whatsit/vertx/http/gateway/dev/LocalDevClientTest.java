package io.github.pangzixiang.whatsit.vertx.http.gateway.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.ProxyRequestContext;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnector;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnectorOptions;
import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.EventHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
public class LocalDevClientTest {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
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
                        public Future<MultiMap> processProxyResponseHeaders(MultiMap responseHeaders) {
                            return Future.succeededFuture(responseHeaders.add("test", "test"));
                        }

                        @Override
                        public void afterProxyRequest(ProxyRequestContext proxyRequestContext) {
                            log.info("afterProxyRequest {}",proxyRequestContext.getHttpClientResponse().headers());
                        }

                    });

                    vertx.setPeriodic(0, 1000, l -> {
                        vertxHttpGatewayConnector.getHealthChecks().checkStatus().onSuccess(result -> {
                            log.info(String.valueOf(result.getUp()));
                        }).onFailure(throwable -> log.error(throwable.getMessage(), throwable));
                    });

                    vertxHttpGatewayConnector.connect();

//                    vertx.setTimer(5000, l -> {
//                       vertxHttpGatewayConnector.close();
//                    });

                }).onFailure(throwable -> log.error(throwable.getMessage(), throwable));
    }
}
