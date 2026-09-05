package com.myredis.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
