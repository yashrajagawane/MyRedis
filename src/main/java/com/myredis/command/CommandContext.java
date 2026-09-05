package com.myredis.command;

import com.myredis.storage.StorageEngine;
import com.myredis.expiration.ExpirationManager;
import com.myredis.persistence.PersistenceManager;
import java.util.List;

/** Per-request context passed to a command. Storage is added in Phase 3. */
public record CommandContext(List<String> arguments, StorageEngine storage, ExpirationManager expiration,
                             PersistenceManager persistence) {
    public CommandContext {
        arguments = List.copyOf(arguments);
        if (storage == null) {
            throw new IllegalArgumentException("storage is required");
        }
        if (expiration == null) {
            throw new IllegalArgumentException("expiration is required");
        }
        if (persistence == null) throw new IllegalArgumentException("persistence is required");
    }
}
