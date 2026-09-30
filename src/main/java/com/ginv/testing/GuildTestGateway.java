package com.ginv.testing;

import com.ginv.utils.GuildDirectory;
import com.lowdragmc.lowdraglib2.Platform;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Singleplayer-only test doubles for the invitation route.
 *
 * <p>Installed by the uitest scenarios (and only reachable in a dev
 * environment running a singleplayer world — {@link #install} throws
 * otherwise, so production builds cannot activate it by accident):
 *
 * <ul>
 *   <li><b>roster</b> — fake tab-list entries with guild levels, served by
 *       {@link GuildDirectory} in place of the real tab list;</li>
 *   <li><b>SkyBlock</b> — overrides {@link com.ginv.utils.SkyBlockDetector}
 *       so queue-by-level preconditions pass outside Hypixel;</li>
 *   <li><b>sent invites</b> — {@link com.ginv.command.InviteRoute} records
 *       sends here instead of hitting a server, so scenarios can assert the
 *       exact names the scheduler emitted, in order.</li>
 * </ul>
 */
public final class GuildTestGateway {

    private static volatile boolean active;
    private static volatile List<GuildDirectory.Entry> roster = List.of();
    private static volatile boolean skyBlock;
    private static final CopyOnWriteArrayList<String> sentInvites = new CopyOnWriteArrayList<>();

    private GuildTestGateway() {
    }

    /**
     * Activates the gateway with a fixture roster and a SkyBlock verdict.
     *
     * @throws IllegalStateException outside a dev environment or outside a
     *         singleplayer world — test doubles must never run in production
     */
    public static void install(List<GuildDirectory.Entry> fixtureRoster, boolean skyBlockOverride) {
        if (!Platform.isDevEnv()) {
            throw new IllegalStateException(
                    "GuildTestGateway is restricted to a development environment");
        }
        if (!Minecraft.getInstance().hasSingleplayerServer()) {
            throw new IllegalStateException(
                    "GuildTestGateway requires a singleplayer world (the mock invite route must never touch a real server)");
        }
        roster = List.copyOf(fixtureRoster);
        skyBlock = skyBlockOverride;
        sentInvites.clear();
        active = true;
    }

    /** Deactivates the gateway and drops all fixtures and recorded sends. */
    public static void reset() {
        active = false;
        roster = List.of();
        skyBlock = false;
        sentInvites.clear();
    }

    public static boolean isActive() {
        return active;
    }

    /** The fixture roster {@link GuildDirectory} serves while active. */
    public static List<GuildDirectory.Entry> roster() {
        return roster;
    }

    /** The mocked SkyBlock verdict, only consulted while active. */
    public static boolean isSkyBlock() {
        return active && skyBlock;
    }

    /** Records one send performed by the scheduler through {@code InviteRoute}. */
    public static void record(String name) {
        sentInvites.add(name);
    }

    /** Invites recorded so far, in send order. */
    public static List<String> sentInvites() {
        return List.copyOf(sentInvites);
    }
}
