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

    public void register(Socket socket) { sockets.add(socket); }
    public void unregister(Socket socket) { sockets.remove(socket); }

    public void closeAll() {
        for (Socket socket : sockets) {
            try { socket.close(); }
            catch (IOException exception) { LOGGER.debug("Could not close client socket", exception); }
        }
        sockets.clear();
    }
}
