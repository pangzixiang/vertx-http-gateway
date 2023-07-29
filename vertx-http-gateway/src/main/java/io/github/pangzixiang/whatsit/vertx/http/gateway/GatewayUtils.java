package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.Counter;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Lock;
import lombok.experimental.UtilityClass;

import java.util.UUID;

@UtilityClass
class GatewayUtils {
    private static final String CONNECTOR_INFO_MAP_NAME = UUID.randomUUID().toString();

    private static final String REQUEST_ID_COUNTER_NAME = "REQUEST_ID_COUNTER";

    public static <K, V> LocalMap<K, V> getConnectorInfoMap(Vertx vertx) {
        return vertx.sharedData().getLocalMap(CONNECTOR_INFO_MAP_NAME);
    }

    public static Future<Lock> getConnectorInfoMapLock(Vertx vertx) {
        return vertx.sharedData().getLocalLock(CONNECTOR_INFO_MAP_NAME);
    }

    public static Future<Long> generateRequestId(Vertx vertx) {
        return vertx.sharedData().getCounter(REQUEST_ID_COUNTER_NAME)
                .compose(Counter::incrementAndGet);
    }
}
