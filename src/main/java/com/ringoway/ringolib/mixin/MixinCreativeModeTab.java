package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeTab.class)
public abstract class MixinCreativeModeTab {

    @Inject(method = "shouldDisplay()Z", at = @At("HEAD"), cancellable = true)
    private void ringolib$hideSuppressedCreativeTab(CallbackInfoReturnable<Boolean> cir) {
        if (!ContentSuppressionRegistry.hasCreativeTabRules()) {
            return;
        }
        ResourceLocation tabId = BuiltInRegistries.CREATIVE_MODE_TAB.getKey((CreativeModeTab) (Object) this);
        if (tabId != null && ContentSuppressionRegistry.isCreativeTabDisabled(tabId)) {
            cir.setReturnValue(false);
        }
    }
}
