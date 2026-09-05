package com.myredis.command;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Maps command names to factories so new commands can be registered additively. */
public final class CommandRegistry {
    private final Map<String, Supplier<Command>> factories = new LinkedHashMap<>();

    public CommandRegistry() {
        register("PING", PingCommand::new);
        register("SET", SetCommand::new);
        register("GET", GetCommand::new);
        register("DEL", DelCommand::new);
        register("EXISTS", ExistsCommand::new);
    }

    public void register(String name, Supplier<Command> factory) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("command name cannot be blank");
        }
        factories.put(name.toUpperCase(), factory);
    }

    Optional<Command> find(String name) {
        Supplier<Command> factory = factories.get(name);
        return factory == null ? Optional.empty() : Optional.of(factory.get());
    }
}
