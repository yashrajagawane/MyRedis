package com.myredis.persistence;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AofWriter implements AutoCloseable {
    private final Path path;
    private final FileChannel channel;
    private final BufferedWriter writer;
    private final FsyncPolicy fsyncPolicy;
    private final ScheduledExecutorService flushExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();

    public AofWriter(Path path) throws IOException {
        this(path, FsyncPolicy.ALWAYS);
    }

    public AofWriter(Path path, FsyncPolicy fsyncPolicy) throws IOException {
        this.path = path;
        this.fsyncPolicy = fsyncPolicy;
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND);
        writer = new BufferedWriter(Channels.newWriter(channel, java.nio.charset.StandardCharsets.UTF_8));
        flushExecutor = fsyncPolicy == FsyncPolicy.EVERY_SECOND
                ? Executors.newSingleThreadScheduledExecutor()
                : null;
        if (flushExecutor != null) {
            flushExecutor.scheduleAtFixedRate(this::forceQuietly, 1, 1, TimeUnit.SECONDS);
        }
    }

    public synchronized void append(List<String> command) throws IOException {
        ensureOpen();
        writer.write(PersistenceCodec.encode(command));
        writer.newLine();
        writer.flush();
        if (fsyncPolicy == FsyncPolicy.ALWAYS) channel.force(false);
    }

    public synchronized long size() throws IOException {
        ensureOpen();
        writer.flush();
        return Files.size(path);
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed.compareAndSet(false, true)) return;
        if (flushExecutor != null) flushExecutor.shutdownNow();
        writer.flush();
        channel.force(false);
        writer.close();
    }

    private void ensureOpen() throws IOException {
        if (closed.get()) throw new IOException("AOF writer is closed");
    }

    private synchronized void forceQuietly() {
        try {
            writer.flush();
            channel.force(false);
        } catch (IOException ignored) {
            // The next command or close reports the durable-write failure.
        }
    }
}
