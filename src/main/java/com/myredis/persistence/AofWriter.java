package com.myredis.persistence;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class AofWriter implements AutoCloseable {
    private final Path path;
    private final BufferedWriter writer;

    public AofWriter(Path path) throws IOException {
        this.path = path;
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        writer = Files.newBufferedWriter(path, java.nio.charset.StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized void append(List<String> command) throws IOException {
        writer.write(PersistenceCodec.encode(command));
        writer.newLine();
        writer.flush();
    }

    public synchronized long size() throws IOException {
        writer.flush();
        return Files.size(path);
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }
}
