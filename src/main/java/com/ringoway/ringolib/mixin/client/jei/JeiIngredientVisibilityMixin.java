package com.ringoway.ringolib.mixin.client.jei;

import com.ringoway.ringolib.client.viewer.RecipeViewerReflect;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.library.ingredients.IngredientVisibility", remap = false)
public abstract class JeiIngredientVisibilityMixin {

    @Inject(
            method = "isIngredientVisible(Lmezz/jei/api/ingredients/ITypedIngredient;Lmezz/jei/api/ingredients/IIngredientHelper;Lmezz/jei/api/ingredients/subtypes/UidContext;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$hideSuppressedIngredient(
            @Coerce Object typedIngredient,
            @Coerce Object ingredientHelper,
            @Coerce Object uidContext,
            CallbackInfoReturnable<Boolean> cir
    ) {
        RecipeViewerReflect.markJeiHooked();
        if (!ContentSuppressionRegistry.hasViewerItemOrFluidRules()) {
            return;
        }
        Object raw = RecipeViewerReflect.getJeiTypedIngredientValue(typedIngredient);
        if (RecipeViewerReflect.isSuppressedRawIngredient(raw)) {
            cir.setReturnValue(false);
        }
    }
}
