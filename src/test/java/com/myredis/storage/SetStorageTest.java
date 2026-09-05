package com.myredis.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SetStorageTest {
    @Test
    void supportsSetMembershipAndRemoval() {
        InMemoryStorageEngine storage = new InMemoryStorageEngine();

        assertEquals(2, storage.addSet("tags", List.of("java", "redis")));
        assertEquals(0, storage.addSet("tags", List.of("java")));
        assertEquals(List.of("java", "redis"), storage.setMembers("tags"));
        assertTrue(storage.isSetMember("tags", "java"));
        assertEquals(1, storage.removeSet("tags", List.of("java")));
        assertFalse(storage.isSetMember("tags", "java"));
    }

    @Test
    void preservesAllConcurrentAdds() throws Exception {
        InMemoryStorageEngine storage = new InMemoryStorageEngine();
        CompletableFuture<?>[] adds = IntStream.range(0, 100)
                .mapToObj(index -> CompletableFuture.runAsync(
                        () -> storage.addSet("concurrent", List.of("member" + index))))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(adds).get(5, TimeUnit.SECONDS);

        assertEquals(100, storage.setCardinality("concurrent"));
    }
}
