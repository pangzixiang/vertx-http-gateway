package io.github.pangzixiang.whatsit.vertx.http.gateway.common;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import lombok.Getter;

/**
 * <p>
 * === request chunk body ===
 * http/1.1 GET /test-service/test
 * Content-Type: application/json
 * [other headers]
 * =====================
 * </p>
 * <p>
 * === response chunk body ===
 * http/1.1 200 OK
 * Content-Type: application/json
 * [other headers]
 * </p>
 */
@Getter
public class RequestMessageInfoChunkBody {
    private final HttpVersion httpVersion;
    private final HttpMethod httpMethod;
    private final String uri;
    private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    public RequestMessageInfoChunkBody(String requestInfoChunkBody) {
        String[] lines = requestInfoChunkBody.split(Utils.lineSeparator);
        String[] firstLine = lines[0].split(" ");
        this.httpVersion = Utils.httpVersionParser(firstLine[0]);
        this.httpMethod = HttpMethod.valueOf(firstLine[1]);
        this.uri = firstLine[2];
        for (int i = 1; i < lines.length; i++) {
            String[] line = lines[i].split(":");
            headers.add(line[0], line[1]);
        }
    }

    public static String build(HttpVersion httpVersion, HttpMethod httpMethod, String uri, MultiMap headers) {
        String firstLine = httpVersion.alpnName() + " " + httpMethod.name() + " " + uri + Utils.lineSeparator;
        StringBuilder result = new StringBuilder(firstLine);
        headers.forEach((key, value) -> {
            result.append(key).append(":").append(value).append(Utils.lineSeparator);
        });
        return result.toString();
    }
}
