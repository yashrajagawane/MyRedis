package com.myredis.command;

import java.util.List;

/** Plain-text response used by the Phase 2 protocol adapter. */
public record CommandResult(String response, List<String> arrayValues) {
    public CommandResult(String response) {
        this(response, null);
    }

    public CommandResult {
        if (response == null) throw new IllegalArgumentException("response is required");
        if (arrayValues != null) arrayValues = List.copyOf(arrayValues);
    }

    public static CommandResult array(List<String> values) {
        List<String> copy = List.copyOf(values);
        return new CommandResult(copy.isEmpty() ? "(nil)" : String.join(" ", copy), copy);
    }

    public static CommandResult ok() {
        return new CommandResult("OK");
    }

    public static CommandResult error(String message) {
        return new CommandResult("-ERR " + message);
    }

    public static CommandResult unknownCommand(String command) {
        return error("unknown command '" + command + "'");
    }
}
