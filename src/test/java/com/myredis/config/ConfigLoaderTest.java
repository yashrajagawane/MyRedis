package com.myredis.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.myredis.persistence.FsyncPolicy;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {
    @Test
    void loadsPropertiesAndCommandLineOverrides() throws Exception {
        Path config = Files.createTempFile("myredis", ".conf");
        Files.writeString(config, "port=6380\naof.enabled=false\naof.fsync=NEVER\n");
        ServerConfig loaded = ConfigLoader.load(new String[]{"--config", config.toString(), "--port", "6381"});

        assertEquals(6381, loaded.port());
        assertFalse(loaded.aofEnabled());
        assertEquals(FsyncPolicy.NEVER, loaded.fsyncPolicy());
        assertEquals(16_777_216, loaded.maxValueBytes());
        assertEquals(1_024, loaded.maxArrayElements());
        assertEquals(10_000, loaded.maxConnections());
        assertEquals("", loaded.authPassword());
        assertEquals(0, loaded.clientIdleTimeoutSeconds());
        assertEquals(0, loaded.maxCommandsPerSecond());
        Files.deleteIfExists(config);
    }

    @Test
    void defaultsToLoopbackBindingForSafeLocalStartup() throws Exception {
        assertEquals("127.0.0.1", ConfigLoader.defaults().getProperty("host"));
    }

    @Test
    void rejectsInvalidBooleanValuesInsteadOfSilentlyDisablingAof() throws Exception {
        Path config = Files.createTempFile("myredis", ".conf");
        Files.writeString(config, "aof.enabled=treu\n");

        assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.load(new String[]{"--config", config.toString()}));
        Files.deleteIfExists(config);
    }

    @Test
    void rejectsInvalidFsyncPolicyWithActionableMessage() throws Exception {
        Path config = Files.createTempFile("myredis", ".conf");
        Files.writeString(config, "aof.fsync=ON\n");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.load(new String[]{"--config", config.toString()}));
        assertTrue(error.getMessage().contains("aof.fsync must be"));
        Files.deleteIfExists(config);
    }

    @Test
    void rejectsUnsupportedLogLevels() throws Exception {
        Path config = Files.createTempFile("myredis", ".conf");
        Files.writeString(config, "log.level=VERBOSE\n");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.load(new String[]{"--config", config.toString()}));
        assertTrue(error.getMessage().contains("log level must be"));
        Files.deleteIfExists(config);
    }

    @Test
    void rejectsBlankPersistencePathsBeforeStartup() throws Exception {
        Path config = Files.createTempFile("myredis", ".conf");
        Files.writeString(config, "aof.path=   \n");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.load(new String[]{"--config", config.toString()}));
        assertTrue(error.getMessage().contains("aof.path must not be blank"));
        Files.deleteIfExists(config);
    }

    @Test
    void rejectsConfigFlagWithoutAPath() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.load(new String[]{"--config"}));
        assertTrue(error.getMessage().contains("--config requires a file path"));
    }

    @Test
    void rejectsUnknownOptionsInsteadOfSilentlyUsingDefaults() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.load(new String[]{"--prot", "6380"}));
        assertTrue(error.getMessage().contains("unknown option: --prot"));
    }

    @Test
    void rejectsKnownOptionsWithoutValues() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.load(new String[]{"--port"}));
        assertTrue(error.getMessage().contains("--port requires a value"));
    }

    @Test
    void rejectsNegativeCommandRateLimit() throws Exception {
        Path config = Files.createTempFile("myredis", ".conf");
        Files.writeString(config, "limits.max.commands.per.second=-1\n");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ConfigLoader.load(new String[]{"--config", config.toString()}));
        assertTrue(error.getMessage().contains("max commands per second"));
        Files.deleteIfExists(config);
    }
}
