package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.DefaultEventHandler;
import io.github.pangzixiang.whatsit.vertx.http.gateway.handler.EventHandler;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.ext.web.Router;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VertxHttpGatewayMainVerticle extends AbstractVerticle {

    private final VertxHttpGatewayOptions vertxHttpGatewayOptions;

    private EventHandler eventHandler;

    private Router customRouter;

    private Router customListenerRouter;

    public VertxHttpGatewayMainVerticle(VertxHttpGatewayOptions vertxHttpGatewayOptions) {
        this.vertxHttpGatewayOptions = vertxHttpGatewayOptions;
        this.eventHandler = new DefaultEventHandler();
        this.customRouter = null;
        this.customListenerRouter = null;
    }

    public VertxHttpGatewayMainVerticle() {
        this(new VertxHttpGatewayOptions());
    }

    public VertxHttpGatewayMainVerticle withEventHandler(EventHandler eventHandler) {
        this.eventHandler = eventHandler;
        return this;
    }

    public VertxHttpGatewayMainVerticle withCustomRouter(Router customRouter) {
        this.customRouter = customRouter;
        return this;
    }

    public VertxHttpGatewayMainVerticle withCustomListenerRouter(Router customListenerRouter) {
        this.customListenerRouter = customListenerRouter;
        return this;
    }

    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        Future<String> listenerServerFuture = getVertx().deployVerticle(() -> new ListenerServerVerticle(vertxHttpGatewayOptions, eventHandler, customListenerRouter),
                new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD).setInstances(vertxHttpGatewayOptions.getListenerServerInstance()));

        listenerServerFuture.compose(unused -> getVertx().deployVerticle(() -> new ProxyServerVerticle(vertxHttpGatewayOptions, eventHandler, customRouter),
                new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD).setInstances(vertxHttpGatewayOptions.getProxyServerInstance()))).onSuccess(unused -> startPromise.complete()).onFailure(startPromise::fail);
    }
}
