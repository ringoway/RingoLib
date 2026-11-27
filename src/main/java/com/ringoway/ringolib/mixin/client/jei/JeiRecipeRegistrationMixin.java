package com.ringoway.ringolib.mixin.client.jei;

import com.ringoway.ringolib.client.viewer.RecipeViewerReflect;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.registration.RecipeRegistration", remap = false)
public abstract class JeiRecipeRegistrationMixin {

    @Inject(
            method = "addRecipes(Lmezz/jei/api/recipe/RecipeType;Ljava/util/List;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipSuppressedCategoryRecipes(
            @Coerce Object recipeType,
            List<?> recipes,
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

    @ModifyVariable(
            method = "addIngredientInfo(Ljava/util/List;Lmezz/jei/api/ingredients/IIngredientType;[Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private List<?> ringolib$filterSuppressedInfoIngredients(List<?> ingredients) {
        return RecipeViewerReflect.filterSuppressedList(ingredients);
    }

    @Inject(
            method = "addIngredientInfo(Ljava/util/List;Lmezz/jei/api/ingredients/IIngredientType;[Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipEmptySuppressedInfo(
            List<?> ingredients,
            @Coerce Object ingredientType,
            Component[] descriptionComponents,
            CallbackInfo ci
    ) {
        if (RecipeViewerReflect.areAllSuppressed(ingredients)) {
            ci.cancel();
        }
    }
}
