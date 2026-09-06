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
        Properties values = defaults();
        Path configPath = findConfigPath(args);
        if (configPath != null) {
            try (InputStream input = Files.newInputStream(configPath)) {
                values.putAll(loadProperties(input));
            }
        }
        applyEnvironment(values, System.getenv());
        applyArguments(values, args);
        return new ServerConfig(values.getProperty("host"), Integer.parseInt(values.getProperty("port")),
                Boolean.parseBoolean(values.getProperty("aof.enabled")), Path.of(values.getProperty("aof.path")),
                Path.of(values.getProperty("snapshot.path")),
                FsyncPolicy.valueOf(values.getProperty("aof.fsync").toUpperCase(Locale.ROOT)),
                Long.parseLong(values.getProperty("snapshot.interval.seconds")), values.getProperty("log.level"),
                Integer.parseInt(values.getProperty("limits.max.value.bytes")),
                Integer.parseInt(values.getProperty("limits.max.array.elements")));
    }

    static Properties defaults() {
        Properties values = new Properties();
        values.setProperty("host", "0.0.0.0");
        values.setProperty("port", "6379");
        values.setProperty("aof.enabled", "true");
        values.setProperty("aof.path", "data/myredis.aof");
        values.setProperty("aof.fsync", "ALWAYS");
        values.setProperty("snapshot.path", "data/myredis.snapshot");
        values.setProperty("snapshot.interval.seconds", "60");
        values.setProperty("log.level", "INFO");
        values.setProperty("limits.max.value.bytes", "16777216");
        values.setProperty("limits.max.array.elements", "1024");
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
    }

    private static void map(Properties values, Map<String, String> environment, String env, String key) {
        String value = environment.get(env);
        if (value != null && !value.isBlank()) values.setProperty(key, value);
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
