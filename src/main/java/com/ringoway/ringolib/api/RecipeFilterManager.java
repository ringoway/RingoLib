package com.ringoway.ringolib.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.core.RingoLibConfig;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import com.ringoway.ringolib.core.content.GlobMatcher;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Filters recipes from {@code config/ringolib/disabled_recipes.json} and Java API rules
 * that persist across {@code /reload}.
 */
public class RecipeFilterManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> OUTPUT_JSON_KEYS = List.of("result", "results", "output", "outputs");

    private record WildcardRule(String rawRule, Pattern pattern) {
    }

    private static final Set<String> CONFIG_RAW_RULES = ConcurrentHashMap.newKeySet();
    private static final Set<String> MATCHED_RULES = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceLocation, String> CONFIG_EXACT = new ConcurrentHashMap<>();
    private static final List<WildcardRule> CONFIG_WILDCARDS = new CopyOnWriteArrayList<>();

    private static final Set<String> API_RAW_RULES = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceLocation, String> API_EXACT = new ConcurrentHashMap<>();
    private static final List<WildcardRule> API_WILDCARDS = new CopyOnWriteArrayList<>();
    private static final List<Predicate<ResourceLocation>> API_ID_PREDICATES = new CopyOnWriteArrayList<>();
    private static final List<BiPredicate<ResourceLocation, JsonElement>> API_JSON_PREDICATES = new CopyOnWriteArrayList<>();
    private static final List<Predicate<ResourceLocation>> API_OUTPUT_ITEM_PREDICATES = new CopyOnWriteArrayList<>();

    public static void disableRecipe(String recipeId) {
        registerRule(recipeId, API_RAW_RULES, API_EXACT, API_WILDCARDS);
    }

    public static void disableRecipe(ResourceLocation recipeId) {
        if (recipeId == null) {
            return;
        }
        String raw = recipeId.toString();
        API_RAW_RULES.add(raw);
        API_EXACT.put(recipeId, raw);
        LOGGER.debug("RingoLib: Registered API disabled recipe: {}", recipeId);
    }

    public static void disableConfigRecipe(String recipeId) {
        registerRule(recipeId, CONFIG_RAW_RULES, CONFIG_EXACT, CONFIG_WILDCARDS);
    }

    public static void disableRecipes(Predicate<ResourceLocation> idPredicate) {
        Objects.requireNonNull(idPredicate, "idPredicate");
        API_ID_PREDICATES.add(idPredicate);
    }

    public static void disableRecipes(BooleanSupplier condition, Predicate<ResourceLocation> idPredicate) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(idPredicate, "idPredicate");
        API_ID_PREDICATES.add(id -> condition.getAsBoolean() && idPredicate.test(id));
    }

    public static void disableRecipesMatchingJson(BiPredicate<ResourceLocation, JsonElement> jsonPredicate) {
        Objects.requireNonNull(jsonPredicate, "jsonPredicate");
        API_JSON_PREDICATES.add(jsonPredicate);
    }

    public static void disableRecipesMatchingJson(
            BooleanSupplier condition,
            BiPredicate<ResourceLocation, JsonElement> jsonPredicate
    ) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(jsonPredicate, "jsonPredicate");
        API_JSON_PREDICATES.add((id, json) -> condition.getAsBoolean() && jsonPredicate.test(id, json));
    }

    public static void disableRecipesByOutput(ResourceLocation itemId) {
        Objects.requireNonNull(itemId, "itemId");
        API_OUTPUT_ITEM_PREDICATES.add(itemId::equals);
    }

    public static void disableRecipesByOutput(String itemId) {
        Predicate<ResourceLocation> matcher = GlobMatcher.toPredicate(itemId);
        API_OUTPUT_ITEM_PREDICATES.add(matcher);
    }

    public static void disableRecipesByOutput(Predicate<ResourceLocation> outputItemPredicate) {
        Objects.requireNonNull(outputItemPredicate, "outputItemPredicate");
        API_OUTPUT_ITEM_PREDICATES.add(outputItemPredicate);
    }

    public static void disableRecipesByOutput(
            BooleanSupplier condition,
            Predicate<ResourceLocation> outputItemPredicate
    ) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(outputItemPredicate, "outputItemPredicate");
        API_OUTPUT_ITEM_PREDICATES.add(id -> condition.getAsBoolean() && outputItemPredicate.test(id));
    }

    private static void registerRule(
            String recipeId,
            Set<String> rawRules,
            Map<ResourceLocation, String> exactMap,
            List<WildcardRule> wildcardList
    ) {
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        String trimmed = recipeId.trim();
        if (GlobMatcher.isGlob(trimmed)) {
            if (rawRules.add(trimmed)) {
                wildcardList.add(new WildcardRule(trimmed, GlobMatcher.compile(trimmed)));
                LOGGER.debug("RingoLib: Registered wildcard recipe filter: {}", trimmed);
            }
        } else {
            ResourceLocation id = ResourceLocation.tryParse(trimmed);
            if (id == null) {
                LOGGER.warn("RingoLib: Ignoring invalid recipe ResourceLocation in filter: '{}'", trimmed);
                return;
            }
            rawRules.add(id.toString());
            exactMap.put(id, id.toString());
            LOGGER.debug("RingoLib: Registered disabled recipe: {}", id);
        }
    }

    /**
     * Saves the rule to {@code disabled_recipes.json} and applies it live to the running server.
     *
     * @return true if the rule was newly added, false if invalid or already present.
     */
    public static boolean disableRecipePersistently(String rule, @Nullable MinecraftServer server) {
        if (rule == null || rule.isBlank()) {
            return false;
        }
        String trimmed = rule.trim();
        if (!GlobMatcher.isGlob(trimmed)) {
            ResourceLocation id = ResourceLocation.tryParse(trimmed);
            if (id == null) {
                return false;
            }
            trimmed = id.toString();
        }

        boolean added = RingoLibConfig.addDisabledRecipeToFile(trimmed);
        disableConfigRecipe(trimmed);

        if (server != null) {
            List<RecipeHolder<?>> remaining = server.getRecipeManager().getRecipes().stream()
                    .filter(holder -> !isRecipeDisabled(holder.id()))
                    .toList();
            server.getRecipeManager().replaceRecipes(remaining);

            ClientboundUpdateRecipesPacket packet =
                    new ClientboundUpdateRecipesPacket(server.getRecipeManager().getOrderedRecipes());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.connection.send(packet);
            }
        }
        return added;
    }

    public static boolean enableRecipePersistently(String rule) {
        if (rule == null || rule.isBlank()) {
            return false;
        }
        boolean removed = RingoLibConfig.removeDisabledRecipeFromFile(rule.trim());
        if (removed) {
            RingoLibConfig.loadDisabledRecipes();
        }
        return removed;
    }

    public static void clearConfigRules() {
        CONFIG_RAW_RULES.clear();
        MATCHED_RULES.clear();
        CONFIG_EXACT.clear();
        CONFIG_WILDCARDS.clear();
    }

    public static void clear() {
        clearConfigRules();
        API_RAW_RULES.clear();
        API_EXACT.clear();
        API_WILDCARDS.clear();
        API_ID_PREDICATES.clear();
        API_JSON_PREDICATES.clear();
        API_OUTPUT_ITEM_PREDICATES.clear();
    }

    public static void reload() {
        RingoLibConfig.loadDisabledRecipes();
    }

    public static boolean isRecipeDisabled(ResourceLocation id) {
        if (id == null) return false;

        String exactConfig = CONFIG_EXACT.get(id);
        if (exactConfig != null) {
            MATCHED_RULES.add(exactConfig);
            return true;
        }
        if (API_EXACT.containsKey(id)) {
            return true;
        }

        if (!CONFIG_WILDCARDS.isEmpty()) {
            String str = id.toString();
            for (WildcardRule rule : CONFIG_WILDCARDS) {
                if (rule.pattern().matcher(str).matches()) {
                    MATCHED_RULES.add(rule.rawRule());
                    return true;
                }
            }
        }

        if (!API_WILDCARDS.isEmpty()) {
            String str = id.toString();
            for (WildcardRule rule : API_WILDCARDS) {
                if (rule.pattern().matcher(str).matches()) {
                    return true;
                }
            }
        }

        if (!API_ID_PREDICATES.isEmpty()) {
            for (Predicate<ResourceLocation> predicate : API_ID_PREDICATES) {
                if (predicate.test(id)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static Set<String> getRawRules() {
        Set<String> all = new TreeSet<>(CONFIG_RAW_RULES);
        all.addAll(API_RAW_RULES);
        return Collections.unmodifiableSet(all);
    }

    public static Set<String> getConfigRawRules() {
        return Collections.unmodifiableSet(new TreeSet<>(CONFIG_RAW_RULES));
    }

    public static Set<String> getUnmatchedRules() {
        Set<String> unmatched = new TreeSet<>(CONFIG_RAW_RULES);
        unmatched.removeAll(MATCHED_RULES);
        return Collections.unmodifiableSet(unmatched);
    }

    public static void filterRecipes(Map<ResourceLocation, JsonElement> recipes) {
        boolean hasIdRules = !CONFIG_EXACT.isEmpty()
                || !CONFIG_WILDCARDS.isEmpty()
                || !API_EXACT.isEmpty()
                || !API_WILDCARDS.isEmpty()
                || !API_ID_PREDICATES.isEmpty();
        boolean hasJsonRules = !API_JSON_PREDICATES.isEmpty();
        boolean hasOutputRules = !API_OUTPUT_ITEM_PREDICATES.isEmpty()
                || ContentSuppressionRegistry.hasRecipeOutputSuppressions();

        if (!hasIdRules && !hasJsonRules && !hasOutputRules) {
            return;
        }

        int removed = 0;
        Iterator<Map.Entry<ResourceLocation, JsonElement>> it = recipes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = it.next();
            ResourceLocation id = entry.getKey();
            JsonElement json = entry.getValue();

            boolean remove = isRecipeDisabled(id)
                    || matchesJsonPredicates(id, json)
                    || (hasOutputRules && matchesSuppressedOutput(json));

            if (remove) {
                LOGGER.debug("RingoLib: Disabled recipe: {}", id);
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info("RingoLib: Filtered out {} disabled recipe(s)", removed);
        }
    }

    private static boolean matchesJsonPredicates(ResourceLocation id, @Nullable JsonElement json) {
        if (API_JSON_PREDICATES.isEmpty() || json == null) {
            return false;
        }
        for (BiPredicate<ResourceLocation, JsonElement> predicate : API_JSON_PREDICATES) {
            if (predicate.test(id, json)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSuppressedOutput(@Nullable JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return false;
        }
        JsonObject obj = json.getAsJsonObject();
        for (String key : OUTPUT_JSON_KEYS) {
            if (obj.has(key) && matchesOutputElement(obj.get(key))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesOutputElement(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonPrimitive()) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            return id != null && isOutputItemDisabled(id);
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (String itemKey : List.of("id", "item")) {
                if (obj.has(itemKey) && obj.get(itemKey).isJsonPrimitive()) {
                    ResourceLocation id = ResourceLocation.tryParse(obj.get(itemKey).getAsString());
                    if (id != null && isOutputItemDisabled(id)) {
                        return true;
                    }
                }
            }
            if (obj.has("stack") && matchesOutputElement(obj.get("stack"))) {
                return true;
            }
            return false;
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (JsonElement child : arr) {
                if (matchesOutputElement(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isOutputItemDisabled(ResourceLocation itemId) {
        if (ContentSuppressionRegistry.isRecipeOutputDisabled(itemId)) {
            return true;
        }
        for (Predicate<ResourceLocation> predicate : API_OUTPUT_ITEM_PREDICATES) {
            if (predicate.test(itemId)) {
                return true;
            }
        }
        return false;
    }
}
