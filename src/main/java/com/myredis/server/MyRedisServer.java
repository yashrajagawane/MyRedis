package com.myredis.server;

import com.myredis.command.CommandParser;
import com.myredis.command.CommandRegistry;
import com.myredis.storage.InMemoryStorageEngine;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Blocking TCP server used as the networking baseline for Phase 1. */
public final class MyRedisServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyRedisServer.class);

    private final int port;
    private final AtomicBoolean running = new AtomicBoolean();
    private final Set<Socket> clientSockets = ConcurrentHashMap.newKeySet();
    private final CommandParser commandParser = new CommandParser(
            new CommandRegistry(), new InMemoryStorageEngine());
    private volatile ServerSocket serverSocket;

    public MyRedisServer(int port) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        this.port = port;
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
                    clientSockets.add(client);
                    Thread.startVirtualThread(new ClientHandler(client, clientSockets, commandParser));
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
            closeClients();
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
        closeClients();
    }

    public int getPort() {
        ServerSocket socket = serverSocket;
        return socket == null ? port : socket.getLocalPort();
    }

    private void closeClients() {
        for (Socket client : clientSockets) {
            try {
                client.close();
            } catch (IOException exception) {
                LOGGER.debug("Could not close client socket", exception);
            }
        }
        clientSockets.clear();
    }
}
