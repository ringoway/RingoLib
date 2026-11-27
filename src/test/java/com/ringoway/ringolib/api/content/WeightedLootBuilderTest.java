package com.ringoway.ringolib.api.content;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeightedLootBuilderTest {

    @Test
    void selectsWeightedIndicesAcrossExactBoundaries() {
        double[] cumulative = {55.0, 75.0, 95.0, 100.0};
        double total = 100.0;

        assertEquals(0, WeightedLootBuilder.selectIndex(cumulative, total, 0.0));
        assertEquals(0, WeightedLootBuilder.selectIndex(cumulative, total, 0.5499));
        assertEquals(1, WeightedLootBuilder.selectIndex(cumulative, total, 0.55));
        assertEquals(1, WeightedLootBuilder.selectIndex(cumulative, total, 0.7499));
        assertEquals(2, WeightedLootBuilder.selectIndex(cumulative, total, 0.75));
        assertEquals(2, WeightedLootBuilder.selectIndex(cumulative, total, 0.9499));
        assertEquals(3, WeightedLootBuilder.selectIndex(cumulative, total, 0.95));
        assertEquals(3, WeightedLootBuilder.selectIndex(cumulative, total, 0.9999));
        assertEquals(3, WeightedLootBuilder.selectIndex(cumulative, total, 1.0));
    }

    @Test
    void exportsStaticOutcomesWithPerEntryCountModes() {
        Supplier<ItemStack> emptySupplier = () -> null;
        WeightedLootBuilder builder = new WeightedLootBuilder()
                .addPreservingCount(55.0, ctx -> null)
                .add(20.0, emptySupplier)
                .addFixed(5.0, emptySupplier)
                .addEmpty(20.0);

        List<ContentSuppressionRegistry.StaticLootOutcome> withoutRulePreserve = builder.toStaticOutcomes(false);
        assertEquals(4, withoutRulePreserve.size());
        assertTrue(withoutRulePreserve.get(0).preserveCount());
        assertFalse(withoutRulePreserve.get(1).preserveCount());
        assertFalse(withoutRulePreserve.get(2).preserveCount());
        assertFalse(withoutRulePreserve.get(3).preserveCount());
        assertTrue(withoutRulePreserve.get(3).emptyEntry());

        List<ContentSuppressionRegistry.StaticLootOutcome> withRulePreserve = builder.toStaticOutcomes(true);
        assertTrue(withRulePreserve.get(0).preserveCount());
        assertTrue(withRulePreserve.get(1).preserveCount());
        assertFalse(withRulePreserve.get(2).preserveCount());
        assertFalse(withRulePreserve.get(3).preserveCount());
        assertTrue(withRulePreserve.get(3).emptyEntry());
    }

    @Test
    void nullCallbackDuringStaticResolutionDefersToRuntime() {
        Supplier<ItemStack> nullSupplier = () -> null;
        WeightedLootBuilder builder = new WeightedLootBuilder()
                .addPreservingCount(55.0, ctx -> null)
                .addFixed(45.0, nullSupplier);

        LootReplaceContext staticCtx = new LootReplaceContext(
                ResourceLocation.parse("create_thaumaturgy:chests/foundry_hall"),
                null,
                null
        );
        assertTrue(staticCtx.isStaticResolution());

        List<ContentSuppressionRegistry.StaticLootOutcome> outcomes = builder.toStaticOutcomes(false);
        assertNull(outcomes.get(0).factory().apply(staticCtx));
        assertNull(outcomes.get(1).factory().apply(staticCtx));
    }

    @Test
    void rejectsNonPositiveNonFiniteWeightsAndEmptyPool() {
        WeightedLootBuilder builder = new WeightedLootBuilder();
        assertThrows(IllegalStateException.class, () -> builder.build(false));
        assertThrows(IllegalStateException.class, () -> builder.toStaticOutcomes(false));

        assertThrows(IllegalArgumentException.class, () -> builder.addEmpty(0.0));
        assertThrows(IllegalArgumentException.class, () -> builder.addEmpty(-5.0));
        assertThrows(IllegalArgumentException.class, () -> builder.addEmpty(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> builder.addEmpty(Double.POSITIVE_INFINITY));
    }
}
