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
    public static final int DEFAULT_MAX_VALUE_BYTES = 16 * 1024 * 1024;
    public static final int DEFAULT_MAX_ARRAY_ELEMENTS = 1_024;
    private static final int MAX_PROTOCOL_LINE_BYTES = 128;
    private final BufferedInputStream input;
    private final int maxValueBytes;
    private final int maxArrayElements;

    public RespDecoder(InputStream input) {
        this(input, DEFAULT_MAX_VALUE_BYTES, DEFAULT_MAX_ARRAY_ELEMENTS);
    }

    public RespDecoder(InputStream input, int maxValueBytes, int maxArrayElements) {
        if (maxValueBytes < 1 || maxArrayElements < 1) {
            throw new IllegalArgumentException("decoder limits must be positive");
        }
        this.input = input instanceof BufferedInputStream buffered ? buffered : new BufferedInputStream(input);
        this.maxValueBytes = maxValueBytes;
        this.maxArrayElements = maxArrayElements;
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
        int count = parseProtocolInteger(readLine(), "invalid RESP array length");
        if (count < 1) throw new ProtocolException("command array cannot be empty");
        if (count > maxArrayElements) throw new ProtocolException("command array is too large");
        List<String> command = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (input.read() != '$') throw new ProtocolException("command arguments must be bulk strings");
            int length = parseProtocolInteger(readLine(), "invalid RESP bulk length");
            if (length < 0) throw new ProtocolException("null command argument");
            if (length > maxValueBytes) throw new ProtocolException("bulk string is too large");
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
            if (bytes.size() > maxValueBytes) throw new ProtocolException("plain command is too large");
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
            if (bytes.size() > MAX_PROTOCOL_LINE_BYTES) throw new ProtocolException("RESP line is too long");
        }
        throw new ProtocolException("unexpected end of RESP input");
    }

    private static int parseProtocolInteger(String value, String message) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ProtocolException(message);
        }
    }
}
