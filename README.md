# vertx-http-gateway
[![Maven Central](https://img.shields.io/maven-central/v/io.github.pangzixiang.whatsit/vertx-http-gateway?logo=apachemaven&logoColor=red)](https://search.maven.org/artifact/io.github.pangzixiang.whatsit/vertx-http-gateway)
#### how to use
- Import the dependency
```xml
<dependency>
    <groupId>io.github.pangzixiang.whatsit</groupId>
    <artifactId>vertx-http-gateway</artifactId>
    <version>{latestVersion}</version>
</dependency>
```
- Deploy the Verticle 'VertxRouterVerticle'
```java
public class Main {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        VertxHttpGatewayOptions options = new VertxHttpGatewayOptions();
        vertx.deployVerticle(new VertxHttpGatewayMainVerticle(options));
    }
}
```

- Design your own load-balancing algorithm
```java
public class YourAlgorithm implements LoadBalanceAlgorithm {
    @Override
    public Future<ServiceRegistrationInstance> handle(Vertx vertx, HttpServerRequest httpServerRequest, ServiceRegistrationInfo serviceRegistrationInfo) {
        
    }
}

public class Main {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        VertxHttpGatewayOptions options = new VertxHttpGatewayOptions();
        options.setLoadBalanceAlgorithm(new YourAlgorithm());
        vertx.deployVerticle(new VertxHttpGatewayMainVerticle(options));
    }
}
```

- Target service to use vertx-http-gateway-connector to connect with Gateway
```xml
<dependency>
    <groupId>io.github.pangzixiang.whatsit</groupId>
    <artifactId>vertx-http-gateway-connector</artifactId>
    <version>{version}</version>
</dependency>
```
```java
public class Main {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        Router router = Router.router(vertx);

        router.get("/test-service/a").handler(rc -> {
            log.debug(rc.request().headers().toString());
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
                    new VertxHttpGatewayConnector(vertx, vertxHttpGatewayConnectorOptions).connect();
                }).onFailure(throwable -> log.error(throwable.getMessage(), throwable));
    }
}
```