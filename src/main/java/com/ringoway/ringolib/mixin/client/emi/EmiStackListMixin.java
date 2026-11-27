package com.ringoway.ringolib.mixin.client.emi;

import com.ringoway.ringolib.client.viewer.RecipeViewerReflect;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

@Pseudo
@Mixin(targets = "dev.emi.emi.registry.EmiStackList", remap = false)
public abstract class EmiStackListMixin {

    @Shadow
    public static List<Predicate<Object>> invalidators;

    @Unique
    private static final Predicate<Object> RINGOLIB_STACK_INVALIDATOR = RecipeViewerReflect::isEmiStackSuppressed;

    @Inject(method = "bake()V", at = @At("HEAD"), remap = false, require = 0)
    private static void ringolib$addStackInvalidator(CallbackInfo ci) {
        RecipeViewerReflect.markEmiHooked();
        if (ContentSuppressionRegistry.hasViewerItemOrFluidRules()
                && invalidators != null
                && !invalidators.contains(RINGOLIB_STACK_INVALIDATOR)) {
            invalidators.add(RINGOLIB_STACK_INVALIDATOR);
        }
    }
}
