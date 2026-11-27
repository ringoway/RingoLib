package com.ringoway.ringolib.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeFilterManagerTest {

    @AfterEach
    void tearDown() {
        RecipeFilterManager.clear();
        ContentSuppressionRegistry.clear();
    }

    @Test
    void apiRulesSurviveConfigReload() {
        RecipeFilterManager.disableConfigRecipe("mod:from_config");
        RecipeFilterManager.disableRecipe("mod:from_api");

        RecipeFilterManager.clearConfigRules();

        assertFalse(RecipeFilterManager.isRecipeDisabled(ResourceLocation.parse("mod:from_config")));
        assertTrue(RecipeFilterManager.isRecipeDisabled(ResourceLocation.parse("mod:from_api")));
    }

    @Test
    void predicateAndJsonFiltersRespectConditions() {
        AtomicBoolean enabled = new AtomicBoolean(true);
        RecipeFilterManager.disableRecipes(enabled::get, id -> id.getPath().startsWith("bad_"));
        RecipeFilterManager.disableRecipesMatchingJson((id, json) ->
                json.isJsonObject() && json.getAsJsonObject().has("ban_me"));

        Map<ResourceLocation, JsonElement> recipes = new HashMap<>();
        recipes.put(ResourceLocation.parse("mod:bad_recipe"), JsonParser.parseString("{}"));
        recipes.put(ResourceLocation.parse("mod:flagged"), JsonParser.parseString("{\"ban_me\":true}"));
        recipes.put(ResourceLocation.parse("mod:valid"), JsonParser.parseString("{}"));

        RecipeFilterManager.filterRecipes(recipes);

        assertFalse(recipes.containsKey(ResourceLocation.parse("mod:bad_recipe")));
        assertFalse(recipes.containsKey(ResourceLocation.parse("mod:flagged")));
        assertTrue(recipes.containsKey(ResourceLocation.parse("mod:valid")));

        enabled.set(false);
        assertFalse(RecipeFilterManager.isRecipeDisabled(ResourceLocation.parse("mod:bad_other")));
    }

    @Test
    void filtersByAllSupportedOutputJsonShapes() {
        RecipeFilterManager.disableRecipesByOutput("dwarfs_tyranny:deepbrew_*");
        ContentSuppressionRegistry.addItemRule(
                ResourceLocation.parse("mod:suppressed_by_api"),
                new ContentSuppressionRegistry.ItemRule(
                        ResourceLocation.parse("mod:suppressed_by_api")::equals,
                        null,
                        () -> true,
                        true, true, true, true, false, false, true, true, true, null
                )
        );

        Map<ResourceLocation, JsonElement> recipes = new HashMap<>();
        recipes.put(ResourceLocation.parse("mod:r1"), JsonParser.parseString("{\"result\":{\"id\":\"dwarfs_tyranny:deepbrew_bottle\"}}"));
        recipes.put(ResourceLocation.parse("mod:r2"), JsonParser.parseString("{\"results\":[{\"item\":\"dwarfs_tyranny:deepbrew_cask\"}]}"));
        recipes.put(ResourceLocation.parse("mod:r3"), JsonParser.parseString("{\"output\":{\"stack\":{\"id\":\"mod:suppressed_by_api\"}}}"));
        recipes.put(ResourceLocation.parse("mod:r4"), JsonParser.parseString("{\"outputs\":[\"mod:suppressed_by_api\"]}"));
        recipes.put(ResourceLocation.parse("mod:keep"), JsonParser.parseString("{\"result\":{\"id\":\"minecraft:bread\"}}"));

        RecipeFilterManager.filterRecipes(recipes);

        assertFalse(recipes.containsKey(ResourceLocation.parse("mod:r1")));
        assertFalse(recipes.containsKey(ResourceLocation.parse("mod:r2")));
        assertFalse(recipes.containsKey(ResourceLocation.parse("mod:r3")));
        assertFalse(recipes.containsKey(ResourceLocation.parse("mod:r4")));
        assertTrue(recipes.containsKey(ResourceLocation.parse("mod:keep")));
    }
}
