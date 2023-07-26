package io.github.pangzixiang.whatsit.vertx.http.gateway.algorithm;

import io.github.pangzixiang.whatsit.vertx.http.gateway.ServiceRegistrationInfo;
import io.github.pangzixiang.whatsit.vertx.http.gateway.ServiceRegistrationInstance;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class RoundRobin implements LoadBalanceAlgorithm {
    private static final String ROUND_ROBIN_COUNTER_NAME = RoundRobin.class.getSimpleName() + UUID.randomUUID();

    @Override
    public Future<ServiceRegistrationInstance> handle(Vertx vertx, HttpServerRequest httpServerRequest, ServiceRegistrationInfo serviceRegistrationInfo) {
        return vertx.sharedData().getLocalCounter(ROUND_ROBIN_COUNTER_NAME).compose(counter -> {
            return counter.getAndIncrement().compose(now -> {
                int index = (int) ((now + 1) % serviceRegistrationInfo.getServiceRegistrationInstances().size());
                return Future.succeededFuture(serviceRegistrationInfo.getServiceRegistrationInstances().get(index));
            }, throwable -> {
                log.error("Failed to get counter for Round Robin, hence default return the first origin", throwable);
                return Future.succeededFuture(serviceRegistrationInfo.getServiceRegistrationInstances().get(0));
            });
        }, throwable -> {
            log.error("Failed to get counter for Round Robin, hence default return the first origin", throwable);
            return Future.succeededFuture(serviceRegistrationInfo.getServiceRegistrationInstances().get(0));
        });
    }
}
