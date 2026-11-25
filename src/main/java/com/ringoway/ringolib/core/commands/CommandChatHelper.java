package com.ringoway.ringolib.core.commands;

import com.ringoway.ringolib.core.network.CopyToClipboardPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Shared Brigadier chat formatting helpers for clickable clipboard copies,
 * command badges, and clickable teleport coordinates.
 */
final class CommandChatHelper {

    private CommandChatHelper() {
    }

    static MutableComponent copyable(String label, String toCopy, ChatFormatting color) {
        return Component.literal(label)
                .withStyle(style -> style
                        .withColor(color)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, toCopy))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable(
                                        "commands.ringolib.chat.click_copy",
                                        Component.literal(toCopy).withStyle(ChatFormatting.GOLD)
                                )
                        ))
                );
    }

    static MutableComponent copyBadge(String badgeText, String toCopy, ChatFormatting color) {
        return copyable(" [" + badgeText + "]", toCopy, color);
    }

    static MutableComponent runBadge(String badgeText, String command, Component hoverText, ChatFormatting color) {
        return Component.literal(" [" + badgeText + "]")
                .withStyle(style -> style
                        .withColor(color)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
                );
    }

    static MutableComponent suggestBadge(String badgeText, String command, Component hoverText, ChatFormatting color) {
        return Component.literal(" [" + badgeText + "]")
                .withStyle(style -> style
                        .withColor(color)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
                );
    }

    static MutableComponent tpCoords(int x, int y, int z, @Nullable String dimensionId) {
        String tpCmd = dimensionId != null
                ? "/execute in " + dimensionId + " run tp @s " + x + " " + y + " " + z
                : "/tp @s " + x + " " + y + " " + z;
        return Component.literal("[" + x + ", " + y + ", " + z + "]")
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, tpCmd))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("chat.coordinates.tooltip")
                        ))
                );
    }

    static void copyToClipboard(CommandSourceStack source, String text) {
        if (source.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new CopyToClipboardPayload(text));
        }
    }
}
