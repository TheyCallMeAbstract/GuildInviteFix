package com.ginv.testing;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gateway's SkyBlock-verdict gating (T14): {@code isSkyBlock()} is
 * {@code active && skyBlock}, so an inactive gateway can never leak a
 * fixture verdict, and {@code reset()} drops the flag, the roster and the
 * recorded sends. {@code install()} itself throws outside a dev world, so
 * the fixture is driven through the backing fields instead of a client.
 */
class GuildTestGatewayTest {

    @AfterEach
    void cleanUp() {
        GuildTestGateway.reset();
    }

    @Test
    void verdictNeverLeaksWhileTheGatewayIsInactive() throws Exception {
        setFlag("skyBlock", true);
        assertFalse(GuildTestGateway.isSkyBlock(),
                "an inactive gateway must never report SkyBlock");
    }

    @Test
    void verdictOverrideSurfacesOnlyWhileActive() throws Exception {
        setFlag("active", true);
        setFlag("skyBlock", true);
        assertTrue(GuildTestGateway.isSkyBlock());
        GuildTestGateway.reset();
        assertFalse(GuildTestGateway.isSkyBlock());
        assertFalse(GuildTestGateway.isActive());
    }

    @Test
    void resetDropsTheFixtureAndRecordedSends() throws Exception {
        setFlag("active", true);
        GuildTestGateway.record("Fixture");
        assertFalse(GuildTestGateway.sentInvites().isEmpty());
        GuildTestGateway.reset();
        assertTrue(GuildTestGateway.sentInvites().isEmpty());
        assertTrue(GuildTestGateway.roster().isEmpty());
        assertFalse(GuildTestGateway.isActive());
    }

    @Test
    void recordIsOrderPreservingWhileActive() throws Exception {
        setFlag("active", true);
        GuildTestGateway.record("First");
        GuildTestGateway.record("Second");
        assertEquals(java.util.List.of("First", "Second"),
                GuildTestGateway.sentInvites());
    }

    private static void setFlag(String name, boolean value) throws Exception {
        Field field = GuildTestGateway.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(null, value);
    }
}
