package com.myredis.persistence;

import com.myredis.command.CommandParser;
import com.myredis.storage.InMemoryStorageEngine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PersistenceManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceManager.class);
    private final boolean enabled;
    private final Path aofPath;
    private final Path snapshotPath;
    private final InMemoryStorageEngine storage;
    private final ScheduledExecutorService snapshotExecutor;
    private final AtomicLong aofWrites = new AtomicLong();
    private final AtomicLong snapshots = new AtomicLong();
    private final AtomicLong persistenceErrors = new AtomicLong();
    private final ReentrantLock mutationLock = new ReentrantLock(true);
    private AofWriter aofWriter;

    public static PersistenceManager disabled(InMemoryStorageEngine storage) {
        return new PersistenceManager(storage);
    }

    public PersistenceManager(Path aofPath, Path snapshotPath, InMemoryStorageEngine storage) throws IOException {
        this(aofPath, snapshotPath, storage, FsyncPolicy.ALWAYS, 60);
    }

    public PersistenceManager(Path aofPath, Path snapshotPath, InMemoryStorageEngine storage,
                               FsyncPolicy fsyncPolicy, long snapshotIntervalSeconds) throws IOException {
        this.enabled = true;
        this.aofPath = aofPath;
        this.snapshotPath = snapshotPath;
        this.storage = storage;
        this.aofWriter = new AofWriter(aofPath, fsyncPolicy);
        this.snapshotExecutor = Executors.newSingleThreadScheduledExecutor();
        if (snapshotIntervalSeconds > 0) {
            snapshotExecutor.scheduleAtFixedRate(this::snapshotQuietly, snapshotIntervalSeconds,
                    snapshotIntervalSeconds, TimeUnit.SECONDS);
        }
    }

    private PersistenceManager(InMemoryStorageEngine storage) {
        this.enabled = false;
        this.aofPath = null;
        this.snapshotPath = null;
        this.storage = storage;
        this.snapshotExecutor = null;
    }

    public void record(List<String> command) {
        if (!enabled) return;
        try {
            aofWriter.append(command);
            aofWrites.incrementAndGet();
        } catch (IOException exception) {
            persistenceErrors.incrementAndGet();
            throw new IllegalStateException("Could not append AOF command", exception);
        }
    }

    public <T> T withMutation(Supplier<T> mutation) {
        mutationLock.lock();
        try {
            return mutation.get();
        } finally {
            mutationLock.unlock();
        }
    }

    public void recover(CommandParser parser) throws IOException {
        if (!enabled) return;
        try {
            long offset = new SnapshotLoader().load(snapshotPath, parser);
            int replayed = new AofReplayer().replay(aofPath, offset, parser);
            LOGGER.info("Recovered snapshot and replayed {} AOF commands", replayed);
        } catch (IOException exception) {
            persistenceErrors.incrementAndGet();
            throw exception;
        }
    }

    public synchronized void snapshot() throws IOException {
        if (!enabled) return;
        mutationLock.lock();
        try {
            new SnapshotWriter().write(snapshotPath, storage, aofWriter.size());
            snapshots.incrementAndGet();
        } catch (IOException exception) {
            persistenceErrors.incrementAndGet();
            throw exception;
        } finally {
            mutationLock.unlock();
        }
    }

    public long aofWrites() {
        return aofWrites.get();
    }

    public long snapshots() {
        return snapshots.get();
    }

    public long persistenceErrors() {
        return persistenceErrors.get();
    }

    @Override
    public synchronized void close() {
        if (!enabled) return;
        snapshotExecutor.shutdownNow();
        try {
            snapshot();
        } catch (IOException exception) {
            LOGGER.error("Could not write final snapshot during shutdown", exception);
        } finally {
            try {
                aofWriter.close();
            } catch (IOException exception) {
                LOGGER.error("Could not close AOF cleanly", exception);
            }
        }
    }

    private void snapshotQuietly() {
        try {
            snapshot();
        } catch (IOException exception) {
            LOGGER.error("Periodic snapshot failed", exception);
        }
    }
}
