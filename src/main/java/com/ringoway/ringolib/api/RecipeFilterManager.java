package com.ringoway.ringolib.api;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.core.RingoLibConfig;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Universal manager for filtering and disabling duplicate or unwanted recipes.
 */
public class RecipeFilterManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record WildcardRule(String rawRule, Pattern pattern) {
    }

    private static final Set<String> RAW_RULES = ConcurrentHashMap.newKeySet();
    private static final Set<String> MATCHED_RULES = ConcurrentHashMap.newKeySet();
    private static final Map<ResourceLocation, String> EXACT_DISABLED = new ConcurrentHashMap<>();
    private static final List<WildcardRule> WILDCARD_PATTERNS = new CopyOnWriteArrayList<>();

    public static void disableRecipe(String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return;
        }
        String trimmed = recipeId.trim();
        if (trimmed.contains("*") || trimmed.contains("?")) {
            RAW_RULES.add(trimmed);
            WILDCARD_PATTERNS.add(new WildcardRule(trimmed, compileWildcardPattern(trimmed)));
            LOGGER.debug("RingoLib: Registered wildcard recipe filter: {}", trimmed);
        } else {
            ResourceLocation id = ResourceLocation.tryParse(trimmed);
            if (id == null) {
                LOGGER.warn("RingoLib: Ignoring invalid recipe ResourceLocation in filter: '{}'", trimmed);
                return;
            }
            RAW_RULES.add(id.toString());
            EXACT_DISABLED.put(id, id.toString());
            LOGGER.debug("RingoLib: Registered disabled recipe: {}", id);
        }
    }

    public static void disableRecipe(ResourceLocation recipeId) {
        if (recipeId == null) {
            return;
        }
        String raw = recipeId.toString();
        RAW_RULES.add(raw);
        EXACT_DISABLED.put(recipeId, raw);
        LOGGER.debug("RingoLib: Registered disabled recipe: {}", recipeId);
    }

    /**
     * Persists a recipe filter rule into config/ringolib/disabled_recipes.json and applies it live
     * to the running server's RecipeManager (syncing updated recipes to connected players).
     *
     * @return true if the rule was newly added, false if invalid or already present.
     */
    public static boolean disableRecipePersistently(String rule, @Nullable MinecraftServer server) {
        if (rule == null || rule.isBlank()) {
            return false;
        }
        String trimmed = rule.trim();
        if (!trimmed.contains("*") && !trimmed.contains("?")) {
            ResourceLocation id = ResourceLocation.tryParse(trimmed);
            if (id == null) {
                return false;
            }
            trimmed = id.toString();
        }

        boolean added = RingoLibConfig.addDisabledRecipeToFile(trimmed);
        disableRecipe(trimmed);

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

    /**
     * Removes a rule from config/ringolib/disabled_recipes.json and reloads configured rules.
     */
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

    /**
     * Safely compiles a glob pattern (* and ?) into a bounded regular expression,
     * escaping all literal segments via Pattern.quote and collapsing consecutive wildcards
     * to prevent regex injection and catastrophic backtracking (ReDoS).
     */
    private static Pattern compileWildcardPattern(String glob) {
        String normalized = glob.replaceAll("\\*{2,}", "*");
        StringBuilder regex = new StringBuilder("^");
        int segmentStart = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '*' || c == '?') {
                if (i > segmentStart) {
                    regex.append(Pattern.quote(normalized.substring(segmentStart, i)));
                }
                regex.append(c == '*' ? ".*" : ".");
                segmentStart = i + 1;
            }
        }
        if (segmentStart < normalized.length()) {
            regex.append(Pattern.quote(normalized.substring(segmentStart)));
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    public static void clear() {
        RAW_RULES.clear();
        MATCHED_RULES.clear();
        EXACT_DISABLED.clear();
        WILDCARD_PATTERNS.clear();
    }

    public static void reload() {
        RingoLibConfig.loadDisabledRecipes();
    }

    public static boolean isRecipeDisabled(ResourceLocation id) {
        if (id == null) return false;

        String exactRule = EXACT_DISABLED.get(id);
        if (exactRule != null) {
            MATCHED_RULES.add(exactRule);
            return true;
        }
        if (!WILDCARD_PATTERNS.isEmpty()) {
            String str = id.toString();
            for (WildcardRule rule : WILDCARD_PATTERNS) {
                if (rule.pattern().matcher(str).matches()) {
                    MATCHED_RULES.add(rule.rawRule());
                    return true;
                }
            }
        }
        return false;
    }

    public static Set<String> getRawRules() {
        return Collections.unmodifiableSet(new TreeSet<>(RAW_RULES));
    }

    public static Set<String> getUnmatchedRules() {
        Set<String> unmatched = new TreeSet<>(RAW_RULES);
        unmatched.removeAll(MATCHED_RULES);
        return Collections.unmodifiableSet(unmatched);
    }

    public static void filterRecipes(Map<ResourceLocation, JsonElement> recipes) {
        if (EXACT_DISABLED.isEmpty() && WILDCARD_PATTERNS.isEmpty()) return;

        int removed = 0;
        Iterator<Map.Entry<ResourceLocation, JsonElement>> it = recipes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = it.next();
            if (isRecipeDisabled(entry.getKey())) {
                LOGGER.debug("RingoLib: Disabled duplicate recipe: {}", entry.getKey());
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info("RingoLib: Filtered out {} disabled recipe(s)", removed);
        }
    }
}
