package com.myredis.command;

import java.util.Arrays;
import java.util.List;
import com.myredis.storage.StorageEngine;
import com.myredis.storage.InMemoryStorageEngine;
import com.myredis.expiration.ExpirationManager;

/** Converts the Phase 2 whitespace command format into an executable command. */
public final class CommandParser {
    private final CommandRegistry registry;
    private final StorageEngine storage;
    private final ExpirationManager expiration;

    public CommandParser(CommandRegistry registry, StorageEngine storage) {
        this(registry, storage, storage instanceof InMemoryStorageEngine inMemory
                ? inMemory.expirationManager() : new ExpirationManager());
    }

    public CommandParser(CommandRegistry registry, StorageEngine storage, ExpirationManager expiration) {
        this.registry = registry;
        this.storage = storage;
        this.expiration = expiration;
    }

    public ParsedCommand parse(String input) {
        if (input == null || input.isBlank()) {
            throw new CommandParseException("empty command");
        }

        List<String> tokens = Arrays.stream(input.trim().split("\\s+"))
                .toList();
        String name = tokens.getFirst().toUpperCase();
        Command command = registry.find(name)
                .orElseThrow(() -> new CommandParseException(
                        "unknown command '" + tokens.getFirst() + "'"));
        return new ParsedCommand(name, command, tokens.subList(1, tokens.size()), storage, expiration);
    }

    public record ParsedCommand(
            String name, Command command, List<String> arguments, StorageEngine storage,
            ExpirationManager expiration) {
        public ParsedCommand {
            arguments = List.copyOf(arguments);
            if (storage == null) {
                throw new IllegalArgumentException("storage is required");
            }
            if (expiration == null) throw new IllegalArgumentException("expiration is required");
        }

        public CommandResult execute() {
            return command.execute(new CommandContext(arguments, storage, expiration));
        }
    }
}
