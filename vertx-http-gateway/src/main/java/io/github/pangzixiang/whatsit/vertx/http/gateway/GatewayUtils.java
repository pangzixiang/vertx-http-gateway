package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;
import io.vertx.core.shareddata.Lock;
import lombok.experimental.UtilityClass;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@UtilityClass
class GatewayUtils {
    private static final String CONNECTOR_INFO_MAP_NAME = UUID.randomUUID().toString();

    private static final AtomicLong atomicLong = new AtomicLong(0L);

    public static <K, V> LocalMap<K, V> getConnectorInfoMap(Vertx vertx) {
        return vertx.sharedData().getLocalMap(CONNECTOR_INFO_MAP_NAME);
    }

    public static Future<Lock> getConnectorInfoMapLock(Vertx vertx) {
        return vertx.sharedData().getLocalLock(CONNECTOR_INFO_MAP_NAME);
    }

    public static byte generateRequestId() {
        return (byte) atomicLong.incrementAndGet();
    }
}
