package com.ringoway.ringolib.api.content;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RecipeFilterManager;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import com.ringoway.ringolib.core.content.GlobMatcher;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class ItemSuppressionBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation AIR_ID = ResourceLocation.withDefaultNamespace("air");

    @Nullable
    private final ResourceLocation exactId;
    private final Predicate<ResourceLocation> idMatcher;
    @Nullable
    private final Predicate<ItemStack> initialStackMatcher;
    @Nullable
    private final String requiredMod;
    private BooleanSupplier condition;

    @Nullable
    private Predicate<ItemStack> stackMatcher;
    private boolean disableRecipes = true;
    private boolean hideFromCreativeTabs = true;
    private boolean hideFromRecipeViewers = true;
    private boolean blockUsage = true;
    private boolean blockCrafting = true;
    private boolean blockPickup = false;
    private boolean purgeFromPlayers = false;
    private boolean stripFromLootTables = true;
    private boolean stripFromTrades = true;
    private boolean unregister = false;
    @Nullable
    private ResourceLocation unregisterTarget;
    @Nullable
    private Consumer<Player> onBlockedUse;
    private final List<String> extraRecipes = new ArrayList<>();
    private final List<String> extraAdvancements = new ArrayList<>();

    ItemSuppressionBuilder(
            @Nullable ResourceLocation exactId,
            Predicate<ResourceLocation> idMatcher,
            @Nullable Predicate<ItemStack> stackMatcher,
            BooleanSupplier condition,
            @Nullable String requiredMod
    ) {
        this.exactId = exactId;
        this.idMatcher = Objects.requireNonNull(idMatcher, "idMatcher");
        this.initialStackMatcher = stackMatcher;
        this.stackMatcher = stackMatcher;
        this.condition = Objects.requireNonNull(condition, "condition");
        this.requiredMod = requiredMod;
    }

    public ItemSuppressionBuilder when(BooleanSupplier extraCondition) {
        Objects.requireNonNull(extraCondition, "extraCondition");
        BooleanSupplier prev = this.condition;
        this.condition = () -> prev.getAsBoolean() && extraCondition.getAsBoolean();
        return this;
    }

    public ItemSuppressionBuilder matchingStack(Predicate<ItemStack> predicate) {
        Objects.requireNonNull(predicate, "predicate");
        this.stackMatcher = this.stackMatcher == null ? predicate : this.stackMatcher.and(predicate);
        return this;
    }

    public ItemSuppressionBuilder disableRecipes(boolean disable) {
        this.disableRecipes = disable;
        return this;
    }

    public ItemSuppressionBuilder keepRecipes() {
        this.disableRecipes = false;
        return this;
    }

    public ItemSuppressionBuilder hideFromCreativeTabs(boolean hide) {
        this.hideFromCreativeTabs = hide;
        return this;
    }

    public ItemSuppressionBuilder hideFromRecipeViewers(boolean hide) {
        this.hideFromRecipeViewers = hide;
        return this;
    }

    public ItemSuppressionBuilder hideEverywhere() {
        this.hideFromCreativeTabs = true;
        this.hideFromRecipeViewers = true;
        return this;
    }

    public ItemSuppressionBuilder blockUsage(boolean block) {
        this.blockUsage = block;
        return this;
    }

    public ItemSuppressionBuilder allowUsage() {
        this.blockUsage = false;
        return this;
    }

    public ItemSuppressionBuilder blockCrafting(boolean block) {
        this.blockCrafting = block;
        return this;
    }

    public ItemSuppressionBuilder allowCrafting() {
        this.blockCrafting = false;
        return this;
    }

    public ItemSuppressionBuilder blockPickup(boolean block) {
        this.blockPickup = block;
        return this;
    }

    public ItemSuppressionBuilder purgeFromPlayers() {
        return purgeFromPlayers(true);
    }

    public ItemSuppressionBuilder purgeFromPlayers(boolean purge) {
        this.purgeFromPlayers = purge;
        if (purge) {
            this.blockPickup = true;
            this.blockCrafting = true;
        }
        return this;
    }

    public ItemSuppressionBuilder stripFromLootTables(boolean strip) {
        this.stripFromLootTables = strip;
        return this;
    }

    public ItemSuppressionBuilder keepInLootTables() {
        this.stripFromLootTables = false;
        return this;
    }

    public ItemSuppressionBuilder stripFromTrades(boolean strip) {
        this.stripFromTrades = strip;
        return this;
    }

    public ItemSuppressionBuilder keepInTrades() {
        this.stripFromTrades = false;
        return this;
    }

    public ItemSuppressionBuilder onBlockedUse(Consumer<Player> callback) {
        Objects.requireNonNull(callback, "callback");
        this.onBlockedUse = this.onBlockedUse == null ? callback : this.onBlockedUse.andThen(callback);
        return this;
    }

    /**
     * Cancels registration for exact item IDs and aliases to {@code minecraft:air}.
     */
    public ItemSuppressionBuilder unregister() {
        this.unregister = true;
        return this;
    }

    public ItemSuppressionBuilder unregisterTo(String targetItemId) {
        ResourceLocation target = ResourceLocation.tryParse(Objects.requireNonNull(targetItemId, "targetItemId"));
        if (target == null) {
            throw new IllegalArgumentException("Invalid target item ResourceLocation: " + targetItemId);
        }
        return unregisterTo(target);
    }

    public ItemSuppressionBuilder unregisterTo(ResourceLocation targetItemId) {
        this.unregister = true;
        this.unregisterTarget = Objects.requireNonNull(targetItemId, "targetItemId");
        return this;
    }

    public ItemSuppressionBuilder alsoDisableRecipes(String... recipeIdsOrGlobs) {
        if (recipeIdsOrGlobs != null) {
            Collections.addAll(this.extraRecipes, recipeIdsOrGlobs);
        }
        return this;
    }

    public ItemSuppressionBuilder alsoDisableAdvancements(String... advancementIdsOrGlobs) {
        if (advancementIdsOrGlobs != null) {
            Collections.addAll(this.extraAdvancements, advancementIdsOrGlobs);
        }
        return this;
    }

    void apply() {
        ContentSuppressionRegistry.addItemRule(
                exactId,
                new ContentSuppressionRegistry.ItemRule(
                        idMatcher,
                        stackMatcher,
                        condition,
                        hideFromCreativeTabs,
                        hideFromRecipeViewers,
                        blockUsage,
                        blockCrafting,
                        blockPickup,
                        purgeFromPlayers,
                        disableRecipes,
                        stripFromLootTables,
                        stripFromTrades,
                        onBlockedUse
                )
        );

        for (String recipePattern : extraRecipes) {
            RecipeFilterManager.disableRecipes(condition, GlobMatcher.toPredicate(recipePattern));
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
            if (exactId == null || initialStackMatcher != null) {
                LOGGER.warn("RingoLib: .unregister() on item rules requires an exact item ID without a stack predicate; applying runtime suppression only.");
                return;
            }
            ResourceLocation target = unregisterTarget != null ? unregisterTarget : AIR_ID;
            RegistryRedirectManager.registerRedirect(
                    Registries.ITEM,
                    exactId,
                    target,
                    requiredMod,
                    condition,
                    "Suppressed item via RingoContentAPI"
            );
            ContentSuppressionRegistry.markItemUnregistered();
        }
    }
}
