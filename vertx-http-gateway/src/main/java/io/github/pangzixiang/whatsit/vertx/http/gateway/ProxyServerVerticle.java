package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.EventHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AllArgsConstructor
class ProxyServerVerticle extends AbstractVerticle {

    private final VertxHttpGatewayOptions vertxHttpGatewayOptions;
    private final EventHandler eventHandler;
    private final Router customRouter;

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Router router = Router.router(getVertx());

        if (customRouter != null) {
            router.route().subRouter(customRouter);
        }

        ProxyServerHandler proxyServerHandler = new ProxyServerHandler(vertxHttpGatewayOptions, eventHandler);
        Future<String> deployVerticle = getVertx().deployVerticle(proxyServerHandler);

        router.route("/:base*").handler(proxyServerHandler);

        Future<HttpServer> httpServerFuture = getVertx().createHttpServer()
                .requestHandler(router)
                .listen(vertxHttpGatewayOptions.getProxyServerPort())
                .onSuccess(httpServer -> {
                    log.info("Proxy Server Started at {}", httpServer.actualPort());
                }).onFailure(throwable -> {
                    log.error("Failed to start Proxy Server", throwable);
                });

        Future.all(deployVerticle, httpServerFuture)
                .onSuccess(unused -> startPromise.complete())
                .onFailure(startPromise::fail);
    }


}
