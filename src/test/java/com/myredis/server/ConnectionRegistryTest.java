package com.myredis.server;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import org.junit.jupiter.api.Test;

class ConnectionRegistryTest {
    @Test
    void enforcesTheConfiguredConnectionLimit() throws Exception {
        ConnectionRegistry registry = new ConnectionRegistry(1);
        Socket first = new Socket();
        Socket second = new Socket();
        try {
            assertTrue(registry.register(first));
            assertFalse(registry.register(second));
            registry.unregister(first);
            assertTrue(registry.register(second));
        } finally {
            first.close();
            second.close();
            registry.closeAll();
        }
    }
}
