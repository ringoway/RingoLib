package com.ringoway.ringolib.mixin.client.jei;

import com.ringoway.ringolib.client.viewer.RecipeViewerReflect;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.RecipeManagerInternal", remap = false)
public abstract class JeiRecipeManagerInternalMixin {

    @Inject(
            method = "isRecipeTypeHidden(Lmezz/jei/api/recipe/RecipeType;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$hideSuppressedRecipeType(
            @Coerce Object recipeType,
            CallbackInfoReturnable<Boolean> cir
    ) {
        RecipeViewerReflect.markJeiHooked();
        if (!ContentSuppressionRegistry.hasCategoryRules()) {
            return;
        }
        ResourceLocation uid = RecipeViewerReflect.getJeiRecipeTypeUid(recipeType);
        if (uid != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(uid)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$ignoreDynamicAddForSuppressedCategory(
            @Coerce Object recipeType,
            List<?> recipes,
            CallbackInfo ci
    ) {
        if (!ContentSuppressionRegistry.hasCategoryRules()) {
            return;
        }
        ResourceLocation uid = RecipeViewerReflect.getJeiRecipeTypeUid(recipeType);
        if (uid != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(uid)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "hideRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/Collection;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$ignoreHideForSuppressedCategory(
            @Coerce Object recipeType,
            Collection<?> recipes,
            CallbackInfo ci
    ) {
        if (!ContentSuppressionRegistry.hasCategoryRules()) {
            return;
        }
        ResourceLocation uid = RecipeViewerReflect.getJeiRecipeTypeUid(recipeType);
        if (uid != null && ContentSuppressionRegistry.isRecipeCategoryDisabled(uid)) {
            ci.cancel();
        }
    }
}
