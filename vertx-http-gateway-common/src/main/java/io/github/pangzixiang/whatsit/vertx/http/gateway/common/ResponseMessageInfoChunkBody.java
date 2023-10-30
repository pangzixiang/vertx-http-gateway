package io.github.pangzixiang.whatsit.vertx.http.gateway.common;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpVersion;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class ResponseMessageInfoChunkBody {
    private final HttpVersion httpVersion;
    private final String statusMessage;
    private final int statusCode;
    private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    /**
     * http/1.1 200 OK
     * [headers]
     */
    public ResponseMessageInfoChunkBody(String responseInfoChunkBody) {
        String[] chunkLines = responseInfoChunkBody.split(Utils.lineSeparator);
        String[] firstLine = chunkLines[0].split(" ");
        this.httpVersion = Utils.httpVersionParser(firstLine[0]);
        this.statusMessage = String.join(" ", Arrays.asList(firstLine).subList(2, firstLine.length));
        this.statusCode = Integer.parseInt(firstLine[1]);
        for (int i = 1; i < chunkLines.length; i++) {
            String[] line = chunkLines[i].split(":");
            this.headers.add(line[0], line[1]);
        }
    }

    /**
     * http/1.1 200 OK
     * [headers]
     */
    public static String build(HttpVersion httpVersion, String statusMessage, int statusCode, MultiMap headers) {
        String firstLine = httpVersion.alpnName() + " " + statusCode + " " + statusMessage + Utils.lineSeparator;
        StringBuilder result = new StringBuilder(firstLine);
        headers.forEach((key, value) -> {
            result.append(key).append(":").append(value).append(Utils.lineSeparator);
        });

        return result.toString();
    }
}
