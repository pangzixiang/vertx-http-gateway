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
class ListenerServerVerticle extends AbstractVerticle {
    private final VertxHttpGatewayOptions vertxHttpGatewayOptions;
    private final EventHandler eventHandler;
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Router router = Router.router(getVertx());

        ListenerServerHandler listenerServerHandler = new ListenerServerHandler(eventHandler);

        Future<String> deployFuture = getVertx().deployVerticle(listenerServerHandler);

        router.route(vertxHttpGatewayOptions.getListenerServerRegisterPath()).handler(listenerServerHandler);

        Future<HttpServer> httpServerFuture = getVertx().createHttpServer(vertxHttpGatewayOptions.getListenerServerOptions())
                .requestHandler(router)
                .listen(vertxHttpGatewayOptions.getListenerServerPort())
                .onSuccess(httpServer -> log.info("Listener Server Started at {}", httpServer.actualPort()))
                .onFailure(throwable -> log.error("Failed to start Listener Server", throwable));

        Future.all(deployFuture, httpServerFuture)
                .onSuccess(unused -> startPromise.complete())
                .onFailure(startPromise::fail);
    }


}
