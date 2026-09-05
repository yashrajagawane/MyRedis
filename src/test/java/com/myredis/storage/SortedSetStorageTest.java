package com.myredis.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SortedSetStorageTest {
    @Test
    void ordersByScoreThenMemberAndSupportsUpdates() {
        InMemoryStorageEngine storage = new InMemoryStorageEngine();

        assertEquals(3, storage.addSortedSetMembers("scores",
                List.of("20", "bob", "10", "alice", "10", "anna")));
        assertEquals(List.of("alice", "anna", "bob"), storage.sortedSetRange("scores", 0, -1));
        assertEquals(1, storage.sortedSetRank("scores", "anna").orElseThrow());
        assertEquals(0, storage.addSortedSetMembers("scores", List.of("30", "alice")));
        assertEquals(30.0, storage.sortedSetScore("scores", "alice").orElseThrow());
        assertEquals(List.of("anna", "bob", "alice"), storage.sortedSetRange("scores", 0, -1));
    }

    @Test
    void preservesConcurrentSortedSetAdds() throws Exception {
        InMemoryStorageEngine storage = new InMemoryStorageEngine();
        CompletableFuture<?>[] adds = IntStream.range(0, 100)
                .mapToObj(index -> CompletableFuture.runAsync(() -> storage.addSortedSetMembers(
                        "concurrent", List.of(Integer.toString(index), "member" + index))))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(adds).get(5, TimeUnit.SECONDS);

        assertEquals(100, storage.sortedSetRange("concurrent", 0, -1).size());
    }
}
