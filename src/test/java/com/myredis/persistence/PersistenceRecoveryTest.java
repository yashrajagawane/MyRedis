package com.myredis.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.myredis.command.CommandParser;
import com.myredis.command.CommandRegistry;
import com.myredis.expiration.ExpirationManager;
import com.myredis.storage.InMemoryStorageEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PersistenceRecoveryTest {
    @Test
    void restoresSnapshotAndReplaysAofTail() throws Exception {
        Path directory = Files.createTempDirectory("myredis-persistence-");
        Path aof = directory.resolve("myredis.aof");
        Path snapshot = directory.resolve("myredis.snapshot");

        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        PersistenceManager writer = new PersistenceManager(aof, snapshot, storage);
        CommandParser parser = new CommandParser(new CommandRegistry(), storage, expiration, writer);
        parser.parse("SET name Yash").execute();
        parser.parse("SET temporary value PX 5000").execute();
        writer.snapshot();
        parser.parse("SET tail restored").execute();

        ExpirationManager recoveredExpiration = new ExpirationManager();
        InMemoryStorageEngine recoveredStorage = new InMemoryStorageEngine(recoveredExpiration);
        PersistenceManager recovered = new PersistenceManager(aof, snapshot, recoveredStorage);
        CommandParser recoveredParser = new CommandParser(
                new CommandRegistry(), recoveredStorage, recoveredExpiration, recovered);
        recovered.recover(recoveredParser);

        assertEquals("Yash", recoveredParser.parse("GET name").executeWithoutPersistence().response());
        assertEquals("restored", recoveredParser.parse("GET tail").executeWithoutPersistence().response());
        assertEquals("value", recoveredParser.parse("GET temporary").executeWithoutPersistence().response());

        writer.close();
        recovered.close();
    }
}
