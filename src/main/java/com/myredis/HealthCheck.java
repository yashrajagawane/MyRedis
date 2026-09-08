package com.myredis;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Minimal container health probe using the server's plain PING protocol. */
public final class HealthCheck {
    private HealthCheck() {
    }

    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 6379), 2_000);
            socket.setSoTimeout(2_000);
            OutputStream output = socket.getOutputStream();
            output.write("PING\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            InputStream input = socket.getInputStream();
            byte[] response = input.readNBytes(6);
            if (!"PONG\r\n".equals(new String(response, StandardCharsets.US_ASCII))) {
                throw new IllegalStateException("unexpected health response");
            }
        }
    }
}
