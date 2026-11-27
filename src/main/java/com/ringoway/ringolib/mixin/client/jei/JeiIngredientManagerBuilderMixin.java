package com.ringoway.ringolib.mixin.client.jei;

import com.ringoway.ringolib.client.viewer.RecipeViewerReflect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.registration.IngredientManagerBuilder", remap = false)
public abstract class JeiIngredientManagerBuilderMixin {

    @ModifyVariable(
            method = "addExtraIngredients(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private Collection<?> ringolib$filterExtraIngredients(Collection<?> extraIngredients) {
        return RecipeViewerReflect.filterSuppressed(extraIngredients);
    }

    @Inject(
            method = "addExtraIngredients(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipEmptyExtraIngredients(
            @Coerce Object ingredientType,
            Collection<?> extraIngredients,
            CallbackInfo ci
    ) {
        if (RecipeViewerReflect.areAllSuppressed(extraIngredients)) {
            ci.cancel();
        }
    }
}
