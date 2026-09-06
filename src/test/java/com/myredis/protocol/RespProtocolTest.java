package com.myredis.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.myredis.command.CommandResult;
import java.io.ByteArrayInputStream;
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
    void encodesScalarAndArrayResponses() {
        RespEncoder encoder = new RespEncoder();

        assertEquals("+OK\r\n", new String(encoder.encode(CommandResult.ok(), "SET"), StandardCharsets.UTF_8));
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
    void rejectsRequestsAboveConfiguredLimits() {
        byte[] largeBulk = "*1\r\n$5\r\nhello\r\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class, () -> new RespDecoder(
                new ByteArrayInputStream(largeBulk), 4, 1).readCommand());

        byte[] largeArray = "*2\r\n$1\na\r\n$1\nb\r\n".getBytes(StandardCharsets.UTF_8);
        assertThrows(ProtocolException.class, () -> new RespDecoder(
                new ByteArrayInputStream(largeArray), 16, 1).readCommand());
    }
}
