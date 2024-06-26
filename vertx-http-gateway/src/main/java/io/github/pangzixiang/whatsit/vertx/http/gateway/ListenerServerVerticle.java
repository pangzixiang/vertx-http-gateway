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
class ListenerServerVerticle extends AbstractVerticle {
    private final VertxHttpGatewayOptions vertxHttpGatewayOptions;
    private final EventHandler eventHandler;
    private final Router customListenerRouter;
    @Override
    public void start() throws Exception {
        Router router = Router.router(getVertx());

        if (customListenerRouter != null) {
            router.route().subRouter(customListenerRouter);
        }

        ListenerServerHandler listenerServerHandler = new ListenerServerHandler(eventHandler);

        Future.await(getVertx().deployVerticle(listenerServerHandler, new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)));

        router.route(vertxHttpGatewayOptions.getListenerServerRegisterPath()).handler(listenerServerHandler);

        HttpServer httpServer = Future.await(getVertx().createHttpServer(vertxHttpGatewayOptions.getListenerServerOptions())
                .requestHandler(router)
                .listen(vertxHttpGatewayOptions.getListenerServerPort()));

        log.debug("Listener Server Started at {}", httpServer.actualPort());
    }


}
