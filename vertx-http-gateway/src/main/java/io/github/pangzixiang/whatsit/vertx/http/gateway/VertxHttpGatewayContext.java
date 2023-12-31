package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.vertx.core.Vertx;
import io.vertx.core.shareddata.LocalMap;

import java.util.ArrayList;
import java.util.List;

/**
 * The type Vertx http gateway context.
 */
public class VertxHttpGatewayContext {

    private final Vertx vertx;

    private static VertxHttpGatewayContext vertxHttpGatewayContext;

    private VertxHttpGatewayContext(Vertx vertx) {
        this.vertx = vertx;
    }

    /**
     * Gets instance.
     *
     * @param vertx the vertx
     * @return the instance
     */
    public static VertxHttpGatewayContext getInstance(Vertx vertx) {
        if (vertxHttpGatewayContext == null) {
            vertxHttpGatewayContext = new VertxHttpGatewayContext(vertx);
        }
        return vertxHttpGatewayContext;
    }

    /**
     * Gets connector service details.
     *
     * @return the connector service details
     */
    public List<ConnectorServiceDetails> getConnectorServiceDetails() {
        List<ConnectorServiceDetails> connectorServiceDetails = new ArrayList<>();
        LocalMap<String, ServiceRegistrationInfo> serviceRegistrationInfoLocalMap = GatewayUtils.getConnectorInfoMap(vertx);
        serviceRegistrationInfoLocalMap.forEach((serviceName, info) -> {
            List<ConnectorServiceDetails.Instance> instances = info.getServiceRegistrationInstances().stream()
                    .map(serviceRegistrationInstance -> ConnectorServiceDetails.Instance.builder()
                            .instanceId(serviceRegistrationInstance.getInstanceId())
                            .remoteAddress(serviceRegistrationInstance.getRemoteAddress())
                            .remotePort(Integer.parseInt(serviceRegistrationInstance.getRemotePort()))
                            .connectTime(serviceRegistrationInstance.getConnectTime())
                            .build())
                    .toList();
            ConnectorServiceDetails details = ConnectorServiceDetails.builder()
                    .serviceName(serviceName)
                    .basePath("/" + serviceName)
                    .instances(instances)
                    .build();
            connectorServiceDetails.add(details);
        });

        return connectorServiceDetails;
    }
}
