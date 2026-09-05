package com.myredis.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HashStorageTest {
    @Test
    void supportsFieldUpdatesAndRemoval() {
        InMemoryStorageEngine storage = new InMemoryStorageEngine();

        assertEquals(2, storage.putHashFields("user", List.of("name", "Yash", "city", "Pune")));
        assertEquals(0, storage.putHashFields("user", List.of("name", "Updated")));
        assertEquals("Updated", storage.getHashField("user", "name").orElseThrow());
        assertEquals(List.of("city", "Pune", "name", "Updated"), storage.getAllHashFields("user"));
        assertTrue(storage.hasHashField("user", "city"));
        assertEquals(1, storage.removeHashFields("user", List.of("city")));
        assertFalse(storage.hasHashField("user", "city"));
    }
}
