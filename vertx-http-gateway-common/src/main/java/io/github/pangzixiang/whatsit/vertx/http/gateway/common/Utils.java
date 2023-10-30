package io.github.pangzixiang.whatsit.vertx.http.gateway.common;

import io.vertx.core.http.HttpVersion;
import lombok.experimental.UtilityClass;

@UtilityClass
class Utils {
    static final String lineSeparator = "\n";
    static HttpVersion httpVersionParser(String alpnName) {
        for (HttpVersion httpVersion: HttpVersion.values()) {
            if (httpVersion.alpnName().equals(alpnName)) {
                return httpVersion;
            }
        }
        return HttpVersion.valueOf(alpnName);
    }

}
