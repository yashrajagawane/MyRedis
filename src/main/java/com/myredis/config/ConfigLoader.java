package com.myredis.config;

import com.myredis.persistence.FsyncPolicy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/** Loads defaults, an optional properties file, environment values, and CLI overrides. */
public final class ConfigLoader {
    private ConfigLoader() {
    }

    public static ServerConfig load(String[] args) throws IOException {
        validateArguments(args);
        Properties values = defaults();
        Path configPath = findConfigPath(args);
        if (configPath != null) {
            try (InputStream input = Files.newInputStream(configPath)) {
                values.putAll(loadProperties(input));
            }
        }
        applyEnvironment(values, System.getenv());
        applyArguments(values, args);
        try {
            return new ServerConfig(requiredProperty(values, "host"), Integer.parseInt(requiredProperty(values, "port")),
                    parseBoolean(values, "aof.enabled"), parsePath(values, "aof.path"),
                    parsePath(values, "snapshot.path"), parseFsyncPolicy(values),
                    Long.parseLong(requiredProperty(values, "snapshot.interval.seconds")),
                    requiredProperty(values, "log.level"),
                    Integer.parseInt(requiredProperty(values, "limits.max.value.bytes")),
                    Integer.parseInt(requiredProperty(values, "limits.max.array.elements")),
                    Integer.parseInt(requiredProperty(values, "limits.max.connections")),
                    values.getProperty("auth.password", ""));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid MyRedis configuration: " + exception.getMessage(), exception);
        }
    }

    static Properties defaults() {
        Properties values = new Properties();
        values.setProperty("host", "127.0.0.1");
        values.setProperty("port", "6379");
        values.setProperty("aof.enabled", "true");
        values.setProperty("aof.path", "data/myredis.aof");
        values.setProperty("aof.fsync", "ALWAYS");
        values.setProperty("snapshot.path", "data/myredis.snapshot");
        values.setProperty("snapshot.interval.seconds", "60");
        values.setProperty("log.level", "INFO");
        values.setProperty("limits.max.value.bytes", "16777216");
        values.setProperty("limits.max.array.elements", "1024");
        values.setProperty("limits.max.connections", "10000");
        values.setProperty("auth.password", "");
        return values;
    }

    private static Properties loadProperties(InputStream input) throws IOException {
        Properties values = new Properties();
        values.load(input);
        return values;
    }

    private static Path findConfigPath(String[] args) {
        for (int index = 0; index < args.length - 1; index++) {
            if (args[index].equals("--config")) return Path.of(args[index + 1]);
        }
        Path defaultPath = Path.of("myredis.conf");
        return Files.exists(defaultPath) ? defaultPath : null;
    }

    private static void validateArguments(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("unexpected argument: " + argument);
            }
            if (argument.equals("--config")) {
                if (index == args.length - 1) throw new IllegalArgumentException("--config requires a file path");
                index++;
                continue;
            }
            if (index == args.length - 1) {
                throw new IllegalArgumentException(argument + " requires a value");
            }
            String key = argument.substring(2).replace('-', '.');
            if (!defaults().containsKey(key)) {
                throw new IllegalArgumentException("unknown option: " + argument);
            }
            index++;
        }
    }

    private static void applyEnvironment(Properties values, Map<String, String> environment) {
        map(values, environment, "MYREDIS_HOST", "host");
        map(values, environment, "MYREDIS_PORT", "port");
        map(values, environment, "MYREDIS_AOF_ENABLED", "aof.enabled");
        map(values, environment, "MYREDIS_AOF_PATH", "aof.path");
        map(values, environment, "MYREDIS_AOF_FSYNC", "aof.fsync");
        map(values, environment, "MYREDIS_SNAPSHOT_PATH", "snapshot.path");
        map(values, environment, "MYREDIS_SNAPSHOT_INTERVAL_SECONDS", "snapshot.interval.seconds");
        map(values, environment, "MYREDIS_LOG_LEVEL", "log.level");
        map(values, environment, "MYREDIS_MAX_VALUE_BYTES", "limits.max.value.bytes");
        map(values, environment, "MYREDIS_MAX_ARRAY_ELEMENTS", "limits.max.array.elements");
        map(values, environment, "MYREDIS_MAX_CONNECTIONS", "limits.max.connections");
        map(values, environment, "MYREDIS_AUTH_PASSWORD", "auth.password");
    }

    private static void map(Properties values, Map<String, String> environment, String env, String key) {
        String value = environment.get(env);
        if (value != null && !value.isBlank()) values.setProperty(key, value);
    }

    private static boolean parseBoolean(Properties values, String key) {
        String value = requiredProperty(values, key);
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        throw new IllegalArgumentException(key + " must be true or false");
    }

    private static FsyncPolicy parseFsyncPolicy(Properties values) {
        String value = requiredProperty(values, "aof.fsync");
        try {
            return FsyncPolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("aof.fsync must be ALWAYS, EVERY_SECOND, or NEVER", exception);
        }
    }

    private static Path parsePath(Properties values, String key) {
        String value = requiredProperty(values, key);
        try {
            return Path.of(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(key + " must be a valid filesystem path", exception);
        }
    }

    private static String requiredProperty(Properties values, String key) {
        String value = values.getProperty(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " must not be blank");
        return value;
    }

    private static void applyArguments(Properties values, String[] args) {
        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if (argument.equals("--config")) index++;
            else if (argument.startsWith("--") && index + 1 < args.length) {
                String key = argument.substring(2).replace('-', '.');
                if (values.containsKey(key)) values.setProperty(key, args[++index]);
            }
        }
    }
}
