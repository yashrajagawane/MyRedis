package com.myredis.protocol;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Minimal RESP2 decoder for command arrays and bulk-string arguments. */
public final class RespDecoder {
    private final BufferedInputStream input;

    public RespDecoder(InputStream input) {
        this.input = input instanceof BufferedInputStream buffered ? buffered : new BufferedInputStream(input);
    }

    public List<String> readCommand() throws IOException {
        int prefix = input.read();
        if (prefix < 0) return null;
        if (prefix != '*') throw new ProtocolException("expected RESP array");
        return readCommandBody();
    }

    public List<String> readCommandAfterPrefix() throws IOException {
        return readCommandBody();
    }

    private List<String> readCommandBody() throws IOException {
        int count = Integer.parseInt(readLine());
        if (count < 1) throw new ProtocolException("command array cannot be empty");
        List<String> command = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (input.read() != '$') throw new ProtocolException("command arguments must be bulk strings");
            int length = Integer.parseInt(readLine());
            if (length < 0) throw new ProtocolException("null command argument");
            byte[] bytes = input.readNBytes(length);
            if (bytes.length != length || input.read() != '\r' || input.read() != '\n') {
                throw new ProtocolException("incomplete bulk string");
            }
            command.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return List.copyOf(command);
    }

    public String readPlainLine(int firstByte) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(firstByte);
        int next;
        while ((next = input.read()) >= 0) {
            if (next == '\n') break;
            if (next != '\r') bytes.write(next);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    public int readFirstByte() throws IOException { return input.read(); }

    private String readLine() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int current;
        while ((current = input.read()) >= 0) {
            if (current == '\r') {
                if (input.read() != '\n') throw new ProtocolException("invalid RESP line ending");
                return bytes.toString(StandardCharsets.US_ASCII);
            }
            bytes.write(current);
        }
        throw new ProtocolException("unexpected end of RESP input");
    }
}
