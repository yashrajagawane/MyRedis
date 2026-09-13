package com.myredis;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Minimal container health probe using the server's plain PING protocol. */
public final class HealthCheck {
    static final int DEFAULT_PORT = 6379;

    private HealthCheck() {
    }

    public static void main(String[] args) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", portFromEnvironment(System.getenv())), 2_000);
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

    static int portFromEnvironment(Map<String, String> environment) {
        String value = environment.get("MYREDIS_PORT");
        if (value == null || value.isBlank()) return DEFAULT_PORT;
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) throw new NumberFormatException();
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("MYREDIS_PORT must be between 1 and 65535", exception);
        }
    }
}
