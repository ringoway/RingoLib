package com.ringoway.ringolib.mixin.client.emi;

import com.ringoway.ringolib.client.viewer.RecipeViewerReflect;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiRecipes", remap = false)
public abstract class EmiRecipesMixin {

    @Shadow
    public static List<Object> categories;

    @Shadow
    private static Map<Object, List<Object>> workstations;

    @Shadow
    public static List<Predicate<Object>> invalidators;

    @Unique
    private static final Predicate<Object> RINGOLIB_RECIPE_INVALIDATOR = RecipeViewerReflect::isEmiRecipeSuppressed;

    @Inject(
            method = "addCategory(Ldev/emi/emi/api/recipe/EmiRecipeCategory;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void ringolib$skipSuppressedCategory(@Coerce Object category, CallbackInfo ci) {
        if (!ContentSuppressionRegistry.hasCategoryRules()) {
            return;
        }
        ResourceLocation catId = RecipeViewerReflect.getEmiCategoryId(category);
        if (catId != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(catId)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "addWorkstation(Ldev/emi/emi/api/recipe/EmiRecipeCategory;Ldev/emi/emi/api/stack/EmiIngredient;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void ringolib$skipSuppressedWorkstation(
            @Coerce Object category,
            @Coerce Object workstation,
            CallbackInfo ci
    ) {
        if (ContentSuppressionRegistry.hasCategoryRules()) {
            ResourceLocation catId = RecipeViewerReflect.getEmiCategoryId(category);
            if (catId != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(catId)) {
                ci.cancel();
                return;
            }
        }
        if (RecipeViewerReflect.isEmiIngredientSuppressed(workstation)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "addRecipe(Ldev/emi/emi/api/recipe/EmiRecipe;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void ringolib$skipSuppressedRecipe(@Coerce Object recipe, CallbackInfo ci) {
        if (RecipeViewerReflect.isEmiRecipeSuppressed(recipe)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "bake()V",
            at = @At("HEAD"),
            remap = false,
            require = 0
    )
    private static void ringolib$pruneSuppressedBeforeBake(CallbackInfo ci) {
        RecipeViewerReflect.markEmiHooked();
        if (ContentSuppressionRegistry.hasCategoryRules() && categories != null) {
            categories.removeIf(cat -> {
                ResourceLocation id = RecipeViewerReflect.getEmiCategoryId(cat);
                return id != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(id);
            });
        }

        if (workstations != null && (ContentSuppressionRegistry.hasCategoryRules()
                || ContentSuppressionRegistry.hasViewerItemOrFluidRules())) {
            Iterator<Map.Entry<Object, List<Object>>> it = workstations.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Object, List<Object>> entry = it.next();
                if (ContentSuppressionRegistry.hasCategoryRules()) {
                    ResourceLocation id = RecipeViewerReflect.getEmiCategoryId(entry.getKey());
                    if (id != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(id)) {
                        it.remove();
                        continue;
                    }
                }
                List<Object> wsList = entry.getValue();
                if (wsList != null && ContentSuppressionRegistry.hasViewerItemOrFluidRules()) {
                    wsList.removeIf(RecipeViewerReflect::isEmiIngredientSuppressed);
                    if (wsList.isEmpty()) {
                        it.remove();
                    }
                }
            }
        }

        if (invalidators != null && !invalidators.contains(RINGOLIB_RECIPE_INVALIDATOR)) {
            invalidators.add(RINGOLIB_RECIPE_INVALIDATOR);
        }
    }
}
