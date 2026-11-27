package com.ringoway.ringolib.api.content;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import com.ringoway.ringolib.core.content.GlobMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class LootRuleBuilder {
    private BooleanSupplier condition;
    @Nullable
    private Predicate<ResourceLocation> tableMatcher;
    private boolean allTables = false;

    @Nullable
    private Predicate<ResourceLocation> itemIdMatcher;
    @Nullable
    private Predicate<ItemStack> stackFilter;

    private float chance = 1.0f;
    private boolean preserveCount = false;
    private boolean rewriteLootTables = true;
    private boolean isRemoveAction = false;
    @Nullable
    private Function<LootReplaceContext, List<ItemStack>> transformer;
    private boolean transformerPreservesCountIfFlagged = false;

    LootRuleBuilder(BooleanSupplier condition) {
        this.condition = Objects.requireNonNull(condition, "condition");
    }

    public LootRuleBuilder when(BooleanSupplier extraCondition) {
        Objects.requireNonNull(extraCondition, "extraCondition");
        BooleanSupplier prev = this.condition;
        this.condition = () -> prev.getAsBoolean() && extraCondition.getAsBoolean();
        return this;
    }

    public LootRuleBuilder inTable(String tableIdOrGlob) {
        Objects.requireNonNull(tableIdOrGlob, "tableIdOrGlob");
        return inTables(GlobMatcher.toPredicate(tableIdOrGlob));
    }

    public LootRuleBuilder inTable(ResourceLocation tableId) {
        Objects.requireNonNull(tableId, "tableId");
        return inTables(tableId::equals);
    }

    public LootRuleBuilder inTables(String... tableIdsOrGlobs) {
        if (tableIdsOrGlobs != null) {
            for (String pattern : tableIdsOrGlobs) {
                inTable(pattern);
            }
        }
        return this;
    }

    public LootRuleBuilder inTables(Predicate<ResourceLocation> matcher) {
        Objects.requireNonNull(matcher, "matcher");
        this.allTables = false;
        this.tableMatcher = this.tableMatcher == null ? matcher : this.tableMatcher.or(matcher);
        return this;
    }

    public LootRuleBuilder inAllTables() {
        this.allTables = true;
        this.tableMatcher = null;
        return this;
    }

    public LootRuleBuilder item(String itemIdOrGlob) {
        Objects.requireNonNull(itemIdOrGlob, "itemIdOrGlob");
        return items(GlobMatcher.toPredicate(itemIdOrGlob));
    }

    public LootRuleBuilder item(ResourceLocation itemId) {
        Objects.requireNonNull(itemId, "itemId");
        return items(itemId::equals);
    }

    public LootRuleBuilder items(String... itemIdsOrGlobs) {
        if (itemIdsOrGlobs != null) {
            for (String pattern : itemIdsOrGlobs) {
                item(pattern);
            }
        }
        return this;
    }

    public LootRuleBuilder items(Predicate<ResourceLocation> idMatcher) {
        Objects.requireNonNull(idMatcher, "idMatcher");
        this.itemIdMatcher = this.itemIdMatcher == null ? idMatcher : this.itemIdMatcher.or(idMatcher);
        return this;
    }

    public LootRuleBuilder matching(Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        this.stackFilter = this.stackFilter == null ? predicate : this.stackFilter.and(predicate);
        return this;
    }

    public LootRuleBuilder withTag(TagKey<Item> tag) {
        Objects.requireNonNull(tag, "tag");
        return matching(stack -> stack.is(tag));
    }

    public LootRuleBuilder chance(float probability) {
        if (probability < 0.0f || probability > 1.0f || Float.isNaN(probability)) {
            throw new IllegalArgumentException("Loot rule probability must be in [0.0, 1.0], got: " + probability);
        }
        this.chance = probability;
        return this;
    }

    public LootRuleBuilder preserveCount() {
        return preserveCount(true);
    }

    public LootRuleBuilder preserveCount(boolean preserve) {
        this.preserveCount = preserve;
        return this;
    }

    /**
     * Skips static {@code LootTableLoadEvent} rewriting and runs only in runtime loot hooks.
     */
    public LootRuleBuilder runtimeOnly() {
        this.rewriteLootTables = false;
        return this;
    }

    public LootRuleBuilder remove() {
        this.isRemoveAction = true;
        this.deferredPool = null;
        this.transformer = ctx -> List.of();
        this.transformerPreservesCountIfFlagged = false;
        return this;
    }

    public LootRuleBuilder replaceWith(ItemLike item) {
        Objects.requireNonNull(item, "item");
        this.isRemoveAction = false;
        this.deferredPool = null;
        this.transformer = ctx -> List.of(new ItemStack(item.asItem(), 1));
        this.transformerPreservesCountIfFlagged = true;
        return this;
    }

    public LootRuleBuilder replaceWith(Supplier<ItemStack> factory) {
        Objects.requireNonNull(factory, "factory");
        this.isRemoveAction = false;
        this.deferredPool = null;
        this.transformer = ctx -> {
            ItemStack produced = factory.get();
            if (produced == null) {
                return ctx.isStaticResolution() ? null : List.of();
            }
            return produced.isEmpty() ? List.of() : List.of(produced.copy());
        };
        this.transformerPreservesCountIfFlagged = true;
        return this;
    }

    public LootRuleBuilder replaceWith(Function<LootReplaceContext, ItemStack> replacer) {
        Objects.requireNonNull(replacer, "replacer");
        this.isRemoveAction = false;
        this.deferredPool = null;
        this.transformer = ctx -> {
            ItemStack produced = replacer.apply(ctx);
            if (produced == null) {
                return ctx.isStaticResolution() ? null : List.of();
            }
            return produced.isEmpty() ? List.of() : List.of(produced);
        };
        this.transformerPreservesCountIfFlagged = true;
        return this;
    }

    public LootRuleBuilder replaceWithMany(Function<LootReplaceContext, List<ItemStack>> replacer) {
        Objects.requireNonNull(replacer, "replacer");
        this.isRemoveAction = false;
        this.deferredPool = null;
        this.transformer = replacer;
        this.transformerPreservesCountIfFlagged = false;
        return this;
    }

    public LootRuleBuilder replaceWithWeighted(Consumer<WeightedLootBuilder> poolConfigurator) {
        Objects.requireNonNull(poolConfigurator, "poolConfigurator");
        WeightedLootBuilder pool = new WeightedLootBuilder();
        poolConfigurator.accept(pool);
        this.isRemoveAction = false;
        this.deferredPool = pool;
        this.transformer = null;
        this.transformerPreservesCountIfFlagged = false;
        return this;
    }

    @Nullable
    private WeightedLootBuilder deferredPool;

    void apply() {
        Predicate<ResourceLocation> finalTableMatcher = allTables ? null : tableMatcher;

        Predicate<ItemStack> finalStackMatcher;
        final Predicate<ResourceLocation> capturedIdMatcher = this.itemIdMatcher;
        final Predicate<ItemStack> capturedStackFilter = this.stackFilter;
        if (capturedIdMatcher != null && capturedStackFilter != null) {
            finalStackMatcher = stack -> !stack.isEmpty()
                    && capturedIdMatcher.test(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    && capturedStackFilter.test(stack);
        } else if (capturedIdMatcher != null) {
            finalStackMatcher = stack -> !stack.isEmpty()
                    && capturedIdMatcher.test(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        } else if (capturedStackFilter != null) {
            finalStackMatcher = stack -> !stack.isEmpty() && capturedStackFilter.test(stack);
        } else {
            finalStackMatcher = stack -> !stack.isEmpty();
        }

        ContentSuppressionRegistry.StaticLootPlan staticPlan = buildStaticPlan();

        Function<LootReplaceContext, List<ItemStack>> resolvedTransformer;
        if (deferredPool != null) {
            resolvedTransformer = deferredPool.build(this.preserveCount);
        } else if (this.transformer == null) {
            resolvedTransformer = ctx -> List.of();
        } else if (this.preserveCount && this.transformerPreservesCountIfFlagged) {
            final Function<LootReplaceContext, List<ItemStack>> delegate = this.transformer;
            resolvedTransformer = ctx -> {
                List<ItemStack> list = delegate.apply(ctx);
                if (list != null && list.size() == 1) {
                    ItemStack single = list.get(0);
                    if (single != null && !single.isEmpty()) {
                        return List.of(ctx.copyWithCount(single));
                    }
                }
                return list;
            };
        } else {
            resolvedTransformer = this.transformer;
        }

        if (this.chance < 1.0f) {
            final float prob = this.chance;
            final Function<LootReplaceContext, List<ItemStack>> delegate = resolvedTransformer;
            resolvedTransformer = ctx -> {
                if (prob <= 0.0f || ctx.random().nextFloat() >= prob) {
                    return null;
                }
                return delegate.apply(ctx);
            };
        }

        ContentSuppressionRegistry.addLootRule(new ContentSuppressionRegistry.LootRule(
                finalTableMatcher,
                finalStackMatcher,
                resolvedTransformer,
                condition,
                capturedIdMatcher,
                staticPlan
        ));
    }

    @Nullable
    private ContentSuppressionRegistry.StaticLootPlan buildStaticPlan() {
        if (!this.rewriteLootTables || this.stackFilter != null || this.chance <= 0.0f) {
            return null;
        }
        List<ContentSuppressionRegistry.StaticLootOutcome> outcomes;
        if (this.deferredPool != null) {
            outcomes = this.deferredPool.toStaticOutcomes(this.preserveCount);
        } else if (this.isRemoveAction || this.transformer == null) {
            outcomes = List.of(new ContentSuppressionRegistry.StaticLootOutcome(
                    1.0, ctx -> List.of(), false, true
            ));
        } else {
            outcomes = List.of(new ContentSuppressionRegistry.StaticLootOutcome(
                    1.0,
                    this.transformer,
                    this.preserveCount && this.transformerPreservesCountIfFlagged,
                    false
            ));
        }
        return new ContentSuppressionRegistry.StaticLootPlan(this.chance, outcomes);
    }
}
