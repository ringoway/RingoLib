package com.ringoway.ringolib.mixin.client.emi;

import com.ringoway.ringolib.client.viewer.RecipeViewerReflect;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.emi.emi.runtime.EmiHidden", remap = false)
public abstract class EmiHiddenMixin {

    @Inject(
            method = "isDisabled(Ldev/emi/emi/api/stack/EmiIngredient;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void ringolib$markSuppressedIngredientDisabled(
            @Coerce Object ingredient,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (ContentSuppressionRegistry.hasViewerItemOrFluidRules()
                && RecipeViewerReflect.isEmiIngredientSuppressed(ingredient)) {
            cir.setReturnValue(true);
        }
    }
}
