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
@Mixin(targets = "mezz.jei.library.ingredients.IngredientManager", remap = false)
public abstract class JeiIngredientManagerMixin {

    @ModifyVariable(
            method = "addIngredientsAtRuntime(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private Collection<?> ringolib$filterRuntimeAddedIngredients(Collection<?> ingredients) {
        RecipeViewerReflect.markJeiHooked();
        return RecipeViewerReflect.filterSuppressed(ingredients);
    }

    @Inject(
            method = "addIngredientsAtRuntime(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipEmptyRuntimeIngredients(
            @Coerce Object ingredientType,
            Collection<?> ingredients,
            CallbackInfo ci
    ) {
        RecipeViewerReflect.markJeiHooked();
        if (RecipeViewerReflect.areAllSuppressed(ingredients)) {
            ci.cancel();
        }
    }

    @ModifyVariable(
            method = "removeIngredientsAtRuntime(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false,
            require = 0
    )
    private Collection<?> ringolib$filterRuntimeRemovedIngredients(Collection<?> ingredients) {
        RecipeViewerReflect.markJeiHooked();
        return RecipeViewerReflect.filterSuppressed(ingredients);
    }

    @Inject(
            method = "removeIngredientsAtRuntime(Lmezz/jei/api/ingredients/IIngredientType;Ljava/util/Collection;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipEmptyRuntimeRemovedIngredients(
            @Coerce Object ingredientType,
            Collection<?> ingredients,
            CallbackInfo ci
    ) {
        RecipeViewerReflect.markJeiHooked();
        if (RecipeViewerReflect.areAllSuppressed(ingredients)) {
            ci.cancel();
        }
    }
}
