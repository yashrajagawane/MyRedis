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
    void supportsEverySecondAndNeverFsyncPolicies() throws Exception {
        Path directory = Files.createTempDirectory("myredis-fsync-");
        try (AofWriter everySecond = new AofWriter(directory.resolve("every.aof"), FsyncPolicy.EVERY_SECOND);
             AofWriter never = new AofWriter(directory.resolve("never.aof"), FsyncPolicy.NEVER)) {
            everySecond.append(java.util.List.of("SET", "a", "one"));
            never.append(java.util.List.of("SET", "b", "two"));
        }
        assertEquals(true, Files.size(directory.resolve("every.aof")) > 0);
        assertEquals(true, Files.size(directory.resolve("never.aof")) > 0);
    }

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

    @Test
    void preservesSpacesAndEmptyValuesDuringRecovery() throws Exception {
        Path directory = Files.createTempDirectory("myredis-argument-recovery-");
        Path aof = directory.resolve("myredis.aof");
        Path snapshot = directory.resolve("myredis.snapshot");

        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        PersistenceManager writer = new PersistenceManager(aof, snapshot, storage);
        CommandParser parser = new CommandParser(new CommandRegistry(), storage, expiration, writer);
        parser.parse(java.util.List.of("SET", "spaced", "hello world")).execute();
        parser.parse(java.util.List.of("SET", "empty", "")).execute();
        writer.snapshot();
        writer.close();

        ExpirationManager recoveredExpiration = new ExpirationManager();
        InMemoryStorageEngine recoveredStorage = new InMemoryStorageEngine(recoveredExpiration);
        PersistenceManager recovered = new PersistenceManager(aof, snapshot, recoveredStorage);
        CommandParser recoveredParser = new CommandParser(
                new CommandRegistry(), recoveredStorage, recoveredExpiration, recovered);
        recovered.recover(recoveredParser);

        assertEquals("hello world", recoveredParser.parse("GET spaced").executeWithoutPersistence().response());
        assertEquals("", recoveredParser.parse(java.util.List.of("GET", "empty"))
                .executeWithoutPersistence().response());
        recovered.close();
    }
}
