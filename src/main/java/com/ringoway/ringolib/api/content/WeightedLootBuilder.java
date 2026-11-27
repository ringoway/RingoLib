package com.ringoway.ringolib.api.content;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Weighted loot replacement pool builder using positive relative weights.
 */
public final class WeightedLootBuilder {

    public enum CountMode {
        INHERIT_RULE,
        PRESERVE_COUNT,
        FIXED_COUNT
    }

    private record Entry(
            double weight,
            Function<LootReplaceContext, List<ItemStack>> factory,
            CountMode countMode,
            boolean emptyEntry
    ) {
    }

    private final List<Entry> entries = new ArrayList<>();

    WeightedLootBuilder() {
    }

    public WeightedLootBuilder add(double weight, Function<LootReplaceContext, ItemStack> factory) {
        return addSingle(weight, factory, CountMode.INHERIT_RULE);
    }

    public WeightedLootBuilder add(double weight, Supplier<ItemStack> factory) {
        return addSingleSupplier(weight, factory, CountMode.INHERIT_RULE);
    }

    public WeightedLootBuilder add(double weight, ItemLike item) {
        return addSingleItem(weight, item, 1, CountMode.INHERIT_RULE);
    }

    public WeightedLootBuilder add(double weight, ItemLike item, int count) {
        return addSingleItem(weight, item, count, CountMode.FIXED_COUNT);
    }

    public WeightedLootBuilder addPreservingCount(double weight, ItemLike item) {
        return addSingleItem(weight, item, 1, CountMode.PRESERVE_COUNT);
    }

    public WeightedLootBuilder addPreservingCount(double weight, Supplier<ItemStack> factory) {
        return addSingleSupplier(weight, factory, CountMode.PRESERVE_COUNT);
    }

    public WeightedLootBuilder addPreservingCount(double weight, Function<LootReplaceContext, ItemStack> factory) {
        return addSingle(weight, factory, CountMode.PRESERVE_COUNT);
    }

    public WeightedLootBuilder addFixed(double weight, ItemLike item, int count) {
        return addSingleItem(weight, item, count, CountMode.FIXED_COUNT);
    }

    public WeightedLootBuilder addFixed(double weight, Supplier<ItemStack> factory) {
        return addSingleSupplier(weight, factory, CountMode.FIXED_COUNT);
    }

    public WeightedLootBuilder addFixed(double weight, Function<LootReplaceContext, ItemStack> factory) {
        return addSingle(weight, factory, CountMode.FIXED_COUNT);
    }

    public WeightedLootBuilder addMany(double weight, Function<LootReplaceContext, List<ItemStack>> factory) {
        validateWeight(weight);
        Objects.requireNonNull(factory, "factory");
        entries.add(new Entry(weight, ctx -> {
            List<ItemStack> result = factory.apply(ctx);
            return result == null ? List.of() : result;
        }, CountMode.FIXED_COUNT, false));
        return this;
    }

    public WeightedLootBuilder addEmpty(double weight) {
        validateWeight(weight);
        entries.add(new Entry(weight, ctx -> List.of(), CountMode.FIXED_COUNT, true));
        return this;
    }

    private WeightedLootBuilder addSingle(
            double weight,
            Function<LootReplaceContext, ItemStack> factory,
            CountMode countMode
    ) {
        validateWeight(weight);
        Objects.requireNonNull(factory, "factory");
        entries.add(new Entry(weight, ctx -> {
            ItemStack stack = factory.apply(ctx);
            if (stack == null) {
                return ctx.isStaticResolution() ? null : List.of();
            }
            return stack.isEmpty() ? List.of() : List.of(stack);
        }, countMode, false));
        return this;
    }

    private WeightedLootBuilder addSingleSupplier(
            double weight,
            Supplier<ItemStack> factory,
            CountMode countMode
    ) {
        validateWeight(weight);
        Objects.requireNonNull(factory, "factory");
        entries.add(new Entry(weight, ctx -> {
            ItemStack stack = factory.get();
            if (stack == null) {
                return ctx.isStaticResolution() ? null : List.of();
            }
            return stack.isEmpty() ? List.of() : List.of(stack.copy());
        }, countMode, false));
        return this;
    }

    private WeightedLootBuilder addSingleItem(
            double weight,
            ItemLike item,
            int count,
            CountMode countMode
    ) {
        validateWeight(weight);
        Objects.requireNonNull(item, "item");
        if (count <= 0) {
            throw new IllegalArgumentException("Loot stack count must be > 0, got: " + count);
        }
        entries.add(new Entry(weight, ctx -> List.of(new ItemStack(item.asItem(), count)), countMode, false));
        return this;
    }

    Function<LootReplaceContext, List<ItemStack>> build(boolean rulePreserveCount) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("Weighted loot replacement pool must contain at least one entry");
        }
        int size = entries.size();
        double[] cumulative = new double[size];
        @SuppressWarnings("unchecked")
        Function<LootReplaceContext, List<ItemStack>>[] factories = new Function[size];
        boolean[] preserveFlags = new boolean[size];
        double total = 0.0;
        for (int i = 0; i < size; i++) {
            Entry e = entries.get(i);
            total += e.weight();
            if (!Double.isFinite(total)) {
                throw new IllegalStateException("Total pool weight overflowed to non-finite value");
            }
            cumulative[i] = total;
            factories[i] = e.factory();
            preserveFlags[i] = shouldPreserveCount(e.countMode(), rulePreserveCount);
        }
        final double finalTotal = total;

        return ctx -> {
            int index = selectIndex(cumulative, finalTotal, ctx.random().nextDouble());
            List<ItemStack> result = factories[index].apply(ctx);
            if (result == null) {
                return List.of();
            }
            if (preserveFlags[index] && result.size() == 1) {
                ItemStack single = result.get(0);
                if (single != null && !single.isEmpty()) {
                    return List.of(ctx.copyWithCount(single));
                }
            }
            return result;
        };
    }

    List<ContentSuppressionRegistry.StaticLootOutcome> toStaticOutcomes(boolean rulePreserveCount) {
        if (entries.isEmpty()) {
            throw new IllegalStateException("Weighted loot replacement pool must contain at least one entry");
        }
        List<ContentSuppressionRegistry.StaticLootOutcome> outcomes = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            outcomes.add(new ContentSuppressionRegistry.StaticLootOutcome(
                    entry.weight(),
                    entry.factory(),
                    shouldPreserveCount(entry.countMode(), rulePreserveCount),
                    entry.emptyEntry()
            ));
        }
        return List.copyOf(outcomes);
    }

    private static boolean shouldPreserveCount(CountMode mode, boolean rulePreserveCount) {
        return switch (mode) {
            case PRESERVE_COUNT -> true;
            case FIXED_COUNT -> false;
            case INHERIT_RULE -> rulePreserveCount;
        };
    }

    static int selectIndex(double[] cumulativeWeights, double totalWeight, double unitRandom) {
        double clamped = Math.max(0.0, Math.min(1.0, unitRandom));
        double roll = clamped * totalWeight;
        for (int i = 0; i < cumulativeWeights.length; i++) {
            if (roll < cumulativeWeights[i]) {
                return i;
            }
        }
        return cumulativeWeights.length - 1;
    }

    private static void validateWeight(double weight) {
        if (weight <= 0.0 || !Double.isFinite(weight)) {
            throw new IllegalArgumentException("Loot entry weight must be finite and > 0, got: " + weight);
        }
    }
}
