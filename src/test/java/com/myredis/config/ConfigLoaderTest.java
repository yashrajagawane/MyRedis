package com.myredis.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        Files.deleteIfExists(config);
    }
}
