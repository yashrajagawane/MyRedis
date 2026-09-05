package com.myredis.persistence;

import com.myredis.command.CommandParser;
import com.myredis.storage.InMemoryStorageEngine;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PersistenceManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceManager.class);
    private final boolean enabled;
    private final Path aofPath;
    private final Path snapshotPath;
    private final InMemoryStorageEngine storage;
    private AofWriter aofWriter;

    public static PersistenceManager disabled(InMemoryStorageEngine storage) {
        return new PersistenceManager(storage);
    }

    public PersistenceManager(Path aofPath, Path snapshotPath, InMemoryStorageEngine storage) throws IOException {
        this.enabled = true;
        this.aofPath = aofPath;
        this.snapshotPath = snapshotPath;
        this.storage = storage;
        this.aofWriter = new AofWriter(aofPath);
    }

    private PersistenceManager(InMemoryStorageEngine storage) {
        this.enabled = false;
        this.aofPath = null;
        this.snapshotPath = null;
        this.storage = storage;
    }

    public void record(List<String> command) {
        if (!enabled) return;
        try {
            aofWriter.append(command);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not append AOF command", exception);
        }
    }

    public void recover(CommandParser parser) throws IOException {
        if (!enabled) return;
        long offset = new SnapshotLoader().load(snapshotPath, parser);
        int replayed = new AofReplayer().replay(aofPath, offset, parser);
        LOGGER.info("Recovered snapshot and replayed {} AOF commands", replayed);
    }

    public synchronized void snapshot() throws IOException {
        if (!enabled) return;
        new SnapshotWriter().write(snapshotPath, storage, aofWriter.size());
    }

    @Override
    public synchronized void close() {
        if (!enabled) return;
        try {
            snapshot();
            aofWriter.close();
        } catch (IOException exception) {
            LOGGER.error("Could not close persistence cleanly", exception);
        }
    }
}
