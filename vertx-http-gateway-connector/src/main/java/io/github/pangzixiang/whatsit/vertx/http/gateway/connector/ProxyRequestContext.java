package io.github.pangzixiang.whatsit.vertx.http.gateway.connector;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ProxyRequestContext {
    private HttpMethod requestHttpMethod;
    private String requestUri;
    private MultiMap requestHeaders;
    private HttpVersion requestHttpVersion;
    private long requestId;
    private HttpClientResponse httpClientResponse;
}
