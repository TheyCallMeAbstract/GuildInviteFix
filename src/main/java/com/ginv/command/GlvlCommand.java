package com.ginv.command;

import com.ginv.utils.SkyBlockDetector;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GlvlCommand {

    /** Matches the first [number] in a color-stripped string */
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\[(\\d+)]");

    /**
     * Extracts the guild level from a player's team prefix.
     * The server renders levels as [int] inside the team prefix component,
     * e.g. "§8[§e101§8] §a" — after stripping colors: "[101] ".
     *
     * @return the level integer, or -1 if no level found
     */
    private static int extractLevel(PlayerInfo info) {
        PlayerTeam team = info.getTeam();
        if (team == null) return -1;
        Component prefix = team.getPlayerPrefix();
        if (prefix == null) return -1;
        String text = prefix.getString().replaceAll("§.", "");
        Matcher matcher = LEVEL_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(
                        ClientCommandManager.literal("glvl")
                                .then(ClientCommandManager.argument("level", IntegerArgumentType.integer(0))
                                        .executes(GlvlCommand::execute))
                )
        );
    }

    /**
     * Invites all players in the tab list whose team-prefix level >= the given threshold.
     * Players with no team or no prefix (NPCs) are skipped entirely.
     */
    private static int execute(CommandContext<FabricClientCommandSource> context) {
        if (!SkyBlockDetector.isSkyBlock()) {
            context.getSource().sendFeedback(Component.literal(
                    "§c[Glvl] §fYou're not in SkyBlock! Please join a SkyBlock lobby first."
            ));
            return 0;
        }

        int minLevel = IntegerArgumentType.getInteger(context, "level");

        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            context.getSource().sendFeedback(Component.literal(
                    "§c[Glvl] §fNot connected to a server."
            ));
            return 0;
        }

        List<String> targets = new ArrayList<>();
        int skippedNoLevel = 0;
        int skippedLowLevel = 0;

        for (PlayerInfo info : connection.getOnlinePlayers()) {
            String username = info.getProfile().name();
            if (username == null || username.startsWith("!")) continue;

            PlayerTeam team = info.getTeam();
            if (team == null) continue;
            Component prefix = team.getPlayerPrefix();
            if (prefix == null) continue;

            int level = extractLevel(info);
            if (level < 0) {
                skippedNoLevel++;
                continue;
            }
            if (level < minLevel) {
                skippedLowLevel++;
                continue;
            }
            targets.add(username);
        }

        if (targets.isEmpty()) {
            context.getSource().sendFeedback(Component.literal(
                    "§c[Glvl] §fNo players found with level §e≥ " + minLevel + "§f. " +
                    "(skipped " + skippedNoLevel + " with no level, " + skippedLowLevel + " below threshold)"
            ));
            return 0;
        }

        context.getSource().sendFeedback(Component.literal(
                "§a[Glvl] §fQueued §e" + targets.size() + " §fguild invite(s) " +
                "§7(level ≥ " + minLevel + ", skipped " + skippedNoLevel + " no-level, " + skippedLowLevel + " below)"
        ));

        GinvCommand.queueAndSchedule(targets);

        return Command.SINGLE_SUCCESS;
    }
}
