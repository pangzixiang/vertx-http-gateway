package io.github.pangzixiang.whatsit.vertx.http.gateway.common;


import io.vertx.core.buffer.Buffer;
import lombok.Getter;

@Getter
public class MessageChunk {
    private final byte chunkType;
    private final long requestId;
    private final Buffer chunkBody;

    public MessageChunk(Buffer chunk) {
        this.chunkType = chunk.getByte(0);
        this.requestId = chunk.getLong(1);
        this.chunkBody = chunk.getBuffer(9, chunk.length());
    }

    public static Buffer build(byte chunkType, Long requestId, Buffer chunkBody) {
        return Buffer.buffer()
                .appendByte(chunkType)
                .appendLong(requestId)
                .appendBuffer(chunkBody);
    }

    public static Buffer build(byte chunkType, Long requestId, String chunkBody) {
        return Buffer.buffer()
                .appendByte(chunkType)
                .appendLong(requestId)
                .appendString(chunkBody);
    }

    public static Buffer build(byte chunkType, Long requestId) {
        return Buffer.buffer()
                .appendByte(chunkType)
                .appendLong(requestId);
    }

    public static Buffer build(MessageChunkType chunkType, Long requestId, Buffer chunkBody) {
        return build(chunkType.getFlag(), requestId, chunkBody);
    }

    public static Buffer build(MessageChunkType chunkType, Long requestId, String chunkBody) {
        return build(chunkType.getFlag(), requestId, chunkBody);
    }

    public static Buffer build(MessageChunkType chunkType, Long requestId) {
        return build(chunkType.getFlag(), requestId);
    }
}
