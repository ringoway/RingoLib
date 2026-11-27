package com.ringoway.ringolib.core.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.ringoway.ringolib.core.ban.BannedItemsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

final class BanCommands {

    private BanCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildNode() {
        return Commands.literal("ban")
                .then(Commands.literal("add")
                        .then(Commands.argument("item_id", StringArgumentType.greedyString())
                                .suggests(BanCommands::suggestAllItems)
                                .executes(BanCommands::banItem)
                        )
                )
                .then(Commands.literal("remove")
                        .then(Commands.argument("item_id", StringArgumentType.greedyString())
                                .suggests(BanCommands::suggestBannedItems)
                                .executes(BanCommands::unbanItem)
                        )
                )
                .then(Commands.literal("list")
                        .executes(BanCommands::listBannedItems)
                )
                .then(Commands.literal("clear")
                        .executes(BanCommands::clearBannedItems)
                );
    }

    private static String stripQuotes(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private static CompletableFuture<Suggestions> suggestAllItems(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder);
    }

    private static CompletableFuture<Suggestions> suggestBannedItems(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggestResource(BannedItemsManager.getBannedItems(), builder);
    }

    private static int banItem(CommandContext<CommandSourceStack> context) {
        String itemIdStr = stripQuotes(StringArgumentType.getString(context, "item_id"));
        ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
        if (itemId == null) {
            context.getSource().sendFailure(
                    Component.translatable("commands.ringolib.ban.invalid_id", itemIdStr)
                            .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            context.getSource().sendFailure(
                    Component.translatable("commands.ringolib.ban.item_not_found", itemIdStr)
                            .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (BannedItemsManager.banItem(itemId)) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.ban.added", itemId.toString())
                            .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(
                    Component.translatable("commands.ringolib.ban.already_banned", itemId.toString())
                            .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
    }

    private static int unbanItem(CommandContext<CommandSourceStack> context) {
        String itemIdStr = stripQuotes(StringArgumentType.getString(context, "item_id"));
        ResourceLocation itemId = ResourceLocation.tryParse(itemIdStr);
        if (itemId == null) {
            context.getSource().sendFailure(
                    Component.translatable("commands.ringolib.ban.invalid_id", itemIdStr)
                            .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (BannedItemsManager.unbanItem(itemId)) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.ban.removed", itemId.toString())
                            .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } else {
            context.getSource().sendFailure(
                    Component.translatable("commands.ringolib.ban.not_banned", itemId.toString())
                            .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
    }

    private static int listBannedItems(CommandContext<CommandSourceStack> context) {
        Set<ResourceLocation> banned = BannedItemsManager.getBannedItems();
        if (banned.isEmpty()) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.ban.list_empty")
                            .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        context.getSource().sendSuccess(() ->
                Component.translatable("commands.ringolib.ban.list_header", banned.size())
                        .withStyle(ChatFormatting.GOLD), false);

        banned.stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(item -> context.getSource().sendSuccess(() ->
                        Component.literal("  - ").withStyle(ChatFormatting.YELLOW)
                                .append(CommandChatHelper.copyable(item, item, ChatFormatting.GRAY))
                                .append(CommandChatHelper.runBadge(
                                        "Unban",
                                        "/rl ban remove " + item,
                                        Component.translatable("commands.ringolib.ban.unban_hover", item),
                                        ChatFormatting.GREEN
                                )),
                        false));

        return banned.size();
    }

    private static int clearBannedItems(CommandContext<CommandSourceStack> context) {
        int count = BannedItemsManager.getBannedItems().size();
        if (count == 0) {
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.ban.list_empty")
                            .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        BannedItemsManager.clearAll();
        context.getSource().sendSuccess(() ->
                Component.translatable("commands.ringolib.ban.cleared", count)
                        .withStyle(ChatFormatting.GREEN), true);
        return count;
    }
}
