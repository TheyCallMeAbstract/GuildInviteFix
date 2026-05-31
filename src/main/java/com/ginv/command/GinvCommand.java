package com.ginv.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
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
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "GinvScheduler");
        t.setDaemon(true);
        return t;
    });
    private static final Random random = new Random();

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

        // Collect online player names in ur current server.
        List<String> onlineNames = connection.getOnlinePlayers().stream()
                .map(info -> info.getProfile().name())
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
                        ClientCommandManager.literal("ginv")
                                .then(ClientCommandManager.argument("names", StringArgumentType.greedyString())
                                        .suggests(SUGGEST_PLAYER_NAMES)
                                        .executes(GinvCommand::executeWithArgs))
                                .executes(GinvCommand::executeWithoutArgs)
                )
        );
    }

    /**
     * Called when the player provides usernames.
     * Parses, deduplicates, stores them, then sends /guild invite for each
     * with a random 0-1s delay between each command.
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

        ginvTargets.clear();
        ginvTargets.addAll(parsed);

        context.getSource().sendFeedback(Component.literal(
                "§a[Ginv] §fQueued §e" + ginvTargets.size() + " §fguild invite(s)."
        ));

        // Schedule each /guild invite command
        List<String> targetList = List.copyOf(ginvTargets);
        long delay = 0;

        for (int i = 0; i < targetList.size(); i++) {
            String playerName = targetList.get(i);
            final long scheduledDelay = delay;

            scheduler.schedule(() -> {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                if (connection != null) {
                    connection.sendCommand("guild invite " + playerName);
                }
            }, scheduledDelay, TimeUnit.MILLISECONDS);

            // Add random 0-500ms delays for good measure.
            if (i < targetList.size() - 1) {
                delay += random.nextInt(501);
            }
        }

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
            context.getSource().sendFeedback(Component.literal(
                    "§a[Ginv] §fCurrent targets (" + ginvTargets.size() + "): §e" + String.join("§f, §e", ginvTargets)
            ));
        }
        return Command.SINGLE_SUCCESS;
    }

    public static Set<String> getGinvTargets() {
        return Collections.unmodifiableSet(ginvTargets);
    }

    public static void setGinvTargets(Set<String> targets) {
        ginvTargets.clear();
        ginvTargets.addAll(targets);
    }

    public static void clearTargets() {
        ginvTargets.clear();
    }
}
