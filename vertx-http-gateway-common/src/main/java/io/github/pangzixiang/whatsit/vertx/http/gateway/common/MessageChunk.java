package io.github.pangzixiang.whatsit.vertx.http.gateway.common;


import io.vertx.core.buffer.Buffer;
import lombok.Getter;

@Getter
public class MessageChunk {
    private final byte chunkType;
    private final byte requestId;
    private final Buffer chunkBody;

    public MessageChunk(Buffer chunk) {
        this.chunkType = chunk.getByte(0);
        this.requestId = chunk.getByte(1);
        this.chunkBody = chunk.getBuffer(2, chunk.length());
    }

    public static Buffer build(byte chunkType, byte requestId, Buffer chunkBody) {
        return Buffer.buffer()
                .appendByte(chunkType)
                .appendByte(requestId)
                .appendBuffer(chunkBody);
    }

    public static Buffer build(byte chunkType, byte requestId, String chunkBody) {
        return Buffer.buffer()
                .appendByte(chunkType)
                .appendByte(requestId)
                .appendString(chunkBody);
    }

    public static Buffer build(byte chunkType, byte requestId) {
        return Buffer.buffer()
                .appendByte(chunkType)
                .appendByte(requestId);
    }

    public static Buffer build(MessageChunkType chunkType, byte requestId, Buffer chunkBody) {
        return build(chunkType.getFlag(), requestId, chunkBody);
    }

    public static Buffer build(MessageChunkType chunkType, byte requestId, String chunkBody) {
        return build(chunkType.getFlag(), requestId, chunkBody);
    }

    public static Buffer build(MessageChunkType chunkType, byte requestId) {
        return build(chunkType.getFlag(), requestId);
    }
}
