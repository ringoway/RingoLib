package com.ringoway.ringolib.api.content;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RecipeFilterManager;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import com.ringoway.ringolib.core.content.GlobMatcher;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class FluidSuppressionBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceKey<Registry<Object>> FLUID_TYPE_REGISTRY_KEY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("neoforge", "fluid_type"));
    private static final ResourceLocation EMPTY_FLUID_ID = ResourceLocation.withDefaultNamespace("empty");
    private static final ResourceLocation EMPTY_FLUID_TYPE_ID = ResourceLocation.fromNamespaceAndPath("neoforge", "empty");
    private static final ResourceLocation AIR_ID = ResourceLocation.withDefaultNamespace("air");

    @Nullable
    private final ResourceLocation exactId;
    private final Predicate<ResourceLocation> idMatcher;
    @Nullable
    private final String requiredMod;
    private BooleanSupplier condition;

    private boolean hideFromRecipeViewers = true;
    private boolean unregister = false;
    @Nullable
    private ResourceLocation unregisterTarget;
    private final List<Runnable> extraActions = new ArrayList<>();
    private final List<String> extraRecipes = new ArrayList<>();

    FluidSuppressionBuilder(
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

    public FluidSuppressionBuilder when(BooleanSupplier extraCondition) {
        Objects.requireNonNull(extraCondition, "extraCondition");
        BooleanSupplier prev = this.condition;
        this.condition = () -> prev.getAsBoolean() && extraCondition.getAsBoolean();
        return this;
    }

    public FluidSuppressionBuilder hideFromRecipeViewers(boolean hide) {
        this.hideFromRecipeViewers = hide;
        return this;
    }

    public FluidSuppressionBuilder bucket(String bucketItemId) {
        return bucket(bucketItemId, b -> {});
    }

    public FluidSuppressionBuilder bucket(String bucketItemId, Consumer<ItemSuppressionBuilder> options) {
        Objects.requireNonNull(bucketItemId, "bucketItemId");
        Objects.requireNonNull(options, "options");
        extraActions.add(() -> {
            ResourceLocation exactBucket = GlobMatcher.isGlob(bucketItemId)
                    ? null
                    : ResourceLocation.tryParse(bucketItemId.trim());
            ItemSuppressionBuilder itemBuilder = new ItemSuppressionBuilder(
                    exactBucket,
                    GlobMatcher.toPredicate(bucketItemId),
                    null,
                    condition,
                    requiredMod
            );
            if (unregister) {
                itemBuilder.unregister();
            }
            options.accept(itemBuilder);
            itemBuilder.apply();
        });
        return this;
    }

    public FluidSuppressionBuilder unregister() {
        this.unregister = true;
        return this;
    }

    public FluidSuppressionBuilder unregisterTo(String targetFluidId) {
        ResourceLocation parsed = ResourceLocation.tryParse(Objects.requireNonNull(targetFluidId, "targetFluidId"));
        if (parsed == null) {
            throw new IllegalArgumentException("Invalid target fluid ResourceLocation: " + targetFluidId);
        }
        this.unregister = true;
        this.unregisterTarget = parsed;
        return this;
    }

    public FluidSuppressionBuilder alsoDisableRecipes(String... recipeIdsOrGlobs) {
        if (recipeIdsOrGlobs != null) {
            Collections.addAll(this.extraRecipes, recipeIdsOrGlobs);
        }
        return this;
    }

    void apply() {
        ContentSuppressionRegistry.addFluidRule(
                exactId,
                new ContentSuppressionRegistry.FluidRule(idMatcher, condition, hideFromRecipeViewers)
        );
        if (exactId != null) {
            ResourceLocation flowingFrom = ResourceLocation.fromNamespaceAndPath(
                    exactId.getNamespace(), "flowing_" + exactId.getPath()
            );
            ContentSuppressionRegistry.addFluidRule(
                    flowingFrom,
                    new ContentSuppressionRegistry.FluidRule(flowingFrom::equals, condition, hideFromRecipeViewers)
            );
        }

        for (Runnable action : extraActions) {
            action.run();
        }
        for (String recipePattern : extraRecipes) {
            RecipeFilterManager.disableRecipes(condition, GlobMatcher.toPredicate(recipePattern));
        }

        if (unregister) {
            if (exactId == null) {
                LOGGER.warn("RingoLib: .unregister() on fluid rules requires an exact fluid ID; applying viewer hiding only.");
                return;
            }
            ResourceLocation fluidTarget = unregisterTarget != null ? unregisterTarget : EMPTY_FLUID_ID;
            ResourceLocation flowingFrom = ResourceLocation.fromNamespaceAndPath(
                    exactId.getNamespace(), "flowing_" + exactId.getPath()
            );
            ResourceLocation flowingTarget = unregisterTarget != null
                    ? ResourceLocation.fromNamespaceAndPath(unregisterTarget.getNamespace(), "flowing_" + unregisterTarget.getPath())
                    : EMPTY_FLUID_ID;
            ResourceLocation typeTarget = unregisterTarget != null ? unregisterTarget : EMPTY_FLUID_TYPE_ID;
            ResourceLocation blockTarget = unregisterTarget != null ? unregisterTarget : AIR_ID;

            RegistryRedirectManager.registerRedirect(Registries.FLUID, exactId, fluidTarget, requiredMod, condition, "Suppressed fluid");
            RegistryRedirectManager.registerRedirect(Registries.FLUID, flowingFrom, flowingTarget, requiredMod, condition, "Suppressed flowing fluid");
            RegistryRedirectManager.registerRedirect(FLUID_TYPE_REGISTRY_KEY, exactId, typeTarget, requiredMod, condition, "Suppressed fluid type");
            RegistryRedirectManager.registerRedirect(Registries.BLOCK, exactId, blockTarget, requiredMod, condition, "Suppressed liquid block");
            ContentSuppressionRegistry.markFluidUnregistered();
        }
    }
}
