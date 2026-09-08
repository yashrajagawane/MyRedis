package com.myredis.expiration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myredis.storage.InMemoryStorageEngine;
import org.junit.jupiter.api.Test;

class ExpirationTest {
    @Test
    void expiresKeysOnAccessAndReportsMissingTtl() throws Exception {
        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        storage.setString("key", "value");
        expiration.setExpiryMillis("key", 20);

        Thread.sleep(40);

        assertTrue(storage.getString("key").isEmpty());
        assertEquals(-1, expiration.ttlSeconds("key").orElseThrow());
        assertEquals(1, expiration.expiredKeys());
    }
}
