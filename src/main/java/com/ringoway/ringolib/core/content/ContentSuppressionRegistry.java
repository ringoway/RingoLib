package com.ringoway.ringolib.core.content;

import com.google.gson.JsonElement;
import com.ringoway.ringolib.api.content.LootReplaceContext;
import com.ringoway.ringolib.api.content.StructureBlockContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ContentSuppressionRegistry {

    public record BlockRule(
            Predicate<ResourceLocation> idMatcher,
            @Nullable Predicate<BlockState> stateMatcher,
            BooleanSupplier condition,
            boolean disableTicker,
            boolean disableGameEvents,
            boolean blockInteraction,
            boolean blockPlacement,
            @Nullable BiConsumer<Player, BlockPos> onBlockedInteract
    ) {
        public boolean matches(ResourceLocation blockId, BlockState state) {
            return condition.getAsBoolean()
                    && idMatcher.test(blockId)
                    && (stateMatcher == null || stateMatcher.test(state));
        }
    }

    public record ItemRule(
            Predicate<ResourceLocation> idMatcher,
            @Nullable Predicate<ItemStack> stackMatcher,
            BooleanSupplier condition,
            boolean hideFromCreativeTabs,
            boolean hideFromRecipeViewers,
            boolean blockUsage,
            boolean blockCrafting,
            boolean blockPickup,
            boolean purgeFromInventory,
            boolean disableOutputRecipes,
            boolean stripFromLootTables,
            boolean stripFromTrades,
            @Nullable Consumer<Player> onBlockedUse
    ) {
        public boolean matchesStack(ResourceLocation itemId, ItemStack stack) {
            return condition.getAsBoolean()
                    && idMatcher.test(itemId)
                    && (stackMatcher == null || stackMatcher.test(stack));
        }

        public boolean matchesItemIdOnly(ResourceLocation itemId) {
            return stackMatcher == null
                    && condition.getAsBoolean()
                    && idMatcher.test(itemId);
        }
    }

    public record BlockEntityRule(
            Predicate<ResourceLocation> beTypeIdMatcher,
            @Nullable Predicate<BlockState> stateMatcher,
            BooleanSupplier condition,
            boolean disableTicker,
            boolean disableGameEvents
    ) {
        public boolean matches(ResourceLocation beTypeId, @Nullable BlockState state) {
            return condition.getAsBoolean()
                    && beTypeIdMatcher.test(beTypeId)
                    && (stateMatcher == null || (state != null && stateMatcher.test(state)));
        }
    }

    public record FluidRule(
            Predicate<ResourceLocation> idMatcher,
            BooleanSupplier condition,
            boolean hideFromRecipeViewers
    ) {
        public boolean matches(ResourceLocation fluidId) {
            return condition.getAsBoolean() && idMatcher.test(fluidId);
        }
    }

    public record RecipeCategoryRule(
            Predicate<ResourceLocation> idMatcher,
            BooleanSupplier condition
    ) {
        public boolean matches(ResourceLocation categoryId) {
            return condition.getAsBoolean() && idMatcher.test(categoryId);
        }
    }

    public record CreativeTabRule(
            Predicate<ResourceLocation> idMatcher,
            BooleanSupplier condition
    ) {
        public boolean matches(ResourceLocation tabId) {
            return condition.getAsBoolean() && idMatcher.test(tabId);
        }
    }

    public record WorldBlockReplacementRule(
            Predicate<ResourceLocation> idMatcher,
            Function<ResourceLocation, ResourceLocation> targetResolver,
            BooleanSupplier condition
    ) {
        public boolean matches(ResourceLocation blockId) {
            return condition.getAsBoolean() && idMatcher.test(blockId);
        }
    }

    public record StaticLootOutcome(
            double weight,
            Function<LootReplaceContext, List<ItemStack>> factory,
            boolean preserveCount,
            boolean emptyEntry
    ) {
    }

    public record StaticLootPlan(
            float chance,
            List<StaticLootOutcome> outcomes
    ) {
    }

    public record LootRule(
            @Nullable Predicate<ResourceLocation> tableMatcher,
            Predicate<ItemStack> stackMatcher,
            @Nullable Function<LootReplaceContext, List<ItemStack>> transformer,
            BooleanSupplier condition,
            @Nullable Predicate<ResourceLocation> staticItemIdMatcher,
            @Nullable StaticLootPlan staticPlan
    ) {
        public LootRule(
                @Nullable Predicate<ResourceLocation> tableMatcher,
                Predicate<ItemStack> stackMatcher,
                @Nullable Function<LootReplaceContext, List<ItemStack>> transformer,
                BooleanSupplier condition
        ) {
            this(tableMatcher, stackMatcher, transformer, condition, null, null);
        }

        public boolean matchesTable(@Nullable ResourceLocation lootTableId) {
            return condition.getAsBoolean()
                    && (tableMatcher == null || (lootTableId != null && tableMatcher.test(lootTableId)));
        }

        public boolean matches(@Nullable ResourceLocation lootTableId, ItemStack stack) {
            return matchesTable(lootTableId) && stackMatcher.test(stack);
        }

        public boolean canRewriteStatically() {
            return condition.getAsBoolean() && staticPlan != null;
        }

        public boolean matchesStaticItemId(ResourceLocation itemId) {
            return staticItemIdMatcher == null || staticItemIdMatcher.test(itemId);
        }
    }

    public record AdvancementRule(
            Predicate<ResourceLocation> idMatcher,
            @Nullable BiPredicate<ResourceLocation, JsonElement> jsonMatcher,
            BooleanSupplier condition
    ) {
        public boolean matches(ResourceLocation advancementId, @Nullable JsonElement json) {
            return condition.getAsBoolean()
                    && idMatcher.test(advancementId)
                    && (jsonMatcher == null || (json != null && jsonMatcher.test(advancementId, json)));
        }
    }

    public record TradeRule(
            @Nullable Predicate<ResourceLocation> professionMatcher,
            Predicate<ItemStack> itemMatcher,
            BooleanSupplier condition
    ) {
        public boolean matches(@Nullable ResourceLocation professionId, ItemStack stack) {
            return condition.getAsBoolean()
                    && (professionMatcher == null || (professionId != null && professionMatcher.test(professionId)))
                    && itemMatcher.test(stack);
        }
    }

    public record StructureBlockRule(
            @Nullable Predicate<ResourceLocation> templateMatcher,
            Predicate<BlockState> stateMatcher,
            Function<StructureBlockContext, StructureTemplate.StructureBlockInfo> transformer,
            BooleanSupplier condition
    ) {
        public boolean matchesTemplate(@Nullable ResourceLocation templateId) {
            return condition.getAsBoolean()
                    && (templateMatcher == null || (templateId != null && templateMatcher.test(templateId)));
        }

        public boolean matches(@Nullable ResourceLocation templateId, BlockState state) {
            return matchesTemplate(templateId) && stateMatcher.test(state);
        }
    }

    public record StructureProcessorRule(
            @Nullable Predicate<ResourceLocation> templateMatcher,
            StructureProcessor processor,
            BooleanSupplier condition
    ) {
        public boolean matchesTemplate(@Nullable ResourceLocation templateId) {
            return condition.getAsBoolean()
                    && (templateMatcher == null || (templateId != null && templateMatcher.test(templateId)));
        }
    }

    public record Stats(
            int blocks,
            int items,
            int fluids,
            int blockEntities,
            int recipeCategories,
            int creativeTabs,
            int worldReplacements,
            int lootRules,
            int advancements,
            int trades,
            int structureRules
    ) {
        public int total() {
            return blocks + items + fluids + blockEntities + recipeCategories
                    + creativeTabs + worldReplacements + lootRules + advancements + trades + structureRules;
        }
    }

    private static final Map<ResourceLocation, List<BlockRule>> EXACT_BLOCK_RULES = new ConcurrentHashMap<>();
    private static final List<BlockRule> DYNAMIC_BLOCK_RULES = new CopyOnWriteArrayList<>();

    private static final Map<ResourceLocation, List<ItemRule>> EXACT_ITEM_RULES = new ConcurrentHashMap<>();
    private static final List<ItemRule> DYNAMIC_ITEM_RULES = new CopyOnWriteArrayList<>();

    private static final Map<ResourceLocation, List<BlockEntityRule>> EXACT_BE_RULES = new ConcurrentHashMap<>();
    private static final List<BlockEntityRule> DYNAMIC_BE_RULES = new CopyOnWriteArrayList<>();

    private static final Map<ResourceLocation, List<FluidRule>> EXACT_FLUID_RULES = new ConcurrentHashMap<>();
    private static final List<FluidRule> DYNAMIC_FLUID_RULES = new CopyOnWriteArrayList<>();

    private static final Map<ResourceLocation, List<RecipeCategoryRule>> EXACT_CATEGORY_RULES = new ConcurrentHashMap<>();
    private static final List<RecipeCategoryRule> DYNAMIC_CATEGORY_RULES = new CopyOnWriteArrayList<>();

    private static final Map<ResourceLocation, List<CreativeTabRule>> EXACT_TAB_RULES = new ConcurrentHashMap<>();
    private static final List<CreativeTabRule> DYNAMIC_TAB_RULES = new CopyOnWriteArrayList<>();

    private static final Map<ResourceLocation, List<WorldBlockReplacementRule>> EXACT_WORLD_REPLACEMENTS = new ConcurrentHashMap<>();
    private static final List<WorldBlockReplacementRule> DYNAMIC_WORLD_REPLACEMENTS = new CopyOnWriteArrayList<>();

    private static final List<LootRule> DYNAMIC_LOOT_RULES = new CopyOnWriteArrayList<>();
    private static final Map<ResourceLocation, Set<LootRule>> STATICALLY_APPLIED_LOOT_RULES = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, List<AdvancementRule>> EXACT_ADVANCEMENT_RULES = new ConcurrentHashMap<>();
    private static final List<AdvancementRule> DYNAMIC_ADVANCEMENT_RULES = new CopyOnWriteArrayList<>();

    private static final List<TradeRule> DYNAMIC_TRADE_RULES = new CopyOnWriteArrayList<>();

    private static final List<StructureBlockRule> DYNAMIC_STRUCTURE_BLOCK_RULES = new CopyOnWriteArrayList<>();
    private static final List<StructureProcessorRule> DYNAMIC_STRUCTURE_PROCESSOR_RULES = new CopyOnWriteArrayList<>();

    private static volatile boolean hasTickerRules;
    private static volatile boolean hasGameEventRules;
    private static volatile boolean hasBlockInteractionOrPlacementRules;
    private static volatile boolean hasItemRules;
    private static volatile boolean hasFluidRules;
    private static volatile boolean hasCategoryRules;
    private static volatile boolean hasCreativeTabRules;
    private static volatile boolean hasWorldReplacements;
    private static volatile boolean hasRecipeOutputRules;
    private static volatile boolean hasPurgeRules;
    private static volatile boolean hasAnyUnregisteredItems;
    private static volatile boolean hasAnyUnregisteredFluids;
    private static volatile boolean hasLootRules;
    private static volatile boolean hasItemLootStripRules;
    private static volatile boolean hasAdvancementRules;
    private static volatile boolean hasTradeRules;
    private static volatile boolean hasStructureRules;

    private ContentSuppressionRegistry() {
    }

    public static void addBlockRule(@Nullable ResourceLocation exactId, BlockRule rule) {
        registerEntry(exactId, rule, EXACT_BLOCK_RULES, DYNAMIC_BLOCK_RULES);
        if (rule.disableTicker()) {
            hasTickerRules = true;
        }
        if (rule.disableGameEvents()) {
            hasGameEventRules = true;
        }
        if (rule.blockInteraction() || rule.blockPlacement()) {
            hasBlockInteractionOrPlacementRules = true;
        }
    }

    public static void addItemRule(@Nullable ResourceLocation exactId, ItemRule rule) {
        registerEntry(exactId, rule, EXACT_ITEM_RULES, DYNAMIC_ITEM_RULES);
        hasItemRules = true;
        if (rule.disableOutputRecipes() && rule.stackMatcher() == null) {
            hasRecipeOutputRules = true;
        }
        if (rule.purgeFromInventory()) {
            hasPurgeRules = true;
        }
        if (rule.stripFromLootTables()) {
            hasLootRules = true;
            if (rule.stackMatcher() == null) {
                hasItemLootStripRules = true;
            }
        }
        if (rule.stripFromTrades()) {
            hasTradeRules = true;
        }
    }

    public static void addBlockEntityRule(@Nullable ResourceLocation exactId, BlockEntityRule rule) {
        registerEntry(exactId, rule, EXACT_BE_RULES, DYNAMIC_BE_RULES);
        if (rule.disableTicker()) {
            hasTickerRules = true;
        }
        if (rule.disableGameEvents()) {
            hasGameEventRules = true;
        }
    }

    public static void addFluidRule(@Nullable ResourceLocation exactId, FluidRule rule) {
        registerEntry(exactId, rule, EXACT_FLUID_RULES, DYNAMIC_FLUID_RULES);
        hasFluidRules = true;
    }

    public static void addRecipeCategoryRule(@Nullable ResourceLocation exactId, RecipeCategoryRule rule) {
        registerEntry(exactId, rule, EXACT_CATEGORY_RULES, DYNAMIC_CATEGORY_RULES);
        hasCategoryRules = true;
    }

    public static void addCreativeTabRule(@Nullable ResourceLocation exactId, CreativeTabRule rule) {
        registerEntry(exactId, rule, EXACT_TAB_RULES, DYNAMIC_TAB_RULES);
        hasCreativeTabRules = true;
    }

    public static void addWorldBlockReplacement(@Nullable ResourceLocation exactId, WorldBlockReplacementRule rule) {
        registerEntry(exactId, rule, EXACT_WORLD_REPLACEMENTS, DYNAMIC_WORLD_REPLACEMENTS);
        hasWorldReplacements = true;
    }

    public static void addLootRule(LootRule rule) {
        DYNAMIC_LOOT_RULES.add(rule);
        hasLootRules = true;
    }

    public static void addAdvancementRule(@Nullable ResourceLocation exactId, AdvancementRule rule) {
        registerEntry(exactId, rule, EXACT_ADVANCEMENT_RULES, DYNAMIC_ADVANCEMENT_RULES);
        hasAdvancementRules = true;
    }

    public static void addTradeRule(TradeRule rule) {
        DYNAMIC_TRADE_RULES.add(rule);
        hasTradeRules = true;
    }

    public static void addStructureBlockRule(StructureBlockRule rule) {
        DYNAMIC_STRUCTURE_BLOCK_RULES.add(rule);
        hasStructureRules = true;
    }

    public static void addStructureProcessorRule(StructureProcessorRule rule) {
        DYNAMIC_STRUCTURE_PROCESSOR_RULES.add(rule);
        hasStructureRules = true;
    }

    public static void markItemUnregistered() {
        hasAnyUnregisteredItems = true;
    }

    public static void markFluidUnregistered() {
        hasAnyUnregisteredFluids = true;
    }

    public static void clear() {
        EXACT_BLOCK_RULES.clear();
        DYNAMIC_BLOCK_RULES.clear();
        EXACT_ITEM_RULES.clear();
        DYNAMIC_ITEM_RULES.clear();
        EXACT_BE_RULES.clear();
        DYNAMIC_BE_RULES.clear();
        EXACT_FLUID_RULES.clear();
        DYNAMIC_FLUID_RULES.clear();
        EXACT_CATEGORY_RULES.clear();
        DYNAMIC_CATEGORY_RULES.clear();
        EXACT_TAB_RULES.clear();
        DYNAMIC_TAB_RULES.clear();
        EXACT_WORLD_REPLACEMENTS.clear();
        DYNAMIC_WORLD_REPLACEMENTS.clear();
        DYNAMIC_LOOT_RULES.clear();
        STATICALLY_APPLIED_LOOT_RULES.clear();
        EXACT_ADVANCEMENT_RULES.clear();
        DYNAMIC_ADVANCEMENT_RULES.clear();
        DYNAMIC_TRADE_RULES.clear();
        DYNAMIC_STRUCTURE_BLOCK_RULES.clear();
        DYNAMIC_STRUCTURE_PROCESSOR_RULES.clear();

        hasTickerRules = false;
        hasGameEventRules = false;
        hasBlockInteractionOrPlacementRules = false;
        hasItemRules = false;
        hasFluidRules = false;
        hasCategoryRules = false;
        hasCreativeTabRules = false;
        hasWorldReplacements = false;
        hasRecipeOutputRules = false;
        hasPurgeRules = false;
        hasAnyUnregisteredItems = false;
        hasAnyUnregisteredFluids = false;
        hasLootRules = false;
        hasItemLootStripRules = false;
        hasAdvancementRules = false;
        hasTradeRules = false;
        hasStructureRules = false;
    }

    public static boolean hasTickerRules() {
        return hasTickerRules;
    }

    public static boolean hasGameEventRules() {
        return hasGameEventRules;
    }

    public static boolean hasBlockInteractionOrPlacementRules() {
        return hasBlockInteractionOrPlacementRules;
    }

    public static boolean hasItemRules() {
        return hasItemRules;
    }

    public static boolean hasViewerItemOrFluidRules() {
        return hasItemRules || hasFluidRules;
    }

    public static boolean hasCategoryRules() {
        return hasCategoryRules;
    }

    public static boolean hasCreativeTabRules() {
        return hasCreativeTabRules;
    }

    public static boolean hasWorldReplacements() {
        return hasWorldReplacements;
    }

    public static boolean hasRecipeOutputSuppressions() {
        return hasRecipeOutputRules;
    }

    public static boolean hasPurgeRules() {
        return hasPurgeRules;
    }

    public static boolean hasAnyUnregisteredItems() {
        return hasAnyUnregisteredItems;
    }

    public static boolean hasAnyUnregisteredFluids() {
        return hasAnyUnregisteredFluids;
    }

    public static boolean hasLootRules() {
        return hasLootRules;
    }

    public static boolean hasItemLootStripRules() {
        return hasItemLootStripRules;
    }

    public static boolean isItemIdStrippedFromLootTables(@Nullable ResourceLocation itemId) {
        if (!hasItemLootStripRules || itemId == null) {
            return false;
        }
        return matchesAny(itemId, EXACT_ITEM_RULES, DYNAMIC_ITEM_RULES,
                rule -> rule.stripFromLootTables() && rule.matchesItemIdOnly(itemId));
    }

    public static List<LootRule> getStaticLootRulesForTable(@Nullable ResourceLocation tableId) {
        if (!hasLootRules || tableId == null || DYNAMIC_LOOT_RULES.isEmpty()) {
            return List.of();
        }
        List<LootRule> matched = new ArrayList<>();
        for (LootRule rule : DYNAMIC_LOOT_RULES) {
            if (rule.canRewriteStatically() && rule.matchesTable(tableId)) {
                matched.add(rule);
            }
        }
        return matched;
    }

    public static void clearStaticallyAppliedLootRules(@Nullable ResourceLocation tableId) {
        if (tableId != null) {
            STATICALLY_APPLIED_LOOT_RULES.remove(tableId);
        }
    }

    public static void markLootRuleStaticallyApplied(@Nullable ResourceLocation tableId, LootRule rule) {
        if (tableId != null && rule != null) {
            STATICALLY_APPLIED_LOOT_RULES
                    .computeIfAbsent(tableId, k -> ConcurrentHashMap.newKeySet())
                    .add(rule);
        }
    }

    public static boolean isLootRuleStaticallyApplied(@Nullable ResourceLocation tableId, LootRule rule) {
        if (tableId == null || rule == null) {
            return false;
        }
        Set<LootRule> applied = STATICALLY_APPLIED_LOOT_RULES.get(tableId);
        return applied != null && applied.contains(rule);
    }

    public static boolean hasAdvancementRules() {
        return hasAdvancementRules;
    }

    public static boolean hasTradeRules() {
        return hasTradeRules;
    }

    public static boolean hasStructureRules() {
        return hasStructureRules;
    }

    public static boolean isBlockTickerSuppressed(@Nullable BlockState state, @Nullable BlockEntityType<?> beType) {
        if (!hasTickerRules) {
            return false;
        }
        if (state != null) {
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (matchesAny(blockId, EXACT_BLOCK_RULES, DYNAMIC_BLOCK_RULES,
                    rule -> rule.disableTicker() && rule.matches(blockId, state))) {
                return true;
            }
        }
        if (beType != null) {
            ResourceLocation beId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(beType);
            if (beId != null && matchesAny(beId, EXACT_BE_RULES, DYNAMIC_BE_RULES,
                    rule -> rule.disableTicker() && rule.matches(beId, state))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBlockEntityGameEventSuppressed(@Nullable BlockEntity blockEntity) {
        if (!hasGameEventRules || blockEntity == null) {
            return false;
        }
        BlockState state = blockEntity.getBlockState();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (matchesAny(blockId, EXACT_BLOCK_RULES, DYNAMIC_BLOCK_RULES,
                rule -> rule.disableGameEvents() && rule.matches(blockId, state))) {
            return true;
        }
        ResourceLocation beId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType());
        return beId != null && matchesAny(beId, EXACT_BE_RULES, DYNAMIC_BE_RULES,
                rule -> rule.disableGameEvents() && rule.matches(beId, state));
    }

    public static boolean isBlockInteractionBlocked(@Nullable BlockState state) {
        if (!hasBlockInteractionOrPlacementRules || state == null) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return matchesAny(blockId, EXACT_BLOCK_RULES, DYNAMIC_BLOCK_RULES,
                rule -> rule.blockInteraction() && rule.matches(blockId, state));
    }

    public static void notifyBlockedInteract(BlockState state, Player player, BlockPos pos) {
        if (!hasBlockInteractionOrPlacementRules) {
            return;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        forEachMatch(blockId, EXACT_BLOCK_RULES, DYNAMIC_BLOCK_RULES,
                rule -> rule.blockInteraction() && rule.onBlockedInteract() != null && rule.matches(blockId, state),
                rule -> rule.onBlockedInteract().accept(player, pos));
    }

    public static boolean isBlockPlacementBlocked(@Nullable BlockState state) {
        if (!hasBlockInteractionOrPlacementRules || state == null) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return matchesAny(blockId, EXACT_BLOCK_RULES, DYNAMIC_BLOCK_RULES,
                rule -> rule.blockPlacement() && rule.matches(blockId, state));
    }

    public static boolean isItemHiddenFromCreativeTab(@Nullable ItemStack stack) {
        return matchesItemFlag(stack, ItemRule::hideFromCreativeTabs);
    }

    public static boolean isItemHiddenFromRecipeViewer(@Nullable ItemStack stack) {
        return matchesItemFlag(stack, ItemRule::hideFromRecipeViewers);
    }

    public static boolean isItemHiddenFromRecipeViewer(@Nullable Item item) {
        if (!hasItemRules || item == null) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return matchesAny(itemId, EXACT_ITEM_RULES, DYNAMIC_ITEM_RULES,
                rule -> rule.hideFromRecipeViewers() && rule.matchesItemIdOnly(itemId));
    }

    public static boolean isItemUsageBlocked(@Nullable ItemStack stack) {
        return matchesItemFlag(stack, ItemRule::blockUsage);
    }

    public static void notifyBlockedUse(ItemStack stack, Player player) {
        if (!hasItemRules || stack.isEmpty()) {
            return;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        forEachMatch(itemId, EXACT_ITEM_RULES, DYNAMIC_ITEM_RULES,
                rule -> rule.blockUsage() && rule.onBlockedUse() != null && rule.matchesStack(itemId, stack),
                rule -> rule.onBlockedUse().accept(player));
    }

    public static boolean isItemCraftingBlocked(@Nullable ItemStack stack) {
        return matchesItemFlag(stack, ItemRule::blockCrafting);
    }

    public static boolean isItemPickupBlocked(@Nullable ItemStack stack) {
        return matchesItemFlag(stack, ItemRule::blockPickup);
    }

    public static boolean isItemPurgedFromInventory(@Nullable ItemStack stack) {
        return matchesItemFlag(stack, ItemRule::purgeFromInventory);
    }

    public static boolean isRecipeOutputDisabled(@Nullable ResourceLocation itemId) {
        if (!hasRecipeOutputRules || itemId == null) {
            return false;
        }
        return matchesAny(itemId, EXACT_ITEM_RULES, DYNAMIC_ITEM_RULES,
                rule -> rule.disableOutputRecipes() && rule.matchesItemIdOnly(itemId));
    }

    public static boolean isFluidHiddenFromRecipeViewer(@Nullable ResourceLocation fluidId) {
        if (!hasFluidRules || fluidId == null) {
            return false;
        }
        return matchesAny(fluidId, EXACT_FLUID_RULES, DYNAMIC_FLUID_RULES,
                rule -> rule.hideFromRecipeViewers() && rule.matches(fluidId));
    }

    public static boolean isRecipeCategoryDisabled(@Nullable ResourceLocation categoryId) {
        if (!hasCategoryRules || categoryId == null) {
            return false;
        }
        return matchesAny(categoryId, EXACT_CATEGORY_RULES, DYNAMIC_CATEGORY_RULES,
                rule -> rule.matches(categoryId));
    }

    public static boolean isCreativeTabDisabled(@Nullable ResourceLocation tabId) {
        if (!hasCreativeTabRules || tabId == null) {
            return false;
        }
        return matchesAny(tabId, EXACT_TAB_RULES, DYNAMIC_TAB_RULES,
                rule -> rule.matches(tabId));
    }

    public static void filterLoot(@Nullable ResourceLocation lootTableId, @Nullable List<ItemStack> generatedLoot) {
        filterLoot(lootTableId, generatedLoot, null);
    }

    public static void filterLoot(
            @Nullable ResourceLocation lootTableId,
            @Nullable List<ItemStack> generatedLoot,
            @Nullable LootContext lootContext
    ) {
        if (!hasLootRules || generatedLoot == null || generatedLoot.isEmpty()) {
            return;
        }
        for (int i = generatedLoot.size() - 1; i >= 0; i--) {
            ItemStack stack = generatedLoot.get(i);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            boolean handled = false;
            if (!DYNAMIC_LOOT_RULES.isEmpty()) {
                LootReplaceContext ctx = null;
                for (LootRule rule : DYNAMIC_LOOT_RULES) {
                    if (isLootRuleStaticallyApplied(lootTableId, rule)) {
                        continue;
                    }
                    if (rule.matches(lootTableId, stack)) {
                        if (rule.transformer() == null) {
                            generatedLoot.remove(i);
                            handled = true;
                            break;
                        }
                        if (ctx == null) {
                            ctx = new LootReplaceContext(lootTableId, stack, lootContext);
                        }
                        List<ItemStack> replacement = rule.transformer().apply(ctx);
                        if (replacement != null) {
                            generatedLoot.remove(i);
                            int insertAt = i;
                            for (ItemStack out : replacement) {
                                if (out != null && !out.isEmpty()) {
                                    generatedLoot.add(insertAt++, out);
                                }
                            }
                            handled = true;
                            break;
                        }
                    }
                }
            }
            if (!handled && matchesItemFlag(stack, ItemRule::stripFromLootTables)) {
                generatedLoot.remove(i);
            }
        }
    }

    public static boolean isAdvancementDisabled(
            @Nullable ResourceLocation advancementId,
            @Nullable JsonElement json
    ) {
        if (!hasAdvancementRules || advancementId == null) {
            return false;
        }
        return matchesAny(advancementId, EXACT_ADVANCEMENT_RULES, DYNAMIC_ADVANCEMENT_RULES,
                rule -> rule.matches(advancementId, json));
    }

    public static boolean isTradeItemSuppressed(@Nullable ResourceLocation professionId, @Nullable ItemStack stack) {
        if (!hasTradeRules || stack == null || stack.isEmpty()) {
            return false;
        }
        for (TradeRule rule : DYNAMIC_TRADE_RULES) {
            if (rule.matches(professionId, stack)) {
                return true;
            }
        }
        return matchesItemFlag(stack, ItemRule::stripFromTrades);
    }

    public static boolean matchesAnyStructureTemplate(@Nullable ResourceLocation templateId) {
        if (!hasStructureRules) {
            return false;
        }
        for (StructureBlockRule rule : DYNAMIC_STRUCTURE_BLOCK_RULES) {
            if (rule.matchesTemplate(templateId)) {
                return true;
            }
        }
        for (StructureProcessorRule rule : DYNAMIC_STRUCTURE_PROCESSOR_RULES) {
            if (rule.matchesTemplate(templateId)) {
                return true;
            }
        }
        return false;
    }

    public static List<StructureTemplate.StructureBlockInfo> processStructureBlocks(
            @Nullable ResourceLocation templateId,
            ServerLevelAccessor level,
            BlockPos offset,
            BlockPos originPos,
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> blockInfos,
            @Nullable StructureTemplate template
    ) {
        if (!hasStructureRules || blockInfos == null || blockInfos.isEmpty()) {
            return blockInfos;
        }

        List<StructureTemplate.StructureBlockInfo> working = blockInfos;
        boolean modified = false;

        for (StructureProcessorRule processorRule : DYNAMIC_STRUCTURE_PROCESSOR_RULES) {
            if (!processorRule.matchesTemplate(templateId)) {
                continue;
            }
            StructureProcessor processor = processorRule.processor();
            List<StructureTemplate.StructureBlockInfo> next = new ArrayList<>(working.size());
            for (StructureTemplate.StructureBlockInfo info : working) {
                StructureTemplate.StructureBlockInfo processed = processor.process(
                        level, offset, originPos, info, info, settings, template
                );
                if (processed != null) {
                    next.add(processed);
                }
            }
            working = processor.finalizeProcessing(level, offset, originPos, working, next, settings);
            modified = true;
            if (working.isEmpty()) {
                return working;
            }
        }

        if (!DYNAMIC_STRUCTURE_BLOCK_RULES.isEmpty()) {
            List<StructureBlockRule> activeBlockRules = null;
            for (StructureBlockRule rule : DYNAMIC_STRUCTURE_BLOCK_RULES) {
                if (rule.matchesTemplate(templateId)) {
                    if (activeBlockRules == null) {
                        activeBlockRules = new ArrayList<>();
                    }
                    activeBlockRules.add(rule);
                }
            }
            if (activeBlockRules != null) {
                List<StructureTemplate.StructureBlockInfo> transformedList = null;
                int size = working.size();
                for (int i = 0; i < size; i++) {
                    StructureTemplate.StructureBlockInfo originalInfo = working.get(i);
                    StructureTemplate.StructureBlockInfo current = originalInfo;
                    for (StructureBlockRule rule : activeBlockRules) {
                        if (current == null) {
                            break;
                        }
                        if (rule.matches(templateId, current.state())) {
                            RandomSource random = settings.getRandom(current.pos());
                            StructureBlockContext ctx = new StructureBlockContext(
                                    templateId,
                                    level,
                                    current.pos(),
                                    originPos,
                                    current.state(),
                                    current.nbt(),
                                    settings,
                                    random
                            );
                            current = rule.transformer().apply(ctx);
                        }
                    }
                    if (current != originalInfo) {
                        if (transformedList == null) {
                            transformedList = new ArrayList<>(size);
                            for (int j = 0; j < i; j++) {
                                transformedList.add(working.get(j));
                            }
                        }
                        if (current != null) {
                            transformedList.add(current);
                        }
                    } else if (transformedList != null) {
                        transformedList.add(current);
                    }
                }
                if (transformedList != null) {
                    return transformedList;
                }
            }
        }

        return modified ? working : blockInfos;
    }

    @Nullable
    public static ResourceLocation resolveWorldBlockReplacement(@Nullable ResourceLocation blockId) {
        if (!hasWorldReplacements || blockId == null) {
            return null;
        }
        List<WorldBlockReplacementRule> exact = EXACT_WORLD_REPLACEMENTS.get(blockId);
        if (exact != null) {
            for (WorldBlockReplacementRule rule : exact) {
                if (rule.matches(blockId)) {
                    ResourceLocation resolved = rule.targetResolver().apply(blockId);
                    if (resolved != null) {
                        return resolved;
                    }
                }
            }
        }
        for (WorldBlockReplacementRule rule : DYNAMIC_WORLD_REPLACEMENTS) {
            if (rule.matches(blockId)) {
                ResourceLocation resolved = rule.targetResolver().apply(blockId);
                if (resolved != null) {
                    return resolved;
                }
            }
        }
        return null;
    }

    @Nullable
    public static String resolveWorldBlockReplacement(@Nullable String blockIdStr) {
        if (!hasWorldReplacements || blockIdStr == null || blockIdStr.isEmpty()) {
            return null;
        }
        ResourceLocation blockId = ResourceLocation.tryParse(blockIdStr);
        if (blockId == null) {
            return null;
        }
        ResourceLocation target = resolveWorldBlockReplacement(blockId);
        return target != null ? target.toString() : null;
    }

    public static Map<ResourceLocation, ResourceLocation> getExactWorldReplacementTargets() {
        if (EXACT_WORLD_REPLACEMENTS.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, ResourceLocation> result = new LinkedHashMap<>();
        for (var entry : EXACT_WORLD_REPLACEMENTS.entrySet()) {
            ResourceLocation fromId = entry.getKey();
            for (WorldBlockReplacementRule rule : entry.getValue()) {
                if (rule.condition().getAsBoolean()) {
                    ResourceLocation target = rule.targetResolver().apply(fromId);
                    if (target != null) {
                        result.put(fromId, target);
                        break;
                    }
                }
            }
        }
        return result;
    }

    public static Stats getStats() {
        return new Stats(
                countRules(EXACT_BLOCK_RULES, DYNAMIC_BLOCK_RULES),
                countRules(EXACT_ITEM_RULES, DYNAMIC_ITEM_RULES),
                countRules(EXACT_FLUID_RULES, DYNAMIC_FLUID_RULES),
                countRules(EXACT_BE_RULES, DYNAMIC_BE_RULES),
                countRules(EXACT_CATEGORY_RULES, DYNAMIC_CATEGORY_RULES),
                countRules(EXACT_TAB_RULES, DYNAMIC_TAB_RULES),
                countRules(EXACT_WORLD_REPLACEMENTS, DYNAMIC_WORLD_REPLACEMENTS),
                DYNAMIC_LOOT_RULES.size(),
                countRules(EXACT_ADVANCEMENT_RULES, DYNAMIC_ADVANCEMENT_RULES),
                DYNAMIC_TRADE_RULES.size(),
                DYNAMIC_STRUCTURE_BLOCK_RULES.size() + DYNAMIC_STRUCTURE_PROCESSOR_RULES.size()
        );
    }

    private static boolean matchesItemFlag(@Nullable ItemStack stack, Predicate<ItemRule> flag) {
        if (!hasItemRules || stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return matchesAny(itemId, EXACT_ITEM_RULES, DYNAMIC_ITEM_RULES,
                rule -> flag.test(rule) && rule.matchesStack(itemId, stack));
    }

    private static <T> void registerEntry(
            @Nullable ResourceLocation exactId,
            T rule,
            Map<ResourceLocation, List<T>> exactMap,
            List<T> dynamicList
    ) {
        if (exactId != null) {
            exactMap.computeIfAbsent(exactId, k -> new CopyOnWriteArrayList<>()).add(rule);
        } else {
            dynamicList.add(rule);
        }
    }

    private static <T> boolean matchesAny(
            ResourceLocation id,
            Map<ResourceLocation, List<T>> exactMap,
            List<T> dynamicList,
            Predicate<T> predicate
    ) {
        List<T> exact = exactMap.get(id);
        if (exact != null) {
            for (T rule : exact) {
                if (predicate.test(rule)) {
                    return true;
                }
            }
        }
        if (!dynamicList.isEmpty()) {
            for (T rule : dynamicList) {
                if (predicate.test(rule)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static <T> void forEachMatch(
            ResourceLocation id,
            Map<ResourceLocation, List<T>> exactMap,
            List<T> dynamicList,
            Predicate<T> predicate,
            Consumer<T> action
    ) {
        List<T> exact = exactMap.get(id);
        if (exact != null) {
            for (T rule : exact) {
                if (predicate.test(rule)) {
                    action.accept(rule);
                }
            }
        }
        if (!dynamicList.isEmpty()) {
            for (T rule : dynamicList) {
                if (predicate.test(rule)) {
                    action.accept(rule);
                }
            }
        }
    }

    private static <T> int countRules(Map<ResourceLocation, List<T>> exactMap, List<T> dynamicList) {
        int count = dynamicList.size();
        for (List<T> list : exactMap.values()) {
            count += list.size();
        }
        return count;
    }
}
