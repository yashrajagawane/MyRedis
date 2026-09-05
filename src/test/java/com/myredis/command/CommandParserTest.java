package com.myredis.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import com.myredis.storage.InMemoryStorageEngine;

class CommandParserTest {
    private final CommandParser parser = new CommandParser(
            new CommandRegistry(), new InMemoryStorageEngine());

    @Test
    void parsesCommandCaseInsensitively() {
        CommandParser.ParsedCommand parsed = parser.parse("set key value");

        assertEquals("SET", parsed.name());
        assertEquals("OK", parsed.execute().response());
    }

    @Test
    void validatesCommandArguments() {
        assertEquals("-ERR wrong number of arguments for 'get' command",
                parser.parse("GET").execute().response());
        assertEquals("-ERR wrong number of arguments for 'set' command",
                parser.parse("SET key").execute().response());
        assertEquals("-ERR wrong number of arguments for 'del' command",
                parser.parse("DEL").execute().response());
    }

    @Test
    void supportsPhaseTwoCommandShapes() {
        assertEquals("PONG", parser.parse("PING").execute().response());
        assertEquals("value", parser.parse("PING value").execute().response());
        assertEquals("(nil)", parser.parse("GET key").execute().response());
        assertEquals("0", parser.parse("EXISTS key").execute().response());
        assertEquals("0", parser.parse("DEL key").execute().response());
    }

    @Test
    void storesAndReadsValuesThroughCommands() {
        assertEquals("OK", parser.parse("SET key value").execute().response());
        assertEquals("value", parser.parse("GET key").execute().response());
        assertEquals("1", parser.parse("EXISTS key").execute().response());
        assertEquals("1", parser.parse("DEL key").execute().response());
        assertEquals("(nil)", parser.parse("GET key").execute().response());
    }

    @Test
    void rejectsBlankAndUnknownCommands() {
        assertThrows(CommandParseException.class, () -> parser.parse("  "));
        assertThrows(CommandParseException.class, () -> parser.parse("NOPE"));
    }
}
