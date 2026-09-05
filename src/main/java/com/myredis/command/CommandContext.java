package com.myredis.command;

import java.util.List;

/** Per-request context passed to a command. Storage is added in Phase 3. */
public record CommandContext(List<String> arguments) {
    public CommandContext {
        arguments = List.copyOf(arguments);
    }
}
