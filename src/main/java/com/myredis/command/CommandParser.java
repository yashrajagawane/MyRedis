package com.myredis.command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import com.myredis.storage.StorageEngine;
import com.myredis.storage.InMemoryStorageEngine;
import com.myredis.expiration.ExpirationManager;
import com.myredis.persistence.PersistenceManager;
import com.myredis.observability.ServerMetrics;

/** Converts the Phase 2 whitespace command format into an executable command. */
public final class CommandParser {
    private final CommandRegistry registry;
    private final StorageEngine storage;
    private final ExpirationManager expiration;
    private final PersistenceManager persistence;
    private final ServerMetrics metrics;

    public CommandParser(CommandRegistry registry, StorageEngine storage) {
        this(registry, storage, storage instanceof InMemoryStorageEngine inMemory
                ? inMemory.expirationManager() : new ExpirationManager(),
                storage instanceof InMemoryStorageEngine inMemory
                        ? PersistenceManager.disabled(inMemory) : null);
    }

    public CommandParser(CommandRegistry registry, StorageEngine storage, ExpirationManager expiration) {
        this(registry, storage, expiration, storage instanceof InMemoryStorageEngine inMemory
                ? PersistenceManager.disabled(inMemory) : null);
    }

    public CommandParser(CommandRegistry registry, StorageEngine storage,
                         ExpirationManager expiration, PersistenceManager persistence) {
        this(registry, storage, expiration, persistence, new ServerMetrics());
    }

    public CommandParser(CommandRegistry registry, StorageEngine storage,
                         ExpirationManager expiration, PersistenceManager persistence, ServerMetrics metrics) {
        this.registry = registry;
        this.storage = storage;
        this.expiration = expiration;
        this.persistence = persistence;
        this.metrics = metrics;
    }

    public ParsedCommand parse(String input) {
        if (input == null || input.isBlank()) {
            throw new CommandParseException("empty command");
        }

        List<String> tokens = Arrays.stream(input.trim().split("\\s+")).toList();
        return parse(tokens);
    }

    public ServerMetrics metrics() {
        return metrics;
    }

    public ParsedCommand parse(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) throw new CommandParseException("empty command");
        String name = tokens.getFirst().toUpperCase(Locale.ROOT);
        Command command = registry.find(name)
                .orElseThrow(() -> new CommandParseException(
                        "unknown command '" + tokens.getFirst() + "'"));
        return new ParsedCommand(name, command, tokens.subList(1, tokens.size()), storage, expiration, persistence,
                metrics);
    }

    public record ParsedCommand(
            String name, Command command, List<String> arguments, StorageEngine storage,
            ExpirationManager expiration, PersistenceManager persistence, ServerMetrics metrics) {
        public ParsedCommand {
            arguments = List.copyOf(arguments);
            if (storage == null) {
                throw new IllegalArgumentException("storage is required");
            }
            if (expiration == null) throw new IllegalArgumentException("expiration is required");
            if (persistence == null) throw new IllegalArgumentException("persistence is required");
            if (metrics == null) throw new IllegalArgumentException("metrics are required");
        }

        public CommandResult execute() {
            metrics.recordCommand(name);
            long startedAt = System.nanoTime();
            try {
                if (!isMutating(name)) return executeCommand();
                return persistence.withMutation(() -> {
                    CommandResult result = executeCommand();
                    if (!result.response().startsWith("-ERR")) {
                        persistence.record(java.util.stream.Stream.concat(
                                java.util.stream.Stream.of(name), arguments.stream()).toList());
                    }
                    return result;
                });
            } finally {
                metrics.recordCommandLatency(System.nanoTime() - startedAt);
            }
        }

        public CommandResult executeWithoutPersistence() {
            return executeCommand();
        }

        private CommandResult executeCommand() {
            return command.execute(new CommandContext(arguments, storage, expiration, persistence, metrics));
        }

        private static boolean isMutating(String command) {
            return java.util.Set.of("SET", "DEL", "LPUSH", "RPUSH", "LPOP", "RPOP", "SADD", "SREM",
                    "HSET", "HDEL", "ZADD", "ZREM", "EXPIRE", "PEXPIRE", "PERSIST", "INCR", "DECR", "INCRBY")
                    .contains(command);
        }
    }
}
