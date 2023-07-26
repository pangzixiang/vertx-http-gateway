package io.github.pangzixiang.whatsit.vertx.http.gateway.common;

import io.vertx.core.MultiMap;
import lombok.Getter;

@Getter
public class ResponseMessageInfoChunkBody {
    private final String statusMessage;
    private final int statusCode;
    private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();
    public ResponseMessageInfoChunkBody(String responseInfoChunkBody) {
        String[] chunkLines = responseInfoChunkBody.split(System.lineSeparator());
        this.statusMessage = chunkLines[0];
        this.statusCode = Integer.parseInt(chunkLines[1]);
        for (int i = 2; i < chunkLines.length; i++) {
            String[] line = chunkLines[i].split(":");
            this.headers.add(line[0], line[1]);
        }
    }

    public static String build(String statusMessage, int statusCode, MultiMap headers) {
        String firstLine = statusMessage + System.lineSeparator() + statusCode + System.lineSeparator();
        StringBuilder result = new StringBuilder(firstLine);
        headers.forEach((key, value) -> {
            result.append(key).append(":").append(value).append(System.lineSeparator());
        });

        return result.toString();
    }
}
