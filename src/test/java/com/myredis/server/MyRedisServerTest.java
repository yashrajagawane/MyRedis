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

    @Test
    void servesResp2Requests() throws Exception {
        MyRedisServer server = new MyRedisServer(0);
        CompletableFuture<Void> serverTask = startAsync(server);
        try {
            while (server.getPort() == 0) Thread.sleep(10);
            try (Socket client = new Socket("localhost", server.getPort());
                 java.io.InputStream input = client.getInputStream();
                 java.io.OutputStream output = client.getOutputStream()) {
                output.write("*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                assertEquals("+PONG", readRespLine(input));
                output.write("*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                assertEquals("+OK", readRespLine(input));
                output.write("*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n".getBytes(StandardCharsets.UTF_8));
                output.flush();
                assertEquals("$5", readRespLine(input));
                assertEquals("value", readRespLine(input));
            }
        } finally {
            server.stop();
        }
        serverTask.get(2, TimeUnit.SECONDS);
    }

    @Test
    void quitClosesOnlyTheRequestingClientAfterAcknowledgingIt() throws Exception {
        MyRedisServer server = new MyRedisServer(0);
        CompletableFuture<Void> serverTask = startAsync(server);
        try {
            while (server.getPort() == 0) Thread.sleep(10);
            try (Socket quittingClient = new Socket("localhost", server.getPort());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         quittingClient.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                         quittingClient.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write("QUIT\r\n");
                writer.flush();
                assertEquals("OK", reader.readLine());
                assertEquals(null, reader.readLine());
            }

            try (Socket survivingClient = new Socket("localhost", server.getPort());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         survivingClient.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                         survivingClient.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write("PING\r\n");
                writer.flush();
                assertEquals("PONG", reader.readLine());
            }
        } finally {
            server.stop();
        }
        serverTask.get(2, TimeUnit.SECONDS);
    }

    @Test
    void preservesResponseOrderForPipelinedPlainAndRespCommands() throws Exception {
        MyRedisServer server = new MyRedisServer(0);
        CompletableFuture<Void> serverTask = startAsync(server);
        try {
            while (server.getPort() == 0) Thread.sleep(10);
            try (Socket client = new Socket("localhost", server.getPort());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         client.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                         client.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write("SET pipelined plain\r\nGET pipelined\r\nPING\r\n");
                writer.flush();
                assertEquals("OK", reader.readLine());
                assertEquals("plain", reader.readLine());
                assertEquals("PONG", reader.readLine());
            }

            try (Socket client = new Socket("localhost", server.getPort());
                 java.io.InputStream input = client.getInputStream();
                 java.io.OutputStream output = client.getOutputStream()) {
                output.write(("*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n"
                        + "*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n"
                        + "*1\r\n$4\r\nPING\r\n").getBytes(StandardCharsets.UTF_8));
                output.flush();
                assertEquals("+OK", readRespLine(input));
                assertEquals("$5", readRespLine(input));
                assertEquals("value", readRespLine(input));
                assertEquals("+PONG", readRespLine(input));
            }
        } finally {
            server.stop();
        }
        serverTask.get(2, TimeUnit.SECONDS);
    }

    @Test
    void returnsAnErrorInsteadOfClosingConnectionForWrongType() throws Exception {
        MyRedisServer server = new MyRedisServer(0);
        CompletableFuture<Void> serverTask = startAsync(server);
        try {
            while (server.getPort() == 0) Thread.sleep(10);
            try (Socket client = new Socket("localhost", server.getPort());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(
                         client.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                         client.getOutputStream(), StandardCharsets.UTF_8))) {
                writer.write("LPUSH list value\r\n");
                writer.flush();
                assertEquals("1", reader.readLine());
                writer.write("GET list\r\n");
                writer.flush();
                assertEquals("-ERR WRONGTYPE key 'list' contains LIST, expected STRING", reader.readLine());
            }
        } finally {
            server.stop();
        }
        serverTask.get(2, TimeUnit.SECONDS);
    }

    private static String readRespLine(java.io.InputStream input) throws Exception {
        StringBuilder line = new StringBuilder();
        int current;
        while ((current = input.read()) >= 0 && current != '\r') {
            if (current != '\n') line.append((char) current);
        }
        if (input.read() == '\n') return line.toString();
        throw new AssertionError("incomplete RESP response");
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
