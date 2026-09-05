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
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MyRedisServerTest {
    @Test
    void acknowledgesInputAndStopsCleanly() throws Exception {
        MyRedisServer server = new MyRedisServer(0);
        CompletableFuture<Void> serverTask = startAsync(server);

        while (server.getPort() == 0) {
            Thread.sleep(10);
        }

        try (Socket client = new Socket("localhost", server.getPort());
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write("PING\r\n");
            writer.flush();
            assertEquals("PONG", reader.readLine());
        } finally {
            server.stop();
        }

        serverTask.get(2, TimeUnit.SECONDS);
    }

    @Test
    void servesManyClientsConcurrently() throws Exception {
        MyRedisServer server = new MyRedisServer(0);
        CompletableFuture<Void> serverTask = startAsync(server);
        try {
            while (server.getPort() == 0) {
                Thread.sleep(10);
            }

            CompletableFuture<?>[] clients = IntStream.range(0, 50)
                    .mapToObj(index -> CompletableFuture.runAsync(() -> {
                        try (Socket client = new Socket("localhost", server.getPort());
                             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                                     client.getOutputStream(), StandardCharsets.UTF_8));
                             BufferedReader reader = new BufferedReader(new InputStreamReader(
                                     client.getInputStream(), StandardCharsets.UTF_8))) {
                            writer.write("SET key" + index + " value" + index + "\r\n");
                            writer.flush();
                            assertEquals("OK", reader.readLine());
                            writer.write("GET key" + index + "\r\n");
                            writer.flush();
                            assertEquals("value" + index, reader.readLine());
                        } catch (Exception exception) {
                            throw new RuntimeException(exception);
                        }
                    }))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(clients).get(5, TimeUnit.SECONDS);
        } finally {
            server.stop();
        }
        serverTask.get(2, TimeUnit.SECONDS);
    }

    private static CompletableFuture<Void> startAsync(MyRedisServer server) {
        return CompletableFuture.runAsync(() -> {
            try {
                server.start();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }
}
