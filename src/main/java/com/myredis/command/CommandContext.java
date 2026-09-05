package com.myredis.command;

import com.myredis.storage.StorageEngine;
import java.util.List;

/** Per-request context passed to a command. Storage is added in Phase 3. */
public record CommandContext(List<String> arguments, StorageEngine storage) {
    public CommandContext {
        arguments = List.copyOf(arguments);
        if (storage == null) {
            throw new IllegalArgumentException("storage is required");
        }
    }
}
