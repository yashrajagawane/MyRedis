package com.myredis.persistence;

import com.myredis.command.CommandParseException;
import com.myredis.command.CommandParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AofReplayer {
    public int replay(Path path, long offset, CommandParser parser) throws IOException {
        if (!Files.exists(path)) return 0;
        byte[] bytes = Files.readAllBytes(path);
        if (offset >= bytes.length) return 0;
        String tail = new String(bytes, (int) offset, bytes.length - (int) offset, StandardCharsets.UTF_8);
        int replayed = 0;
        for (String line : tail.split("\\R")) {
            if (line.isBlank()) continue;
            try {
                List<String> arguments = PersistenceCodec.decode(line);
                parser.parse(arguments).executeWithoutPersistence();
                replayed++;
            } catch (IllegalArgumentException | CommandParseException exception) {
                throw new IOException("Could not replay AOF command", exception);
            }
        }
        return replayed;
    }
}
