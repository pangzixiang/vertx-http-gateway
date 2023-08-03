package io.github.pangzixiang.whatsit.vertx.http.gateway.common;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import lombok.Getter;

/**
 * <p>
 * === request chunk body ===
 * GET /test-service/test
 * Content-Type: application/json
 * [other headers]
 * =====================
 * </p>
 * <p>
 * === response chunk body ===
 * 200 OK
 * Content-Type: application/json
 * [other headers]
 * </p>
 */
@Getter
public class RequestMessageInfoChunkBody {
    private final HttpMethod httpMethod;
    private final String uri;
    private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    public RequestMessageInfoChunkBody(String requestInfoChunkBody) {
        String[] lines = requestInfoChunkBody.split(System.lineSeparator());
        String[] firstLine = lines[0].split(" ");
        this.httpMethod = HttpMethod.valueOf(firstLine[0]);
        this.uri = firstLine[1];
        for (int i = 1; i < lines.length; i++) {
            String[] line = lines[i].split(":");
            headers.add(line[0], line[1]);
        }
    }

    public static String build(HttpMethod httpMethod, String uri, String query, MultiMap headers) {
        String requestQuery = query == null ? "" : "?" + query;
        String firstLine = httpMethod.name() + " " + uri + requestQuery + System.lineSeparator();
        StringBuilder result = new StringBuilder(firstLine);
        headers.forEach((key, value) -> {
            result.append(key).append(":").append(value).append(System.lineSeparator());
        });
        return result.toString();
    }
}
