package com.ringoway.ringolib.core.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ringoway.ringolib.compat.CuriosCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class PlayerDebugCommands {

    private PlayerDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildPlayerNode() {
        return Commands.literal("player")
                .then(buildReviveNode())
                .then(buildForceRespawnNode())
                .then(buildDropInventoryNode())
                .then(buildAttributesNode());
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildDropInventoryNode() {
        return Commands.literal("drop_inventory")
                .executes(context -> dropPlayerInventory(
                        context.getSource(),
                        List.of(context.getSource().getPlayerOrException())
                ))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> dropPlayerInventory(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets")
                        ))
                );
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildForceRespawnNode() {
        return Commands.literal("force_respawn")
                .executes(context -> forceRespawnPlayers(
                        context.getSource(),
                        List.of(context.getSource().getPlayerOrException())
                ))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> forceRespawnPlayers(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets")
                        ))
                );
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildReviveNode() {
        return Commands.literal("revive")
                .executes(context -> revivePlayers(
                        context.getSource(),
                        List.of(context.getSource().getPlayerOrException())
                ))
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> revivePlayers(
                                context.getSource(),
                                EntityArgument.getPlayers(context, "targets")
                        ))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildAttributesNode() {
        return Commands.literal("attributes")
                .executes(context -> inspectAttributes(
                        context.getSource(),
                        context.getSource().getPlayerOrException()
                ))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(context -> inspectAttributes(
                                context.getSource(),
                                EntityArgument.getPlayer(context, "target")
                        ))
                );
    }

    private static int inspectAttributes(CommandSourceStack source, ServerPlayer player) {
        source.sendSuccess(() -> Component.literal("=== ")
                .append(Component.translatable("commands.ringolib.player.attributes.header", player.getName().getString()))
                .append(" ===")
                .withStyle(ChatFormatting.GOLD), false);

        List<AttributeInstance> interesting = player.getAttributes().getSyncableAttributes().stream()
                .filter(inst -> !inst.getModifiers().isEmpty() || Math.abs(inst.getValue() - inst.getBaseValue()) > 1e-6)
                .sorted(Comparator.comparing(inst -> {
                    ResourceLocation id = BuiltInRegistries.ATTRIBUTE.getKey(inst.getAttribute().value());
                    return id != null ? id.toString() : "";
                }))
                .toList();

        if (interesting.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.player.attributes.none")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        for (AttributeInstance inst : interesting) {
            ResourceLocation attrLoc = BuiltInRegistries.ATTRIBUTE.getKey(inst.getAttribute().value());
            String attrId = attrLoc != null ? attrLoc.toString() : "?";

            source.sendSuccess(() -> Component.literal("  ")
                    .append(CommandChatHelper.copyable(attrId, attrId, ChatFormatting.YELLOW))
                    .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format("%.4f", inst.getValue())).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (base " + String.format("%.4f", inst.getBaseValue()) + ")")
                            .withStyle(ChatFormatting.DARK_GRAY)), false);

            for (AttributeModifier mod : inst.getModifiers()) {
                String modId = mod.id().toString();
                source.sendSuccess(() -> Component.literal("    - ").withStyle(ChatFormatting.GRAY)
                        .append(CommandChatHelper.copyable(modId, modId, ChatFormatting.AQUA))
                        .append(Component.literal(" [" + mod.operation().name() + " " + String.format("%+.4f", mod.amount()) + "]")
                                .withStyle(mod.amount() >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED)), false);
            }
        }
        return interesting.size();
    }

    private static int dropPlayerInventory(CommandSourceStack source, Collection<ServerPlayer> targets) {
        int totalStacks = 0;
        for (ServerPlayer player : targets) {
            totalStacks += dropSinglePlayerInventory(player);
        }

        int finalCount = totalStacks;
        source.sendSuccess(() ->
                Component.translatable("commands.ringolib.player.dropped_inventory", finalCount)
                        .withStyle(ChatFormatting.GREEN), true);
        return totalStacks;
    }

    static int dropSinglePlayerInventory(ServerPlayer player) {
        int dropped = 0;

        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty()) {
                player.drop(stack.copy(), true, false);
                player.getInventory().items.set(i, ItemStack.EMPTY);
                dropped++;
            }
        }

        for (int i = 0; i < player.getInventory().armor.size(); i++) {
            ItemStack stack = player.getInventory().armor.get(i);
            if (!stack.isEmpty()) {
                player.drop(stack.copy(), true, false);
                player.getInventory().armor.set(i, ItemStack.EMPTY);
                dropped++;
            }
        }

        for (int i = 0; i < player.getInventory().offhand.size(); i++) {
            ItemStack stack = player.getInventory().offhand.get(i);
            if (!stack.isEmpty()) {
                player.drop(stack.copy(), true, false);
                player.getInventory().offhand.set(i, ItemStack.EMPTY);
                dropped++;
            }
        }

        dropped += CuriosCompat.dropAllCurios(player);
        player.containerMenu.broadcastChanges();
        return dropped;
    }

    private static int forceRespawnPlayers(CommandSourceStack source, Collection<ServerPlayer> targets) {
        var server = source.getServer();
        for (ServerPlayer player : targets) {
            boolean keepInventory = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);

            if (!keepInventory) {
                dropSinglePlayerInventory(player);
            }

            player.removeAllEffects();
            player.hurtDuration = 0;
            player.hurtTime = 0;
            player.deathTime = 0;
            player.setAbsorptionAmount(0.0F);

            ServerPlayer newPlayer = server.getPlayerList().respawn(player, keepInventory, Entity.RemovalReason.KILLED);
            newPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(newPlayer.getAbilities()));
            newPlayer.connection.send(new ClientboundUpdateAttributesPacket(newPlayer.getId(), newPlayer.getAttributes().getSyncableAttributes()));
            newPlayer.connection.send(new ClientboundSetHealthPacket(
                    newPlayer.getHealth(),
                    newPlayer.getFoodData().getFoodLevel(),
                    newPlayer.getFoodData().getSaturationLevel()
            ));
        }

        source.sendSuccess(() ->
                Component.translatable("commands.ringolib.player.force_respawned", targets.size())
                        .withStyle(ChatFormatting.GREEN), true);
        return targets.size();
    }

    private static int revivePlayers(CommandSourceStack source, Collection<ServerPlayer> targets) {
        for (ServerPlayer player : targets) {
            player.removeAllEffects();
            player.hurtDuration = 0;
            player.hurtTime = 0;
            player.deathTime = 0;
            player.setAbsorptionAmount(0.0F);

            float targetHealth = player.getMaxHealth();
            if (Float.isNaN(targetHealth) || Float.isInfinite(targetHealth) || targetHealth <= 0.0F) {
                targetHealth = 20.0F;
            }
            player.setHealth(targetHealth);

            player.connection.send(new ClientboundSetHealthPacket(
                    player.getHealth(),
                    player.getFoodData().getFoodLevel(),
                    player.getFoodData().getSaturationLevel()
            ));
            player.connection.send(new ClientboundPlayerAbilitiesPacket(player.getAbilities()));
            player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), player.getAttributes().getSyncableAttributes()));
        }

        source.sendSuccess(() ->
                Component.translatable("commands.ringolib.player.revived", targets.size())
                        .withStyle(ChatFormatting.GREEN), true);
        return targets.size();
    }
}
