package com.ringoway.ringolib.core.content;

import com.ringoway.ringolib.api.content.LootReplaceContext;
import com.ringoway.ringolib.mixin.loot.AlternativesEntryAccessor;
import com.ringoway.ringolib.mixin.loot.CompositeEntryBaseAccessor;
import com.ringoway.ringolib.mixin.loot.EmptyLootItemAccessor;
import com.ringoway.ringolib.mixin.loot.EntryGroupAccessor;
import com.ringoway.ringolib.mixin.loot.LootItemAccessor;
import com.ringoway.ringolib.mixin.loot.LootPoolAccessor;
import com.ringoway.ringolib.mixin.loot.LootPoolEntryContainerAccessor;
import com.ringoway.ringolib.mixin.loot.LootPoolSingletonContainerAccessor;
import com.ringoway.ringolib.mixin.loot.LootTableAccessor;
import com.ringoway.ringolib.mixin.loot.SequentialEntryAccessor;
import com.ringoway.ringolib.mixin.loot.SetComponentsFunctionAccessor;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.DynamicLoot;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.EntryGroup;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.entries.NestedLootTable;
import net.minecraft.world.level.storage.loot.entries.SequentialEntry;
import net.minecraft.world.level.storage.loot.entries.TagEntry;
import net.minecraft.world.level.storage.loot.functions.LimitCount;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Rewrites static {@link LootTable} pools during {@code LootTableLoadEvent} so drops and viewers stay in sync.
 * Complex rules fall back to {@code MixinCommonHooks}.
 */
public final class LootTableRewriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LootTableRewriter.class);

    private LootTableRewriter() {
    }

    private enum ContainerContext {
        POOL_OR_GROUP,
        ALTERNATIVES_OR_SEQUENTIAL
    }

    private record ProbedBranch(
            double weight,
            @Nullable ItemStack template,
            boolean preserveCount,
            boolean emptyEntry,
            boolean keepOriginal
    ) {
    }

    private record ProbedReplacement(
            @Nullable ContentSuppressionRegistry.LootRule matchedRule,
            List<ProbedBranch> branches,
            LootWeightMath.IntegerWeights integerWeights
    ) {
    }

    private static final class TableAnalysis {
        boolean hasOpaqueEntries = false;
        final Set<ContentSuppressionRegistry.LootRule> abortedRules =
                Collections.newSetFromMap(new IdentityHashMap<>());
        final Set<ContentSuppressionRegistry.LootRule> appliedRules =
                Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<LootItem, ProbedReplacement> replacements = new IdentityHashMap<>();
    }

    @Nullable
    public static LootTable rewrite(
            @Nullable ResourceLocation tableId,
            @Nullable LootTable table,
            @Nullable HolderLookup.Provider registries
    ) {
        if (tableId == null || table == null || table == LootTable.EMPTY) {
            return null;
        }
        ContentSuppressionRegistry.clearStaticallyAppliedLootRules(tableId);

        List<ContentSuppressionRegistry.LootRule> candidateRules =
                ContentSuppressionRegistry.getStaticLootRulesForTable(tableId);
        boolean hasItemStrips = ContentSuppressionRegistry.hasItemLootStripRules();
        if (candidateRules.isEmpty() && !hasItemStrips) {
            return null;
        }

        LootTableAccessor tableAcc = (LootTableAccessor) table;
        List<LootPool> originalPools = tableAcc.ringolib$getPools();
        if (originalPools == null || originalPools.isEmpty()) {
            return null;
        }

        TableAnalysis analysis = new TableAnalysis();
        for (LootPool pool : originalPools) {
            List<LootPoolEntryContainer> entries = ((LootPoolAccessor) pool).ringolib$getEntries();
            if (entries != null) {
                for (LootPoolEntryContainer entry : entries) {
                    inspectEntry(entry, ContainerContext.POOL_OR_GROUP, tableId, registries,
                            candidateRules, hasItemStrips, analysis);
                }
            }
        }

        if (analysis.replacements.isEmpty()) {
            return null;
        }

        // Remove any entry bindings belonging to rules that later aborted in the same table
        boolean anyValidReplacement = false;
        for (ProbedReplacement replacement : analysis.replacements.values()) {
            ContentSuppressionRegistry.LootRule rule = replacement.matchedRule();
            if (rule == null || !analysis.abortedRules.contains(rule)) {
                anyValidReplacement = true;
                break;
            }
        }
        if (!anyValidReplacement) {
            return null;
        }

        List<LootPool> rewrittenPools = new ArrayList<>(originalPools.size());
        boolean tableModified = false;

        for (LootPool pool : originalPools) {
            LootPool rewrittenPool = rewritePool(pool, analysis);
            if (rewrittenPool != pool) {
                tableModified = true;
            }
            rewrittenPools.add(rewrittenPool);
        }

        if (!tableModified) {
            return null;
        }

        LootTable rewrittenTable = LootTableAccessor.ringolib$create(
                tableAcc.ringolib$getParamSet(),
                tableAcc.ringolib$getRandomSequence(),
                new ArrayList<>(rewrittenPools),
                tableAcc.ringolib$getFunctions()
        );
        ResourceLocation effectiveId = table.getLootTableId() != null ? table.getLootTableId() : tableId;
        rewrittenTable.setLootTableId(effectiveId);

        // Mark rules only if no opaque entry (TagEntry / NestedLootTable / DynamicLoot) could also emit matching items at runtime
        if (!analysis.hasOpaqueEntries) {
            for (ContentSuppressionRegistry.LootRule rule : analysis.appliedRules) {
                if (!analysis.abortedRules.contains(rule)) {
                    ContentSuppressionRegistry.markLootRuleStaticallyApplied(effectiveId, rule);
                }
            }
        }

        return rewrittenTable;
    }

    private static void inspectEntry(
            LootPoolEntryContainer entry,
            ContainerContext containerContext,
            ResourceLocation tableId,
            @Nullable HolderLookup.Provider registries,
            List<ContentSuppressionRegistry.LootRule> candidateRules,
            boolean hasItemStrips,
            TableAnalysis analysis
    ) {
        if (entry instanceof LootItem lootItem) {
            inspectLootItem(lootItem, containerContext, tableId, registries, candidateRules, hasItemStrips, analysis);
            return;
        }
        if (entry instanceof EmptyLootItem) {
            return;
        }
        if (entry instanceof CompositeEntryBase composite) {
            ContainerContext childContext = entry instanceof EntryGroup
                    ? ContainerContext.POOL_OR_GROUP
                    : ContainerContext.ALTERNATIVES_OR_SEQUENTIAL;
            List<LootPoolEntryContainer> children = ((CompositeEntryBaseAccessor) composite).ringolib$getChildren();
            if (children != null) {
                for (LootPoolEntryContainer child : children) {
                    inspectEntry(child, childContext, tableId, registries, candidateRules, hasItemStrips, analysis);
                }
            }
            return;
        }
        if (entry instanceof TagEntry || entry instanceof NestedLootTable || entry instanceof DynamicLoot) {
            analysis.hasOpaqueEntries = true;
        } else {
            analysis.hasOpaqueEntries = true;
        }
    }

    private static void inspectLootItem(
            LootItem lootItem,
            ContainerContext containerContext,
            ResourceLocation tableId,
            @Nullable HolderLookup.Provider registries,
            List<ContentSuppressionRegistry.LootRule> candidateRules,
            boolean hasItemStrips,
            TableAnalysis analysis
    ) {
        Item item = ((LootItemAccessor) lootItem).ringolib$getItem().value();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);

        for (ContentSuppressionRegistry.LootRule rule : candidateRules) {
            if (!rule.matchesStaticItemId(itemId)) {
                continue;
            }
            if (analysis.abortedRules.contains(rule)) {
                return;
            }

            ContentSuppressionRegistry.StaticLootPlan plan = rule.staticPlan();
            if (plan == null) {
                return;
            }

            boolean multiBranch = plan.chance() < 1.0f || plan.outcomes().size() > 1;
            if (multiBranch && containerContext == ContainerContext.ALTERNATIVES_OR_SEQUENTIAL) {
                analysis.abortedRules.add(rule);
                return;
            }

            ProbedReplacement probed = probeStaticPlan(rule, plan, tableId, item, registries);
            if (probed == null) {
                analysis.abortedRules.add(rule);
                return;
            }

            analysis.replacements.put(lootItem, probed);
            return;
        }

        if (hasItemStrips && ContentSuppressionRegistry.isItemIdStrippedFromLootTables(itemId)) {
            List<ProbedBranch> emptyBranch = List.of(new ProbedBranch(1.0, null, false, true, false));
            LootWeightMath.IntegerWeights unit = new LootWeightMath.IntegerWeights(new int[]{1}, 1);
            analysis.replacements.put(lootItem, new ProbedReplacement(null, emptyBranch, unit));
        }
    }

    @Nullable
    private static ProbedReplacement probeStaticPlan(
            ContentSuppressionRegistry.LootRule rule,
            ContentSuppressionRegistry.StaticLootPlan plan,
            ResourceLocation tableId,
            Item originalItem,
            @Nullable HolderLookup.Provider registries
    ) {
        List<ContentSuppressionRegistry.StaticLootOutcome> rawOutcomes = plan.outcomes();
        if (rawOutcomes.isEmpty()) {
            return null;
        }

        LootReplaceContext probeCtx = new LootReplaceContext(tableId, new ItemStack(originalItem, 1), null, registries);
        List<ProbedBranch> branches = new ArrayList<>(rawOutcomes.size() + 1);
        double outcomeWeightSum = 0.0;

        for (ContentSuppressionRegistry.StaticLootOutcome outcome : rawOutcomes) {
            outcomeWeightSum += outcome.weight();
            if (outcome.emptyEntry()) {
                branches.add(new ProbedBranch(outcome.weight(), null, false, true, false));
                continue;
            }

            List<ItemStack> produced;
            try {
                produced = outcome.factory().apply(probeCtx);
            } catch (RuntimeException ex) {
                LOGGER.debug("RingoLib: Aborting static rewrite for {} in {} due to callback exception: {}",
                        BuiltInRegistries.ITEM.getKey(originalItem), tableId, ex.getMessage());
                return null;
            }

            if (produced == null) {
                return null;
            }
            if (produced.isEmpty()) {
                branches.add(new ProbedBranch(outcome.weight(), null, false, true, false));
                continue;
            }
            if (produced.size() > 1) {
                return null;
            }

            ItemStack first = produced.get(0);
            if (first == null || first.isEmpty()) {
                branches.add(new ProbedBranch(outcome.weight(), null, false, true, false));
            } else {
                branches.add(new ProbedBranch(outcome.weight(), first.copy(), outcome.preserveCount(), false, false));
            }
        }

        if (plan.chance() < 1.0f && outcomeWeightSum > 0.0) {
            double p = plan.chance();
            double keepWeight = outcomeWeightSum * ((1.0 - p) / p);
            if (keepWeight > 0.0 && Double.isFinite(keepWeight)) {
                branches.add(new ProbedBranch(keepWeight, null, true, false, true));
            }
        }

        double[] weights = new double[branches.size()];
        for (int i = 0; i < branches.size(); i++) {
            weights[i] = branches.get(i).weight();
        }
        LootWeightMath.IntegerWeights integerWeights = LootWeightMath.toExactIntegerWeights(weights);
        return new ProbedReplacement(rule, List.copyOf(branches), integerWeights);
    }

    private static LootPool rewritePool(LootPool pool, TableAnalysis analysis) {
        LootPoolAccessor poolAcc = (LootPoolAccessor) pool;
        List<LootPoolEntryContainer> originalEntries = poolAcc.ringolib$getEntries();
        if (originalEntries == null || originalEntries.isEmpty()) {
            return pool;
        }

        List<Integer> denominators = new ArrayList<>();
        int[] maxWeightHolder = {1};
        boolean[] hasActivePoolMatch = {false};

        for (LootPoolEntryContainer entry : originalEntries) {
            collectPoolMetrics(entry, analysis, denominators, maxWeightHolder, hasActivePoolMatch);
        }

        if (!hasActivePoolMatch[0]) {
            return pool;
        }

        int poolScale = LootWeightMath.computePoolScale(denominators, maxWeightHolder[0]);
        List<LootPoolEntryContainer> newEntries = new ArrayList<>(originalEntries.size());
        boolean poolChanged = false;

        for (LootPoolEntryContainer entry : originalEntries) {
            List<LootPoolEntryContainer> converted = rewriteEntry(entry, poolScale, analysis);
            if (converted.size() != 1 || converted.get(0) != entry) {
                poolChanged = true;
            }
            newEntries.addAll(converted);
        }

        if (!poolChanged && poolScale == 1) {
            return pool;
        }

        return LootPoolAccessor.ringolib$create(
                new ArrayList<>(newEntries),
                poolAcc.ringolib$getConditions(),
                poolAcc.ringolib$getFunctions(),
                poolAcc.ringolib$getRolls(),
                poolAcc.ringolib$getBonusRolls(),
                Optional.ofNullable(poolAcc.ringolib$getName())
        );
    }

    private static void collectPoolMetrics(
            LootPoolEntryContainer entry,
            TableAnalysis analysis,
            List<Integer> denominators,
            int[] maxWeightHolder,
            boolean[] hasActivePoolMatch
    ) {
        if (entry instanceof LootPoolSingletonContainer singleton) {
            int weight = ((LootPoolSingletonContainerAccessor) singleton).ringolib$getWeight();
            if (weight > maxWeightHolder[0]) {
                maxWeightHolder[0] = weight;
            }
        }
        if (entry instanceof LootItem lootItem) {
            ProbedReplacement rep = analysis.replacements.get(lootItem);
            if (rep != null && (rep.matchedRule() == null || !analysis.abortedRules.contains(rep.matchedRule()))) {
                hasActivePoolMatch[0] = true;
                if (rep.integerWeights().denominator() > 1) {
                    denominators.add(rep.integerWeights().denominator());
                }
            }
            return;
        }
        if (entry instanceof CompositeEntryBase composite) {
            List<LootPoolEntryContainer> children = ((CompositeEntryBaseAccessor) composite).ringolib$getChildren();
            if (children != null) {
                for (LootPoolEntryContainer child : children) {
                    collectPoolMetrics(child, analysis, denominators, maxWeightHolder, hasActivePoolMatch);
                }
            }
        }
    }

    private static List<LootPoolEntryContainer> rewriteEntry(
            LootPoolEntryContainer entry,
            int poolScale,
            TableAnalysis analysis
    ) {
        if (entry instanceof LootItem lootItem) {
            ProbedReplacement rep = analysis.replacements.get(lootItem);
            if (rep != null && (rep.matchedRule() == null || !analysis.abortedRules.contains(rep.matchedRule()))) {
                if (rep.matchedRule() != null) {
                    analysis.appliedRules.add(rep.matchedRule());
                }
                return buildReplacementEntries(lootItem, rep, poolScale);
            }
            scaleSingletonWeight(lootItem, poolScale);
            return List.of(lootItem);
        }

        if (entry instanceof LootPoolSingletonContainer singleton) {
            scaleSingletonWeight(singleton, poolScale);
            return List.of(singleton);
        }

        if (entry instanceof CompositeEntryBase composite) {
            List<LootPoolEntryContainer> origChildren = ((CompositeEntryBaseAccessor) composite).ringolib$getChildren();
            if (origChildren == null || origChildren.isEmpty()) {
                return List.of(composite);
            }
            List<LootPoolEntryContainer> newChildren = new ArrayList<>(origChildren.size());
            boolean childrenChanged = false;
            for (LootPoolEntryContainer child : origChildren) {
                List<LootPoolEntryContainer> rewrittenChild = rewriteEntry(child, poolScale, analysis);
                if (rewrittenChild.size() != 1 || rewrittenChild.get(0) != child) {
                    childrenChanged = true;
                }
                newChildren.addAll(rewrittenChild);
            }
            if (!childrenChanged) {
                return List.of(composite);
            }
            List<LootItemCondition> conditions = ((LootPoolEntryContainerAccessor) composite).ringolib$getConditions();
            if (composite instanceof AlternativesEntry) {
                return List.of(AlternativesEntryAccessor.ringolib$create(new ArrayList<>(newChildren), conditions));
            }
            if (composite instanceof EntryGroup) {
                return List.of(EntryGroupAccessor.ringolib$create(new ArrayList<>(newChildren), conditions));
            }
            if (composite instanceof SequentialEntry) {
                return List.of(SequentialEntryAccessor.ringolib$create(new ArrayList<>(newChildren), conditions));
            }
        }

        return List.of(entry);
    }

    private static void scaleSingletonWeight(LootPoolSingletonContainer singleton, int poolScale) {
        if (poolScale <= 1) {
            return;
        }
        LootPoolSingletonContainerAccessor acc = (LootPoolSingletonContainerAccessor) singleton;
        int origWeight = acc.ringolib$getWeight();
        int origQuality = acc.ringolib$getQuality();
        acc.ringolib$setWeight(clampScale(origWeight, poolScale));
        if (origQuality != 0) {
            acc.ringolib$setQuality(clampScale(origQuality, poolScale));
        }
    }

    private static int clampScale(int value, int scale) {
        long scaled = (long) value * scale;
        if (scaled > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (scaled < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) scaled;
    }

    private static List<LootPoolEntryContainer> buildReplacementEntries(
            LootItem original,
            ProbedReplacement rep,
            int poolScale
    ) {
        LootPoolSingletonContainerAccessor singletonAcc = (LootPoolSingletonContainerAccessor) original;
        LootItemAccessor itemAcc = (LootItemAccessor) original;
        List<LootItemCondition> conditions = ((LootPoolEntryContainerAccessor) original).ringolib$getConditions();
        List<LootItemFunction> origFunctions = singletonAcc.ringolib$getFunctions();
        int origWeight = Math.max(1, singletonAcc.ringolib$getWeight());
        int origQuality = singletonAcc.ringolib$getQuality();

        int[] weights = LootWeightMath.distributeWeight(origWeight, poolScale, rep.integerWeights());
        List<ProbedBranch> branches = rep.branches();
        List<LootPoolEntryContainer> result = new ArrayList<>(branches.size());

        for (int i = 0; i < branches.size(); i++) {
            ProbedBranch branch = branches.get(i);
            int w = weights[i];
            int q = origQuality == 0 ? 0 : (int) Math.round(((double) origQuality * w) / origWeight);

            if (branch.keepOriginal()) {
                result.add(LootItemAccessor.ringolib$create(
                        itemAcc.ringolib$getItem(),
                        w,
                        q,
                        conditions,
                        origFunctions
                ));
                continue;
            }

            if (branch.emptyEntry() || branch.template() == null || branch.template().isEmpty()) {
                result.add(EmptyLootItemAccessor.ringolib$create(w, q, conditions, List.of()));
                continue;
            }

            ItemStack template = branch.template();
            List<LootItemFunction> functions = buildFunctions(template, origFunctions, branch.preserveCount());
            @SuppressWarnings("deprecation")
            LootPoolEntryContainer replacementEntry = LootItemAccessor.ringolib$create(
                    template.getItemHolder(),
                    w,
                    q,
                    conditions,
                    functions
            );
            result.add(replacementEntry);
        }

        return result;
    }

    private static List<LootItemFunction> buildFunctions(
            ItemStack template,
            @Nullable List<LootItemFunction> origFunctions,
            boolean preserveCount
    ) {
        List<LootItemFunction> functions = new ArrayList<>();

        DataComponentPatch patch = template.getComponentsPatch();
        if (!patch.isEmpty()) {
            functions.add(SetComponentsFunctionAccessor.ringolib$create(List.of(), patch));
        }

        boolean copiedSetCount = false;
        if (preserveCount && origFunctions != null) {
            for (LootItemFunction fn : origFunctions) {
                if (fn instanceof SetItemCountFunction) {
                    functions.add(fn);
                    copiedSetCount = true;
                } else if (fn instanceof LimitCount) {
                    functions.add(fn);
                }
            }
        }

        if (!copiedSetCount && template.getCount() > 1) {
            functions.add(SetItemCountFunction.setCount(ConstantValue.exactly(template.getCount())).build());
        }

        return functions.isEmpty() ? List.of() : List.copyOf(functions);
    }
}
