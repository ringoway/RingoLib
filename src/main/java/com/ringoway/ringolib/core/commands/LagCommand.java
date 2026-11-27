package com.ringoway.ringolib.core.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.ringoway.ringolib.compat.CuriosCompat;
import com.ringoway.ringolib.core.DummyItem;
import com.ringoway.ringolib.mixin.LevelAccessorMixin;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class LagCommand {

    private static final Set<String> VALUABLE_ITEMS = Set.of(
            "minecraft:netherite_sword", "minecraft:netherite_pickaxe", "minecraft:netherite_axe",
            "minecraft:netherite_shovel", "minecraft:netherite_hoe",
            "minecraft:netherite_helmet", "minecraft:netherite_chestplate",
            "minecraft:netherite_leggings", "minecraft:netherite_boots",
            "minecraft:netherite_ingot", "minecraft:netherite_block",
            "minecraft:elytra", "minecraft:nether_star", "minecraft:totem_of_undying",
            "minecraft:enchanted_golden_apple", "minecraft:dragon_egg", "minecraft:beacon",
            "minecraft:trident", "minecraft:heart_of_the_sea"
    );

    private static final int MAX_HISTORY = 10;
    private static final List<HistorySnapshot> HISTORY = new ArrayList<>();

    private LagCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
        return Commands.literal("lag")
                .requires(source -> source.hasPermission(2))
                .executes(LagCommand::overview)
                .then(Commands.literal("items")
                        .executes(ctx -> items(ctx, 10))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> items(ctx, IntegerArgumentType.getInteger(ctx, "count")))
                        )
                )
                .then(Commands.literal("entities")
                        .executes(ctx -> entities(ctx, 15))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> entities(ctx, IntegerArgumentType.getInteger(ctx, "count")))
                        )
                )
                .then(Commands.literal("tiles")
                        .executes(ctx -> tiles(ctx, 15))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                .executes(ctx -> tiles(ctx, IntegerArgumentType.getInteger(ctx, "count")))
                        )
                )
                .then(Commands.literal("players")
                        .executes(LagCommand::players)
                )
                .then(Commands.literal("curios")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(LagCommand::curios)
                        )
                )
                .then(Commands.literal("near")
                        .executes(ctx -> near(ctx, 100))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 500))
                                .executes(ctx -> near(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                        )
                )
                .then(Commands.literal("hotspots")
                        .executes(ctx -> hotspots(ctx, 5))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                .executes(ctx -> hotspots(ctx, IntegerArgumentType.getInteger(ctx, "count")))
                        )
                )
                .then(Commands.literal("history")
                        .executes(LagCommand::showHistory)
                )
                .then(Commands.literal("cleanup")
                        .then(Commands.literal("items")
                                .executes(ctx -> cleanupItems(ctx, 0, null))
                                .then(Commands.argument("mod_or_radius", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String arg = StringArgumentType.getString(ctx, "mod_or_radius");
                                            try {
                                                int radius = Integer.parseInt(arg);
                                                return cleanupItems(ctx, radius, null);
                                            } catch (NumberFormatException e) {
                                                return cleanupItems(ctx, 0, arg);
                                            }
                                        })
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 500))
                                                .executes(ctx -> cleanupItems(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "radius"),
                                                        StringArgumentType.getString(ctx, "mod_or_radius")))
                                        )
                                )
                        )
                        .then(Commands.literal("mobs")
                                .executes(ctx -> cleanupMobs(ctx, 0))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 500))
                                        .executes(ctx -> cleanupMobs(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                                )
                        )
                        .then(Commands.literal("type")
                                .then(Commands.argument("entity_type", StringArgumentType.word())
                                        .executes(ctx -> cleanupType(ctx, 0, 0))
                                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 500))
                                                .executes(ctx -> cleanupType(ctx, IntegerArgumentType.getInteger(ctx, "radius"), 0))
                                                .then(Commands.argument("keep", IntegerArgumentType.integer(0, 1000))
                                                        .executes(ctx -> cleanupType(ctx,
                                                                IntegerArgumentType.getInteger(ctx, "radius"),
                                                                IntegerArgumentType.getInteger(ctx, "keep")))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("all")
                                .executes(ctx -> cleanupAll(ctx, 0))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 500))
                                        .executes(ctx -> cleanupAll(ctx, IntegerArgumentType.getInteger(ctx, "radius")))
                                )
                        )
                )
                .then(Commands.literal("auto")
                        .then(Commands.literal("items")
                                .then(Commands.argument("threshold", IntegerArgumentType.integer(0, 10000))
                                        .executes(LagCommand::setAutoCleanup)
                                )
                        )
                )
                .then(Commands.literal("alert")
                        .then(Commands.argument("mspt", IntegerArgumentType.integer(0, 1000))
                                .executes(LagCommand::setAlert)
                        )
                );
    }

    private static int overview(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        double tps = calculateTps(server);
        double mspt = calculateMspt(server);

        int totalEntities = 0;
        int totalItems = 0;
        int totalTickingTiles = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                totalEntities++;
                if (entity instanceof ItemEntity) totalItems++;
            }
            totalTickingTiles += countActiveTickers(level);
        }
        recordSnapshot(tps, mspt, totalEntities, totalItems);

        sendHeader(source, "Server Health");

        ChatFormatting tpsColor = tps >= 18 ? ChatFormatting.GREEN
                : tps >= 15 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        sendKeyValue(source, "TPS", String.format("%.1f / 20.0", tps), tpsColor);

        ChatFormatting msptColor = mspt <= 35 ? ChatFormatting.GREEN
                : mspt <= 50 ? ChatFormatting.YELLOW : ChatFormatting.RED;
        sendKeyValue(source, "MSPT", String.format("%.1f ms", mspt), msptColor);

        if (LagMonitor.autoItemThreshold > 0) {
            sendKeyValue(source, "Auto-cleanup", "items > " + LagMonitor.autoItemThreshold, ChatFormatting.AQUA);
        }
        if (LagMonitor.alertMsptThreshold > 0) {
            sendKeyValue(source, "Alert", "MSPT > " + LagMonitor.alertMsptThreshold, ChatFormatting.AQUA);
        }

        int totalEntitiesFinal = totalEntities;
        int totalItemsFinal = totalItems;
        int totalTilesFinal = totalTickingTiles;

        for (ServerLevel level : server.getAllLevels()) {
            String dimName = level.dimension().location().toString();
            int entities = 0;
            int items = 0;

            for (Entity entity : level.getAllEntities()) {
                entities++;
                if (entity instanceof ItemEntity) items++;
            }
            int tiles = countActiveTickers(level);

            if (entities == 0 && tiles == 0) continue;

            int loadedChunks = level.getChunkSource().getLoadedChunksCount();

            sendSubHeader(source, dimName);
            sendKeyValue(source, "Entities", String.format("%,d", entities), ChatFormatting.WHITE);
            sendKeyValue(source, "Ticking BlockEntities", String.format("%,d", tiles),
                    tiles > 2000 ? ChatFormatting.RED : tiles > 500 ? ChatFormatting.YELLOW : ChatFormatting.GREEN);

            ChatFormatting itemColor = items > 500 ? ChatFormatting.RED
                    : items > 100 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
            sendKeyValue(source, "Dropped items", String.format("%,d", items), itemColor);
            sendKeyValue(source, "Loaded chunks", String.format("%,d", loadedChunks), ChatFormatting.WHITE);
        }

        sendSubHeader(source, "Total");
        sendKeyValue(source, "Entities", String.format("%,d", totalEntitiesFinal), ChatFormatting.WHITE);
        sendKeyValue(source, "Ticking BlockEntities", String.format("%,d", totalTilesFinal), ChatFormatting.WHITE);
        ChatFormatting totalItemColor = totalItemsFinal > 500 ? ChatFormatting.RED
                : totalItemsFinal > 100 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        sendKeyValue(source, "Dropped items", String.format("%,d", totalItemsFinal), totalItemColor);

        if (HISTORY.size() >= 2) {
            HistorySnapshot prev = HISTORY.get(HISTORY.size() - 2);
            String trend = mspt > prev.mspt + 5 ? " ^^ worsening" : mspt < prev.mspt - 5 ? " vv improving" : " -- stable";
            ChatFormatting trendColor = mspt > prev.mspt + 5 ? ChatFormatting.RED
                    : mspt < prev.mspt - 5 ? ChatFormatting.GREEN : ChatFormatting.GRAY;
            sendKeyValue(source, "Trend", String.format("%.1f ms -> %.1f ms%s", prev.mspt, mspt, trend), trendColor);
        }

        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();
        sendSubHeader(source, "Players (" + onlinePlayers.size() + ")");
        for (ServerPlayer player : onlinePlayers) {
            BlockPos pos = player.blockPosition();
            String dimId = player.level().dimension().location().toString();
            String dimPath = player.level().dimension().location().getPath();
            int nearbyEntities = countEntitiesNear(player.serverLevel(), pos, 100);
            int nearbyItems = countItemsNear(player.serverLevel(), pos, 100);
            int curiosCount = CuriosCompat.countEquippedCurios(player);

            ChatFormatting nearbyColor = nearbyEntities > 300 ? ChatFormatting.RED
                    : nearbyEntities > 100 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;

            String curiosSuffix = CuriosCompat.isLoaded() ? (" " + curiosCount + "c") : "";
            source.sendSuccess(() -> Component.literal("  " + player.getName().getString() + " ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(CommandChatHelper.tpCoords(pos.getX(), pos.getY(), pos.getZ(), dimId))
                    .append(Component.literal(" " + dimPath)
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" | " + nearbyEntities + "e " + nearbyItems + "i" + curiosSuffix)
                            .withStyle(nearbyColor)), false);
        }

        return 1;
    }

    private static int tiles(CommandContext<CommandSourceStack> context, int topN) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        Map<String, Integer> typeCounts = new HashMap<>();
        Map<String, Integer> dimTotals = new LinkedHashMap<>();
        Map<String, TileCluster> chunkClusters = new HashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            String dimId = level.dimension().location().toString();
            List<TickingBlockEntity> tickers = ((LevelAccessorMixin) level).ringolib$getBlockEntityTickers();
            int dimActive = 0;

            for (TickingBlockEntity ticker : tickers) {
                if (ticker == null || ticker.isRemoved()) continue;

                dimActive++;
                String type = ticker.getType();
                typeCounts.merge(type, 1, Integer::sum);

                BlockPos pos = ticker.getPos();
                ChunkPos chunk = new ChunkPos(pos);
                String clusterKey = dimId + "|" + chunk.x + "," + chunk.z;
                TileCluster cluster = chunkClusters.computeIfAbsent(clusterKey,
                        k -> new TileCluster(dimId, chunk.x, chunk.z));
                cluster.total++;
                cluster.sumY += pos.getY();
                cluster.typeCounts.merge(type, 1, Integer::sum);
            }

            if (dimActive > 0) {
                dimTotals.put(dimId, dimActive);
            }
        }

        int total = dimTotals.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            source.sendSuccess(() -> Component.literal("No active ticking BlockEntities.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        sendHeader(source, "Ticking BlockEntity Breakdown");
        sendKeyValue(source, "Total active tickers", String.format("%,d", total),
                total > 2000 ? ChatFormatting.RED : total > 500 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);

        for (Map.Entry<String, Integer> dim : dimTotals.entrySet()) {
            sendKeyValue(source, "  " + dim.getKey(), String.format("%,d", dim.getValue()), ChatFormatting.GRAY);
        }

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Top " + topN + " ticking BlockEntity types:")
                .withStyle(ChatFormatting.YELLOW), false);

        List<Map.Entry<String, Integer>> sortedTypes = typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topN)
                .toList();

        int rank = 1;
        for (Map.Entry<String, Integer> entry : sortedTypes) {
            sendNumberedEntry(source, rank++, entry.getKey(), entry.getValue());
        }

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Hotspot chunks:")
                .withStyle(ChatFormatting.YELLOW), false);

        List<TileCluster> sortedChunks = chunkClusters.values().stream()
                .sorted(Comparator.comparingInt((TileCluster c) -> c.total).reversed())
                .limit(5)
                .toList();

        int chunkRank = 1;
        for (TileCluster cluster : sortedChunks) {
            int centerX = cluster.chunkX * 16 + 8;
            int centerZ = cluster.chunkZ * 16 + 8;
            int avgY = Math.round((float) cluster.sumY / cluster.total);
            int cr = chunkRank;

            source.sendSuccess(() -> Component.literal("  #" + cr + ": ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(CommandChatHelper.tpCoords(centerX, avgY, centerZ, cluster.dimId))
                    .append(Component.literal(" in " + cluster.dimId)
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" (" + String.format("%,d", cluster.total) + " tickers)")
                            .withStyle(cluster.total > 100 ? ChatFormatting.RED : ChatFormatting.YELLOW)), false);

            cluster.typeCounts.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(3)
                    .forEach(e -> source.sendSuccess(() ->
                            Component.literal("      " + e.getKey() + " x " + String.format("%,d", e.getValue()))
                                    .withStyle(ChatFormatting.DARK_GRAY), false));
            chunkRank++;
        }

        return total;
    }

    private static int countActiveTickers(ServerLevel level) {
        int active = 0;
        for (TickingBlockEntity ticker : ((LevelAccessorMixin) level).ringolib$getBlockEntityTickers()) {
            if (ticker != null && !ticker.isRemoved()) {
                active++;
            }
        }
        return active;
    }

    private static int items(CommandContext<CommandSourceStack> context, int topN) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        Map<ResourceLocation, Integer> itemCounts = new HashMap<>();
        Map<String, List<ItemData>> chunkClusters = new HashMap<>();
        int totalItemEntities = 0;
        int valuableOnGround = 0;

        for (ServerLevel level : server.getAllLevels()) {
            String dimKey = level.dimension().location().toString();

            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity itemEntity)) continue;

                totalItemEntities++;
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());

                if (isValuable(itemEntity.getItem())) valuableOnGround++;

                itemCounts.merge(itemId, 1, Integer::sum);

                ChunkPos chunk = new ChunkPos(entity.blockPosition());
                String clusterKey = dimKey + "|" + chunk.x + "," + chunk.z;
                chunkClusters.computeIfAbsent(clusterKey, k -> new ArrayList<>())
                        .add(new ItemData(itemId, entity.blockPosition().getY()));
            }
        }

        if (totalItemEntities == 0) {
            source.sendSuccess(() -> Component.literal("No dropped items on the ground.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        sendHeader(source, "Dropped Item Analysis");
        int finalTotal = totalItemEntities;
        source.sendSuccess(() -> Component.literal("Total item entities: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format("%,d", finalTotal))
                        .withStyle(ChatFormatting.WHITE)), false);

        if (valuableOnGround > 0) {
            int fv = valuableOnGround;
            source.sendSuccess(() -> Component.literal("  Valuable items on ground: " + fv)
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Top items on ground:")
                .withStyle(ChatFormatting.YELLOW), false);

        List<Map.Entry<ResourceLocation, Integer>> sortedItems = itemCounts.entrySet().stream()
                .sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed())
                .limit(topN)
                .toList();

        int rank = 1;
        for (Map.Entry<ResourceLocation, Integer> entry : sortedItems) {
            sendNumberedEntry(source, rank++, entry.getKey().toString(), entry.getValue());
        }

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Hotspot chunks:")
                .withStyle(ChatFormatting.YELLOW), false);

        List<Map.Entry<String, List<ItemData>>> sortedClusters = chunkClusters.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(5)
                .toList();

        int hotspotRank = 1;
        for (Map.Entry<String, List<ItemData>> cluster : sortedClusters) {
            List<ItemData> clusterItems = cluster.getValue();
            int clusterSize = clusterItems.size();

            String[] parts = cluster.getKey().split("\\|");
            String dim = parts[0];
            String[] chunkCoords = parts[1].split(",");
            int chunkX = Integer.parseInt(chunkCoords[0]);
            int chunkZ = Integer.parseInt(chunkCoords[1]);
            int centerX = chunkX * 16 + 8;
            int centerZ = chunkZ * 16 + 8;
            int avgY = (int) clusterItems.stream().mapToInt(d -> d.y).average().orElse(64);

            int hr = hotspotRank;
            source.sendSuccess(() -> Component.literal("  #" + hr + ": ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(CommandChatHelper.tpCoords(centerX, avgY, centerZ, dim))
                    .append(Component.literal(" in " + dim)
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" (" + String.format("%,d", clusterSize) + " items)")
                            .withStyle(ChatFormatting.RED)), false);

            Map<ResourceLocation, Integer> chunkItemCounts = new HashMap<>();
            for (ItemData data : clusterItems) {
                chunkItemCounts.merge(data.itemId, 1, Integer::sum);
            }
            chunkItemCounts.entrySet().stream()
                    .sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> source.sendSuccess(() ->
                            Component.literal("      " + e.getKey() + " x " + String.format("%,d", e.getValue()))
                                    .withStyle(ChatFormatting.DARK_GRAY), false));

            hotspotRank++;
        }

        return totalItemEntities;
    }

    private static int entities(CommandContext<CommandSourceStack> context, int topN) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        Map<EntityType<?>, Integer> globalCounts = new HashMap<>();
        Map<String, Integer> dimTotals = new LinkedHashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            String dimName = level.dimension().location().toString();
            int dimCount = 0;

            for (Entity entity : level.getAllEntities()) {
                globalCounts.merge(entity.getType(), 1, Integer::sum);
                dimCount++;
            }

            if (dimCount > 0) {
                dimTotals.put(dimName, dimCount);
            }
        }

        int total = dimTotals.values().stream().mapToInt(Integer::intValue).sum();

        sendHeader(source, "Entity Breakdown");
        sendKeyValue(source, "Total", String.format("%,d", total), ChatFormatting.WHITE);

        for (Map.Entry<String, Integer> dim : dimTotals.entrySet()) {
            sendKeyValue(source, "  " + dim.getKey(), String.format("%,d", dim.getValue()), ChatFormatting.GRAY);
        }

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Top " + topN + " entity types:")
                .withStyle(ChatFormatting.YELLOW), false);

        List<Map.Entry<EntityType<?>, Integer>> sorted = globalCounts.entrySet().stream()
                .sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed())
                .limit(topN)
                .toList();

        int rank = 1;
        for (Map.Entry<EntityType<?>, Integer> entry : sorted) {
            ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey());
            sendNumberedEntry(source, rank++, typeId.toString(), entry.getValue());
        }

        return 1;
    }

    private static int players(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        List<ServerPlayer> onlinePlayers = server.getPlayerList().getPlayers();

        if (onlinePlayers.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No players online.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        sendHeader(source, "Player Analysis (" + onlinePlayers.size() + " online)");

        for (ServerPlayer player : onlinePlayers) {
            BlockPos pos = player.blockPosition();
            ServerLevel level = player.serverLevel();
            String dimId = level.dimension().location().toString();
            String dimPath = level.dimension().location().getPath();

            int nearby50 = countEntitiesNear(level, pos, 50);
            int nearby100 = countEntitiesNear(level, pos, 100);
            int nearby200 = countEntitiesNear(level, pos, 200);
            int itemsNear100 = countItemsNear(level, pos, 100);
            int hostileNear100 = countHostileNear(level, pos, 100);
            int curiosCount = CuriosCompat.countEquippedCurios(player);

            int invItems = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (!player.getInventory().getItem(i).isEmpty()) invItems++;
            }

            sendSubHeader(source, player.getName().getString());
            source.sendSuccess(() -> Component.literal("  Pos: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(CommandChatHelper.tpCoords(pos.getX(), pos.getY(), pos.getZ(), dimId))
                    .append(Component.literal(" " + dimPath)
                            .withStyle(ChatFormatting.DARK_GRAY)), false);

            sendKeyValue(source, "Inventory slots used", String.valueOf(invItems), ChatFormatting.WHITE);

            if (CuriosCompat.isLoaded()) {
                ChatFormatting curiosColor = curiosCount > 30 ? ChatFormatting.RED
                        : curiosCount > 15 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
                sendKeyValue(source, "Curios items", String.valueOf(curiosCount), curiosColor);
            }

            source.sendSuccess(() -> Component.literal("  Nearby entities:")
                    .withStyle(ChatFormatting.GRAY), false);

            sendDensityLine(source, "50 blocks", nearby50, 50);
            sendDensityLine(source, "100 blocks", nearby100, 150);
            sendDensityLine(source, "200 blocks", nearby200, 400);

            sendKeyValue(source, "  Items (100r)", String.valueOf(itemsNear100),
                    itemsNear100 > 50 ? ChatFormatting.RED : ChatFormatting.GREEN);
            sendKeyValue(source, "  Hostile (100r)", String.valueOf(hostileNear100),
                    hostileNear100 > 30 ? ChatFormatting.RED : ChatFormatting.WHITE);

            Map<EntityType<?>, Integer> nearbyTypes = new HashMap<>();
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Player) continue;
                if (entity.blockPosition().distSqr(pos) <= 100 * 100) {
                    nearbyTypes.merge(entity.getType(), 1, Integer::sum);
                }
            }

            List<Map.Entry<EntityType<?>, Integer>> topNearby = nearbyTypes.entrySet().stream()
                    .sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed())
                    .limit(3)
                    .toList();

            if (!topNearby.isEmpty()) {
                source.sendSuccess(() -> Component.literal("  Top nearby types:")
                        .withStyle(ChatFormatting.GRAY), false);
                for (Map.Entry<EntityType<?>, Integer> entry : topNearby) {
                    ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey());
                    source.sendSuccess(() -> Component.literal("    " + typeId + " x " + entry.getValue())
                            .withStyle(ChatFormatting.DARK_GRAY), false);
                }
            }
        }

        return onlinePlayers.size();
    }

    private static int curios(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!CuriosCompat.isLoaded()) {
            source.sendFailure(Component.literal("Curios API is not installed."));
            return 0;
        }

        ServerPlayer player;
        try {
            player = EntityArgument.getPlayer(context, "player");
        } catch (Exception e) {
            source.sendFailure(Component.literal("Player not found."));
            return 0;
        }

        sendHeader(source, "Curios: " + player.getName().getString());

        Map<String, List<String>> byMod = CuriosCompat.getEquippedCuriosByMod(player);
        int totalCount = byMod.values().stream().mapToInt(List::size).sum();

        if (totalCount == 0) {
            source.sendSuccess(() -> Component.literal("No curios equipped.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        ChatFormatting totalColor = totalCount > 30 ? ChatFormatting.RED
                : totalCount > 15 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        sendKeyValue(source, "Total curios", String.valueOf(totalCount), totalColor);
        source.sendSuccess(() -> Component.literal(""), false);

        for (Map.Entry<String, List<String>> mod : byMod.entrySet()) {
            String modName = mod.getKey();
            List<String> modItems = mod.getValue();

            ChatFormatting modColor = modItems.size() > 10 ? ChatFormatting.RED
                    : modItems.size() > 5 ? ChatFormatting.YELLOW : ChatFormatting.WHITE;

            source.sendSuccess(() -> Component.literal("  " + modName + " (" + modItems.size() + ")")
                    .withStyle(modColor), false);

            for (String item : modItems) {
                source.sendSuccess(() -> Component.literal("    " + item)
                        .withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }

        return totalCount;
    }

    private static int near(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        BlockPos center = BlockPos.containing(source.getPosition());
        ServerLevel level = source.getLevel();
        String dimId = level.dimension().location().toString();
        long radiusSq = (long) radius * radius;

        sendHeader(source, "Area Analysis (radius " + radius + ")");
        source.sendSuccess(() -> Component.literal("  Center: ")
                .withStyle(ChatFormatting.GRAY)
                .append(CommandChatHelper.tpCoords(center.getX(), center.getY(), center.getZ(), dimId)), false);

        Map<EntityType<?>, Integer> typeCounts = new HashMap<>();
        int totalEntities = 0;
        int totalItems = 0;
        int totalHostile = 0;
        int totalFriendly = 0;

        Map<ResourceLocation, Integer> itemTypeCounts = new HashMap<>();

        for (Entity entity : level.getAllEntities()) {
            if (entity.blockPosition().distSqr(center) > radiusSq) continue;
            if (entity instanceof Player) continue;

            totalEntities++;
            typeCounts.merge(entity.getType(), 1, Integer::sum);

            if (entity instanceof ItemEntity itemEntity) {
                totalItems++;
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                itemTypeCounts.merge(itemId, 1, Integer::sum);
            } else if (entity instanceof Mob mob) {
                if (mob.getType().getCategory().isFriendly()) {
                    totalFriendly++;
                } else if (mob.getType().getCategory() != MobCategory.MISC) {
                    totalHostile++;
                }
            }
        }

        sendKeyValue(source, "Total entities", String.format("%,d", totalEntities), ChatFormatting.WHITE);
        sendKeyValue(source, "Items on ground", String.format("%,d", totalItems),
                totalItems > 100 ? ChatFormatting.RED : ChatFormatting.GREEN);
        sendKeyValue(source, "Hostile mobs", String.format("%,d", totalHostile),
                totalHostile > 50 ? ChatFormatting.RED : ChatFormatting.WHITE);
        sendKeyValue(source, "Friendly mobs", String.format("%,d", totalFriendly),
                totalFriendly > 80 ? ChatFormatting.YELLOW : ChatFormatting.WHITE);

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.literal("Top entity types:")
                .withStyle(ChatFormatting.YELLOW), false);

        typeCounts.entrySet().stream()
                .sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> {
                    ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey());
                    source.sendSuccess(() -> Component.literal("  " + typeId + " x " + entry.getValue())
                            .withStyle(ChatFormatting.GRAY), false);
                });

        if (!itemTypeCounts.isEmpty()) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("Top items on ground:")
                    .withStyle(ChatFormatting.YELLOW), false);

            itemTypeCounts.entrySet().stream()
                    .sorted(Map.Entry.<ResourceLocation, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(entry -> source.sendSuccess(() ->
                            Component.literal("  " + entry.getKey() + " x " + entry.getValue())
                                    .withStyle(ChatFormatting.GRAY), false));
        }

        return totalEntities;
    }

    private static int hotspots(CommandContext<CommandSourceStack> context, int topN) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        Map<String, HotspotData> regions = new HashMap<>();

        for (ServerLevel level : server.getAllLevels()) {
            String dimKey = level.dimension().location().toString();

            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Player) continue;

                int regionX = Math.floorDiv(entity.blockPosition().getX(), 48);
                int regionZ = Math.floorDiv(entity.blockPosition().getZ(), 48);
                String key = dimKey + "|" + regionX + "," + regionZ;

                HotspotData data = regions.computeIfAbsent(key, k -> new HotspotData(dimKey, regionX, regionZ));
                data.total++;
                data.sumY += entity.blockPosition().getY();
                if (entity instanceof ItemEntity) data.items++;
                if (entity instanceof Mob) data.mobs++;
                data.entityTypes.merge(entity.getType(), 1, Integer::sum);
            }
        }

        if (regions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No entities found.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        sendHeader(source, "Entity Density Hotspots (top " + topN + ")");

        List<HotspotData> sorted = regions.values().stream()
                .sorted(Comparator.comparingInt((HotspotData d) -> d.total).reversed())
                .limit(topN)
                .toList();

        int rank = 1;
        for (HotspotData data : sorted) {
            int centerX = data.regionX * 48 + 24;
            int centerZ = data.regionZ * 48 + 24;
            int avgY = Math.round((float) data.sumY / data.total);

            ChatFormatting color = data.total > 100 ? ChatFormatting.RED
                    : data.total > 30 ? ChatFormatting.YELLOW : ChatFormatting.GREEN;

            int r = rank;
            source.sendSuccess(() -> Component.literal("#" + r + " ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(CommandChatHelper.tpCoords(centerX, avgY, centerZ, data.dim))
                    .append(Component.literal(" " + data.dim)
                            .withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.literal(" | " + data.total + " total")
                            .withStyle(color))
                    .append(Component.literal(" (" + data.mobs + " mobs, " + data.items + " items)")
                            .withStyle(ChatFormatting.GRAY)), false);

            data.entityTypes.entrySet().stream()
                    .sorted(Map.Entry.<EntityType<?>, Integer>comparingByValue().reversed())
                    .limit(3)
                    .forEach(entry -> {
                        ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(entry.getKey());
                        source.sendSuccess(() -> Component.literal("    " + typeId + " x " + entry.getValue())
                                .withStyle(ChatFormatting.DARK_GRAY), false);
                    });

            rank++;
        }

        return sorted.size();
    }

    private static int showHistory(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (HISTORY.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No history yet. Run /rl lag to record a snapshot.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        sendHeader(source, "TPS/MSPT History (" + HISTORY.size() + " snapshots)");
        source.sendSuccess(() -> Component.literal("  #   TPS     MSPT      Entities  Items")
                .withStyle(ChatFormatting.GRAY), false);

        for (int i = 0; i < HISTORY.size(); i++) {
            HistorySnapshot snap = HISTORY.get(i);
            int idx = i + 1;

            String tpsStr = String.format("%.1f", snap.tps);
            String msptStr = String.format("%.1f ms", snap.mspt);
            String entStr = String.format("%,d", snap.totalEntities);
            String itemStr = String.format("%,d", snap.totalItems);

            ChatFormatting msptC = snap.mspt <= 35 ? ChatFormatting.GREEN
                    : snap.mspt <= 50 ? ChatFormatting.YELLOW : ChatFormatting.RED;

            String arrow = "";
            if (i > 0) {
                double diff = snap.mspt - HISTORY.get(i - 1).mspt;
                if (diff > 5) arrow = " ^^";
                else if (diff < -5) arrow = " vv";
            }

            String fArrow = arrow;
            source.sendSuccess(() -> Component.literal("  " + idx + ". ")
                    .withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(tpsStr + "  ")
                            .withStyle(snap.tps >= 18 ? ChatFormatting.GREEN : ChatFormatting.RED))
                    .append(Component.literal(msptStr + fArrow + "  ")
                            .withStyle(msptC))
                    .append(Component.literal(entStr + "  ")
                            .withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(itemStr)
                            .withStyle(snap.totalItems > 500 ? ChatFormatting.RED : ChatFormatting.GRAY)), false);
        }

        if (HISTORY.size() >= 2) {
            HistorySnapshot first = HISTORY.get(0);
            HistorySnapshot last = HISTORY.get(HISTORY.size() - 1);
            double msptChange = last.mspt - first.mspt;
            int entityChange = last.totalEntities - first.totalEntities;

            ChatFormatting changeColor = msptChange > 5 ? ChatFormatting.RED
                    : msptChange < -5 ? ChatFormatting.GREEN : ChatFormatting.GRAY;

            source.sendSuccess(() -> Component.literal(""), false);
            sendKeyValue(source, "MSPT change",
                    String.format("%+.1f ms (%.1f -> %.1f)", msptChange, first.mspt, last.mspt), changeColor);
            sendKeyValue(source, "Entity change",
                    String.format("%+,d (%,d -> %,d)", entityChange, first.totalEntities, last.totalEntities),
                    entityChange > 100 ? ChatFormatting.RED : ChatFormatting.GRAY);
        }

        return HISTORY.size();
    }

    private static int cleanupItems(CommandContext<CommandSourceStack> context, int radius, String modFilter) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        BlockPos center = radius > 0 ? BlockPos.containing(source.getPosition()) : null;
        ServerLevel sourceLevel = radius > 0 ? source.getLevel() : null;

        Map<String, Integer> dimCounts = new LinkedHashMap<>();
        List<String> protectedItems = new ArrayList<>();
        int totalRemoved = 0;

        Iterable<ServerLevel> levels = radius > 0 ? List.of(sourceLevel) : server.getAllLevels();

        for (ServerLevel level : levels) {
            List<ItemEntity> toRemove = new ArrayList<>();

            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof ItemEntity itemEntity)) continue;
                if (radius > 0 && center != null && entity.blockPosition().distSqr(center) > (long) radius * radius) {
                    continue;
                }

                if (modFilter != null) {
                    ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                    if (!itemId.getNamespace().equals(modFilter)) continue;
                }

                if (isValuable(itemEntity.getItem())) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(itemEntity.getItem().getItem());
                    protectedItems.add(id.toString());
                    continue;
                }

                toRemove.add(itemEntity);
            }

            if (!toRemove.isEmpty()) {
                for (ItemEntity item : toRemove) {
                    item.discard();
                }
                String dimName = level.dimension().location().toString();
                dimCounts.put(dimName, toRemove.size());
                totalRemoved += toRemove.size();
            }
        }

        if (totalRemoved == 0 && protectedItems.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No dropped items to clean up.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        String scope = (modFilter != null ? " mod:" + modFilter : "")
                + (radius > 0 ? " r:" + radius : " global");
        sendHeader(source, "Item Cleanup (" + scope.trim() + ")");

        int finalTotal = totalRemoved;
        source.sendSuccess(() -> Component.literal("Removed ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(String.format("%,d", finalTotal))
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" item entities")
                        .withStyle(ChatFormatting.GREEN)), false);

        for (Map.Entry<String, Integer> dim : dimCounts.entrySet()) {
            sendKeyValue(source, dim.getKey(), String.format("%,d", dim.getValue()), ChatFormatting.GRAY);
        }

        if (!protectedItems.isEmpty()) {
            int protCount = protectedItems.size();
            source.sendSuccess(() -> Component.literal("Protected " + protCount + " valuable item(s):")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            Map<String, Long> uniqueCounts = new LinkedHashMap<>();
            for (String item : protectedItems) {
                uniqueCounts.merge(item, 1L, Long::sum);
            }
            for (Map.Entry<String, Long> entry : uniqueCounts.entrySet()) {
                String display = entry.getKey() + (entry.getValue() > 1 ? " x" + entry.getValue() : "");
                source.sendSuccess(() -> Component.literal("  " + display)
                        .withStyle(ChatFormatting.DARK_PURPLE), false);
            }
        }

        return totalRemoved;
    }

    private static int cleanupMobs(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();

        BlockPos center = radius > 0 ? BlockPos.containing(source.getPosition()) : null;
        ServerLevel sourceLevel = radius > 0 ? source.getLevel() : null;

        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        int totalRemoved = 0;

        Iterable<ServerLevel> levels = radius > 0 ? List.of(sourceLevel) : server.getAllLevels();

        for (ServerLevel level : levels) {
            List<Mob> toRemove = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof Mob mob)) continue;
                if (mob.hasCustomName()) continue;
                if (!mob.getType().getCategory().isFriendly() && mob.getType().getCategory() != MobCategory.MISC) {
                    if (radius > 0 && center != null && entity.blockPosition().distSqr(center) > (long) radius * radius) {
                        continue;
                    }
                    toRemove.add(mob);
                }
            }
            for (Mob mob : toRemove) {
                ResourceLocation typeId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
                typeCounts.merge(typeId.toString(), 1, Integer::sum);
                mob.discard();
                totalRemoved++;
            }
        }

        if (totalRemoved == 0) {
            source.sendSuccess(() -> Component.literal("No hostile mobs to clean up.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        String scope = radius > 0 ? "r:" + radius : "global";
        sendHeader(source, "Mob Cleanup (" + scope + ")");

        int finalTotal = totalRemoved;
        source.sendSuccess(() -> Component.literal("Removed ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(String.format("%,d", finalTotal))
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" hostile mobs")
                        .withStyle(ChatFormatting.GREEN)), false);

        typeCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(e -> sendKeyValue(source, e.getKey(), String.format("%,d", e.getValue()), ChatFormatting.GRAY));

        return totalRemoved;
    }

    private static int cleanupType(CommandContext<CommandSourceStack> context, int radius, int keep) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String typeArg = StringArgumentType.getString(context, "entity_type");

        ResourceLocation typeRL = typeArg.contains(":")
                ? ResourceLocation.tryParse(typeArg)
                : ResourceLocation.tryParse("minecraft:" + typeArg);

        Optional<EntityType<?>> optType = typeRL != null
                ? BuiltInRegistries.ENTITY_TYPE.getOptional(typeRL)
                : Optional.empty();
        if (optType.isEmpty()) {
            source.sendFailure(Component.literal("Unknown entity type: " + typeArg));
            return 0;
        }

        EntityType<?> targetType = optType.get();
        BlockPos center = radius > 0 ? BlockPos.containing(source.getPosition()) : null;
        ServerLevel sourceLevel = radius > 0 ? source.getLevel() : null;
        Iterable<ServerLevel> levels = radius > 0 ? List.of(sourceLevel) : server.getAllLevels();

        List<Entity> candidates = new ArrayList<>();
        for (ServerLevel level : levels) {
            for (Entity entity : level.getAllEntities()) {
                if (entity.getType() != targetType) continue;
                if (entity instanceof Player) continue;
                if (entity instanceof Mob mob && mob.hasCustomName()) continue;
                if (radius > 0 && center != null && entity.blockPosition().distSqr(center) > (long) radius * radius) {
                    continue;
                }
                candidates.add(entity);
            }
        }

        if (candidates.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No " + typeRL + " found.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        if (keep > 0 && center != null) {
            BlockPos fc = center;
            candidates.sort(Comparator.comparingDouble((Entity e) -> e.blockPosition().distSqr(fc)));
        }

        int toKeep = Math.min(keep, candidates.size());
        int removed = 0;
        for (int i = toKeep; i < candidates.size(); i++) {
            candidates.get(i).discard();
            removed++;
        }

        int fRemoved = removed;
        int fKept = toKeep;
        String scope = (radius > 0 ? " r:" + radius : " global") + (keep > 0 ? " keep:" + keep : "");
        sendHeader(source, "Type Cleanup (" + scope.trim() + ")");

        source.sendSuccess(() -> Component.literal("Removed ")
                .withStyle(ChatFormatting.GREEN)
                .append(Component.literal(String.format("%,d", fRemoved))
                        .withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" " + typeRL)
                        .withStyle(ChatFormatting.GREEN)), false);

        if (fKept > 0) {
            source.sendSuccess(() -> Component.literal("  Kept " + fKept + " closest")
                    .withStyle(ChatFormatting.AQUA), false);
        }

        return removed;
    }

    private static int cleanupAll(CommandContext<CommandSourceStack> context, int radius) {
        int items = cleanupItems(context, radius, null);
        int mobs = cleanupMobs(context, radius);
        return items + mobs;
    }

    private static int setAutoCleanup(CommandContext<CommandSourceStack> context) {
        int threshold = IntegerArgumentType.getInteger(context, "threshold");
        CommandSourceStack source = context.getSource();

        LagMonitor.autoItemThreshold = threshold;
        if (threshold == 0) {
            source.sendSuccess(() -> Component.literal("Auto item cleanup disabled.")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            source.sendSuccess(() -> Component.literal("Auto item cleanup enabled: will clean items when count > " + threshold)
                    .withStyle(ChatFormatting.GREEN), false);
            source.sendSuccess(() -> Component.literal("  Valuable items are always protected.")
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int setAlert(CommandContext<CommandSourceStack> context) {
        int mspt = IntegerArgumentType.getInteger(context, "mspt");
        CommandSourceStack source = context.getSource();

        LagMonitor.alertMsptThreshold = mspt;
        if (mspt == 0) {
            source.sendSuccess(() -> Component.literal("MSPT alert disabled.")
                    .withStyle(ChatFormatting.YELLOW), false);
        } else {
            source.sendSuccess(() -> Component.literal("MSPT alert enabled: will warn operators when MSPT > " + mspt + " ms")
                    .withStyle(ChatFormatting.GREEN), false);
        }
        return 1;
    }

    private static synchronized void recordSnapshot(double tps, double mspt, int totalEntities, int totalItems) {
        if (HISTORY.size() >= MAX_HISTORY) {
            HISTORY.remove(0);
        }
        HISTORY.add(new HistorySnapshot(tps, mspt, totalEntities, totalItems, System.currentTimeMillis()));
    }

    static boolean isValuable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof DummyItem) return true;
        if (stack.isEnchanted()) return true;
        if (stack.has(DataComponents.CUSTOM_NAME)) return true;
        if (stack.has(DataComponents.STORED_ENCHANTMENTS)) return true;
        if (stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyStream().findAny().isPresent()) {
            return true;
        }
        if (!stack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).isEmpty()) {
            return true;
        }
        if (stack.getRarity() == Rarity.EPIC || stack.getRarity() == Rarity.RARE) return true;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return VALUABLE_ITEMS.contains(id.toString());
    }

    private static int countEntitiesNear(ServerLevel level, BlockPos center, int radius) {
        int count = 0;
        long radiusSq = (long) radius * radius;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Player) continue;
            if (entity.blockPosition().distSqr(center) <= radiusSq) count++;
        }
        return count;
    }

    private static int countItemsNear(ServerLevel level, BlockPos center, int radius) {
        int count = 0;
        long radiusSq = (long) radius * radius;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity && entity.blockPosition().distSqr(center) <= radiusSq) count++;
        }
        return count;
    }

    private static int countHostileNear(ServerLevel level, BlockPos center, int radius) {
        int count = 0;
        long radiusSq = (long) radius * radius;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Mob mob && !mob.getType().getCategory().isFriendly()
                    && mob.getType().getCategory() != MobCategory.MISC
                    && entity.blockPosition().distSqr(center) <= radiusSq) {
                count++;
            }
        }
        return count;
    }

    static double calculateTps(MinecraftServer server) {
        long[] tickTimes = server.getTickTimesNanos();
        if (tickTimes.length == 0) return 20.0;

        long sum = 0;
        for (long t : tickTimes) {
            sum += t;
        }
        double avgMs = (sum / (double) tickTimes.length) / 1_000_000.0;
        if (avgMs <= 0) return 20.0;
        return Math.min(20.0, 1000.0 / avgMs);
    }

    static double calculateMspt(MinecraftServer server) {
        long[] tickTimes = server.getTickTimesNanos();
        if (tickTimes.length == 0) return 0.0;

        long sum = 0;
        for (long t : tickTimes) {
            sum += t;
        }
        return (sum / (double) tickTimes.length) / 1_000_000.0;
    }

    private static void sendHeader(CommandSourceStack source, String title) {
        source.sendSuccess(() ->
                Component.literal("=== " + title + " ===")
                        .withStyle(ChatFormatting.GOLD), false);
    }

    private static void sendSubHeader(CommandSourceStack source, String title) {
        source.sendSuccess(() ->
                Component.literal("--- " + title + " ---")
                        .withStyle(ChatFormatting.YELLOW), false);
    }

    private static void sendKeyValue(CommandSourceStack source, String label, String value, ChatFormatting valueColor) {
        source.sendSuccess(() ->
                Component.literal("  " + label + ": ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(value)
                                .withStyle(valueColor)), false);
    }

    private static void sendNumberedEntry(CommandSourceStack source, int rank, String name, int count) {
        source.sendSuccess(() ->
                Component.literal("  " + rank + ". ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(CommandChatHelper.copyable(name, name, ChatFormatting.WHITE))
                        .append(Component.literal("  x " + String.format("%,d", count))
                                .withStyle(ChatFormatting.YELLOW)), false);
    }

    private static void sendDensityLine(CommandSourceStack source, String label, int count, int warnThreshold) {
        ChatFormatting color = count > warnThreshold * 2 ? ChatFormatting.RED
                : count > warnThreshold ? ChatFormatting.YELLOW : ChatFormatting.GREEN;
        sendKeyValue(source, "  " + label, String.valueOf(count), color);
    }

    private record ItemData(ResourceLocation itemId, int y) {
    }

    private record HistorySnapshot(double tps, double mspt, int totalEntities, int totalItems, long timestamp) {
    }

    private static final class TileCluster {
        final String dimId;
        final int chunkX;
        final int chunkZ;
        int total;
        long sumY;
        final Map<String, Integer> typeCounts = new HashMap<>();

        TileCluster(String dimId, int chunkX, int chunkZ) {
            this.dimId = dimId;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    private static final class HotspotData {
        final String dim;
        final int regionX;
        final int regionZ;
        int total;
        long sumY;
        int items;
        int mobs;
        final Map<EntityType<?>, Integer> entityTypes = new HashMap<>();

        HotspotData(String dim, int regionX, int regionZ) {
            this.dim = dim;
            this.regionX = regionX;
            this.regionZ = regionZ;
        }
    }
}
