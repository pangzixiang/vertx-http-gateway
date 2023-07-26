package io.github.pangzixiang.whatsit.vertx.http.gateway.algorithm;

import io.github.pangzixiang.whatsit.vertx.http.gateway.ServiceRegistrationInfo;
import io.github.pangzixiang.whatsit.vertx.http.gateway.ServiceRegistrationInstance;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;

public interface LoadBalanceAlgorithm {
    Future<ServiceRegistrationInstance> handle(Vertx vertx, HttpServerRequest httpServerRequest, ServiceRegistrationInfo serviceRegistrationInfo);
}
