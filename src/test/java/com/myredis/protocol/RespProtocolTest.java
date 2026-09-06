package com.myredis.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.myredis.command.CommandResult;
import java.io.ByteArrayInputStream;
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
    }
}
