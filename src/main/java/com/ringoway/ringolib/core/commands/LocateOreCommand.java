package com.ringoway.ringolib.core.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Spiral chunk-section ore locator supporting both single block IDs and #block_tags.
 * Available under /locate ore <target> [radius] and /rl locate ore <target> [radius].
 */
final class LocateOreCommand {

    private static final DynamicCommandExceptionType ERROR_INVALID_TARGET =
            new DynamicCommandExceptionType(id -> Component.translatable("commands.ringolib.locate_ore.unknown_block", id));

    private LocateOreCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildLocateNode() {
        return Commands.literal("locate")
                .then(buildOreNode());
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildOreNode() {
        return Commands.literal("ore")
                .then(Commands.argument("target", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.BLOCK))
                        .suggests(LocateOreCommand::suggestOres)
                        .executes(context -> locateOre(context, 512))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 2048))
                                .executes(context -> locateOre(context, IntegerArgumentType.getInteger(context, "radius")))
                        )
                );
    }

    private static CompletableFuture<Suggestions> suggestOres(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        Set<String> suggestions = new LinkedHashSet<>();

        try {
            BuiltInRegistries.BLOCK.getTagNames().forEach(tagKey -> {
                ResourceLocation loc = tagKey.location();
                if (loc.getNamespace().equals("c") && loc.getPath().startsWith("ores")) {
                    suggestions.add("#" + loc);
                    BuiltInRegistries.BLOCK.getTag(tagKey).ifPresent(tag -> {
                        for (var holder : tag) {
                            holder.unwrapKey().ifPresent(hKey -> suggestions.add(hKey.location().toString()));
                        }
                    });
                }
            });
        } catch (Exception ignored) {
        }

        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (id.getPath().contains("ore")) {
                suggestions.add(id.toString());
            }
        }

        return SharedSuggestionProvider.suggest(suggestions, builder);
    }

    private static int locateOre(CommandContext<CommandSourceStack> context, int radiusBlocks) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ResourceOrTagKeyArgument.Result<Block> targetArg =
                ResourceOrTagKeyArgument.getResourceOrTagKey(context, "target", Registries.BLOCK, ERROR_INVALID_TARGET);

        Either<ResourceKey<Block>, TagKey<Block>> either = targetArg.unwrap();
        Predicate<BlockState> matcher;
        String displayName;

        if (either.left().isPresent()) {
            ResourceLocation blockId = either.left().get().location();
            Optional<Block> optBlock = BuiltInRegistries.BLOCK.getOptional(blockId);
            if (optBlock.isEmpty()) {
                source.sendFailure(Component.translatable("commands.ringolib.locate_ore.unknown_block", blockId.toString())
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            Block targetBlock = optBlock.get();
            matcher = state -> state.is(targetBlock);
            displayName = blockId.toString();
        } else {
            TagKey<Block> tagKey = either.right().orElseThrow();
            boolean hasAny = BuiltInRegistries.BLOCK.getTag(tagKey)
                    .map(t -> t.stream().findAny().isPresent())
                    .orElse(false);
            if (!hasAny) {
                source.sendFailure(Component.translatable("commands.ringolib.locate_ore.unknown_tag", "#" + tagKey.location())
                        .withStyle(ChatFormatting.RED));
                return 0;
            }
            matcher = state -> state.is(tagKey);
            displayName = "#" + tagKey.location();
        }

        long startNanos = System.nanoTime();
        ServerLevel level = source.getLevel();
        BlockPos playerPos = BlockPos.containing(source.getPosition());
        int centerChunkX = playerPos.getX() >> 4;
        int centerChunkZ = playerPos.getZ() >> 4;
        int maxChunkRadius = (radiusBlocks + 15) / 16;

        long radiusSq = (long) radiusBlocks * radiusBlocks;
        long closestFoundDistSq = Long.MAX_VALUE;
        BlockPos closestPos = null;

        int[] loadBudget = {256};
        int chunksChecked = 0;

        for (int r = 0; r <= maxChunkRadius; r++) {
            if (closestPos != null && r > 0) {
                long minRingDist = Math.max(0, (r - 1) * 16L);
                if (minRingDist * minRingDist >= closestFoundDistSq) {
                    break;
                }
            }

            List<int[]> ringCoords = getRingChunkCoordinates(centerChunkX, centerChunkZ, r);
            for (int[] coords : ringCoords) {
                int cx = coords[0];
                int cz = coords[1];

                int minX = cx << 4;
                int maxX = minX + 15;
                int minZ = cz << 4;
                int maxZ = minZ + 15;

                int dx = Math.max(0, Math.max(minX - playerPos.getX(), playerPos.getX() - maxX));
                int dz = Math.max(0, Math.max(minZ - playerPos.getZ(), playerPos.getZ() - maxZ));
                long minChunkDistSq = (long) dx * dx + (long) dz * dz;

                if (minChunkDistSq > radiusSq) continue;
                if (closestFoundDistSq != Long.MAX_VALUE && minChunkDistSq >= closestFoundDistSq) continue;

                ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) {
                    if (loadBudget[0] > 0) {
                        chunk = level.getChunk(cx, cz, ChunkStatus.FULL, true);
                        loadBudget[0]--;
                    } else {
                        continue;
                    }
                }
                if (chunk == null) continue;
                chunksChecked++;

                LevelChunkSection[] sections = chunk.getSections();
                for (int sIdx = 0; sIdx < sections.length; sIdx++) {
                    LevelChunkSection section = sections[sIdx];
                    if (section == null || section.hasOnlyAir()) continue;
                    if (!section.maybeHas(matcher)) continue;

                    int sectionBottomY = level.getMinBuildHeight() + (sIdx << 4);
                    int dy = Math.max(0, Math.max(sectionBottomY - playerPos.getY(), playerPos.getY() - (sectionBottomY + 15)));
                    long minSectionDistSq = (long) dx * dx + (long) dz * dz + (long) dy * dy;
                    if (minSectionDistSq >= closestFoundDistSq || minSectionDistSq > radiusSq) continue;

                    for (int y = 0; y < 16; y++) {
                        int worldY = sectionBottomY + y;
                        if (worldY < level.getMinBuildHeight() || worldY >= level.getMaxBuildHeight()) continue;
                        for (int x = 0; x < 16; x++) {
                            for (int z = 0; z < 16; z++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (matcher.test(state)) {
                                    int worldX = (cx << 4) + x;
                                    int worldZ = (cz << 4) + z;
                                    long distSq = (long) (worldX - playerPos.getX()) * (worldX - playerPos.getX())
                                            + (long) (worldY - playerPos.getY()) * (worldY - playerPos.getY())
                                            + (long) (worldZ - playerPos.getZ()) * (worldZ - playerPos.getZ());
                                    if (distSq <= radiusSq && distSq < closestFoundDistSq) {
                                        closestFoundDistSq = distSq;
                                        closestPos = new BlockPos(worldX, worldY, worldZ);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        long elapsedMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);
        int finalChunksChecked = chunksChecked;

        if (closestPos != null) {
            int distance = (int) Math.round(Math.sqrt(closestFoundDistSq));
            String dimId = level.dimension().location().toString();
            Component posComponent = CommandChatHelper.tpCoords(
                    closestPos.getX(), closestPos.getY(), closestPos.getZ(), dimId
            );

            source.sendSuccess(() ->
                    Component.translatable(
                            "commands.ringolib.locate_ore.found_timed",
                            displayName,
                            posComponent,
                            distance,
                            finalChunksChecked,
                            elapsedMs
                    ), false);
            return 1;
        } else {
            source.sendFailure(Component.translatable(
                    "commands.ringolib.locate_ore.not_found",
                    displayName,
                    radiusBlocks
            ));
            return 0;
        }
    }

    private static List<int[]> getRingChunkCoordinates(int centerX, int centerZ, int r) {
        if (r == 0) {
            return List.of(new int[]{centerX, centerZ});
        }
        List<int[]> list = new ArrayList<>(8 * r);
        for (int x = centerX - r; x <= centerX + r; x++) {
            list.add(new int[]{x, centerZ - r});
            list.add(new int[]{x, centerZ + r});
        }
        for (int z = centerZ - r + 1; z <= centerZ + r - 1; z++) {
            list.add(new int[]{centerX - r, z});
            list.add(new int[]{centerX + r, z});
        }
        return list;
    }
}
