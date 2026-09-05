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
        register("LPUSH", LpushCommand::new);
        register("RPUSH", RpushCommand::new);
        register("LPOP", LpopCommand::new);
        register("RPOP", RpopCommand::new);
        register("LRANGE", LrangeCommand::new);
        register("LLEN", LlenCommand::new);
        register("SADD", SaddCommand::new);
        register("SREM", SremCommand::new);
        register("SMEMBERS", SmembersCommand::new);
        register("SISMEMBER", SismemberCommand::new);
        register("SCARD", ScardCommand::new);
        register("HSET", HsetCommand::new);
        register("HGET", HgetCommand::new);
        register("HDEL", HdelCommand::new);
        register("HGETALL", HgetallCommand::new);
        register("HEXISTS", HexistsCommand::new);
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
