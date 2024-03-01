package io.github.pangzixiang.whatsit.vertx.http.gateway.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayMainVerticle;
import io.github.pangzixiang.whatsit.vertx.http.gateway.VertxHttpGatewayOptions;
import io.github.pangzixiang.whatsit.vertx.http.gateway.connector.VertxHttpGatewayConnector;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static io.github.pangzixiang.whatsit.vertx.http.gateway.test.TestUtils.connectToGateway;
import static io.github.pangzixiang.whatsit.vertx.http.gateway.test.TestUtils.getFreePorts;

@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class End2EndHttp2Test {

    private int gatewayRegisterPort;
    private int gatewayProxyPort;
    private Router gatewayCustomRouter;
    private Router testServiceRouter;
    private static final String SERVICE_NAME = "test-service";

    @BeforeAll
    public void setUp(Vertx vertx, VertxTestContext vertxTestContext) {
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

        List<Integer> ports = getFreePorts();
        gatewayRegisterPort = ports.getFirst();
        gatewayProxyPort = ports.getLast();

        gatewayCustomRouter = Router.router(vertx);
        Router getewayRegisterCustomRouter = Router.router(vertx);
        testServiceRouter = Router.router(vertx);

        SelfSignedCertificate selfSignedCertificate = SelfSignedCertificate.create();
        HttpServerOptions sslOptions = new HttpServerOptions()
                .setSsl(true)
                .setKeyCertOptions(selfSignedCertificate.keyCertOptions())
                .setTrustOptions(selfSignedCertificate.trustOptions());
        HttpServerOptions sslOptions2 = new HttpServerOptions()
                .setSsl(true)
                .setKeyCertOptions(selfSignedCertificate.keyCertOptions())
                .setTrustOptions(selfSignedCertificate.trustOptions())
                .setUseAlpn(true)
                ;

        VertxHttpGatewayOptions vertxHttpGatewayOptions = new VertxHttpGatewayOptions()
                .setProxyTimeout(5000)
                .setListenerServerOptions(sslOptions)
                .setProxyServerOptions(sslOptions2)
                .setProxyServerPort(gatewayProxyPort)
                .setListenerServerPort(gatewayRegisterPort);

        vertx.deployVerticle(new VertxHttpGatewayMainVerticle(vertxHttpGatewayOptions)
                .withCustomRouter(gatewayCustomRouter)
                .withCustomListenerRouter(getewayRegisterCustomRouter)
        ).compose(unused -> setUpTestServiceAndConnectGateway(vertx, testServiceRouter)).onComplete(vertxTestContext.succeeding(unused -> {
            vertxTestContext.completeNow();
        }));
    }

    @ParameterizedTest
    @EnumSource(value = TestHttpMethod.class)
    void testGatewayCustomRouter(TestHttpMethod httpMethod, Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setVerifyHost(false).setUseAlpn(true).setAlpnVersions(List.of(HttpVersion.HTTP_2)).setTrustAll(true));
        gatewayCustomRouter.route(HttpMethod.valueOf(httpMethod.name()), "/" + httpMethod).handler(routingContext -> routingContext.response().putHeader("t1", "test").end("ok"));
        webClient.request(HttpMethod.valueOf(httpMethod.name()), gatewayProxyPort, "localhost", "/" + httpMethod).send().onComplete(vertxTestContext.succeeding(bufferHttpResponse -> {
            Assertions.assertEquals(HttpVersion.HTTP_2, bufferHttpResponse.version());
            Assertions.assertEquals(200, bufferHttpResponse.statusCode());
            Assertions.assertEquals("ok", bufferHttpResponse.bodyAsString());
            Assertions.assertEquals("test", bufferHttpResponse.getHeader("t1"));
            vertxTestContext.completeNow();
        }));
    }

    @ParameterizedTest
    @EnumSource(value = TestHttpMethod.class)
    void testRequest(TestHttpMethod httpMethod, Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setVerifyHost(false).setUseAlpn(true).setAlpnVersions(List.of(HttpVersion.HTTP_2)).setTrustAll(true));
        testServiceRouter.route(HttpMethod.valueOf(httpMethod.name()), "/" + SERVICE_NAME + "/" + httpMethod).handler(routingContext -> routingContext.response().putHeader("t1", "test").end("ok"));
        webClient.request(HttpMethod.valueOf(httpMethod.name()), gatewayProxyPort, "localhost", "/" + SERVICE_NAME + "/" + httpMethod).send().onComplete(vertxTestContext.succeeding(bufferHttpResponse -> {
            Assertions.assertEquals(HttpVersion.HTTP_2, bufferHttpResponse.version());
            Assertions.assertEquals(200, bufferHttpResponse.statusCode());
            Assertions.assertEquals("ok", bufferHttpResponse.bodyAsString());
            Assertions.assertEquals("test", bufferHttpResponse.getHeader("t1"));
            vertxTestContext.completeNow();
        }));
    }

    @Test
    void testSSERequest(Vertx vertx, VertxTestContext vertxTestContext) {
        HttpClient httpClient = vertx.createHttpClient(new HttpClientOptions().setSsl(true).setVerifyHost(false).setUseAlpn(true).setAlpnVersions(List.of(HttpVersion.HTTP_2)).setTrustAll(true));

        testServiceRouter.get("/" + SERVICE_NAME + "/sse").handler(routingContext -> {
            HttpServerResponse httpResponse = routingContext.response();
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

        httpClient.request(HttpMethod.GET, gatewayProxyPort, "localhost", "/" + SERVICE_NAME + "/sse").compose(HttpClientRequest::send).onComplete(vertxTestContext.succeeding(httpClientResponse -> {
            Assertions.assertEquals(HttpVersion.HTTP_2, httpClientResponse.version());
            Assertions.assertEquals(200, httpClientResponse.statusCode());
            Buffer b = Buffer.buffer();
            httpClientResponse.handler(buffer -> {
                log.info("Received sse response {}", buffer);
                b.appendBuffer(buffer);
            });
            vertx.setTimer(5000, l -> {
                Assertions.assertTrue(StringUtils.isNotEmpty(b.toString()));
                vertxTestContext.completeNow();
            });
        }));
    }

    @Test
    void testStaticFileRequest(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setVerifyHost(false).setUseAlpn(true).setAlpnVersions(List.of(HttpVersion.HTTP_2)).setTrustAll(true));
        testServiceRouter.get("/" + SERVICE_NAME + "/html").handler(routingContext -> routingContext.response().sendFile("test.html"));
        webClient.get(gatewayProxyPort, "localhost", "/" + SERVICE_NAME + "/html").send().onComplete(vertxTestContext.succeeding(bufferHttpResponse -> {
            Assertions.assertEquals(HttpVersion.HTTP_2, bufferHttpResponse.version());
            Assertions.assertEquals(vertx.fileSystem().readFileBlocking("test.html"), bufferHttpResponse.body());
            Assertions.assertEquals(200, bufferHttpResponse.statusCode());
            vertxTestContext.completeNow();
        }));
    }

    @Test
    void testRequestWithBody(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setVerifyHost(false).setUseAlpn(true).setAlpnVersions(List.of(HttpVersion.HTTP_2)).setTrustAll(true));
        testServiceRouter.post("/" + SERVICE_NAME + "/post").handler(routingContext -> {
            String body = routingContext.body().asString();
            if (body.equals("test")) {
                routingContext.response().putHeader("t1", "test").end("ok");
            } else {
                routingContext.response().setStatusCode(400).end();
            }
        });
        webClient.post(gatewayProxyPort, "localhost", "/" + SERVICE_NAME + "/post").sendBuffer(Buffer.buffer("test")).onComplete(vertxTestContext.succeeding(bufferHttpResponse -> {
            Assertions.assertEquals(HttpVersion.HTTP_2, bufferHttpResponse.version());
            Assertions.assertEquals(200, bufferHttpResponse.statusCode());
            Assertions.assertEquals("ok", bufferHttpResponse.bodyAsString());
            Assertions.assertEquals("test", bufferHttpResponse.getHeader("t1"));
            vertxTestContext.completeNow();
        }));
    }

    @Test
    void testRequestWithQueryParam(Vertx vertx, VertxTestContext vertxTestContext) {
        WebClient webClient = WebClient.create(vertx, new WebClientOptions().setSsl(true).setVerifyHost(false).setUseAlpn(true).setAlpnVersions(List.of(HttpVersion.HTTP_2)).setTrustAll(true));
        testServiceRouter.get("/" + SERVICE_NAME + "/queryParam").handler(routingContext -> {
            String test = routingContext.queryParam("test").getFirst();
            String header = routingContext.queryParam("header").getFirst();
            if (StringUtils.isNotEmpty(test)) {
                routingContext.response().putHeader("t1", header).end(test);
            } else {
                routingContext.response().setStatusCode(400).end();
            }
        });
        webClient.get(gatewayProxyPort, "localhost", "/" + SERVICE_NAME + "/queryParam").addQueryParam("test", "test").addQueryParam("header", "header").send().onComplete(vertxTestContext.succeeding(bufferHttpResponse -> {
            Assertions.assertEquals(200, bufferHttpResponse.statusCode());
            Assertions.assertEquals(HttpVersion.HTTP_2, bufferHttpResponse.version());
            Assertions.assertEquals("test", bufferHttpResponse.bodyAsString());
            Assertions.assertEquals("header", bufferHttpResponse.getHeader("t1"));
            vertxTestContext.completeNow();
        }));
    }

    private Future<VertxHttpGatewayConnector> setUpTestServiceAndConnectGateway(Vertx vertx, Router router) {
        router.route().handler(BodyHandler.create());
        router.route().handler(routingContext -> {
            log.info("receive request for {}", routingContext.normalizedPath());
            routingContext.next();
        });
        return vertx.createHttpServer().requestHandler(router).listen(0).compose(httpServer -> {
            log.info("Test Service started at {}", httpServer.actualPort());
            return connectToGateway(vertx, httpServer.actualPort(), SERVICE_NAME, gatewayRegisterPort, true);
        });
    }



    private enum TestHttpMethod {
        OPTIONS,
        GET,
//        HEAD,
        POST,
        PUT,
        DELETE,
        TRACE,
//        CONNECT,
        PATCH,
        PROPFIND,
        PROPPATCH,
        MKCOL,
        COPY,
        MOVE,
        LOCK,
        UNLOCK,
        MKCALENDAR,
        VERSION_CONTROL,
        REPORT,
        CHECKOUT,
        CHECKIN,
        UNCHECKOUT,
        MKWORKSPACE,
        UPDATE,
        LABEL,
        MERGE,
        BASELINE_CONTROL,
        MKACTIVITY,
        ORDERPATCH,
        ACL,
        SEARCH,
    }
}
