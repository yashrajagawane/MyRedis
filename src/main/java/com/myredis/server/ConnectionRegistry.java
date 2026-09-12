package com.myredis.server;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tracks open client sockets so shutdown can interrupt every connection. */
public final class ConnectionRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConnectionRegistry.class);
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final int maxConnections;
    private boolean accepting = true;

    public ConnectionRegistry(int maxConnections) {
        if (maxConnections < 1) throw new IllegalArgumentException("max connections must be positive");
        this.maxConnections = maxConnections;
    }

    public synchronized boolean register(Socket socket) {
        if (!accepting || sockets.size() >= maxConnections) return false;
        return sockets.add(socket);
    }
    public void unregister(Socket socket) { sockets.remove(socket); }

    public synchronized void closeAll() {
        accepting = false;
        for (Socket socket : sockets) {
            try { socket.close(); }
            catch (IOException exception) { LOGGER.debug("Could not close client socket", exception); }
        }
        sockets.clear();
    }

    public synchronized void open() {
        accepting = true;
    }
}
