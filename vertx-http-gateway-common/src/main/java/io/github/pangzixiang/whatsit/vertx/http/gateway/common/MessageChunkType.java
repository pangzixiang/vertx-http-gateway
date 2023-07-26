package io.github.pangzixiang.whatsit.vertx.http.gateway.common;

import lombok.Getter;

@Getter
public enum MessageChunkType {
    INFO((byte) 0),
    BODY((byte) 1),
    ENDING((byte) 2),
    ERROR((byte) -2);

    private final byte flag;

    MessageChunkType(byte flag) {
        this.flag = flag;
    }
}
