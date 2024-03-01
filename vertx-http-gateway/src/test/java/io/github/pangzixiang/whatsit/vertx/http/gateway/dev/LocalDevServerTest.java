package io.github.pangzixiang.whatsit.vertx.http.gateway.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.github.pangzixiang.whatsit.vertx.http.gateway.ServiceRegistrationInstance;
import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayContext;
import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayMainVerticle;
import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayOptions;
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
import io.vertx.core.http.*;
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
public class LocalDevServerTest {
    public static void main(String[] args) {
        ObjectMapper objectMapper = DatabindCodec.mapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        LocalDateTimeSerializer localDateTimeSerializer = new LocalDateTimeSerializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        LocalDateTimeDeserializer localDateTimeDeserializer = new LocalDateTimeDeserializer(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        LocalDateSerializer localDateSerializer = new LocalDateSerializer(DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDateDeserializer localDateDeserializer = new LocalDateDeserializer(DateTimeFormatter.ISO_LOCAL_DATE);
        javaTimeModule.addSerializer(LocalDateTime.class, localDateTimeSerializer);
        javaTimeModule.addSerializer(LocalDate.class, localDateSerializer);
        javaTimeModule.addDeserializer(LocalDateTime.class, localDateTimeDeserializer);
        javaTimeModule.addDeserializer(LocalDate.class, localDateDeserializer);
        objectMapper.registerModule(javaTimeModule);

        Vertx vertx = Vertx.vertx();
        VertxHttpGatewayOptions vertxHttpGatewayOptions = new VertxHttpGatewayOptions();
        Router customRouter = Router.router(vertx);
        customRouter.route("/test").handler(rc -> rc.response().end("test"));
        customRouter.route("/connectors").handler(rc -> rc.response().end(Json.encode(VertxHttpGatewayContext.getInstance(vertx).getConnectorServiceDetails())));
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
                    public Future<MultiMap> processProxyResponseHeaders(MultiMap responseHeaders) {
                        return Future.succeededFuture(responseHeaders.add("test2", "test2"));
                    }

                    @Override
                    public void afterProxyRequest(long requestId, HttpServerRequest httpServerRequest, ServiceRegistrationInstance serviceRegistrationInstance) {
                        log.info("afterProxyRequest");
                    }
                })).onSuccess(s -> log.info("Succeeded to start Http vertx gateway")).onFailure(throwable -> log.error("Failed to start vertx http gateway", throwable));
    }
}
