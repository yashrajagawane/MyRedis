package com.myredis;

import com.myredis.server.MyRedisServer;

/** Application entry point for the Phase 1 TCP server. */
public final class MyRedisApplication {
    private MyRedisApplication() {
    }

    public static void main(String[] args) throws Exception {
        MyRedisServer server = new MyRedisServer(6379);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "myredis-shutdown"));
        server.start();
    }
}
