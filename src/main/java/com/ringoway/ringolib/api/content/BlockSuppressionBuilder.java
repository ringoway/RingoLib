package com.ringoway.ringolib.api.content;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RecipeFilterManager;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import com.ringoway.ringolib.core.RingoLibRegistry;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import com.ringoway.ringolib.core.content.GlobMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class BlockSuppressionBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation AIR_ID = ResourceLocation.withDefaultNamespace("air");

    @Nullable
    private final ResourceLocation exactId;
    private final Predicate<ResourceLocation> idMatcher;
    @Nullable
    private final String requiredMod;
    private BooleanSupplier condition;

    @Nullable
    private Predicate<BlockState> stateMatcher;
    private boolean disableTicker = true;
    private boolean disableGameEvents = true;
    private boolean blockInteraction = true;
    private boolean blockPlacement = true;
    @Nullable
    private BiConsumer<Player, BlockPos> onBlockedInteract;

    private boolean disableBlockItem = true;
    private boolean disableRecipes = true;
    private boolean hideFromCreativeTabs = true;
    private boolean hideFromRecipeViewers = true;
    private boolean blockPickup = false;
    private boolean purgeFromPlayers = false;
    private boolean stripFromLootTables = true;
    private boolean stripFromTrades = true;

    private boolean unregister = false;
    @Nullable
    private ResourceLocation unregisterTarget;

    private boolean suppressBlockEntity = false;
    private boolean unregisterBlockEntity = false;
    @Nullable
    private ResourceLocation customBlockEntityId;

    @Nullable
    private Function<ResourceLocation, ResourceLocation> worldTargetResolver;

    private final List<String> extraRecipes = new ArrayList<>();
    private final List<String> extraCategories = new ArrayList<>();
    private final List<String> extraAdvancements = new ArrayList<>();

    BlockSuppressionBuilder(
            @Nullable ResourceLocation exactId,
            Predicate<ResourceLocation> idMatcher,
            BooleanSupplier condition,
            @Nullable String requiredMod
    ) {
        this.exactId = exactId;
        this.idMatcher = Objects.requireNonNull(idMatcher, "idMatcher");
        this.condition = Objects.requireNonNull(condition, "condition");
        this.requiredMod = requiredMod;
    }

    public BlockSuppressionBuilder when(BooleanSupplier extraCondition) {
        Objects.requireNonNull(extraCondition, "extraCondition");
        BooleanSupplier prev = this.condition;
        this.condition = () -> prev.getAsBoolean() && extraCondition.getAsBoolean();
        return this;
    }

    public BlockSuppressionBuilder matchingState(Predicate<BlockState> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        this.stateMatcher = this.stateMatcher == null ? predicate : this.stateMatcher.and(predicate);
        return this;
    }

    public BlockSuppressionBuilder disableTicker(boolean disable) {
        this.disableTicker = disable;
        return this;
    }

    public BlockSuppressionBuilder keepTicker() {
        this.disableTicker = false;
        return this;
    }

    public BlockSuppressionBuilder disableGameEvents(boolean disable) {
        this.disableGameEvents = disable;
        return this;
    }

    public BlockSuppressionBuilder blockInteraction(boolean block) {
        this.blockInteraction = block;
        return this;
    }

    public BlockSuppressionBuilder allowInteraction() {
        this.blockInteraction = false;
        return this;
    }

    public BlockSuppressionBuilder blockPlacement(boolean block) {
        this.blockPlacement = block;
        return this;
    }

    public BlockSuppressionBuilder allowPlacement() {
        this.blockPlacement = false;
        return this;
    }

    public BlockSuppressionBuilder onBlockedInteract(BiConsumer<Player, BlockPos> callback) {
        Objects.requireNonNull(callback, "callback");
        this.onBlockedInteract = this.onBlockedInteract == null ? callback : this.onBlockedInteract.andThen(callback);
        return this;
    }

    public BlockSuppressionBuilder disableBlockItem(boolean disable) {
        this.disableBlockItem = disable;
        return this;
    }

    public BlockSuppressionBuilder keepBlockItem() {
        this.disableBlockItem = false;
        return this;
    }

    public BlockSuppressionBuilder disableRecipes(boolean disable) {
        this.disableRecipes = disable;
        return this;
    }

    public BlockSuppressionBuilder keepRecipes() {
        this.disableRecipes = false;
        return this;
    }

    public BlockSuppressionBuilder hideFromCreativeTabs(boolean hide) {
        this.hideFromCreativeTabs = hide;
        return this;
    }

    public BlockSuppressionBuilder hideFromRecipeViewers(boolean hide) {
        this.hideFromRecipeViewers = hide;
        return this;
    }

    public BlockSuppressionBuilder hideEverywhere() {
        this.hideFromCreativeTabs = true;
        this.hideFromRecipeViewers = true;
        return this;
    }

    public BlockSuppressionBuilder blockPickup(boolean block) {
        this.blockPickup = block;
        return this;
    }

    public BlockSuppressionBuilder purgeFromPlayers() {
        return purgeFromPlayers(true);
    }

    public BlockSuppressionBuilder purgeFromPlayers(boolean purge) {
        this.purgeFromPlayers = purge;
        if (purge) {
            this.blockPickup = true;
        }
        return this;
    }

    public BlockSuppressionBuilder stripFromLootTables(boolean strip) {
        this.stripFromLootTables = strip;
        return this;
    }

    public BlockSuppressionBuilder keepInLootTables() {
        this.stripFromLootTables = false;
        return this;
    }

    public BlockSuppressionBuilder stripFromTrades(boolean strip) {
        this.stripFromTrades = strip;
        return this;
    }

    public BlockSuppressionBuilder keepInTrades() {
        this.stripFromTrades = false;
        return this;
    }

    public BlockSuppressionBuilder replaceInWorld(String targetBlockId) {
        ResourceLocation parsed = ResourceLocation.tryParse(Objects.requireNonNull(targetBlockId, "targetBlockId"));
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid replacement block ResourceLocation: " + targetBlockId);
        }
        return replaceInWorld(parsed);
    }

    public BlockSuppressionBuilder replaceInWorld(ResourceLocation targetBlockId) {
        Objects.requireNonNull(targetBlockId, "targetBlockId");
        this.worldTargetResolver = ignored -> targetBlockId;
        return this;
    }

    public BlockSuppressionBuilder replaceInWorld(Supplier<ResourceLocation> targetSupplier) {
        Objects.requireNonNull(targetSupplier, "targetSupplier");
        this.worldTargetResolver = ignored -> targetSupplier.get();
        return this;
    }

    public BlockSuppressionBuilder replaceInWorld(Function<ResourceLocation, ResourceLocation> targetResolver) {
        this.worldTargetResolver = Objects.requireNonNull(targetResolver, "targetResolver");
        return this;
    }

    public BlockSuppressionBuilder disableBlockEntity() {
        this.suppressBlockEntity = true;
        return this;
    }

    public BlockSuppressionBuilder disableBlockEntity(String beTypeId) {
        ResourceLocation parsed = ResourceLocation.tryParse(Objects.requireNonNull(beTypeId, "beTypeId"));
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid BlockEntityType ResourceLocation: " + beTypeId);
        }
        return disableBlockEntity(parsed);
    }

    public BlockSuppressionBuilder disableBlockEntity(ResourceLocation beTypeId) {
        this.suppressBlockEntity = true;
        this.customBlockEntityId = Objects.requireNonNull(beTypeId, "beTypeId");
        return this;
    }

    /**
     * Cancels registration for exact block IDs and aliases to {@code minecraft:air}.
     */
    public BlockSuppressionBuilder unregister() {
        this.unregister = true;
        return this;
    }

    public BlockSuppressionBuilder unregisterTo(String targetBlockId) {
        ResourceLocation parsed = ResourceLocation.tryParse(Objects.requireNonNull(targetBlockId, "targetBlockId"));
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid target block ResourceLocation: " + targetBlockId);
        }
        return unregisterTo(parsed);
    }

    public BlockSuppressionBuilder unregisterTo(ResourceLocation targetBlockId) {
        this.unregister = true;
        this.unregisterTarget = Objects.requireNonNull(targetBlockId, "targetBlockId");
        return this;
    }

    /**
     * Unregisters the paired BlockEntityType and aliases it to {@code ringolib:disabled_block_entity}.
     */
    public BlockSuppressionBuilder unregisterBlockEntity() {
        this.suppressBlockEntity = true;
        this.unregisterBlockEntity = true;
        return this;
    }

    public BlockSuppressionBuilder unregisterBlockEntity(String beTypeId) {
        disableBlockEntity(beTypeId);
        this.unregisterBlockEntity = true;
        return this;
    }

    public BlockSuppressionBuilder unregisterBlockEntity(ResourceLocation beTypeId) {
        disableBlockEntity(beTypeId);
        this.unregisterBlockEntity = true;
        return this;
    }

    public BlockSuppressionBuilder alsoDisableRecipes(String... recipeIdsOrGlobs) {
        if (recipeIdsOrGlobs != null) {
            Collections.addAll(this.extraRecipes, recipeIdsOrGlobs);
        }
        return this;
    }

    public BlockSuppressionBuilder alsoDisableCategory(String... categoryIdsOrGlobs) {
        if (categoryIdsOrGlobs != null) {
            Collections.addAll(this.extraCategories, categoryIdsOrGlobs);
        }
        return this;
    }

    public BlockSuppressionBuilder alsoDisableAdvancements(String... advancementIdsOrGlobs) {
        if (advancementIdsOrGlobs != null) {
            Collections.addAll(this.extraAdvancements, advancementIdsOrGlobs);
        }
        return this;
    }

    void apply() {
        ContentSuppressionRegistry.addBlockRule(
                exactId,
                new ContentSuppressionRegistry.BlockRule(
                        idMatcher,
                        stateMatcher,
                        condition,
                        disableTicker,
                        disableGameEvents,
                        blockInteraction,
                        blockPlacement,
                        onBlockedInteract
                )
        );

        if (disableBlockItem) {
            ContentSuppressionRegistry.addItemRule(
                    exactId,
                    new ContentSuppressionRegistry.ItemRule(
                            idMatcher,
                            null,
                            condition,
                            hideFromCreativeTabs,
                            hideFromRecipeViewers,
                            blockInteraction,
                            blockPlacement,
                            blockPickup,
                            purgeFromPlayers,
                            disableRecipes,
                            stripFromLootTables,
                            stripFromTrades,
                            null
                    )
            );
        }

        if (worldTargetResolver != null) {
            ContentSuppressionRegistry.addWorldBlockReplacement(
                    exactId,
                    new ContentSuppressionRegistry.WorldBlockReplacementRule(
                            idMatcher,
                            worldTargetResolver,
                            condition
                    )
            );
        }

        if (suppressBlockEntity) {
            ResourceLocation beExactId = customBlockEntityId != null ? customBlockEntityId : exactId;
            Predicate<ResourceLocation> beMatcher = customBlockEntityId != null
                    ? customBlockEntityId::equals
                    : idMatcher;
            ContentSuppressionRegistry.addBlockEntityRule(
                    beExactId,
                    new ContentSuppressionRegistry.BlockEntityRule(
                            beMatcher,
                            stateMatcher,
                            condition,
                            disableTicker,
                            disableGameEvents
                    )
            );
            if (unregisterBlockEntity) {
                if (beExactId == null) {
                    LOGGER.warn("RingoLib: .unregisterBlockEntity() requires an exact ID; applying ticker suppression only.");
                } else {
                    RegistryRedirectManager.registerRedirect(
                            Registries.BLOCK_ENTITY_TYPE,
                            beExactId,
                            RingoLibRegistry.DISABLED_BLOCK_ENTITY_ID,
                            requiredMod,
                            condition,
                            "Suppressed BlockEntityType via RingoContentAPI"
                    );
                }
            }
        }

        for (String recipePattern : extraRecipes) {
            RecipeFilterManager.disableRecipes(condition, GlobMatcher.toPredicate(recipePattern));
        }
        for (String categoryPattern : extraCategories) {
            Predicate<ResourceLocation> catMatcher = GlobMatcher.toPredicate(categoryPattern);
            ResourceLocation exactCat = GlobMatcher.isGlob(categoryPattern)
                    ? null
                    : ResourceLocation.tryParse(categoryPattern.trim());
            ContentSuppressionRegistry.addRecipeCategoryRule(
                    exactCat,
                    new ContentSuppressionRegistry.RecipeCategoryRule(catMatcher, condition)
            );
        }
        for (String advPattern : extraAdvancements) {
            Predicate<ResourceLocation> advMatcher = GlobMatcher.toPredicate(advPattern);
            ResourceLocation exactAdv = GlobMatcher.isGlob(advPattern)
                    ? null
                    : ResourceLocation.tryParse(advPattern.trim());
            ContentSuppressionRegistry.addAdvancementRule(
                    exactAdv,
                    new ContentSuppressionRegistry.AdvancementRule(advMatcher, null, condition)
            );
        }

        if (unregister) {
            if (exactId == null) {
                LOGGER.warn("RingoLib: .unregister() on block rules requires an exact block ID; applying runtime suppression only.");
                return;
            }
            ResourceLocation target = unregisterTarget != null ? unregisterTarget : AIR_ID;
            RegistryRedirectManager.registerRedirect(
                    Registries.BLOCK,
                    exactId,
                    target,
                    requiredMod,
                    condition,
                    "Suppressed block via RingoContentAPI"
            );
            if (disableBlockItem) {
                RegistryRedirectManager.registerRedirect(
                        Registries.ITEM,
                        exactId,
                        target,
                        requiredMod,
                        condition,
                        "Suppressed block item via RingoContentAPI"
                );
                ContentSuppressionRegistry.markItemUnregistered();
            }
        }
    }
}
