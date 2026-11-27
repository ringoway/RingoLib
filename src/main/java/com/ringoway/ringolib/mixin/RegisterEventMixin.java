package com.ringoway.ringolib.mixin;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(RegisterEvent.class)
public abstract class RegisterEventMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(
            method = "register(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T> void ringolib$cancelDeferredRegistration(
            ResourceKey<? extends Registry<T>> registryKey,
            ResourceLocation name,
            Supplier<T> valueSupplier,
            CallbackInfo ci
    ) {
        com.ringoway.ringolib.RingoLib.ensureSuppressionsPosted();
        if (RegistryRedirectManager.shouldCancel(registryKey, name)) {
            LOGGER.info("RingoLib: Intercepted and cancelled DeferredRegister for [{}] {}", registryKey.location(), name);
            ci.cancel();
        }
    }
}
