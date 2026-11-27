package com.ringoway.ringolib.mixin.client.jei;

import com.ringoway.ringolib.client.viewer.RecipeViewerReflect;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.library.load.registration.SubtypeRegistration", remap = false)
public abstract class JeiSubtypeRegistrationMixin {

    @Inject(
            method = "registerSubtypeInterpreter(Lmezz/jei/api/ingredients/IIngredientTypeWithSubtypes;Ljava/lang/Object;Lmezz/jei/api/ingredients/subtypes/ISubtypeInterpreter;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$skipSuppressedSubtypeInterpreter(
            @Coerce Object type,
            Object base,
            @Coerce Object interpreter,
            CallbackInfo ci
    ) {
        if (base == Items.AIR || RecipeViewerReflect.isSuppressedRawIngredient(base)) {
            ci.cancel();
        }
    }
}
