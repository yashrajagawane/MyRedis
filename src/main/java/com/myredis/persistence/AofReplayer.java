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
        if (offset < 0) throw new IOException("Invalid AOF replay offset");
        if (!Files.exists(path)) return 0;
        long fileSize = Files.size(path);
        if (offset > fileSize) throw new IOException("Snapshot AOF offset exceeds AOF length");
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
                    var result = parser.parse(arguments).executeWithoutPersistence();
                    if (result.response().startsWith("-ERR")) {
                        throw new IllegalArgumentException("replayed command returned an error: "
                                + result.response());
                    }
                    replayed++;
                } catch (IllegalArgumentException | CommandParseException exception) {
                    if (file.getFilePointer() >= fileSize && !hasRecordTerminator(file, recordStart)) {
                        file.setLength(recordStart);
                        return replayed;
                    }
                    throw new IOException("Could not replay AOF command", exception);
                }
            }
        }
        return replayed;
    }

    private boolean hasRecordTerminator(RandomAccessFile file, long recordStart) throws IOException {
        long end = file.getFilePointer();
        if (end <= recordStart) return false;
        file.seek(end - 1);
        int lastByte = file.read();
        file.seek(end);
        return lastByte == '\n' || lastByte == '\r';
    }
}
