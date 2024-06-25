package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.EventHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
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
    public void start() throws Exception {
        Router router = Router.router(getVertx());

        if (customRouter != null) {
            router.route().subRouter(customRouter);
        }

        ProxyServerHandler proxyServerHandler = new ProxyServerHandler(vertxHttpGatewayOptions, eventHandler);
        Future.await(getVertx().deployVerticle(proxyServerHandler, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)));

        router.route("/:base*").handler(proxyServerHandler);

        HttpServer httpServer = Future.await(getVertx().createHttpServer(vertxHttpGatewayOptions.getProxyServerOptions())
                .requestHandler(router)
                .listen(vertxHttpGatewayOptions.getProxyServerPort()));

        log.debug("Proxy Server Started at {}", httpServer.actualPort());
    }


}
