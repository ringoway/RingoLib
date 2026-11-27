package com.ringoway.ringolib.core.commands;

import com.ringoway.ringolib.RingoLib;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = RingoLib.MOD_ID)
public final class LagMonitor {

    public static volatile int autoItemThreshold = 0;
    public static volatile int alertMsptThreshold = 0;

    private static final int AUTO_CLEANUP_INTERVAL = 20 * 60;
    private static final int ALERT_INTERVAL = 20 * 30;

    private static int autoCleanupCooldown = 0;
    private static int alertCooldown = 0;

    private LagMonitor() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();

        if (autoItemThreshold > 0) {
            autoCleanupCooldown--;
            if (autoCleanupCooldown <= 0) {
                autoCleanupCooldown = AUTO_CLEANUP_INTERVAL;
                checkAutoCleanup(server);
            }
        }

        if (alertMsptThreshold > 0) {
            alertCooldown--;
            if (alertCooldown <= 0) {
                alertCooldown = ALERT_INTERVAL;
                checkMsptAlert(server);
            }
        }
    }

    private static void checkAutoCleanup(MinecraftServer server) {
        int totalItems = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity) totalItems++;
            }
        }

        if (totalItems <= autoItemThreshold) return;

        int removed = 0;
        for (ServerLevel level : server.getAllLevels()) {
            List<ItemEntity> toRemove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof ItemEntity itemEntity && !LagCommand.isValuable(itemEntity.getItem())) {
                    toRemove.add(itemEntity);
                }
            }
            for (ItemEntity item : toRemove) {
                item.discard();
                removed++;
            }
        }

        if (removed > 0) {
            int fRemoved = removed;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.literal("[RingoLib Auto] ")
                            .withStyle(ChatFormatting.DARK_AQUA)
                            .append(Component.literal("Cleaned " + fRemoved + " items (threshold: " + autoItemThreshold + ")")
                                    .withStyle(ChatFormatting.GRAY)));
                }
            }
        }
    }

    private static void checkMsptAlert(MinecraftServer server) {
        double mspt = LagCommand.calculateMspt(server);
        if (mspt <= alertMsptThreshold) return;

        int totalItems = 0;
        int totalEntities = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                totalEntities++;
                if (entity instanceof ItemEntity) totalItems++;
            }
        }

        double tps = LagCommand.calculateTps(server);
        int fItems = totalItems;
        int fEntities = totalEntities;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("[RingoLib Alert] ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(String.format("MSPT: %.1f ms (threshold: %d) | TPS: %.1f | %d entities, %d items",
                                        mspt, alertMsptThreshold, tps, fEntities, fItems))
                                .withStyle(ChatFormatting.YELLOW)));
            }
        }
    }
}
