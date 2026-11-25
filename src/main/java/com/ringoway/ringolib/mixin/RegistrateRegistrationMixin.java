package com.ringoway.ringolib.mixin;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tterrag.registrate.AbstractRegistrate$Registration", remap = false)
public abstract class RegistrateRegistrationMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    private ResourceLocation name;

    @Shadow
    @Final
    private ResourceKey<? extends Registry<?>> type;

    @Inject(method = "register", at = @At("HEAD"), cancellable = true)
    private void ringolib$cancelRegistrateRegistration(RegisterEvent event, CallbackInfo ci) {
        if (RegistryRedirectManager.shouldCancel(this.type, this.name)) {
            LOGGER.info("RingoLib: Intercepted and cancelled Registrate registration for [{}] {}", this.type.location(), this.name);
            ci.cancel();
        }
    }
}
