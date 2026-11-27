package com.ringoway.ringolib.core.content;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentSuppressionRegistryTest {

    @AfterEach
    void tearDown() {
        ContentSuppressionRegistry.clear();
    }

    @Test
    void categoryTabAdvancementAndWorldRulesRespectConditions() {
        AtomicBoolean active = new AtomicBoolean(true);

        ContentSuppressionRegistry.addRecipeCategoryRule(
                ResourceLocation.parse("dwarfs_tyranny:deepbrew"),
                new ContentSuppressionRegistry.RecipeCategoryRule(
                        ResourceLocation.parse("dwarfs_tyranny:deepbrew")::equals,
                        active::get
                )
        );
        ContentSuppressionRegistry.addCreativeTabRule(
                null,
                new ContentSuppressionRegistry.CreativeTabRule(
                        id -> id.getNamespace().equals("legacy"),
                        active::get
                )
        );
        ContentSuppressionRegistry.addAdvancementRule(
                ResourceLocation.parse("dwarfs_tyranny:recipes/infusion_cauldron_recipe"),
                new ContentSuppressionRegistry.AdvancementRule(
                        ResourceLocation.parse("dwarfs_tyranny:recipes/infusion_cauldron_recipe")::equals,
                        null,
                        active::get
                )
        );
        ContentSuppressionRegistry.addAdvancementRule(
                null,
                new ContentSuppressionRegistry.AdvancementRule(
                        id -> true,
                        (id, json) -> json.isJsonObject() && json.getAsJsonObject().has("hidden_tag"),
                        active::get
                )
        );
        ContentSuppressionRegistry.addWorldBlockReplacement(
                ResourceLocation.parse("dwarfs_tyranny:infusion_cauldron"),
                new ContentSuppressionRegistry.WorldBlockReplacementRule(
                        ResourceLocation.parse("dwarfs_tyranny:infusion_cauldron")::equals,
                        id -> ResourceLocation.parse("minecraft:cauldron"),
                        active::get
                )
        );

        assertTrue(ContentSuppressionRegistry.isRecipeCategoryDisabled(ResourceLocation.parse("dwarfs_tyranny:deepbrew")));
        assertFalse(ContentSuppressionRegistry.isRecipeCategoryDisabled(ResourceLocation.parse("brewery:brewing")));

        assertTrue(ContentSuppressionRegistry.isCreativeTabDisabled(ResourceLocation.parse("legacy:tools")));
        assertFalse(ContentSuppressionRegistry.isCreativeTabDisabled(ResourceLocation.parse("minecraft:building_blocks")));

        assertTrue(ContentSuppressionRegistry.isAdvancementDisabled(
                ResourceLocation.parse("dwarfs_tyranny:recipes/infusion_cauldron_recipe"), null));
        assertTrue(ContentSuppressionRegistry.isAdvancementDisabled(
                ResourceLocation.parse("other:adv"), JsonParser.parseString("{\"hidden_tag\":1}")));
        assertFalse(ContentSuppressionRegistry.isAdvancementDisabled(
                ResourceLocation.parse("other:adv"), JsonParser.parseString("{}")));

        assertEquals("minecraft:cauldron",
                ContentSuppressionRegistry.resolveWorldBlockReplacement("dwarfs_tyranny:infusion_cauldron"));
        assertEquals(Map.of(
                        ResourceLocation.parse("dwarfs_tyranny:infusion_cauldron"),
                        ResourceLocation.parse("minecraft:cauldron")),
                ContentSuppressionRegistry.getExactWorldReplacementTargets());

        active.set(false);
        assertFalse(ContentSuppressionRegistry.isRecipeCategoryDisabled(ResourceLocation.parse("dwarfs_tyranny:deepbrew")));
        assertNull(ContentSuppressionRegistry.resolveWorldBlockReplacement("dwarfs_tyranny:infusion_cauldron"));

        ContentSuppressionRegistry.Stats stats = ContentSuppressionRegistry.getStats();
        assertEquals(1, stats.recipeCategories());
        assertEquals(1, stats.creativeTabs());
        assertEquals(2, stats.advancements());
        assertEquals(1, stats.worldReplacements());
        assertEquals(0, stats.structureRules());
        assertEquals(5, stats.total());

        ContentSuppressionRegistry.clear();
        assertEquals(0, ContentSuppressionRegistry.getStats().total());
        assertFalse(ContentSuppressionRegistry.hasCategoryRules());
        assertFalse(ContentSuppressionRegistry.hasAdvancementRules());
        assertFalse(ContentSuppressionRegistry.hasWorldReplacements());
        assertFalse(ContentSuppressionRegistry.hasStructureRules());
    }

    @Test
    void structureTemplateMatchingSupportsGlobsConditionsAndClear() {
        AtomicBoolean active = new AtomicBoolean(true);

        ContentSuppressionRegistry.addStructureBlockRule(new ContentSuppressionRegistry.StructureBlockRule(
                GlobMatcher.toPredicate("create_thaumaturgy:colossus_foundry/*")
                        .or(GlobMatcher.toPredicate("create_thaumaturgy:engineers_workshop/*")),
                state -> true,
                ctx -> null,
                active::get
        ));

        assertTrue(ContentSuppressionRegistry.hasStructureRules());
        assertTrue(ContentSuppressionRegistry.matchesAnyStructureTemplate(
                ResourceLocation.parse("create_thaumaturgy:colossus_foundry/saw_floor")));
        assertTrue(ContentSuppressionRegistry.matchesAnyStructureTemplate(
                ResourceLocation.parse("create_thaumaturgy:engineers_workshop/oak_saws")));
        assertFalse(ContentSuppressionRegistry.matchesAnyStructureTemplate(
                ResourceLocation.parse("minecraft:village/plains/houses/plains_small_house_1")));
        assertFalse(ContentSuppressionRegistry.matchesAnyStructureTemplate(null));

        assertEquals(1, ContentSuppressionRegistry.getStats().structureRules());

        active.set(false);
        assertFalse(ContentSuppressionRegistry.matchesAnyStructureTemplate(
                ResourceLocation.parse("create_thaumaturgy:colossus_foundry/saw_floor")));

        ContentSuppressionRegistry.clear();
        assertFalse(ContentSuppressionRegistry.hasStructureRules());
        assertEquals(0, ContentSuppressionRegistry.getStats().structureRules());
    }
}
