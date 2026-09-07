package com.myredis.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myredis.expiration.ExpirationManager;
import org.junit.jupiter.api.Test;

class InMemoryStorageEngineTest {
    private final InMemoryStorageEngine storage = new InMemoryStorageEngine();

    @Test
    void storesReadsChecksAndDeletesStrings() {
        storage.setString("key", "value");

        assertTrue(storage.exists("key"));
        assertEquals("value", storage.getString("key").orElseThrow());
        assertTrue(storage.delete("key"));
        assertFalse(storage.exists("key"));
        assertFalse(storage.delete("key"));
    }

    @Test
    void missingStringReturnsEmpty() {
        assertTrue(storage.getString("missing").isEmpty());
    }

    @Test
    void expiredCleanupDoesNotRemoveAReplacementWithoutExpiry() throws Exception {
        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine expiringStorage = new InMemoryStorageEngine(expiration);
        expiringStorage.setString("key", "old");
        expiration.setExpiryMillis("key", 1);
        Thread.sleep(10);
        expiringStorage.setString("key", "new");

        assertFalse(expiringStorage.removeIfExpired("key"));
        assertEquals("new", expiringStorage.getString("key").orElseThrow());
    }

    @Test
    void expiredKeysAreNotIncludedInSnapshots() throws Exception {
        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine expiringStorage = new InMemoryStorageEngine(expiration);
        expiringStorage.setString("key", "value");
        expiration.setExpiryMillis("key", 1);
        Thread.sleep(10);

        assertTrue(expiringStorage.snapshotCommands().stream()
                .noneMatch(command -> command.size() > 1 && command.get(1).equals("key")));
        assertFalse(expiringStorage.exists("key"));
    }
}
