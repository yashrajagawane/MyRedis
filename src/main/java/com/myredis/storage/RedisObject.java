package com.myredis.storage;

/** Tagged value stored by the in-memory engine. */
public record RedisObject(RedisType type, Object value) {
    public RedisObject {
        if (type == null || value == null) {
            throw new IllegalArgumentException("type and value are required");
        }
    }
}
