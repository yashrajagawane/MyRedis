package com.myredis.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myredis.expiration.ExpirationManager;
import com.myredis.storage.InMemoryStorageEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SnapshotWriterTest {
    @Test
    void replacesSnapshotWithoutLeavingTemporaryFiles() throws Exception {
        Path directory = Files.createTempDirectory("myredis-snapshot-");
        Path snapshot = directory.resolve("myredis.snapshot");
        InMemoryStorageEngine storage = new InMemoryStorageEngine(new ExpirationManager());
        SnapshotWriter writer = new SnapshotWriter();

        storage.setString("key", "first");
        writer.write(snapshot, storage, 10);
        storage.setString("key", "second");
        writer.write(snapshot, storage, 20);

        assertEquals(SnapshotWriter.VERSION, Files.readAllLines(snapshot).getFirst());
        assertEquals("AOF_OFFSET 20", Files.readAllLines(snapshot).get(1));
        try (var files = Files.list(directory)) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().contains(".tmp-")));
        }
    }

    @Test
    void writesMillisecondPrecisionExpirationCommands() throws Exception {
        Path directory = Files.createTempDirectory("myredis-snapshot-ttl-");
        Path snapshot = directory.resolve("myredis.snapshot");
        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        storage.setString("key", "value");
        expiration.setExpiryMillis("key", 5_000);

        new SnapshotWriter().write(snapshot, storage, 0);

        assertTrue(Files.readAllLines(snapshot).stream().skip(2)
                .map(PersistenceCodec::decode)
                .anyMatch(command -> command.getFirst().equals("PEXPIRE")));
    }
}
