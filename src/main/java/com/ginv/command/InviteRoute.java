package com.ginv.command;

import com.ginv.testing.GuildTestGateway;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * The single network edge of the invitation route.
 *
 * <p>Production behaviour is unchanged — send {@code /guild invite <name>}
 * through the client connection. When the test gateway is installed (dev
 * environment + singleplayer only) the invite is recorded instead, so the
 * uitest scenarios can assert the full queue → scheduler → send path without
 * a Hypixel server. Everything upstream (queue, delays, freeze,
 * whitelist/blacklist, store recording) stays real in both modes.
 */
public final class InviteRoute {

    private InviteRoute() {
    }

    /**
     * Sends one guild invite.
     *
     * @return {@code true} if the invite was delivered (or recorded by the
     *         test gateway) and should be counted in the store
     */
    public static boolean send(String name) {
        if (GuildTestGateway.isActive()) {
            GuildTestGateway.record(name);
            return true;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return false;
        connection.sendCommand("guild invite " + name);
        return true;
    }
}
