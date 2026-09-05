package com.myredis.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ListStorageTest {
    @Test
    void supportsDequeOperationsAndRedisStyleRanges() {
        InMemoryStorageEngine storage = new InMemoryStorageEngine();

        assertEquals(3, storage.pushRight("list", List.of("a", "b", "c")));
        assertEquals(List.of("a", "b"), storage.range("list", 0, 1));
        assertEquals(List.of("b", "c"), storage.range("list", -2, -1));
        assertEquals("a", storage.popLeft("list").orElseThrow());
        assertEquals("c", storage.popRight("list").orElseThrow());
        assertEquals(1, storage.listLength("list"));
        assertEquals("b", storage.popLeft("list").orElseThrow());
        assertTrue(storage.popLeft("list").isEmpty());
        assertEquals(0, storage.listLength("list"));
    }

    @Test
    void preservesAllConcurrentPushesForOneList() throws Exception {
        InMemoryStorageEngine storage = new InMemoryStorageEngine();
        CompletableFuture<?>[] pushes = IntStream.range(0, 100)
                .mapToObj(index -> CompletableFuture.runAsync(
                        () -> storage.pushLeft("concurrent", List.of("value" + index))))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(pushes).get(5, TimeUnit.SECONDS);

        assertEquals(100, storage.listLength("concurrent"));
    }
}
