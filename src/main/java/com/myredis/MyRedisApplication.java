package com.myredis;

import com.myredis.command.CommandParser;
import com.myredis.command.CommandRegistry;
import com.myredis.expiration.ExpirationManager;
import com.myredis.persistence.PersistenceManager;
import com.myredis.server.MyRedisServer;
import com.myredis.storage.InMemoryStorageEngine;
import java.nio.file.Path;

/** Application entry point for the Phase 1 TCP server. */
public final class MyRedisApplication {
    private MyRedisApplication() {
    }

    public static void main(String[] args) throws Exception {
        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        PersistenceManager persistence = new PersistenceManager(
                Path.of("data", "myredis.aof"), Path.of("data", "myredis.snapshot"), storage);
        CommandParser parser = new CommandParser(new CommandRegistry(), storage, expiration, persistence);
        persistence.recover(parser);
        MyRedisServer server = new MyRedisServer(6379, storage, parser, expiration, persistence);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "myredis-shutdown"));
        server.start();
    }
}
