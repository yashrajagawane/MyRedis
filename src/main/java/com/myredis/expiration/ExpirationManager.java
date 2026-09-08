package com.myredis.expiration;

import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe TTL bookkeeping shared by commands, storage, and the scheduler. */
public final class ExpirationManager {
    private final Map<String, Long> expiryTimestamps = new ConcurrentHashMap<>();

    public void setExpiryMillis(String key, long durationMillis) {
        if (durationMillis <= 0) throw new IllegalArgumentException("invalid expire time");
        try {
            expiryTimestamps.put(key, Math.addExact(System.currentTimeMillis(), durationMillis));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("invalid expire time", exception);
        }
    }

    public OptionalLong ttlSeconds(String key) {
        long remaining = ttlMillis(key).orElse(-2);
        if (remaining < 0) return OptionalLong.of(remaining);
        return OptionalLong.of(Math.max(1, remaining / 1000));
    }

    public OptionalLong ttlMillis(String key) {
        Long expiresAt = expiryTimestamps.get(key);
        if (expiresAt == null) return OptionalLong.of(-1);
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) {
            expiryTimestamps.remove(key, expiresAt);
            return OptionalLong.of(-2);
        }
        return OptionalLong.of(remaining);
    }

    public boolean persist(String key) {
        return expiryTimestamps.remove(key) != null;
    }

    public boolean isExpired(String key) {
        Long expiresAt = expiryTimestamps.get(key);
        if (expiresAt == null) return false;
        if (expiresAt > System.currentTimeMillis()) return false;
        return expiryTimestamps.remove(key, expiresAt);
    }

    public void removeExpiry(String key) {
        expiryTimestamps.remove(key);
    }

    public Map<String, Long> expirySnapshot() {
        return Map.copyOf(expiryTimestamps);
    }
}
