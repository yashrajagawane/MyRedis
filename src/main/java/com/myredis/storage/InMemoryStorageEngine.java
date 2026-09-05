package com.myredis.storage;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe String storage for concurrent Phase 4 clients. */
public final class InMemoryStorageEngine implements StorageEngine {
    private final Map<String, RedisObject> values = new ConcurrentHashMap<>();

    @Override
    public void setString(String key, String value) {
        requireKey(key);
        values.put(key, new RedisObject(RedisType.STRING, value));
    }

    @Override
    public Optional<String> getString(String key) {
        RedisObject object = values.get(key);
        if (object == null) {
            return Optional.empty();
        }
        requireType(key, object, RedisType.STRING);
        return Optional.of((String) object.value());
    }

    @Override
    public boolean delete(String key) {
        return values.remove(key) != null;
    }

    @Override
    public boolean exists(String key) {
        return values.containsKey(key);
    }

    private static void requireKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key cannot be empty");
        }
    }

    private static void requireType(String key, RedisObject object, RedisType expected) {
        if (object.type() != expected) {
            throw new WrongTypeException(key, expected, object.type());
        }
    }
}
