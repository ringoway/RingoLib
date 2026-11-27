package com.ringoway.ringolib.mixin.client.jei;

import com.ringoway.ringolib.client.viewer.RecipeViewerReflect;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.registration.RecipeCatalystRegistration", remap = false)
public abstract class JeiRecipeCatalystRegistrationMixin {

    @Inject(
            method = "addRecipeCatalyst(Lmezz/jei/api/recipe/RecipeType;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipCatalystConsumerForSuppressedCategory(
            @Coerce Object recipeType,
            Consumer<?> consumer,
            CallbackInfo ci
    ) {
        RecipeViewerReflect.markJeiHooked();
        if (!ContentSuppressionRegistry.hasCategoryRules()) {
            return;
        }
        ResourceLocation uid = RecipeViewerReflect.getJeiRecipeTypeUid(recipeType);
        if (uid != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(uid)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "addRecipeCatalyst(Lmezz/jei/api/ingredients/IIngredientType;Ljava/lang/Object;[Lmezz/jei/api/recipe/RecipeType;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipSuppressedCatalyst(
            @Coerce Object ingredientType,
            Object ingredient,
            @Coerce Object[] recipeTypes,
            CallbackInfo ci
    ) {
        RecipeViewerReflect.markJeiHooked();
        if (RecipeViewerReflect.isEmptyOrSuppressedRawIngredient(ingredient)) {
            ci.cancel();
            return;
        }
        if (ContentSuppressionRegistry.hasCategoryRules() && recipeTypes != null && recipeTypes.length > 0) {
            for (Object rt : recipeTypes) {
                ResourceLocation uid = RecipeViewerReflect.getJeiRecipeTypeUid(rt);
                if (uid == null || !ContentSuppressionRegistry.isRecipeCategoryDisabled(uid)) {
                    return;
                }
            }
            ci.cancel();
        }
    }

    @ModifyVariable(
            method = "addRecipeCatalysts(Lmezz/jei/api/recipe/RecipeType;[Lnet/minecraft/world/level/ItemLike;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private ItemLike[] ringolib$filterCatalystsItemLike(ItemLike[] ingredients) {
        return RecipeViewerReflect.filterSuppressedItemLikes(ingredients);
    }

    @Inject(
            method = "addRecipeCatalysts(Lmezz/jei/api/recipe/RecipeType;[Lnet/minecraft/world/level/ItemLike;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipSuppressedCatalystsItemLike(
            @Coerce Object recipeType,
            ItemLike[] ingredients,
            CallbackInfo ci
    ) {
        RecipeViewerReflect.markJeiHooked();
        if (ContentSuppressionRegistry.hasCategoryRules()) {
            ResourceLocation uid = RecipeViewerReflect.getJeiRecipeTypeUid(recipeType);
            if (uid != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(uid)) {
                ci.cancel();
                return;
            }
        }
        if (RecipeViewerReflect.areAllItemLikesSuppressed(ingredients)) {
            ci.cancel();
        }
    }

    @ModifyVariable(
            method = "addRecipeCatalysts(Lmezz/jei/api/recipe/RecipeType;Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/List;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private List<?> ringolib$filterCatalystsList(List<?> ingredients) {
        return RecipeViewerReflect.filterSuppressedList(ingredients);
    }

    @Inject(
            method = "addRecipeCatalysts(Lmezz/jei/api/recipe/RecipeType;Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/List;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipSuppressedCatalystsList(
            @Coerce Object recipeType,
            @Coerce Object ingredientType,
            List<?> ingredients,
            CallbackInfo ci
    ) {
        RecipeViewerReflect.markJeiHooked();
        if (ContentSuppressionRegistry.hasCategoryRules()) {
            ResourceLocation uid = RecipeViewerReflect.getJeiRecipeTypeUid(recipeType);
            if (uid != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(uid)) {
                ci.cancel();
                return;
            }
        }
        if (RecipeViewerReflect.areAllSuppressed(ingredients)) {
            ci.cancel();
        }
    }
}
