package com.myredis.command;

public final class CommandParseException extends RuntimeException {
    public CommandParseException(String message) {
        super(message);
    }
}
