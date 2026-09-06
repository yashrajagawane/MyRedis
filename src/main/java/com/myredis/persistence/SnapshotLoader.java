package com.myredis.persistence;

import com.myredis.command.CommandParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SnapshotLoader {
    public long load(Path path, CommandParser parser) throws IOException {
        if (!Files.exists(path)) return 0;
        var lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        if (lines.size() < 2 || !SnapshotWriter.VERSION.equals(lines.getFirst())) {
            throw new IOException("Unsupported snapshot format");
        }
        long offset = Long.parseLong(lines.get(1).substring("AOF_OFFSET ".length()));
        for (String line : lines.subList(2, lines.size())) {
            if (!line.isBlank()) {
                parser.parse(PersistenceCodec.decode(line)).executeWithoutPersistence();
            }
        }
        return offset;
    }
}
