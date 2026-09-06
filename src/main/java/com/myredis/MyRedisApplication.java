package com.myredis;

import com.myredis.command.CommandParser;
import com.myredis.command.CommandRegistry;
import com.myredis.config.ConfigLoader;
import com.myredis.config.ServerConfig;
import com.myredis.expiration.ExpirationManager;
import com.myredis.persistence.PersistenceManager;
import com.myredis.server.MyRedisServer;
import com.myredis.storage.InMemoryStorageEngine;

/** Application entry point for the Phase 1 TCP server. */
public final class MyRedisApplication {
    private MyRedisApplication() {
    }

    public static void main(String[] args) throws Exception {
        ServerConfig config = ConfigLoader.load(args);
        System.setProperty("MYREDIS_LOG_LEVEL", config.logLevel());
        ExpirationManager expiration = new ExpirationManager();
        InMemoryStorageEngine storage = new InMemoryStorageEngine(expiration);
        PersistenceManager persistence = config.aofEnabled()
                ? new PersistenceManager(config.aofPath(), config.snapshotPath(), storage,
                config.fsyncPolicy(), config.snapshotIntervalSeconds())
                : PersistenceManager.disabled(storage);
        CommandParser parser = new CommandParser(new CommandRegistry(), storage, expiration, persistence);
        persistence.recover(parser);
        MyRedisServer server = new MyRedisServer(config.host(), config.port(), storage, parser, expiration, persistence,
                config.maxValueBytes(), config.maxArrayElements());
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "myredis-shutdown"));
        server.start();
    }
}
