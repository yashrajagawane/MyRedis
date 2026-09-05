package com.myredis.persistence;

import com.myredis.storage.InMemoryStorageEngine;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class SnapshotWriter {
    public static final String VERSION = "MYREDIS-SNAPSHOT-1";

    public void write(Path path, InMemoryStorageEngine storage, long aofOffset) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(VERSION);
            writer.newLine();
            writer.write("AOF_OFFSET " + aofOffset);
            writer.newLine();
            for (var command : storage.snapshotCommands()) {
                writer.write(PersistenceCodec.encode(command));
                writer.newLine();
            }
        }
    }
}
