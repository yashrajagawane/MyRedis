package com.myredis.command;

/** Plain-text response used by the Phase 2 protocol adapter. */
public record CommandResult(String response) {
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
