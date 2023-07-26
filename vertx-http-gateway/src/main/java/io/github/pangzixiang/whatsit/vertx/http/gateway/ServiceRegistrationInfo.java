package io.github.pangzixiang.whatsit.vertx.http.gateway;

import io.vertx.core.shareddata.Shareable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ServiceRegistrationInfo implements Shareable {
    private String basePath;
    private final List<ServiceRegistrationInstance> serviceRegistrationInstances = new ArrayList<>();

    public boolean addTargetServer(ServiceRegistrationInstance serviceRegistrationInstance) {
        return this.serviceRegistrationInstances.add(serviceRegistrationInstance);
    }

    public boolean removeTargetServer(ServiceRegistrationInstance serviceRegistrationInstance) {
        return this.serviceRegistrationInstances.remove(serviceRegistrationInstance);
    }
}
