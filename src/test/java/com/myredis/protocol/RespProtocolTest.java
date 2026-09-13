package com.myredis.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.myredis.command.CommandResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RespProtocolTest {
    @Test
    void decodesCommandArrays() throws Exception {
        byte[] request = "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n".getBytes(StandardCharsets.UTF_8);

        assertEquals(List.of("SET", "key", "value"),
                new RespDecoder(new ByteArrayInputStream(request)).readCommand());
    }

    @Test
    void decodesPipelinedCommandsAcrossFragmentedReads() throws Exception {
        byte[] request = ("*1\r\n$4\r\nPING\r\n"
                + "*2\r\n$3\r\nGET\r\n$3\r\nkey\r\n").getBytes(StandardCharsets.UTF_8);
        RespDecoder decoder = new RespDecoder(new FragmentedInputStream(request, 2));

        assertEquals(List.of("PING"), decoder.readCommand());
        assertEquals(List.of("GET", "key"), decoder.readCommand());
        assertEquals(null, decoder.readCommand());
    }

    @Test
    void rejectsNonBulkElementsAsProtocolErrors() {
        byte[] request = ("*1\r\n+PING\r\n*1\r\n$4\r\nPING\r\n").getBytes(StandardCharsets.UTF_8);

        assertThrows(ProtocolException.class, () -> new RespDecoder(
                new ByteArrayInputStream(request)).readCommand());
    }

    @Test
    void encodesScalarAndArrayResponses() {
        RespEncoder encoder = new RespEncoder();

        assertEquals("+OK\r\n", new String(encoder.encode(CommandResult.ok(), "SET"), StandardCharsets.UTF_8));
        assertEquals("+OK\r\n", new String(encoder.encode(CommandResult.ok(), "QUIT"), StandardCharsets.UTF_8));
        assertEquals(":2\r\n", new String(encoder.encode(new CommandResult("2"), "LLEN"), StandardCharsets.UTF_8));
        assertEquals("$5\r\nvalue\r\n", new String(
                encoder.encode(new CommandResult("value"), "GET"), StandardCharsets.UTF_8));
        assertEquals("*2\r\n$1\r\na\r\n$1\r\nb\r\n", new String(
                encoder.encode(new CommandResult("a b"), "LRANGE"), StandardCharsets.UTF_8));
        assertEquals("*2\r\n$11\r\nhello world\r\n$0\r\n\r\n", new String(
                encoder.encode(CommandResult.array(List.of("hello world", "")), "LRANGE"),
                StandardCharsets.UTF_8));
    }

    @Test
    void encodesPingWithoutMessageAsSimpleStringAndWithMessageAsBulkString() {
        RespEncoder encoder = new RespEncoder();

        assertEquals("+PONG\r\n", new String(
                encoder.encode(new CommandResult("PONG"), "PING", List.of()), StandardCharsets.UTF_8));
        assertEquals("$11\r\nhello world\r\n", new String(
                encoder.encode(new CommandResult("hello world"), "PING", List.of("hello world")),
                StandardCharsets.UTF_8));
    }

    @Test
    void encodesPingMessagesContainingLineBreaksAsSafeBulkStrings() {
        RespEncoder encoder = new RespEncoder();

        assertEquals("$7\r\nhello\r\n\r\n", new String(
                encoder.encode(new CommandResult("hello\r\n"), "PING", List.of("hello\r\n")),
                StandardCharsets.UTF_8));
    }

    @Test
    void encodesUtf8BulkAndArrayValuesUsingByteLengths() {
        RespEncoder encoder = new RespEncoder();

        assertEquals("$18\r\nनमस्ते\r\n", new String(
                encoder.encode(new CommandResult("नमस्ते"), "GET"), StandardCharsets.UTF_8));
        assertEquals("*2\r\n$6\r\n你好\r\n$6\r\n世界\r\n", new String(
                encoder.encode(CommandResult.array(List.of("你好", "世界")), "LRANGE"),
                StandardCharsets.UTF_8));
    }

    @Test
    void rejectsRequestsAboveConfiguredLimits() {
        byte[] largeBulk = "*1\r\n$5\r\nhello\r\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class, () -> new RespDecoder(
                new ByteArrayInputStream(largeBulk), 4, 1).readCommand());

        byte[] largeArray = "*2\r\n$1\na\r\n$1\nb\r\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class, () -> new RespDecoder(
                new ByteArrayInputStream(largeArray), 16, 1).readCommand());
    }

    @Test
    void rejectsMalformedRespMetadataAsProtocolErrors() {
        byte[] invalidArrayLength = "*not-a-number\r\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class, () -> new RespDecoder(
                new ByteArrayInputStream(invalidArrayLength)).readCommand());

        byte[] negativeBulkLength = "*1\r\n$-1\r\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class, () -> new RespDecoder(
                new ByteArrayInputStream(negativeBulkLength)).readCommand());
    }

    @Test
    void rejectsNullAndIncompleteBulkArguments() {
        byte[] nullArgument = "*1\r\n$-1\r\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class, () -> new RespDecoder(
                new ByteArrayInputStream(nullArgument)).readCommand());

        byte[] incompleteArgument = "*1\r\n$4\r\nGET\r\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class, () -> new RespDecoder(
                new ByteArrayInputStream(incompleteArgument)).readCommand());
    }

    @Test
    void rejectsPlainCommandsTruncatedBeforeLineEnding() throws Exception {
        RespDecoder decoder = new RespDecoder(
                new ByteArrayInputStream("PING".getBytes(StandardCharsets.UTF_8)));

        assertThrows(ProtocolException.class, () -> decoder.readPlainLine(decoder.readFirstByte()));
    }

    private static final class FragmentedInputStream extends InputStream {
        private final byte[] data;
        private final int chunkSize;
        private int offset;

        private FragmentedInputStream(byte[] data, int chunkSize) {
            this.data = data;
            this.chunkSize = chunkSize;
        }

        @Override
        public int read() {
            return offset < data.length ? data[offset++] & 0xff : -1;
        }

        @Override
        public int read(byte[] target, int targetOffset, int length) throws IOException {
            if (offset >= data.length) return -1;
            int count = Math.min(Math.min(length, chunkSize), data.length - offset);
            System.arraycopy(data, offset, target, targetOffset, count);
            offset += count;
            return count;
        }
    }
}
