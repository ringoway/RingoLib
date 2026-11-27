package com.ringoway.ringolib.api.content;

import com.google.gson.JsonElement;
import com.ringoway.ringolib.api.RecipeFilterManager;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import com.ringoway.ringolib.core.content.GlobMatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ContentSuppressionScope {
    private final BooleanSupplier condition;
    @Nullable
    private final String requiredMod;

    ContentSuppressionScope(BooleanSupplier condition, @Nullable String requiredMod) {
        this.condition = Objects.requireNonNull(condition, "condition");
        this.requiredMod = requiredMod;
    }

    public ContentSuppressionScope when(BooleanSupplier extraCondition) {
        Objects.requireNonNull(extraCondition, "extraCondition");
        return new ContentSuppressionScope(
                () -> this.condition.getAsBoolean() && extraCondition.getAsBoolean(),
                this.requiredMod
        );
    }

    public ContentSuppressionScope ifModLoaded(String modId) {
        Objects.requireNonNull(modId, "modId");
        return new ContentSuppressionScope(
                () -> this.condition.getAsBoolean() && ModList.get().isLoaded(modId),
                this.requiredMod != null ? this.requiredMod : modId
        );
    }

    public ContentSuppressionScope ifModMissing(String modId) {
        Objects.requireNonNull(modId, "modId");
        return new ContentSuppressionScope(
                () -> this.condition.getAsBoolean() && !ModList.get().isLoaded(modId),
                this.requiredMod
        );
    }

    public ContentSuppressionScope block(String blockIdOrGlob) {
        return block(blockIdOrGlob, b -> {});
    }

    public ContentSuppressionScope block(ResourceLocation blockId) {
        return block(blockId, b -> {});
    }

    public ContentSuppressionScope block(String blockIdOrGlob, Consumer<BlockSuppressionBuilder> options) {
        Objects.requireNonNull(blockIdOrGlob, "blockIdOrGlob");
        Objects.requireNonNull(options, "options");
        ResourceLocation exact = GlobMatcher.isGlob(blockIdOrGlob) ? null : parseId(blockIdOrGlob);
        BlockSuppressionBuilder builder = new BlockSuppressionBuilder(
                exact,
                GlobMatcher.toPredicate(blockIdOrGlob),
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope block(ResourceLocation blockId, Consumer<BlockSuppressionBuilder> options) {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(options, "options");
        BlockSuppressionBuilder builder = new BlockSuppressionBuilder(
                blockId,
                blockId::equals,
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope blocks(Predicate<ResourceLocation> idMatcher) {
        return blocks(idMatcher, b -> {});
    }

    public ContentSuppressionScope blocks(
            Predicate<ResourceLocation> idMatcher,
            Consumer<BlockSuppressionBuilder> options
    ) {
        Objects.requireNonNull(idMatcher, "idMatcher");
        Objects.requireNonNull(options, "options");
        BlockSuppressionBuilder builder = new BlockSuppressionBuilder(null, idMatcher, condition, requiredMod);
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope blockStates(
            Predicate<BlockState> stateMatcher,
            Consumer<BlockSuppressionBuilder> options
    ) {
        Objects.requireNonNull(stateMatcher, "stateMatcher");
        Objects.requireNonNull(options, "options");
        BlockSuppressionBuilder builder = new BlockSuppressionBuilder(null, id -> true, condition, requiredMod);
        builder.matchingState(stateMatcher);
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope item(String itemIdOrGlob) {
        return item(itemIdOrGlob, b -> {});
    }

    public ContentSuppressionScope item(ResourceLocation itemId) {
        return item(itemId, b -> {});
    }

    public ContentSuppressionScope item(String itemIdOrGlob, Consumer<ItemSuppressionBuilder> options) {
        Objects.requireNonNull(itemIdOrGlob, "itemIdOrGlob");
        Objects.requireNonNull(options, "options");
        ResourceLocation exact = GlobMatcher.isGlob(itemIdOrGlob) ? null : parseId(itemIdOrGlob);
        ItemSuppressionBuilder builder = new ItemSuppressionBuilder(
                exact,
                GlobMatcher.toPredicate(itemIdOrGlob),
                null,
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope item(ResourceLocation itemId, Consumer<ItemSuppressionBuilder> options) {
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(options, "options");
        ItemSuppressionBuilder builder = new ItemSuppressionBuilder(
                itemId,
                itemId::equals,
                null,
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope items(Predicate<ResourceLocation> idMatcher) {
        return items(idMatcher, b -> {});
    }

    public ContentSuppressionScope items(
            Predicate<ResourceLocation> idMatcher,
            Consumer<ItemSuppressionBuilder> options
    ) {
        Objects.requireNonNull(idMatcher, "idMatcher");
        Objects.requireNonNull(options, "options");
        ItemSuppressionBuilder builder = new ItemSuppressionBuilder(null, idMatcher, null, condition, requiredMod);
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope itemStacks(
            Predicate<ItemStack> stackMatcher,
            Consumer<ItemSuppressionBuilder> options
    ) {
        Objects.requireNonNull(stackMatcher, "stackMatcher");
        Objects.requireNonNull(options, "options");
        ItemSuppressionBuilder builder = new ItemSuppressionBuilder(
                null,
                id -> true,
                stackMatcher,
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope fluid(String fluidIdOrGlob) {
        return fluid(fluidIdOrGlob, b -> {});
    }

    public ContentSuppressionScope fluid(ResourceLocation fluidId) {
        return fluid(fluidId, b -> {});
    }

    public ContentSuppressionScope fluid(ResourceLocation fluidId, Consumer<FluidSuppressionBuilder> options) {
        Objects.requireNonNull(fluidId, "fluidId");
        Objects.requireNonNull(options, "options");
        FluidSuppressionBuilder builder = new FluidSuppressionBuilder(
                fluidId,
                fluidId::equals,
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope fluid(String fluidIdOrGlob, Consumer<FluidSuppressionBuilder> options) {
        Objects.requireNonNull(fluidIdOrGlob, "fluidIdOrGlob");
        Objects.requireNonNull(options, "options");
        ResourceLocation exact = GlobMatcher.isGlob(fluidIdOrGlob) ? null : parseId(fluidIdOrGlob);
        FluidSuppressionBuilder builder = new FluidSuppressionBuilder(
                exact,
                GlobMatcher.toPredicate(fluidIdOrGlob),
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope fluids(
            Predicate<ResourceLocation> idMatcher,
            Consumer<FluidSuppressionBuilder> options
    ) {
        Objects.requireNonNull(idMatcher, "idMatcher");
        Objects.requireNonNull(options, "options");
        FluidSuppressionBuilder builder = new FluidSuppressionBuilder(null, idMatcher, condition, requiredMod);
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope blockEntity(String beTypeIdOrGlob) {
        return blockEntity(beTypeIdOrGlob, b -> {});
    }

    public ContentSuppressionScope blockEntity(ResourceLocation beTypeId) {
        return blockEntity(beTypeId, b -> {});
    }

    public ContentSuppressionScope blockEntity(
            ResourceLocation beTypeId,
            Consumer<BlockEntitySuppressionBuilder> options
    ) {
        Objects.requireNonNull(beTypeId, "beTypeId");
        Objects.requireNonNull(options, "options");
        BlockEntitySuppressionBuilder builder = new BlockEntitySuppressionBuilder(
                beTypeId,
                beTypeId::equals,
                null,
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope blockEntity(
            String beTypeIdOrGlob,
            Consumer<BlockEntitySuppressionBuilder> options
    ) {
        Objects.requireNonNull(beTypeIdOrGlob, "beTypeIdOrGlob");
        Objects.requireNonNull(options, "options");
        ResourceLocation exact = GlobMatcher.isGlob(beTypeIdOrGlob) ? null : parseId(beTypeIdOrGlob);
        BlockEntitySuppressionBuilder builder = new BlockEntitySuppressionBuilder(
                exact,
                GlobMatcher.toPredicate(beTypeIdOrGlob),
                null,
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope blockEntities(
            Predicate<ResourceLocation> beTypeIdMatcher,
            Consumer<BlockEntitySuppressionBuilder> options
    ) {
        Objects.requireNonNull(beTypeIdMatcher, "beTypeIdMatcher");
        Objects.requireNonNull(options, "options");
        BlockEntitySuppressionBuilder builder = new BlockEntitySuppressionBuilder(
                null,
                beTypeIdMatcher,
                null,
                condition,
                requiredMod
        );
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope blockEntityTicker(Predicate<BlockState> stateMatcher) {
        return blockEntityTicker(stateMatcher, id -> true);
    }

    public ContentSuppressionScope blockEntityTicker(
            Predicate<BlockState> stateMatcher,
            Predicate<ResourceLocation> beTypeIdMatcher
    ) {
        Objects.requireNonNull(stateMatcher, "stateMatcher");
        Objects.requireNonNull(beTypeIdMatcher, "beTypeIdMatcher");
        BlockEntitySuppressionBuilder builder = new BlockEntitySuppressionBuilder(
                null,
                beTypeIdMatcher,
                stateMatcher,
                condition,
                requiredMod
        );
        builder.apply();
        return this;
    }

    public ContentSuppressionScope recipeCategory(String categoryIdOrGlob) {
        Objects.requireNonNull(categoryIdOrGlob, "categoryIdOrGlob");
        ResourceLocation exact = GlobMatcher.isGlob(categoryIdOrGlob) ? null : parseId(categoryIdOrGlob);
        ContentSuppressionRegistry.addRecipeCategoryRule(
                exact,
                new ContentSuppressionRegistry.RecipeCategoryRule(
                        GlobMatcher.toPredicate(categoryIdOrGlob),
                        condition
                )
        );
        return this;
    }

    public ContentSuppressionScope recipeCategory(ResourceLocation categoryId) {
        Objects.requireNonNull(categoryId, "categoryId");
        ContentSuppressionRegistry.addRecipeCategoryRule(
                categoryId,
                new ContentSuppressionRegistry.RecipeCategoryRule(categoryId::equals, condition)
        );
        return this;
    }

    public ContentSuppressionScope recipeCategories(String... categoryIdsOrGlobs) {
        if (categoryIdsOrGlobs != null) {
            for (String pattern : categoryIdsOrGlobs) {
                recipeCategory(pattern);
            }
        }
        return this;
    }

    public ContentSuppressionScope recipeCategories(Predicate<ResourceLocation> categoryMatcher) {
        Objects.requireNonNull(categoryMatcher, "categoryMatcher");
        ContentSuppressionRegistry.addRecipeCategoryRule(
                null,
                new ContentSuppressionRegistry.RecipeCategoryRule(categoryMatcher, condition)
        );
        return this;
    }

    public ContentSuppressionScope recipe(String recipeIdOrGlob) {
        RecipeFilterManager.disableRecipes(condition, GlobMatcher.toPredicate(recipeIdOrGlob));
        return this;
    }

    public ContentSuppressionScope recipe(ResourceLocation recipeId) {
        Objects.requireNonNull(recipeId, "recipeId");
        RecipeFilterManager.disableRecipes(condition, recipeId::equals);
        return this;
    }

    public ContentSuppressionScope recipes(String... recipeIdsOrGlobs) {
        if (recipeIdsOrGlobs != null) {
            for (String pattern : recipeIdsOrGlobs) {
                recipe(pattern);
            }
        }
        return this;
    }

    public ContentSuppressionScope recipes(Predicate<ResourceLocation> idMatcher) {
        RecipeFilterManager.disableRecipes(condition, idMatcher);
        return this;
    }

    public ContentSuppressionScope recipesByOutput(String itemIdOrGlob) {
        RecipeFilterManager.disableRecipesByOutput(condition, GlobMatcher.toPredicate(itemIdOrGlob));
        return this;
    }

    public ContentSuppressionScope recipesByOutput(ResourceLocation itemId) {
        Objects.requireNonNull(itemId, "itemId");
        RecipeFilterManager.disableRecipesByOutput(condition, itemId::equals);
        return this;
    }

    public ContentSuppressionScope recipesByOutput(Predicate<ResourceLocation> outputItemMatcher) {
        RecipeFilterManager.disableRecipesByOutput(condition, outputItemMatcher);
        return this;
    }

    public ContentSuppressionScope recipesMatchingJson(BiPredicate<ResourceLocation, JsonElement> jsonPredicate) {
        RecipeFilterManager.disableRecipesMatchingJson(condition, jsonPredicate);
        return this;
    }

    public ContentSuppressionScope creativeTab(String tabIdOrGlob) {
        Objects.requireNonNull(tabIdOrGlob, "tabIdOrGlob");
        ResourceLocation exact = GlobMatcher.isGlob(tabIdOrGlob) ? null : parseId(tabIdOrGlob);
        ContentSuppressionRegistry.addCreativeTabRule(
                exact,
                new ContentSuppressionRegistry.CreativeTabRule(GlobMatcher.toPredicate(tabIdOrGlob), condition)
        );
        return this;
    }

    public ContentSuppressionScope creativeTab(ResourceLocation tabId) {
        Objects.requireNonNull(tabId, "tabId");
        ContentSuppressionRegistry.addCreativeTabRule(
                tabId,
                new ContentSuppressionRegistry.CreativeTabRule(tabId::equals, condition)
        );
        return this;
    }

    public ContentSuppressionScope creativeTabs(Predicate<ResourceLocation> tabMatcher) {
        Objects.requireNonNull(tabMatcher, "tabMatcher");
        ContentSuppressionRegistry.addCreativeTabRule(
                null,
                new ContentSuppressionRegistry.CreativeTabRule(tabMatcher, condition)
        );
        return this;
    }

    public ContentSuppressionScope hideItemFromViewers(String itemIdOrGlob) {
        return item(itemIdOrGlob, b -> b
                .keepRecipes()
                .keepInLootTables()
                .keepInTrades()
                .allowUsage()
                .allowCrafting()
                .hideFromCreativeTabs(false)
                .hideFromRecipeViewers(true));
    }

    public ContentSuppressionScope hideItemsFromViewers(Predicate<ItemStack> stackMatcher) {
        return itemStacks(stackMatcher, b -> b
                .keepRecipes()
                .keepInLootTables()
                .keepInTrades()
                .allowUsage()
                .allowCrafting()
                .hideFromCreativeTabs(false)
                .hideFromRecipeViewers(true));
    }

    public ContentSuppressionScope hideFluidFromViewers(String fluidIdOrGlob) {
        return fluid(fluidIdOrGlob, b -> b.hideFromRecipeViewers(true));
    }

    public ContentSuppressionScope hideFluidsFromViewers(Predicate<ResourceLocation> fluidMatcher) {
        return fluids(fluidMatcher, b -> b.hideFromRecipeViewers(true));
    }

    public ContentSuppressionScope loot(Consumer<LootRuleBuilder> options) {
        Objects.requireNonNull(options, "options");
        LootRuleBuilder builder = new LootRuleBuilder(condition);
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope loot(String tableIdOrGlob, Consumer<LootRuleBuilder> options) {
        Objects.requireNonNull(tableIdOrGlob, "tableIdOrGlob");
        Objects.requireNonNull(options, "options");
        LootRuleBuilder builder = new LootRuleBuilder(condition).inTable(tableIdOrGlob);
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope lootItem(String itemIdOrGlob) {
        Predicate<ResourceLocation> itemIdMatcher = GlobMatcher.toPredicate(Objects.requireNonNull(itemIdOrGlob, "itemIdOrGlob"));
        ContentSuppressionRegistry.addLootRule(new ContentSuppressionRegistry.LootRule(
                null,
                stack -> !stack.isEmpty() && itemIdMatcher.test(BuiltInRegistries.ITEM.getKey(stack.getItem())),
                null,
                condition
        ));
        return this;
    }

    public ContentSuppressionScope loot(
            Predicate<ResourceLocation> tableMatcher,
            Predicate<ItemStack> stackMatcher
    ) {
        Objects.requireNonNull(tableMatcher, "tableMatcher");
        Objects.requireNonNull(stackMatcher, "stackMatcher");
        ContentSuppressionRegistry.addLootRule(new ContentSuppressionRegistry.LootRule(
                tableMatcher,
                stackMatcher,
                null,
                condition
        ));
        return this;
    }

    public ContentSuppressionScope replaceLoot(
            Predicate<ResourceLocation> tableMatcher,
            Predicate<ItemStack> stackMatcher,
            BiFunction<ResourceLocation, ItemStack, ItemStack> replacer
    ) {
        Objects.requireNonNull(tableMatcher, "tableMatcher");
        Objects.requireNonNull(stackMatcher, "stackMatcher");
        Objects.requireNonNull(replacer, "replacer");
        ContentSuppressionRegistry.addLootRule(new ContentSuppressionRegistry.LootRule(
                tableMatcher,
                stackMatcher,
                ctx -> {
                    ItemStack result = replacer.apply(ctx.lootTableId(), ctx.originalStack());
                    return (result == null || result.isEmpty()) ? List.of() : List.of(result);
                },
                condition
        ));
        return this;
    }

    public ContentSuppressionScope structure(Consumer<StructureRuleBuilder> options) {
        Objects.requireNonNull(options, "options");
        StructureRuleBuilder builder = new StructureRuleBuilder(condition);
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope structure(String templateIdOrGlob, Consumer<StructureRuleBuilder> options) {
        Objects.requireNonNull(templateIdOrGlob, "templateIdOrGlob");
        Objects.requireNonNull(options, "options");
        StructureRuleBuilder builder = new StructureRuleBuilder(condition).inTemplate(templateIdOrGlob);
        options.accept(builder);
        builder.apply();
        return this;
    }

    public ContentSuppressionScope advancement(String advancementIdOrGlob) {
        Objects.requireNonNull(advancementIdOrGlob, "advancementIdOrGlob");
        ResourceLocation exact = GlobMatcher.isGlob(advancementIdOrGlob) ? null : parseId(advancementIdOrGlob);
        ContentSuppressionRegistry.addAdvancementRule(
                exact,
                new ContentSuppressionRegistry.AdvancementRule(
                        GlobMatcher.toPredicate(advancementIdOrGlob),
                        null,
                        condition
                )
        );
        return this;
    }

    public ContentSuppressionScope advancement(ResourceLocation advancementId) {
        Objects.requireNonNull(advancementId, "advancementId");
        ContentSuppressionRegistry.addAdvancementRule(
                advancementId,
                new ContentSuppressionRegistry.AdvancementRule(advancementId::equals, null, condition)
        );
        return this;
    }

    public ContentSuppressionScope advancements(String... advancementIdsOrGlobs) {
        if (advancementIdsOrGlobs != null) {
            for (String pattern : advancementIdsOrGlobs) {
                advancement(pattern);
            }
        }
        return this;
    }

    public ContentSuppressionScope advancements(Predicate<ResourceLocation> matcher) {
        Objects.requireNonNull(matcher, "matcher");
        ContentSuppressionRegistry.addAdvancementRule(
                null,
                new ContentSuppressionRegistry.AdvancementRule(matcher, null, condition)
        );
        return this;
    }

    public ContentSuppressionScope advancementsMatchingJson(BiPredicate<ResourceLocation, JsonElement> jsonMatcher) {
        Objects.requireNonNull(jsonMatcher, "jsonMatcher");
        ContentSuppressionRegistry.addAdvancementRule(
                null,
                new ContentSuppressionRegistry.AdvancementRule(id -> true, jsonMatcher, condition)
        );
        return this;
    }

    public ContentSuppressionScope tradeItem(String itemIdOrGlob) {
        Predicate<ResourceLocation> itemIdMatcher = GlobMatcher.toPredicate(Objects.requireNonNull(itemIdOrGlob, "itemIdOrGlob"));
        ContentSuppressionRegistry.addTradeRule(new ContentSuppressionRegistry.TradeRule(
                null,
                stack -> !stack.isEmpty() && itemIdMatcher.test(BuiltInRegistries.ITEM.getKey(stack.getItem())),
                condition
        ));
        return this;
    }

    public ContentSuppressionScope trades(
            Predicate<ResourceLocation> professionMatcher,
            Predicate<ItemStack> itemMatcher
    ) {
        Objects.requireNonNull(professionMatcher, "professionMatcher");
        Objects.requireNonNull(itemMatcher, "itemMatcher");
        ContentSuppressionRegistry.addTradeRule(new ContentSuppressionRegistry.TradeRule(
                professionMatcher,
                itemMatcher,
                condition
        ));
        return this;
    }

    public ContentSuppressionScope replaceInWorld(String fromBlockId, String toBlockId) {
        ResourceLocation from = parseId(fromBlockId);
        ResourceLocation to = parseId(toBlockId);
        ContentSuppressionRegistry.addWorldBlockReplacement(
                from,
                new ContentSuppressionRegistry.WorldBlockReplacementRule(from::equals, id -> to, condition)
        );
        return this;
    }

    public ContentSuppressionScope replaceInWorld(
            Predicate<ResourceLocation> blockIdMatcher,
            Function<ResourceLocation, ResourceLocation> targetResolver
    ) {
        Objects.requireNonNull(blockIdMatcher, "blockIdMatcher");
        Objects.requireNonNull(targetResolver, "targetResolver");
        ContentSuppressionRegistry.addWorldBlockReplacement(
                null,
                new ContentSuppressionRegistry.WorldBlockReplacementRule(blockIdMatcher, targetResolver, condition)
        );
        return this;
    }

    private static ResourceLocation parseId(String raw) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw.trim());
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid ResourceLocation: " + raw);
        }
        return parsed;
    }
}
