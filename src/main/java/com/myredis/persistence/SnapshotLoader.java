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
        String offsetLine = lines.get(1);
        String offsetPrefix = "AOF_OFFSET ";
        if (!offsetLine.startsWith(offsetPrefix)) {
            throw new IOException("Invalid snapshot AOF offset");
        }
        final long offset;
        try {
            offset = Long.parseLong(offsetLine.substring(offsetPrefix.length()));
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid snapshot AOF offset", exception);
        }
        if (offset < 0) {
            throw new IOException("Invalid snapshot AOF offset");
        }
        for (int index = 2; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) continue;
            try {
                var result = parser.parse(PersistenceCodec.decode(line)).executeWithoutPersistence();
                if (result.response().startsWith("-ERR")) {
                    throw new IllegalArgumentException("snapshot command returned an error: "
                            + result.response());
                }
            } catch (RuntimeException exception) {
                throw new IOException("Could not load snapshot command at line " + (index + 1), exception);
            }
        }
        return offset;
    }
}
