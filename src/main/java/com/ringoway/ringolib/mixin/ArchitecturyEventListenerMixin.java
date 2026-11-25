package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.compat.ArchitecturyDataAccess;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.architectury.registry.registries.forge.RegistrarManagerImpl$RegistryProviderImpl$EventListener", remap = false)
public abstract class ArchitecturyEventListenerMixin {

    @Inject(method = "registerFor", at = @At("HEAD"), remap = false, require = 0)
    private void ringolib$pruneArchitecturyObjects(
            RegisterEvent event,
            ResourceKey<? extends Registry<?>> key,
            @Coerce Object data,
            CallbackInfo ci
    ) {
        if (data instanceof ArchitecturyDataAccess access) {
            access.ringolib$pruneRedirected(key);
        }
    }
}
