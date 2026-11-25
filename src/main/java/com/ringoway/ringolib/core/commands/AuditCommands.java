package com.ringoway.ringolib.core.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.ringoway.ringolib.RingoLib;
import com.ringoway.ringolib.api.MigrationEntry;
import com.ringoway.ringolib.api.RecipeFilterManager;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import com.ringoway.ringolib.api.RingoMigrationAPI;
import com.ringoway.ringolib.core.RingoLibConfig;
import com.ringoway.ringolib.core.ban.BannedItemsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * RingoLib configuration audit, rule validation, and cross-mod material duplicate detector (/rl audit ...).
 */
final class AuditCommands {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final List<String> ITEM_TAG_PREFIXES = List.of(
            "ingots/", "nuggets/", "storage_blocks/", "raw_materials/",
            "plates/", "dusts/", "gears/", "rods/", "wires/", "gems/"
    );

    private AuditCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildNode() {
        return Commands.literal("audit")
                .then(Commands.literal("status")
                        .executes(AuditCommands::showStatus)
                )
                .then(Commands.literal("validate")
                        .executes(AuditCommands::validateRules)
                )
                .then(Commands.literal("duplicates")
                        .executes(AuditCommands::scanDuplicates)
                );
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        var redirects = RegistryRedirectManager.getAllRedirects();
        int itemRedirects = redirects.getOrDefault(Registries.ITEM, Map.of()).size();
        int blockRedirects = redirects.getOrDefault(Registries.BLOCK, Map.of()).size();
        int fluidRedirects = redirects.getOrDefault(Registries.FLUID, Map.of()).size();
        int totalRedirects = redirects.values().stream().mapToInt(Map::size).sum();

        source.sendSuccess(() -> Component.literal("=== RingoLib Status ===")
                .withStyle(ChatFormatting.GOLD), false);

        sendMetric(source, "Item migrations", RingoMigrationAPI.getAllItemMigrations().size(), ChatFormatting.WHITE);
        sendMetric(source, "Block migrations", RingoMigrationAPI.getAllBlockMigrations().size(), ChatFormatting.WHITE);
        sendMetric(source, "Fluid migrations", RingoMigrationAPI.getAllFluidMigrations().size(), ChatFormatting.WHITE);
        sendMetric(source, "Registry redirects (total)", totalRedirects, ChatFormatting.AQUA);
        sendMetric(source, "  Item / Block / Fluid redirects",
                itemRedirects + " / " + blockRedirects + " / " + fluidRedirects, ChatFormatting.GRAY);
        sendMetric(source, "Disabled recipe rules", RecipeFilterManager.getRawRules().size(), ChatFormatting.YELLOW);
        sendMetric(source, "Unmatched recipe rules", RecipeFilterManager.getUnmatchedRules().size(),
                RecipeFilterManager.getUnmatchedRules().isEmpty() ? ChatFormatting.GREEN : ChatFormatting.RED);
        sendMetric(source, "Banned items", BannedItemsManager.getBannedItems().size(), ChatFormatting.RED);

        return 1;
    }

    private static int validateRules(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<String> issues = new ArrayList<>();

        for (MigrationEntry entry : RingoMigrationAPI.getAllItemMigrations().values()) {
            if (entry.isTargetAvailable()) {
                ResourceLocation to = ResourceLocation.tryParse(entry.targetId());
                if (to == null || !BuiltInRegistries.ITEM.containsKey(to)) {
                    issues.add("[items] Target missing in item registry: " + entry.oldId() + " -> " + entry.targetId());
                }
            }
        }

        for (MigrationEntry entry : RingoMigrationAPI.getAllBlockMigrations().values()) {
            if (entry.isTargetAvailable()) {
                ResourceLocation to = ResourceLocation.tryParse(entry.targetId());
                if (to == null || !BuiltInRegistries.BLOCK.containsKey(to)) {
                    issues.add("[blocks] Target missing in block registry: " + entry.oldId() + " -> " + entry.targetId());
                }
            }
        }

        for (MigrationEntry entry : RingoMigrationAPI.getAllFluidMigrations().values()) {
            if (entry.isTargetAvailable()) {
                ResourceLocation to = ResourceLocation.tryParse(entry.targetId());
                if (to == null || !BuiltInRegistries.FLUID.containsKey(to)) {
                    issues.add("[fluids] Target missing in fluid registry: " + entry.oldId() + " -> " + entry.targetId());
                }
            }
        }

        for (var regEntry : RegistryRedirectManager.getAllRedirects().entrySet()) {
            ResourceKey<? extends Registry<?>> regKey = regEntry.getKey();
            @SuppressWarnings({"rawtypes", "unchecked"})
            Registry<?> registry = BuiltInRegistries.REGISTRY.get((ResourceKey) regKey);
            for (var redirect : regEntry.getValue().values()) {
                if (redirect.isTargetAvailable() && registry != null && !registry.containsKey(redirect.toId())) {
                    issues.add("[" + regKey.location() + "] Redirect target missing: "
                            + redirect.fromId() + " -> " + redirect.toId());
                }
            }
        }

        for (String unmatched : RecipeFilterManager.getUnmatchedRules()) {
            issues.add("[disabled_recipes] Rule matched 0 recipes: " + unmatched);
        }

        for (ResourceLocation bannedId : BannedItemsManager.getBannedItems()) {
            if (!BuiltInRegistries.ITEM.containsKey(bannedId)) {
                issues.add("[banned_items] Item not found in registry: " + bannedId);
            }
        }

        if (issues.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.audit.valid")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendSuccess(() -> Component.translatable("commands.ringolib.audit.issues_header", issues.size())
                .withStyle(ChatFormatting.RED), false);
        int limit = Math.min(25, issues.size());
        for (int i = 0; i < limit; i++) {
            String msg = issues.get(i);
            source.sendSuccess(() -> Component.literal("  - " + msg).withStyle(ChatFormatting.YELLOW), false);
        }
        if (issues.size() > limit) {
            int more = issues.size() - limit;
            source.sendSuccess(() -> Component.literal("  ... +" + more + " more").withStyle(ChatFormatting.GRAY), false);
        }
        return issues.size();
    }

    private static int scanDuplicates(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Map<String, Set<ResourceLocation>> itemGroups = new TreeMap<>();
        Map<String, Set<ResourceLocation>> fluidGroups = new TreeMap<>();

        // 1. Tag-based item duplicate detection (#c:ingots/*, #c:plates/*, etc.)
        BuiltInRegistries.ITEM.getTagNames().forEach(tagKey -> {
            ResourceLocation tagLoc = tagKey.location();
            if (!tagLoc.getNamespace().equals("c")) return;
            String tagPath = tagLoc.getPath();
            boolean trackedFamily = ITEM_TAG_PREFIXES.stream().anyMatch(tagPath::startsWith);
            if (!trackedFamily) return;

            BuiltInRegistries.ITEM.getTag(tagKey).ifPresent(holders -> {
                for (var holder : holders) {
                    holder.unwrapKey().ifPresent(k -> {
                        ResourceLocation itemId = k.location();
                        if (!RegistryRedirectManager.isItemRedirected(itemId)) {
                            itemGroups.computeIfAbsent("tag:c:" + tagPath, x -> new TreeSet<>()).add(itemId);
                        }
                    });
                }
            });
        });

        // 2. Naming-convention item duplicate detection (catches untagged sheets/ingots/nuggets)
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (RegistryRedirectManager.isItemRedirected(id)) continue;

            String key = extractItemNamingGroup(id.getPath());
            if (key != null) {
                itemGroups.computeIfAbsent("name:" + key, x -> new TreeSet<>()).add(id);
            }
        }

        // 3. Tag-based & naming-based fluid duplicate detection
        BuiltInRegistries.FLUID.getTagNames().forEach(tagKey -> {
            ResourceLocation tagLoc = tagKey.location();
            if (!tagLoc.getNamespace().equals("c")) return;
            BuiltInRegistries.FLUID.getTag(tagKey).ifPresent(holders -> {
                for (var holder : holders) {
                    holder.unwrapKey().ifPresent(k -> {
                        ResourceLocation fluidId = k.location();
                        if (!fluidId.getPath().startsWith("flowing_")
                                && !RegistryRedirectManager.shouldCancel(Registries.FLUID, fluidId)) {
                            fluidGroups.computeIfAbsent("tag:c:" + tagLoc.getPath(), x -> new TreeSet<>()).add(fluidId);
                        }
                    });
                }
            });
        });

        for (Fluid fluid : BuiltInRegistries.FLUID) {
            ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
            String path = id.getPath();
            if (path.startsWith("flowing_") || RegistryRedirectManager.shouldCancel(Registries.FLUID, id)) continue;
            if (path.startsWith("molten_") || path.endsWith("_oil") || path.endsWith("_acid")) {
                fluidGroups.computeIfAbsent("name:" + path, x -> new TreeSet<>()).add(id);
            }
        }

        Map<String, Set<ResourceLocation>> multiModItems = filterMultiNamespaceGroups(itemGroups);
        Map<String, Set<ResourceLocation>> multiModFluids = filterMultiNamespaceGroups(fluidGroups);

        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Candidate cross-mod duplicates discovered by /rl audit duplicates");
        root.addProperty("item_duplicate_groups", multiModItems.size());
        root.addProperty("fluid_duplicate_groups", multiModFluids.size());

        JsonObject groupsObj = new JsonObject();
        JsonObject itemGroupsObj = new JsonObject();
        multiModItems.forEach((group, ids) -> itemGroupsObj.add(group, toJsonArray(ids)));
        groupsObj.add("items", itemGroupsObj);

        JsonObject fluidGroupsObj = new JsonObject();
        multiModFluids.forEach((group, ids) -> fluidGroupsObj.add(group, toJsonArray(ids)));
        groupsObj.add("fluids", fluidGroupsObj);
        root.add("groups", groupsObj);

        root.add("migrations_template", buildMigrationsTemplate(multiModItems, multiModFluids));

        Path outFile = RingoLibConfig.getConfigDir().resolve("duplicate_candidates.json");
        try {
            Files.createDirectories(outFile.getParent());
            try (Writer writer = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            RingoLib.LOGGER.error("RingoLib: Failed to write duplicate_candidates.json", e);
        }

        int totalGroups = multiModItems.size() + multiModFluids.size();
        if (totalGroups == 0) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.audit.duplicates_none")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable(
                        "commands.ringolib.audit.duplicates_found",
                        multiModItems.size(),
                        multiModFluids.size()
                ).withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" -> ")).withStyle(ChatFormatting.GRAY)
                .append(CommandChatHelper.copyable(outFile.getFileName().toString(), outFile.toString(), ChatFormatting.YELLOW)),
                false);

        int printed = 0;
        for (var entry : multiModItems.entrySet()) {
            if (printed >= 12) break;
            sendDuplicateGroupLine(source, entry.getKey(), entry.getValue());
            printed++;
        }
        for (var entry : multiModFluids.entrySet()) {
            if (printed >= 15) break;
            sendDuplicateGroupLine(source, "fluid:" + entry.getKey(), entry.getValue());
            printed++;
        }

        return totalGroups;
    }

    private static void sendDuplicateGroupLine(CommandSourceStack source, String group, Set<ResourceLocation> ids) {
        var line = Component.literal("  " + group + ": ").withStyle(ChatFormatting.YELLOW);
        int i = 0;
        for (ResourceLocation id : ids) {
            if (i > 0) line.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
            String s = id.toString();
            line.append(CommandChatHelper.copyable(s, s, ChatFormatting.AQUA));
            i++;
        }
        source.sendSuccess(() -> line, false);
    }

    private static Map<String, Set<ResourceLocation>> filterMultiNamespaceGroups(Map<String, Set<ResourceLocation>> raw) {
        Map<String, Set<ResourceLocation>> filtered = new LinkedHashMap<>();
        for (var entry : raw.entrySet()) {
            Set<String> namespaces = new LinkedHashSet<>();
            for (ResourceLocation id : entry.getValue()) {
                namespaces.add(id.getNamespace());
            }
            if (namespaces.size() >= 2) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private static JsonObject buildMigrationsTemplate(
            Map<String, Set<ResourceLocation>> itemGroups,
            Map<String, Set<ResourceLocation>> fluidGroups
    ) {
        JsonObject template = new JsonObject();
        JsonArray itemsArr = new JsonArray();
        JsonArray blocksArr = new JsonArray();
        JsonArray fluidsArr = new JsonArray();

        for (var entry : itemGroups.entrySet()) {
            List<ResourceLocation> ids = new ArrayList<>(entry.getValue());
            if (ids.size() < 2) continue;
            ResourceLocation target = ids.get(0);
            boolean isBlockGroup = entry.getKey().startsWith("tag:c:storage_blocks/")
                    || entry.getKey().endsWith("_block");

            for (int i = 1; i < ids.size(); i++) {
                ResourceLocation from = ids.get(i);
                JsonObject rule = new JsonObject();
                rule.addProperty("from", from.toString());
                rule.addProperty("to", target.toString());
                rule.addProperty("required_mod", target.getNamespace());
                rule.addProperty("redirect_registry", true);
                itemsArr.add(rule);

                if (isBlockGroup && BuiltInRegistries.BLOCK.containsKey(from) && BuiltInRegistries.BLOCK.containsKey(target)) {
                    blocksArr.add(rule.deepCopy());
                }
            }
        }

        for (var entry : fluidGroups.entrySet()) {
            List<ResourceLocation> ids = new ArrayList<>(entry.getValue());
            if (ids.size() < 2) continue;
            ResourceLocation target = ids.get(0);
            for (int i = 1; i < ids.size(); i++) {
                ResourceLocation from = ids.get(i);
                JsonObject rule = new JsonObject();
                rule.addProperty("from", from.toString());
                rule.addProperty("to", target.toString());
                rule.addProperty("required_mod", target.getNamespace());
                rule.addProperty("redirect_registry", true);
                fluidsArr.add(rule);
            }
        }

        template.add("items", itemsArr);
        template.add("blocks", blocksArr);
        template.add("fluids", fluidsArr);
        return template;
    }

    private static JsonArray toJsonArray(Set<ResourceLocation> ids) {
        JsonArray arr = new JsonArray();
        ids.forEach(id -> arr.add(id.toString()));
        return arr;
    }

    private static String extractItemNamingGroup(String path) {
        for (String suffix : List.of("_ingot", "_nugget", "_sheet", "_plate", "_dust", "_gear", "_rod", "_wire")) {
            if (path.endsWith(suffix) && path.length() > suffix.length()) {
                String normalizedSuffix = suffix.equals("_sheet") ? "_plate" : suffix;
                return path.substring(0, path.length() - suffix.length()) + normalizedSuffix;
            }
        }
        if (path.startsWith("raw_") && path.length() > 4) {
            return path;
        }
        return null;
    }

    private static void sendMetric(CommandSourceStack source, String label, Object value, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal("  " + label + ": ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.valueOf(value)).withStyle(color)), false);
    }
}
