package io.github.pangzixiang.whatsit.vertx.http.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ConnectorServiceDetails {
    private String serviceName;
    private String basePath;
    private List<Instance> instances;

    @AllArgsConstructor
    @NoArgsConstructor
    @Data
    @Builder
    public static class Instance {
        private String instanceId;
        private String remoteAddress;
        private int remotePort;
    }
}
