package com.myredis.persistence;

import com.myredis.command.CommandParseException;
import com.myredis.command.CommandParser;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AofReplayer {
    public int replay(Path path, long offset, CommandParser parser) throws IOException {
        if (!Files.exists(path)) return 0;
        long fileSize = Files.size(path);
        if (offset >= fileSize) return 0;
        int replayed = 0;
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "rw")) {
            file.seek(offset);
            while (file.getFilePointer() < fileSize) {
                long recordStart = file.getFilePointer();
                String line = file.readLine();
                if (line == null || line.isBlank()) continue;
                try {
                    List<String> arguments = PersistenceCodec.decode(
                            new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.US_ASCII));
                    parser.parse(arguments).executeWithoutPersistence();
                    replayed++;
                } catch (IllegalArgumentException | CommandParseException exception) {
                    if (file.getFilePointer() >= fileSize) {
                        file.setLength(recordStart);
                        return replayed;
                    }
                    throw new IOException("Could not replay AOF command", exception);
                }
            }
        }
        return replayed;
    }
}
