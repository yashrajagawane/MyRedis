package com.myredis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class HealthCheckTest {
    @Test
    void usesDefaultPortWhenEnvironmentDoesNotOverrideIt() {
        assertEquals(6379, HealthCheck.portFromEnvironment(Map.of()));
    }

    @Test
    void followsConfiguredEnvironmentPort() {
        assertEquals(6380, HealthCheck.portFromEnvironment(Map.of("MYREDIS_PORT", "6380")));
    }

    @Test
    void rejectsInvalidEnvironmentPort() {
        assertThrows(IllegalStateException.class,
                () -> HealthCheck.portFromEnvironment(Map.of("MYREDIS_PORT", "70000")));
    }
}
