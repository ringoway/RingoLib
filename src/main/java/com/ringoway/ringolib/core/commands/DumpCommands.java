package com.ringoway.ringolib.core.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.ringoway.ringolib.RingoLib;
import com.ringoway.ringolib.core.RingoLibConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

final class DumpCommands {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String[] INGOT_KEYWORDS = {"ingot", "alloy", "steel"};
    private static final List<String> EFFECT_CATEGORIES = List.of("all", "harmful", "beneficial", "neutral");

    private DumpCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildDumpNode() {
        return Commands.literal("dump")
                .then(Commands.literal("ingots")
                        .executes(DumpCommands::dumpIngots)
                )
                .then(Commands.literal("effects")
                        .executes(ctx -> dumpEffects(ctx, "harmful"))
                        .then(Commands.argument("category", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(EFFECT_CATEGORIES, builder))
                                .executes(ctx -> dumpEffects(ctx, StringArgumentType.getString(ctx, "category")))
                        )
                )
                .then(Commands.literal("fluids")
                        .executes(DumpCommands::dumpFluids)
                );
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildIngotsNode() {
        return Commands.literal("ingots")
                .then(Commands.literal("dump")
                        .executes(DumpCommands::dumpIngots)
                );
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildEffectsNode() {
        return Commands.literal("effects")
                .then(Commands.literal("dump")
                        .executes(ctx -> dumpEffects(ctx, "harmful"))
                );
    }

    private static int dumpIngots(CommandContext<CommandSourceStack> context) {
        JsonObject root = new JsonObject();
        Map<String, Integer> modCounts = new TreeMap<>();
        int totalCount = 0;

        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String path = id.getPath();
            String namespace = id.getNamespace();

            boolean matchesKeyword = false;
            for (String keyword : INGOT_KEYWORDS) {
                if (path.contains(keyword)) {
                    matchesKeyword = true;
                    break;
                }
            }
            if (!matchesKeyword) continue;

            List<String> materials = extractMaterialCandidates(path);
            String blockId = null;
            String nuggetId = null;
            String matchedMaterial = materials.get(0);

            for (String mat : materials) {
                String foundBlock = findBlockFormId(mat, namespace);
                if (foundBlock != null) {
                    blockId = foundBlock;
                    matchedMaterial = mat;
                }
                String foundNugget = findNuggetFormId(mat, namespace);
                if (foundNugget != null) {
                    nuggetId = foundNugget;
                    matchedMaterial = mat;
                }
            }

            if (blockId == null && nuggetId == null) continue;

            JsonObject entry = new JsonObject();
            entry.addProperty("name", Component.translatable(item.getDescriptionId()).getString());
            entry.addProperty("material", matchedMaterial);
            entry.addProperty("has_block", blockId != null);
            if (blockId != null) entry.addProperty("block_id", blockId);
            entry.addProperty("has_nugget", nuggetId != null);
            if (nuggetId != null) entry.addProperty("nugget_id", nuggetId);

            JsonArray cTags = new JsonArray();
            BuiltInRegistries.ITEM.wrapAsHolder(item).tags()
                    .map(TagKey::location)
                    .filter(t -> t.getNamespace().equals("c"))
                    .map(ResourceLocation::toString)
                    .sorted()
                    .forEach(cTags::add);
            entry.add("c_tags", cTags);

            root.add(id.toString(), entry);
            modCounts.merge(namespace, 1, Integer::sum);
            totalCount++;
        }

        Path outputFile = RingoLibConfig.getConfigDir().resolve("ingots_dump.json");
        try {
            Files.createDirectories(outputFile.getParent());
            try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            int finalCount = totalCount;
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.dump.ingots.success", finalCount)
                            .withStyle(ChatFormatting.GREEN)
                            .append(CommandChatHelper.copyable(outputFile.toString(), outputFile.toString(), ChatFormatting.YELLOW)),
                    true);

            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.dump.ingots.by_mod")
                            .withStyle(ChatFormatting.GOLD), false);

            modCounts.forEach((modId, count) ->
                    context.getSource().sendSuccess(() ->
                            Component.literal("  " + modId + ": ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(String.valueOf(count))
                                            .withStyle(ChatFormatting.WHITE)), false));
        } catch (IOException e) {
            context.getSource().sendFailure(
                    Component.translatable("commands.ringolib.dump.failed", e.getMessage())
                            .withStyle(ChatFormatting.RED));
            RingoLib.LOGGER.error("RingoLib: Failed to dump ingots", e);
            return 0;
        }

        return totalCount;
    }

    private static int dumpEffects(CommandContext<CommandSourceStack> context, String categoryArg) {
        String norm = categoryArg.toLowerCase(Locale.ROOT);
        if (!EFFECT_CATEGORIES.contains(norm)) {
            context.getSource().sendFailure(
                    Component.translatable("commands.ringolib.dump.effects.invalid_category", categoryArg)
                            .withStyle(ChatFormatting.RED));
            return 0;
        }

        JsonObject root = new JsonObject();
        Map<String, Integer> modCounts = new TreeMap<>();
        int count = 0;

        for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
            MobEffectCategory cat = effect.getCategory();
            boolean matches = switch (norm) {
                case "all" -> true;
                case "harmful" -> cat == MobEffectCategory.HARMFUL;
                case "beneficial" -> cat == MobEffectCategory.BENEFICIAL;
                case "neutral" -> cat == MobEffectCategory.NEUTRAL;
                default -> false;
            };
            if (!matches) continue;

            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
            if (id == null) continue;

            JsonObject effectData = new JsonObject();
            effectData.addProperty("name", Component.translatable(effect.getDescriptionId()).getString());
            effectData.addProperty("color", String.format("#%06X", effect.getColor()));
            effectData.addProperty("category", cat.name());

            root.add(id.toString(), effectData);
            modCounts.merge(id.getNamespace(), 1, Integer::sum);
            count++;
        }

        String fileName = norm.equals("harmful") ? "harmful_effects_dump.json" : ("effects_" + norm + "_dump.json");
        Path outputFile = RingoLibConfig.getConfigDir().resolve(fileName);
        try {
            Files.createDirectories(outputFile.getParent());
            try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            int finalCount = count;
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.dump.effects.success", finalCount)
                            .withStyle(ChatFormatting.GREEN)
                            .append(CommandChatHelper.copyable(outputFile.toString(), outputFile.toString(), ChatFormatting.YELLOW)),
                    true);

            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.dump.effects.by_mod")
                            .withStyle(ChatFormatting.GOLD), false);

            modCounts.forEach((modId, modCount) ->
                    context.getSource().sendSuccess(() ->
                            Component.literal("  " + modId + ": ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(String.valueOf(modCount))
                                            .withStyle(ChatFormatting.WHITE)), false));
        } catch (IOException e) {
            context.getSource().sendFailure(
                    Component.translatable("commands.ringolib.dump.failed", e.getMessage())
                            .withStyle(ChatFormatting.RED));
            RingoLib.LOGGER.error("RingoLib: Failed to dump effects", e);
            return 0;
        }

        return count;
    }

    private static int dumpFluids(CommandContext<CommandSourceStack> context) {
        JsonObject root = new JsonObject();
        Map<String, Integer> modCounts = new TreeMap<>();
        int count = 0;

        for (Fluid fluid : BuiltInRegistries.FLUID) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            if (id.getPath().equals("empty") || id.getPath().startsWith("flowing_")) continue;

            JsonObject entry = new JsonObject();
            entry.addProperty("fluid_type", BuiltInRegistries.FLUID.getKey(fluid).toString());

            ResourceLocation flowingLoc = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "flowing_" + id.getPath());
            if (BuiltInRegistries.FLUID.containsKey(flowingLoc)) {
                entry.addProperty("flowing_id", flowingLoc.toString());
            }

            Block legacyBlock = fluid.defaultFluidState().createLegacyBlock().getBlock();
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(legacyBlock);
            if (!blockId.getPath().equals("air")) {
                entry.addProperty("block_id", blockId.toString());
            }

            Item bucket = fluid.getBucket();
            ResourceLocation bucketId = BuiltInRegistries.ITEM.getKey(bucket);
            if (!bucketId.getPath().equals("air")) {
                entry.addProperty("bucket_id", bucketId.toString());
            }

            JsonArray cTags = new JsonArray();
            Holder<Fluid> holder = BuiltInRegistries.FLUID.wrapAsHolder(fluid);
            holder.tags()
                    .map(TagKey::location)
                    .filter(t -> t.getNamespace().equals("c"))
                    .map(ResourceLocation::toString)
                    .sorted()
                    .forEach(cTags::add);
            entry.add("c_tags", cTags);

            root.add(id.toString(), entry);
            modCounts.merge(id.getNamespace(), 1, Integer::sum);
            count++;
        }

        Path outputFile = RingoLibConfig.getConfigDir().resolve("fluids_dump.json");
        try {
            Files.createDirectories(outputFile.getParent());
            try (Writer writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }

            int finalCount = count;
            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.dump.fluids.success", finalCount)
                            .withStyle(ChatFormatting.GREEN)
                            .append(CommandChatHelper.copyable(outputFile.toString(), outputFile.toString(), ChatFormatting.YELLOW)),
                    true);

            context.getSource().sendSuccess(() ->
                    Component.translatable("commands.ringolib.dump.fluids.by_mod")
                            .withStyle(ChatFormatting.GOLD), false);

            modCounts.forEach((modId, modCount) ->
                    context.getSource().sendSuccess(() ->
                            Component.literal("  " + modId + ": ")
                                    .withStyle(ChatFormatting.GRAY)
                                    .append(Component.literal(String.valueOf(modCount))
                                            .withStyle(ChatFormatting.WHITE)), false));
        } catch (IOException e) {
            context.getSource().sendFailure(
                    Component.translatable("commands.ringolib.dump.failed", e.getMessage())
                            .withStyle(ChatFormatting.RED));
            RingoLib.LOGGER.error("RingoLib: Failed to dump fluids", e);
            return 0;
        }

        return count;
    }

    private static List<String> extractMaterialCandidates(String path) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String keyword : INGOT_KEYWORDS) {
            int idx = path.indexOf(keyword);
            if (idx < 0) continue;

            String before = path.substring(0, idx);
            String after = path.substring(idx + keyword.length());

            if (before.endsWith("_")) before = before.substring(0, before.length() - 1);
            if (after.startsWith("_")) after = after.substring(1);

            if (!before.isEmpty()) result.add(before);
            if (!after.isEmpty()) result.add(after);
        }
        result.add(path);
        return new ArrayList<>(result);
    }

    private static boolean hasTagEntries(String tagPath) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", tagPath));
        try {
            return BuiltInRegistries.ITEM.getTag(tag)
                    .map(holders -> holders.stream().findAny().isPresent())
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private static String findBlockFormId(String material, String namespace) {
        ResourceLocation suffix = ResourceLocation.fromNamespaceAndPath(namespace, material + "_block");
        if (BuiltInRegistries.ITEM.containsKey(suffix)) return suffix.toString();

        ResourceLocation prefix = ResourceLocation.fromNamespaceAndPath(namespace, "block_of_" + material);
        if (BuiltInRegistries.ITEM.containsKey(prefix)) return prefix.toString();

        if (hasTagEntries("storage_blocks/" + material)) return "#c:storage_blocks/" + material;
        return null;
    }

    private static String findNuggetFormId(String material, String namespace) {
        ResourceLocation suffix = ResourceLocation.fromNamespaceAndPath(namespace, material + "_nugget");
        if (BuiltInRegistries.ITEM.containsKey(suffix)) return suffix.toString();

        if (hasTagEntries("nuggets/" + material)) return "#c:nuggets/" + material;
        return null;
    }
}
