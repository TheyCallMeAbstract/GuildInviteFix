package com.ginv.command;

import com.ginv.data.GinvDataStore;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GinvCommand {

    private static final Set<String> ginvTargets = new LinkedHashSet<>();
    private static final LinkedList<String> pendingInvites = new LinkedList<>();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GinvScheduler");
        t.setDaemon(true);
        return t;
    });
    private static final Random random = new Random();

    private static volatile boolean frozen = false;

    /**
     * Suggests player names from the tab list, excluding names already typed.
     * Handles any number of space-separated usernames.
     */

    public static final SuggestionProvider<FabricClientCommandSource> SUGGEST_PLAYER_NAMES = (context, builder) -> {
        String input = builder.getRemaining();

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return builder.buildFuture();
        }

        // Collect online player names, filter out NPCs (names starting with !)
        List<String> onlineNames = connection.getOnlinePlayers().stream()
                .map(info -> info.getProfile().name())
                .filter(name -> !name.startsWith("!"))
                .toList();
        String[] tokens = input.split(" ", -1);
        boolean startingNewToken = input.endsWith(" ");
        String currentToken = startingNewToken ? "" : tokens[tokens.length - 1];
        String prefix = currentToken.toLowerCase(Locale.ROOT);

        // Names already typed.
        Set<String> alreadyTyped = new HashSet<>();
        int limit = startingNewToken ? tokens.length : tokens.length - 1;
        for (int i = 0; i < limit; i++) {
            if (!tokens[i].isEmpty()) {
                alreadyTyped.add(tokens[i].toLowerCase(Locale.ROOT));
            }
        }

        // Build suggestions
        String beforeCurrentToken = input.substring(0, input.length() - currentToken.length());
        for (String name : onlineNames) {
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (!alreadyTyped.contains(lowerName) && lowerName.startsWith(prefix)) {
                builder.suggest(beforeCurrentToken + name);
            }
        }

        return builder.buildFuture();
    };

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommands.literal("ginv")
                                .then(ClientCommands.argument("names", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_PLAYER_NAMES)
                                        .executes(GinvCommand::executeWithArgs))
                                .executes(GinvCommand::executeWithoutArgs)
                )
        );
    }

    /**
     * Called when the player provides usernames.
     * Parses, deduplicates, stores them, then sends /guild invite for each
     * with a random 220-720ms delay between each command.
     */
    private static int executeWithArgs(CommandContext<FabricClientCommandSource> context) {
        String raw = StringArgumentType.getString(context, "names");

        Set<String> parsed = Arrays.stream(raw.split("\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (parsed.isEmpty()) {
            context.getSource().sendFeedback(Component.literal(
                    "§c[Ginv] §fNo valid player names provided."
            ));
            return 0;
        }

        queueAndSchedule(parsed);

        context.getSource().sendFeedback(Component.literal(
                "§a[Ginv] §fQueued §e" + parsed.size() + " §fguild invite(s)."
        ));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Called when the player runs /ginv with no arguments.
     * Shows the current target list.
     */
    private static int executeWithoutArgs(CommandContext<FabricClientCommandSource> context) {
        if (ginvTargets.isEmpty()) {
            context.getSource().sendFeedback(Component.literal(
                    "§c[Ginv] §fNo targets set. Usage: /ginv <player1> [player2] ..."
            ));
        } else {
            String status = frozen ? " §c[FROZEN]" : "";
            context.getSource().sendFeedback(Component.literal(
                    "§a[Ginv] §fCurrent targets (" + ginvTargets.size() + "): §e" + String.join("§f, §e", ginvTargets) + status
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Adds targets to the shared queue and starts processing.
     * Called by all commands that need to batch-invite.
     */
    public static void queueAndSchedule(Collection<String> targets) {
        ginvTargets.addAll(targets);
        pendingInvites.addAll(targets);
        processNext();
    }

    /**
     * Processes the next invite in the queue.
     * Chains itself with a random delay (from persisted settings) until the queue
     * is empty or frozen. Names blocked by the whitelist/blacklist settings are
     * skipped without sending, and every sent invite is recorded in the store.
     */
    private static void processNext() {
        if (pendingInvites.isEmpty()) return;
        scheduler.schedule(() -> {
            if (frozen) return; // will be resumed by toggleFreeze()
            String name = pendingInvites.poll();
            if (name != null && GinvDataStore.isAllowed(name)) {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.sendCommand("guild invite " + name);
                    GinvDataStore.recordInvite(name);
                }
            }
            processNext();
        }, nextDelayMs(), TimeUnit.MILLISECONDS);
    }

    /**
     * Delay for the next send: uniform random in [min, max] from persisted
     * settings (defaults 220-720 ms, the mod's original behaviour).
     */
    private static long nextDelayMs() {
        int min = GinvDataStore.minDelayMs();
        int max = GinvDataStore.maxDelayMs();
        if (max < min) {
            int swap = min;
            min = max;
            max = swap;
        }
        return min + (max <= min ? 0 : random.nextInt(max - min + 1));
    }

    /**
     * Drops a name from both the pending queue and the target set.
     * Used by the menu's per-row remove button.
     */
    public static void removeFromQueue(String name) {
        pendingInvites.removeIf(queued -> queued.equalsIgnoreCase(name));
        ginvTargets.removeIf(target -> target.equalsIgnoreCase(name));
    }

    // --- Freeze control ---

    public static boolean isFrozen() {
        return frozen;
    }

    public static void toggleFreeze() {
        frozen = !frozen;
        if (!frozen) {
            processNext(); // resume processing
        }
    }

    // --- Target accessors ---

    public static Set<String> getGinvTargets() {
        return Collections.unmodifiableSet(ginvTargets);
    }

    public static int getPendingCount() {
        return pendingInvites.size();
    }

    public static void setGinvTargets(Set<String> targets) {
        ginvTargets.clear();
        ginvTargets.addAll(targets);
    }

    public static void clearTargets() {
        ginvTargets.clear();
        pendingInvites.clear();
    }
}
