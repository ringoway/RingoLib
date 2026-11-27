package com.ringoway.ringolib.api.content;

import com.google.gson.JsonElement;
import com.ringoway.ringolib.core.content.GlobMatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Public API for suppressing mod content, viewer categories, loot, trades, advancements, and structure blocks.
 * Call from your {@code @Mod} constructor or {@link RegisterContentSuppressionsEvent} before {@code RegisterEvent}.
 */
public final class RingoContentAPI {
    private static final BooleanSupplier ALWAYS = () -> true;

    private RingoContentAPI() {
    }

    public static void configure(Consumer<ContentSuppressionScope> configurator) {
        Objects.requireNonNull(configurator, "configurator").accept(suppress());
    }

    public static void whenModLoaded(String modId, Consumer<ContentSuppressionScope> configurator) {
        Objects.requireNonNull(configurator, "configurator").accept(suppressForMod(modId));
    }

    public static void when(BooleanSupplier condition, Consumer<ContentSuppressionScope> configurator) {
        Objects.requireNonNull(configurator, "configurator").accept(suppressWhen(condition));
    }

    public static ContentSuppressionScope suppress() {
        return new ContentSuppressionScope(ALWAYS, null);
    }

    public static ContentSuppressionScope suppressForMod(String modId) {
        Objects.requireNonNull(modId, "modId");
        return new ContentSuppressionScope(() -> ModList.get().isLoaded(modId), modId);
    }

    public static ContentSuppressionScope suppressWhen(BooleanSupplier condition) {
        return new ContentSuppressionScope(Objects.requireNonNull(condition, "condition"), null);
    }

    public static void disableBlock(String blockIdOrGlob) {
        suppress().block(blockIdOrGlob);
    }

    public static void disableBlock(ResourceLocation blockId) {
        suppress().block(blockId);
    }

    public static void disableBlock(String blockIdOrGlob, Consumer<BlockSuppressionBuilder> options) {
        suppress().block(blockIdOrGlob, options);
    }

    public static void disableBlock(ResourceLocation blockId, Consumer<BlockSuppressionBuilder> options) {
        suppress().block(blockId, options);
    }

    public static void disableBlocks(
            Predicate<ResourceLocation> matcher,
            Consumer<BlockSuppressionBuilder> options
    ) {
        suppress().blocks(matcher, options);
    }

    public static void disableBlocks(String... blockIdsOrGlobs) {
        if (blockIdsOrGlobs != null) {
            ContentSuppressionScope scope = suppress();
            for (String id : blockIdsOrGlobs) {
                scope.block(id);
            }
        }
    }

    public static void disableItem(String itemIdOrGlob) {
        suppress().item(itemIdOrGlob);
    }

    public static void disableItem(ResourceLocation itemId) {
        suppress().item(itemId);
    }

    public static void disableItem(String itemIdOrGlob, Consumer<ItemSuppressionBuilder> options) {
        suppress().item(itemIdOrGlob, options);
    }

    public static void disableItem(ResourceLocation itemId, Consumer<ItemSuppressionBuilder> options) {
        suppress().item(itemId, options);
    }

    public static void disableItems(
            Predicate<ResourceLocation> matcher,
            Consumer<ItemSuppressionBuilder> options
    ) {
        suppress().items(matcher, options);
    }

    public static void disableItems(String... itemIdsOrGlobs) {
        if (itemIdsOrGlobs != null) {
            ContentSuppressionScope scope = suppress();
            for (String id : itemIdsOrGlobs) {
                scope.item(id);
            }
        }
    }

    public static void disableItemStacks(
            Predicate<ItemStack> stackMatcher,
            Consumer<ItemSuppressionBuilder> options
    ) {
        suppress().itemStacks(stackMatcher, options);
    }

    public static void disableFluid(String fluidIdOrGlob) {
        suppress().fluid(fluidIdOrGlob);
    }

    public static void disableFluid(String fluidIdOrGlob, Consumer<FluidSuppressionBuilder> options) {
        suppress().fluid(fluidIdOrGlob, options);
    }

    public static void disableBlockEntity(String beTypeIdOrGlob) {
        suppress().blockEntity(beTypeIdOrGlob);
    }

    public static void disableBlockEntity(
            String beTypeIdOrGlob,
            Consumer<BlockEntitySuppressionBuilder> options
    ) {
        suppress().blockEntity(beTypeIdOrGlob, options);
    }

    public static void disableBlockEntityTicking(Predicate<BlockState> statePredicate) {
        suppress().blockEntityTicker(statePredicate);
    }

    public static void disableBlockEntityTicking(
            Predicate<BlockState> statePredicate,
            Predicate<ResourceLocation> beTypeIdPredicate
    ) {
        suppress().blockEntityTicker(statePredicate, beTypeIdPredicate);
    }

    public static void disableRecipeCategory(String categoryIdOrGlob) {
        suppress().recipeCategory(categoryIdOrGlob);
    }

    public static void disableRecipeCategory(ResourceLocation categoryId) {
        suppress().recipeCategory(categoryId);
    }

    public static void disableRecipeCategories(String... categoryIdsOrGlobs) {
        suppress().recipeCategories(categoryIdsOrGlobs);
    }

    public static void disableRecipeCategories(Predicate<ResourceLocation> matcher) {
        suppress().recipeCategories(matcher);
    }

    public static void disableRecipeCategories(
            BooleanSupplier condition,
            Predicate<ResourceLocation> matcher
    ) {
        suppressWhen(condition).recipeCategories(matcher);
    }

    public static void disableRecipe(String recipeIdOrGlob) {
        suppress().recipe(recipeIdOrGlob);
    }

    public static void disableRecipes(String... recipeIdsOrGlobs) {
        suppress().recipes(recipeIdsOrGlobs);
    }

    public static void disableRecipes(Predicate<ResourceLocation> idMatcher) {
        suppress().recipes(idMatcher);
    }

    public static void disableRecipesByOutput(String itemIdOrGlob) {
        suppress().recipesByOutput(itemIdOrGlob);
    }

    public static void disableRecipesByOutput(Predicate<ResourceLocation> outputItemMatcher) {
        suppress().recipesByOutput(outputItemMatcher);
    }

    public static void disableRecipesMatchingJson(BiPredicate<ResourceLocation, JsonElement> jsonPredicate) {
        suppress().recipesMatchingJson(jsonPredicate);
    }

    public static void disableCreativeTab(String tabIdOrGlob) {
        suppress().creativeTab(tabIdOrGlob);
    }

    public static void disableCreativeTabs(Predicate<ResourceLocation> tabMatcher) {
        suppress().creativeTabs(tabMatcher);
    }

    public static void replaceBlockInWorld(String fromBlockId, String toBlockId) {
        suppress().replaceInWorld(fromBlockId, toBlockId);
    }

    public static void replaceBlockInWorld(
            Predicate<ResourceLocation> matcher,
            Function<ResourceLocation, ResourceLocation> targetResolver
    ) {
        suppress().replaceInWorld(matcher, targetResolver);
    }

    public static void hideItemFromViewers(String itemIdOrGlob) {
        suppress().hideItemFromViewers(itemIdOrGlob);
    }

    public static void hideItemsFromViewers(Predicate<ItemStack> stackMatcher) {
        suppress().hideItemsFromViewers(stackMatcher);
    }

    public static void hideFluidFromViewers(String fluidIdOrGlob) {
        suppress().hideFluidFromViewers(fluidIdOrGlob);
    }

    public static void hideFluidsFromViewers(Predicate<ResourceLocation> fluidMatcher) {
        suppress().hideFluidsFromViewers(fluidMatcher);
    }

    public static void configureLoot(Consumer<LootRuleBuilder> options) {
        suppress().loot(options);
    }

    public static void inLootTable(String tableIdOrGlob, Consumer<LootRuleBuilder> options) {
        suppress().loot(tableIdOrGlob, options);
    }

    public static void configureStructure(Consumer<StructureRuleBuilder> options) {
        suppress().structure(options);
    }

    public static void inStructure(String templateIdOrGlob, Consumer<StructureRuleBuilder> options) {
        suppress().structure(templateIdOrGlob, options);
    }

    public static void disableLootItem(String itemIdOrGlob) {
        suppress().lootItem(itemIdOrGlob);
    }

    public static void disableLoot(
            Predicate<ResourceLocation> tableMatcher,
            Predicate<ItemStack> stackMatcher
    ) {
        suppress().loot(tableMatcher, stackMatcher);
    }

    public static void replaceLoot(
            Predicate<ResourceLocation> tableMatcher,
            Predicate<ItemStack> stackMatcher,
            BiFunction<ResourceLocation, ItemStack, ItemStack> replacer
    ) {
        suppress().replaceLoot(tableMatcher, stackMatcher, replacer);
    }

    public static void disableAdvancement(String advancementIdOrGlob) {
        suppress().advancement(advancementIdOrGlob);
    }

    public static void disableAdvancement(ResourceLocation advancementId) {
        suppress().advancement(advancementId);
    }

    public static void disableAdvancements(String... advancementIdsOrGlobs) {
        suppress().advancements(advancementIdsOrGlobs);
    }

    public static void disableAdvancements(Predicate<ResourceLocation> matcher) {
        suppress().advancements(matcher);
    }

    public static void disableAdvancementsMatchingJson(BiPredicate<ResourceLocation, JsonElement> jsonMatcher) {
        suppress().advancementsMatchingJson(jsonMatcher);
    }

    public static void disableTradeItem(String itemIdOrGlob) {
        suppress().tradeItem(itemIdOrGlob);
    }

    public static void disableTrades(
            Predicate<ResourceLocation> professionMatcher,
            Predicate<ItemStack> itemMatcher
    ) {
        suppress().trades(professionMatcher, itemMatcher);
    }

    public static Predicate<ResourceLocation> idMatches(String idOrGlob) {
        return GlobMatcher.toPredicate(idOrGlob);
    }

    public static Predicate<ResourceLocation> inNamespace(String namespace) {
        Objects.requireNonNull(namespace, "namespace");
        return id -> id != null && namespace.equals(id.getNamespace());
    }

    public static Predicate<ResourceLocation> pathMatches(Predicate<String> pathPredicate) {
        Objects.requireNonNull(pathPredicate, "pathPredicate");
        return id -> id != null && pathPredicate.test(id.getPath());
    }

    public static Predicate<ItemStack> hasItemTag(TagKey<Item> tag) {
        Objects.requireNonNull(tag, "tag");
        return stack -> stack != null && !stack.isEmpty() && stack.is(tag);
    }

    public static Predicate<BlockState> hasBlockTag(TagKey<Block> tag) {
        Objects.requireNonNull(tag, "tag");
        return state -> state != null && state.is(tag);
    }
}
