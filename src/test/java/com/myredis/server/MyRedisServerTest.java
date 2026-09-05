package com.myredis.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MyRedisServerTest {
    @Test
    void acknowledgesInputAndStopsCleanly() throws Exception {
        MyRedisServer server = new MyRedisServer(0);
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                server.start();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        while (server.getPort() == 0) {
            Thread.sleep(10);
        }

        try (Socket client = new Socket("localhost", server.getPort());
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write("hello\r\n");
            writer.flush();
            assertEquals("OK hello", reader.readLine());
        } finally {
            server.stop();
        }

        serverTask.get(2, TimeUnit.SECONDS);
    }
}
