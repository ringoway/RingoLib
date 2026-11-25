package com.ringoway.ringolib.core.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.serialization.JsonOps;
import com.ringoway.ringolib.RingoLib;
import com.ringoway.ringolib.api.RecipeFilterManager;
import com.ringoway.ringolib.core.RingoLibConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Recipe inspection, live persistent disabling, and crafting-conflict scanner (/rl recipes ...).
 */
final class RecipeDebugCommands {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private RecipeDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildNode() {
        return Commands.literal("recipes")
                .then(Commands.literal("hand")
                        .executes(RecipeDebugCommands::recipesHand)
                )
                .then(Commands.literal("of")
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .suggests((ctx, builder) ->
                                        SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder))
                                .executes(RecipeDebugCommands::recipesOfItem)
                        )
                )
                .then(Commands.literal("disable")
                        .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                .suggests(RecipeDebugCommands::suggestActiveRecipes)
                                .executes(RecipeDebugCommands::disableRecipe)
                        )
                )
                .then(Commands.literal("enable")
                        .then(Commands.argument("pattern", StringArgumentType.greedyString())
                                .suggests(RecipeDebugCommands::suggestDisabledRules)
                                .executes(RecipeDebugCommands::enableRecipe)
                        )
                )
                .then(Commands.literal("list")
                        .executes(RecipeDebugCommands::listDisabled)
                )
                .then(Commands.literal("conflicts")
                        .executes(RecipeDebugCommands::findCraftingConflicts)
                );
    }

    private static CompletableFuture<Suggestions> suggestActiveRecipes(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggestResource(
                context.getSource().getServer().getRecipeManager().getRecipeIds(),
                builder
        );
    }

    private static CompletableFuture<Suggestions> suggestDisabledRules(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder
    ) {
        return SharedSuggestionProvider.suggest(RecipeFilterManager.getRawRules(), builder);
    }

    private static int recipesHand(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack stack = HandCommand.getHeldStack(source);
        if (stack == null) return 0;
        return inspectStackRecipes(source, stack);
    }

    private static int recipesOfItem(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ResourceLocation itemId = ResourceLocationArgument.getId(context, "item");
        Optional<Item> optItem = BuiltInRegistries.ITEM.getOptional(itemId);
        if (optItem.isEmpty()) {
            source.sendFailure(Component.translatable("commands.ringolib.ban.item_not_found", itemId.toString())
                    .withStyle(ChatFormatting.RED));
            return 0;
        }
        return inspectStackRecipes(source, new ItemStack(optItem.get()));
    }

    static int inspectStackRecipes(CommandSourceStack source, ItemStack stack) {
        MinecraftServer server = source.getServer();
        HolderLookup.Provider registries = server.registryAccess();
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();

        List<RecipeHolder<?>> outputs = findOutputRecipes(server, registries, stack.getItem());
        List<RecipeHolder<?>> inputs = findInputRecipes(server, stack);

        source.sendSuccess(() -> Component.literal("=== ")
                .append(Component.translatable("commands.ringolib.recipes.header", itemId))
                .append(" ===")
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.translatable("commands.ringolib.recipes.outputs", outputs.size())
                .withStyle(ChatFormatting.DARK_AQUA), false);

        if (outputs.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.recipes.none")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        } else {
            for (RecipeHolder<?> holder : outputs) {
                sendOutputRecipeLine(source, holder);
            }
        }

        source.sendSuccess(() -> Component.translatable("commands.ringolib.recipes.inputs", inputs.size())
                .withStyle(ChatFormatting.DARK_AQUA), false);

        int shownInputs = 0;
        for (RecipeHolder<?> holder : inputs) {
            if (shownInputs >= 15) {
                int remaining = inputs.size() - shownInputs;
                source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.recipes_more", remaining)
                        .withStyle(ChatFormatting.DARK_GRAY), false);
                break;
            }
            sendInputRecipeLine(source, holder);
            shownInputs++;
        }

        return outputs.size() + inputs.size();
    }

    static void sendOutputRecipeLine(CommandSourceStack source, RecipeHolder<?> holder) {
        String id = holder.id().toString();
        ResourceLocation typeLoc = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
        String typeStr = typeLoc != null ? typeLoc.toString() : "unknown";

        source.sendSuccess(() -> Component.literal("  - ").withStyle(ChatFormatting.YELLOW)
                .append(CommandChatHelper.copyable(id, id, ChatFormatting.GREEN))
                .append(Component.literal(" (" + typeStr + ")").withStyle(ChatFormatting.DARK_GRAY))
                .append(CommandChatHelper.runBadge(
                        "Disable",
                        "/rl recipes disable " + id,
                        Component.translatable("commands.ringolib.recipes.disable_hover", id),
                        ChatFormatting.RED
                )), false);
    }

    private static void sendInputRecipeLine(CommandSourceStack source, RecipeHolder<?> holder) {
        String id = holder.id().toString();
        ResourceLocation typeLoc = BuiltInRegistries.RECIPE_SERIALIZER.getKey(holder.value().getSerializer());
        String typeStr = typeLoc != null ? typeLoc.toString() : "unknown";

        source.sendSuccess(() -> Component.literal("  - ").withStyle(ChatFormatting.GRAY)
                .append(CommandChatHelper.copyable(id, id, ChatFormatting.AQUA))
                .append(Component.literal(" (" + typeStr + ")").withStyle(ChatFormatting.DARK_GRAY))
                .append(CommandChatHelper.runBadge(
                        "Disable",
                        "/rl recipes disable " + id,
                        Component.translatable("commands.ringolib.recipes.disable_hover", id),
                        ChatFormatting.RED
                )), false);
    }

    static List<RecipeHolder<?>> findOutputRecipes(
            MinecraftServer server,
            HolderLookup.Provider registries,
            Item targetItem
    ) {
        String targetId = BuiltInRegistries.ITEM.getKey(targetItem).toString();
        RegistryOps<JsonElement> jsonOps = registries.createSerializationContext(JsonOps.INSTANCE);
        List<RecipeHolder<?>> result = new ArrayList<>();

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            boolean matched = false;
            try {
                ItemStack out = holder.value().getResultItem(registries);
                if (!out.isEmpty() && out.is(targetItem)) {
                    matched = true;
                }
            } catch (Exception ignored) {
            }

            if (!matched) {
                matched = jsonContainsOutputItem(holder.value(), jsonOps, targetId);
            }

            if (matched) {
                result.add(holder);
            }
        }

        result.sort(Comparator.comparing(h -> h.id().toString()));
        return result;
    }

    static List<RecipeHolder<?>> findInputRecipes(MinecraftServer server, ItemStack targetStack) {
        ItemStack probe = targetStack.copyWithCount(1);
        List<RecipeHolder<?>> result = new ArrayList<>();

        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            try {
                for (Ingredient ingredient : holder.value().getIngredients()) {
                    if (!ingredient.isEmpty() && ingredient.test(probe)) {
                        result.add(holder);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        result.sort(Comparator.comparing(h -> h.id().toString()));
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean jsonContainsOutputItem(Recipe<?> recipe, RegistryOps<JsonElement> jsonOps, String targetId) {
        try {
            Optional<JsonElement> encoded = ((com.mojang.serialization.Codec) Recipe.CODEC)
                    .encodeStart(jsonOps, recipe)
                    .result();
            if (encoded.isEmpty() || !encoded.get().isJsonObject()) {
                return false;
            }
            JsonObject obj = encoded.get().getAsJsonObject();
            if (matchesItemJson(obj.get("result"), targetId)) return true;
            if (matchesItemJson(obj.get("results"), targetId)) return true;
            if (matchesItemJson(obj.get("output"), targetId)) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    private static boolean matchesItemJson(JsonElement el, String targetId) {
        if (el == null || el.isJsonNull()) return false;
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            for (String key : List.of("id", "item")) {
                if (obj.has(key) && obj.get(key).isJsonPrimitive() && targetId.equals(obj.get(key).getAsString())) {
                    return true;
                }
            }
        } else if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                if (matchesItemJson(child, targetId)) return true;
            }
        }
        return false;
    }

    private static int disableRecipe(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String rule = stripQuotes(StringArgumentType.getString(context, "pattern"));

        if (!rule.contains("*") && !rule.contains("?") && ResourceLocation.tryParse(rule) == null) {
            source.sendFailure(Component.translatable("commands.ringolib.recipes.invalid_pattern", rule)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean newlyAdded = RecipeFilterManager.disableRecipePersistently(rule, source.getServer());
        if (newlyAdded) {
            source.sendSuccess(() -> Component.translatable(
                            "commands.ringolib.recipes.disabled",
                            CommandChatHelper.copyable(rule, rule, ChatFormatting.YELLOW)
                    ).withStyle(ChatFormatting.GREEN)
                    .append(CommandChatHelper.runBadge(
                            "Undo",
                            "/rl recipes enable " + rule,
                            Component.translatable("commands.ringolib.recipes.enable_hover", rule),
                            ChatFormatting.GRAY
                    )), true);
            return 1;
        } else {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.recipes.already_disabled", rule)
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
    }

    private static int enableRecipe(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String rule = stripQuotes(StringArgumentType.getString(context, "pattern"));

        if (RecipeFilterManager.enableRecipePersistently(rule)) {
            source.sendSuccess(() -> Component.translatable(
                            "commands.ringolib.recipes.enabled",
                            CommandChatHelper.copyable(rule, rule, ChatFormatting.YELLOW)
                    ).withStyle(ChatFormatting.GREEN)
                    .append(CommandChatHelper.runBadge(
                            "Reload",
                            "/reload",
                            Component.translatable("commands.ringolib.recipes.reload_hover"),
                            ChatFormatting.AQUA
                    )), true);
            return 1;
        } else {
            source.sendFailure(Component.translatable("commands.ringolib.recipes.not_disabled", rule)
                    .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
    }

    private static int listDisabled(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Set<String> rules = RecipeFilterManager.getRawRules();

        if (rules.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.recipes.list_empty")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.ringolib.recipes.list_header", rules.size())
                .withStyle(ChatFormatting.GOLD), false);

        for (String rule : rules) {
            source.sendSuccess(() -> Component.literal("  - ").withStyle(ChatFormatting.YELLOW)
                    .append(CommandChatHelper.copyable(rule, rule, ChatFormatting.GRAY))
                    .append(CommandChatHelper.runBadge(
                            "Enable",
                            "/rl recipes enable " + rule,
                            Component.translatable("commands.ringolib.recipes.enable_hover", rule),
                            ChatFormatting.GREEN
                    )), false);
        }
        return rules.size();
    }

    private static int findCraftingConflicts(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        HolderLookup.Provider registries = server.registryAccess();

        List<RecipeHolder<CraftingRecipe>> crafting = server.getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(h -> !h.value().isSpecial())
                .toList();

        JsonArray reportArr = new JsonArray();
        List<ConflictPair> conflicts = new ArrayList<>();

        for (int i = 0; i < crafting.size(); i++) {
            RecipeHolder<CraftingRecipe> a = crafting.get(i);
            ItemStack outA = safeResult(a.value(), registries);
            if (outA.isEmpty()) continue;

            for (int j = i + 1; j < crafting.size(); j++) {
                RecipeHolder<CraftingRecipe> b = crafting.get(j);
                ItemStack outB = safeResult(b.value(), registries);
                if (outB.isEmpty()) continue;
                if (ItemStack.isSameItemSameComponents(outA, outB)) continue;

                if (craftingInputsConflict(a.value(), b.value())) {
                    conflicts.add(new ConflictPair(a.id(), outA, b.id(), outB));

                    JsonObject entry = new JsonObject();
                    entry.addProperty("recipe_a", a.id().toString());
                    entry.addProperty("output_a", BuiltInRegistries.ITEM.getKey(outA.getItem()).toString());
                    entry.addProperty("recipe_b", b.id().toString());
                    entry.addProperty("output_b", BuiltInRegistries.ITEM.getKey(outB.getItem()).toString());
                    reportArr.add(entry);
                }
            }
        }

        Path outFile = RingoLibConfig.getConfigDir().resolve("recipe_conflicts.json");
        try {
            Files.createDirectories(outFile.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("total_conflicts", conflicts.size());
            root.add("conflicts", reportArr);
            try (Writer writer = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception e) {
            RingoLib.LOGGER.error("RingoLib: Failed to write recipe_conflicts.json", e);
        }

        if (conflicts.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.recipes.conflicts_none")
                    .withStyle(ChatFormatting.GREEN), false);
            return 0;
        }

        source.sendSuccess(() -> Component.translatable("commands.ringolib.recipes.conflicts_found", conflicts.size())
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" (" + outFile.getFileName() + ")").withStyle(ChatFormatting.DARK_GRAY)), false);

        int limit = Math.min(15, conflicts.size());
        for (int i = 0; i < limit; i++) {
            ConflictPair c = conflicts.get(i);
            String idA = c.idA.toString();
            String idB = c.idB.toString();
            String outAStr = BuiltInRegistries.ITEM.getKey(c.outA.getItem()).toString();
            String outBStr = BuiltInRegistries.ITEM.getKey(c.outB.getItem()).toString();

            source.sendSuccess(() -> Component.literal("  ")
                    .append(CommandChatHelper.copyable(idA, idA, ChatFormatting.YELLOW))
                    .append(CommandChatHelper.runBadge("Disable", "/rl recipes disable " + idA,
                            Component.translatable("commands.ringolib.recipes.disable_hover", idA), ChatFormatting.RED))
                    .append(Component.literal(" (" + outAStr + ") <-> ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(CommandChatHelper.copyable(idB, idB, ChatFormatting.YELLOW))
                    .append(CommandChatHelper.runBadge("Disable", "/rl recipes disable " + idB,
                            Component.translatable("commands.ringolib.recipes.disable_hover", idB), ChatFormatting.RED))
                    .append(Component.literal(" (" + outBStr + ")").withStyle(ChatFormatting.DARK_GRAY)),
                    false);
        }

        if (conflicts.size() > limit) {
            int more = conflicts.size() - limit;
            source.sendSuccess(() -> Component.translatable("commands.ringolib.recipes.conflicts_more", more, outFile.toString())
                    .withStyle(ChatFormatting.GRAY), false);
        }

        return conflicts.size();
    }

    private static ItemStack safeResult(CraftingRecipe recipe, HolderLookup.Provider registries) {
        try {
            return recipe.getResultItem(registries);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean craftingInputsConflict(CraftingRecipe a, CraftingRecipe b) {
        if (a instanceof ShapedRecipe sa && b instanceof ShapedRecipe sb) {
            if (sa.getWidth() != sb.getWidth() || sa.getHeight() != sb.getHeight()) {
                return false;
            }
            List<Ingredient> ingA = sa.getIngredients();
            List<Ingredient> ingB = sb.getIngredients();
            if (ingA.size() != ingB.size()) return false;
            for (int i = 0; i < ingA.size(); i++) {
                if (!ingredientsSlotOverlap(ingA.get(i), ingB.get(i))) {
                    return false;
                }
            }
            return true;
        }

        if (a instanceof ShapelessRecipe sla && b instanceof ShapelessRecipe slb) {
            return shapelessOverlap(sla.getIngredients(), slb.getIngredients());
        }

        if (a instanceof ShapedRecipe sa && b instanceof ShapelessRecipe slb
                && sa.getWidth() == 1 && sa.getHeight() == 1) {
            return shapelessOverlap(nonEmptyIngredients(sa.getIngredients()), slb.getIngredients());
        }
        if (b instanceof ShapedRecipe sb && a instanceof ShapelessRecipe sla
                && sb.getWidth() == 1 && sb.getHeight() == 1) {
            return shapelessOverlap(sla.getIngredients(), nonEmptyIngredients(sb.getIngredients()));
        }

        return false;
    }

    private static List<Ingredient> nonEmptyIngredients(List<Ingredient> list) {
        return list.stream().filter(i -> !i.isEmpty()).toList();
    }

    private static boolean shapelessOverlap(List<Ingredient> a, List<Ingredient> b) {
        if (a.isEmpty() || a.size() != b.size()) return false;
        boolean[] used = new boolean[b.size()];
        return matchBipartite(a, b, 0, used);
    }

    private static boolean matchBipartite(List<Ingredient> a, List<Ingredient> b, int idx, boolean[] used) {
        if (idx == a.size()) return true;
        Ingredient cur = a.get(idx);
        for (int j = 0; j < b.size(); j++) {
            if (!used[j] && ingredientsOverlap(cur, b.get(j))) {
                used[j] = true;
                if (matchBipartite(a, b, idx + 1, used)) {
                    return true;
                }
                used[j] = false;
            }
        }
        return false;
    }

    private static boolean ingredientsSlotOverlap(Ingredient a, Ingredient b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return ingredientsOverlap(a, b);
    }

    private static boolean ingredientsOverlap(Ingredient a, Ingredient b) {
        ItemStack[] itemsA = a.getItems();
        ItemStack[] itemsB = b.getItems();
        if (itemsA.length == 0 || itemsB.length == 0) return false;

        Set<Item> setA = new HashSet<>();
        for (ItemStack s : itemsA) {
            if (!s.isEmpty()) setA.add(s.getItem());
        }
        for (ItemStack s : itemsB) {
            if (!s.isEmpty() && setA.contains(s.getItem())) {
                return true;
            }
        }
        return false;
    }

    private static String stripQuotes(String raw) {
        String s = raw.trim();
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1).trim();
        }
        return s;
    }

    private record ConflictPair(ResourceLocation idA, ItemStack outA, ResourceLocation idB, ItemStack outB) {
    }
}
