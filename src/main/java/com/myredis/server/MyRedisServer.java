package com.myredis.server;

import com.myredis.command.CommandParser;
import com.myredis.command.CommandRegistry;
import com.myredis.expiration.ExpirationManager;
import com.myredis.expiration.ExpirationScheduler;
import com.myredis.persistence.PersistenceManager;
import com.myredis.storage.InMemoryStorageEngine;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Blocking TCP server used as the networking baseline for Phase 1. */
public final class MyRedisServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyRedisServer.class);

    private final int port;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ConnectionRegistry connectionRegistry = new ConnectionRegistry();
    private final ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExpirationManager expiration;
    private final InMemoryStorageEngine storage;
    private final CommandParser commandParser;
    private final ExpirationScheduler expirationScheduler;
    private final PersistenceManager persistence;
    private volatile ServerSocket serverSocket;

    public MyRedisServer(int port) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.port = port;
        this.expiration = new ExpirationManager();
        this.storage = new InMemoryStorageEngine(expiration);
        this.persistence = PersistenceManager.disabled(storage);
        this.commandParser = new CommandParser(new CommandRegistry(), storage, expiration, persistence);
        this.expirationScheduler = new ExpirationScheduler(expiration, storage);
    }

    public MyRedisServer(int port, InMemoryStorageEngine storage, CommandParser commandParser,
                         ExpirationManager expiration, PersistenceManager persistence) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.port = port;
        this.storage = storage;
        this.commandParser = commandParser;
        this.expiration = expiration;
        this.persistence = persistence;
        this.expirationScheduler = new ExpirationScheduler(expiration, storage);
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("server is already running");
        }

        try (ServerSocket socket = new ServerSocket(port)) {
            serverSocket = socket;
            LOGGER.info("MyRedis listening on port {}", socket.getLocalPort());
            while (running.get()) {
                try {
                    Socket client = socket.accept();
                    connectionRegistry.register(client);
                    clientExecutor.submit(new ClientHandler(client, connectionRegistry, commandParser));
                    LOGGER.info("Client connected from {}", client.getRemoteSocketAddress());
                } catch (IOException exception) {
                    if (running.get()) {
                        LOGGER.error("Accept loop failed", exception);
                    }
                }
            }
        } finally {
            serverSocket = null;
            running.set(false);
            connectionRegistry.closeAll();
            clientExecutor.shutdownNow();
            expirationScheduler.close();
            persistence.close();
            LOGGER.info("MyRedis stopped");
        }
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        ServerSocket socket = serverSocket;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException exception) {
                LOGGER.warn("Could not close server socket cleanly", exception);
            }
        }
        connectionRegistry.closeAll();
        clientExecutor.shutdownNow();
        expirationScheduler.close();
    }

    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? port : socket.getLocalPort();
    }

}
