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
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Blocking TCP server used as the networking baseline for Phase 1. */
public final class MyRedisServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyRedisServer.class);

    private final String host;
    private final int port;
    private final AtomicBoolean running = new AtomicBoolean();
    private final ConnectionRegistry connectionRegistry;
    private final PubSubBroker pubSubBroker;
    private final ExecutorService clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final ExpirationManager expiration;
    private final InMemoryStorageEngine storage;
    private final CommandParser commandParser;
    private final ExpirationScheduler expirationScheduler;
    private final PersistenceManager persistence;
    private final int maxValueBytes;
    private final int maxArrayElements;
    private final String authPassword;
    private final int clientIdleTimeoutSeconds;
    private final int maxCommandsPerSecond;
    private volatile ServerSocket serverSocket;

    public MyRedisServer(int port) {
        this("127.0.0.1", port);
    }

    public MyRedisServer(String host, int port) {
        this(host, port, RespLimits.DEFAULT_MAX_VALUE_BYTES, RespLimits.DEFAULT_MAX_ARRAY_ELEMENTS);
    }

    private MyRedisServer(String host, int port, int maxValueBytes, int maxArrayElements) {
        this(host, port, maxValueBytes, maxArrayElements, 10_000);
    }

    private MyRedisServer(String host, int port, int maxValueBytes, int maxArrayElements, int maxConnections) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.host = host;
        this.port = port;
        this.maxValueBytes = maxValueBytes;
        this.maxArrayElements = maxArrayElements;
        this.authPassword = "";
        this.clientIdleTimeoutSeconds = 0;
        this.maxCommandsPerSecond = 0;
        this.connectionRegistry = new ConnectionRegistry(maxConnections);
        this.pubSubBroker = new PubSubBroker();
        this.expiration = new ExpirationManager();
        this.storage = new InMemoryStorageEngine(expiration);
        this.persistence = PersistenceManager.disabled(storage);
        this.commandParser = new CommandParser(new CommandRegistry(), storage, expiration, persistence);
        this.expirationScheduler = new ExpirationScheduler(expiration, storage);
    }

    public MyRedisServer(int port, InMemoryStorageEngine storage, CommandParser commandParser,
                         ExpirationManager expiration, PersistenceManager persistence) {
        this("127.0.0.1", port, storage, commandParser, expiration, persistence);
    }

    public MyRedisServer(String host, int port, InMemoryStorageEngine storage, CommandParser commandParser,
                         ExpirationManager expiration, PersistenceManager persistence) {
        this(host, port, storage, commandParser, expiration, persistence,
                RespLimits.DEFAULT_MAX_VALUE_BYTES, RespLimits.DEFAULT_MAX_ARRAY_ELEMENTS, 10_000);
    }

    public MyRedisServer(String host, int port, InMemoryStorageEngine storage, CommandParser commandParser,
                         ExpirationManager expiration, PersistenceManager persistence,
                         int maxValueBytes, int maxArrayElements) {
        this(host, port, storage, commandParser, expiration, persistence,
                maxValueBytes, maxArrayElements, 10_000);
    }

    public MyRedisServer(String host, int port, InMemoryStorageEngine storage, CommandParser commandParser,
                         ExpirationManager expiration, PersistenceManager persistence,
                         int maxValueBytes, int maxArrayElements, int maxConnections) {
        this(host, port, storage, commandParser, expiration, persistence,
                maxValueBytes, maxArrayElements, maxConnections, "");
    }

    public MyRedisServer(String host, int port, InMemoryStorageEngine storage, CommandParser commandParser,
                         ExpirationManager expiration, PersistenceManager persistence,
                         int maxValueBytes, int maxArrayElements, int maxConnections, String authPassword) {
        this(host, port, storage, commandParser, expiration, persistence, maxValueBytes, maxArrayElements,
                maxConnections, authPassword, 0);
    }

    public MyRedisServer(String host, int port, InMemoryStorageEngine storage, CommandParser commandParser,
                         ExpirationManager expiration, PersistenceManager persistence,
                         int maxValueBytes, int maxArrayElements, int maxConnections, String authPassword,
                         int clientIdleTimeoutSeconds) {
        this(host, port, storage, commandParser, expiration, persistence, maxValueBytes, maxArrayElements,
                maxConnections, authPassword, clientIdleTimeoutSeconds, 0);
    }

    public MyRedisServer(String host, int port, InMemoryStorageEngine storage, CommandParser commandParser,
                         ExpirationManager expiration, PersistenceManager persistence,
                         int maxValueBytes, int maxArrayElements, int maxConnections, String authPassword,
                         int clientIdleTimeoutSeconds, int maxCommandsPerSecond) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.host = host;
        this.port = port;
        this.maxValueBytes = maxValueBytes;
        this.maxArrayElements = maxArrayElements;
        this.authPassword = authPassword == null ? "" : authPassword;
        if (clientIdleTimeoutSeconds < 0) {
            throw new IllegalArgumentException("client idle timeout must not be negative");
        }
        if (clientIdleTimeoutSeconds > Integer.MAX_VALUE / 1_000) {
            throw new IllegalArgumentException("client idle timeout is too large");
        }
        this.clientIdleTimeoutSeconds = clientIdleTimeoutSeconds;
        if (maxCommandsPerSecond < 0) throw new IllegalArgumentException("max commands per second must not be negative");
        this.maxCommandsPerSecond = maxCommandsPerSecond;
        this.connectionRegistry = new ConnectionRegistry(maxConnections);
        this.pubSubBroker = new PubSubBroker();
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

        try (ServerSocket socket = new ServerSocket()) {
            connectionRegistry.open();
            socket.bind(new InetSocketAddress(host, port));
            serverSocket = socket;
            LOGGER.info("MyRedis listening on port {}", socket.getLocalPort());
            while (running.get()) {
                try {
                    Socket client = socket.accept();
                    if (!connectionRegistry.register(client)) {
                        LOGGER.warn("Maximum client connection limit reached; rejecting {}", client.getRemoteSocketAddress());
                        client.close();
                        continue;
                    }
                    commandParser.metrics().clientConnected();
                    clientExecutor.submit(new ClientHandler(client, connectionRegistry, commandParser,
                            maxValueBytes, maxArrayElements, pubSubBroker, authPassword, clientIdleTimeoutSeconds,
                            maxCommandsPerSecond));
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
            shutdownClients();
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
        shutdownClients();
        expirationScheduler.close();
    }

    private void shutdownClients() {
        clientExecutor.shutdownNow();
        try {
            if (!clientExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                LOGGER.warn("Client handlers did not terminate before shutdown timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("Interrupted while waiting for client handlers to terminate");
        }
    }

    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? port : socket.getLocalPort();
    }

}
