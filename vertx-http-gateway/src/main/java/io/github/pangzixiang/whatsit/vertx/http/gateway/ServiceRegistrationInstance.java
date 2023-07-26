package io.github.pangzixiang.whatsit.vertx.http.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ServiceRegistrationInstance {
    private String remoteAddress;
    private String remotePort;
    private String instanceId;
    private final String eventBusAddress = UUID.randomUUID().toString();
}
