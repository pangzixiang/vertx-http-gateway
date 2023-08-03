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

    private static Buffer build(byte chunkType, long requestId, Buffer chunkBody) {
        Buffer result = Buffer.buffer();
        result.appendByte(chunkType).appendLong(requestId);
        if (chunkBody != null && chunkBody.length() > 0) {
            result.appendBuffer(chunkBody);
        }

        return result;
    }

    private static Buffer build(byte chunkType, long requestId) {
        return build(chunkType, requestId, null);
    }

    public static Buffer build(MessageChunkType chunkType, long requestId, Buffer chunkBody) {
        return build(chunkType.getFlag(), requestId, chunkBody);
    }

    public static Buffer build(MessageChunkType chunkType, long requestId, String chunkBody) {
        return build(chunkType.getFlag(), requestId, Buffer.buffer(chunkBody));
    }

    public static Buffer build(MessageChunkType chunkType, long requestId) {
        return build(chunkType.getFlag(), requestId);
    }
}
