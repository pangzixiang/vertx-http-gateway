package io.github.pangzixiang.whatsit.vertx.http.gateway.common;

import io.vertx.core.MultiMap;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class ResponseMessageInfoChunkBody {
    private final String statusMessage;
    private final int statusCode;
    private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    /**
     * 200 OK
     * [headers]
     */
    public ResponseMessageInfoChunkBody(String responseInfoChunkBody) {
        String[] chunkLines = responseInfoChunkBody.split(System.lineSeparator());
        String[] firstLine = chunkLines[0].split(" ");
        this.statusMessage = String.join(" ", Arrays.asList(firstLine).subList(1, firstLine.length));
        this.statusCode = Integer.parseInt(firstLine[0]);
        for (int i = 1; i < chunkLines.length; i++) {
            String[] line = chunkLines[i].split(":");
            this.headers.add(line[0], line[1]);
        }
    }

    /**
     * 200 OK
     * [headers]
     */
    public static String build(String statusMessage, int statusCode, MultiMap headers) {
        String firstLine = statusCode + " " + statusMessage + System.lineSeparator();
        StringBuilder result = new StringBuilder(firstLine);
        headers.forEach((key, value) -> {
            result.append(key).append(":").append(value).append(System.lineSeparator());
        });

        return result.toString();
    }
}
