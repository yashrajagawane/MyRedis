package com.myredis.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void truncatesAnIncompleteFinalAofRecord() throws Exception {
        Path directory = Files.createTempDirectory("myredis-aof-tail-");
        Path aof = directory.resolve("myredis.aof");
        Path snapshot = directory.resolve("myredis.snapshot");
        String valid = java.util.Base64.getEncoder().encodeToString("SET".getBytes()) + " "
                + java.util.Base64.getEncoder().encodeToString("key".getBytes()) + " "
                + java.util.Base64.getEncoder().encodeToString("value".getBytes()) + "\n";
        Files.writeString(aof, valid + "%%%partial");

        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        PersistenceManager persistence = new PersistenceManager(aof, snapshot, storage);
        CommandParser parser = new CommandParser(new CommandRegistry(), storage, expiration, persistence);

        persistence.recover(parser);

        assertEquals("value", parser.parse("GET key").executeWithoutPersistence().response());
        assertEquals(valid.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, Files.size(aof));
        persistence.close();
    }

    @Test
    void rejectsCorruptionBeforeTheFinalAofRecord() throws Exception {
        Path directory = Files.createTempDirectory("myredis-aof-corrupt-");
        Path aof = directory.resolve("myredis.aof");
        Path snapshot = directory.resolve("myredis.snapshot");
        Files.writeString(aof, "%%%corrupt\nvalid-looking-tail\n");

        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        PersistenceManager persistence = new PersistenceManager(aof, snapshot, storage);
        CommandParser parser = new CommandParser(new CommandRegistry(), storage, expiration, persistence);

        assertThrows(java.io.IOException.class, () -> persistence.recover(parser));
        persistence.close();
    }

    @Test
    void rejectsSnapshotWithOffsetBeyondCurrentAof() throws Exception {
        Path directory = Files.createTempDirectory("myredis-snapshot-offset-");
        Path aof = directory.resolve("myredis.aof");
        Path snapshot = directory.resolve("myredis.snapshot");
        Files.writeString(aof, "");
        Files.writeString(snapshot, SnapshotWriter.VERSION + "\nAOF_OFFSET 10\n");

        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        PersistenceManager persistence = new PersistenceManager(aof, snapshot, storage);
        CommandParser parser = new CommandParser(new CommandRegistry(), storage, expiration, persistence);

        assertThrows(java.io.IOException.class, () -> persistence.recover(parser));
        persistence.close();
    }

    @Test
    void rejectsMalformedSnapshotOffset() throws Exception {
        Path directory = Files.createTempDirectory("myredis-snapshot-format-");
        Path aof = directory.resolve("myredis.aof");
        Path snapshot = directory.resolve("myredis.snapshot");
        Files.writeString(aof, "");
        Files.writeString(snapshot, SnapshotWriter.VERSION + "\nAOF_OFFSET nope\n");

        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        PersistenceManager persistence = new PersistenceManager(aof, snapshot, storage);
        CommandParser parser = new CommandParser(new CommandRegistry(), storage, expiration, persistence);

        assertThrows(java.io.IOException.class, () -> persistence.recover(parser));
        persistence.close();
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

    @Test
    void recoversAtomicNumericMutationsFromAof() throws Exception {
        Path directory = Files.createTempDirectory("myredis-counter-recovery-");
        Path aof = directory.resolve("myredis.aof");
        Path snapshot = directory.resolve("myredis.snapshot");

        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        PersistenceManager writer = new PersistenceManager(aof, snapshot, storage);
        CommandParser parser = new CommandParser(new CommandRegistry(), storage, expiration, writer);
        parser.parse("INCR counter").execute();
        parser.parse("INCRBY counter 4").execute();
        parser.parse("DECR counter").execute();
        writer.close();

        ExpirationManager recoveredExpiration = new ExpirationManager();
        InMemoryStorageEngine recoveredStorage = new InMemoryStorageEngine(recoveredExpiration);
        PersistenceManager recovered = new PersistenceManager(aof, snapshot, recoveredStorage);
        CommandParser recoveredParser = new CommandParser(
                new CommandRegistry(), recoveredStorage, recoveredExpiration, recovered);
        recovered.recover(recoveredParser);

        assertEquals("4", recoveredParser.parse("GET counter").executeWithoutPersistence().response());
        recovered.close();
    }
}
