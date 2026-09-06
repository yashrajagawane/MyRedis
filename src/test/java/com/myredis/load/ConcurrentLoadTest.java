package com.myredis.load;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.myredis.server.MyRedisServer;
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

class ConcurrentLoadTest {
    @Test
    void handlesOneHundredConcurrentSetGetClients() throws Exception {
        MyRedisServer server = new MyRedisServer(0);
        CompletableFuture<Void> serverTask = CompletableFuture.runAsync(() -> {
            try {
                server.start();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        try {
            while (server.getPort() == 0) Thread.sleep(10);
            CompletableFuture<?>[] clients = IntStream.range(0, 100)
                    .mapToObj(index -> CompletableFuture.runAsync(() -> runClient(server, index)))
                    .toArray(CompletableFuture[]::new);
            CompletableFuture.allOf(clients).get(10, TimeUnit.SECONDS);
        } finally {
            server.stop();
        }
        serverTask.get(2, TimeUnit.SECONDS);
    }

    private static void runClient(MyRedisServer server, int index) {
        try (Socket socket = new Socket("localhost", server.getPort());
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                     socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader reader = new BufferedReader(new InputStreamReader(
                     socket.getInputStream(), StandardCharsets.UTF_8))) {
            writer.write("SET load" + index + " value" + index + "\r\n");
            writer.flush();
            assertEquals("OK", reader.readLine());
            writer.write("GET load" + index + "\r\n");
            writer.flush();
            assertEquals("value" + index, reader.readLine());
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
