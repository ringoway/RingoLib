package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.api.RegistryRedirectManager;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Pseudo
@Mixin(targets = "dev.architectury.registry.registries.forge.RegistrarManagerImpl$RegistrarImpl$1", remap = false)
public abstract class ArchitecturyRegistrySupplierMixin {

    @Shadow
    public abstract ResourceLocation getRegistryId();

    @Shadow
    public abstract ResourceLocation getId();

    @Inject(method = "get()Ljava/lang/Object;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ringolib$resolveRedirectedGet(CallbackInfoReturnable<Object> cir) {
        ResourceLocation targetId = ringolib$getTargetId();
        Registry<?> reg = ringolib$getRegistryForTarget(targetId);
        if (reg != null) {
            Object target = reg.get(targetId);
            if (target != null) {
                cir.setReturnValue(target);
            }
        }
    }

    @Inject(method = "isPresent()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ringolib$resolveRedirectedIsPresent(CallbackInfoReturnable<Boolean> cir) {
        ResourceLocation targetId = ringolib$getTargetId();
        if (ringolib$getRegistryForTarget(targetId) != null) {
            cir.setReturnValue(true);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "getHolder()Lnet/minecraft/core/Holder;", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void ringolib$resolveRedirectedHolder(CallbackInfoReturnable<Holder<?>> cir) {
        ResourceLocation targetId = ringolib$getTargetId();
        Registry reg = ringolib$getRegistryForTarget(targetId);
        if (reg != null) {
            Optional<Holder.Reference<?>> holder = reg.getHolder(ResourceKey.create(reg.key(), targetId));
            if (holder.isPresent()) {
                cir.setReturnValue(holder.get());
            }
        }
    }

    @Unique
    @Nullable
    private ResourceLocation ringolib$getTargetId() {
        ResourceKey<? extends Registry<?>> regKey = ResourceKey.createRegistryKey(getRegistryId());
        return RegistryRedirectManager.getRedirectTarget(regKey, getId());
    }

    @Unique
    @Nullable
    private Registry<?> ringolib$getRegistryForTarget(@Nullable ResourceLocation targetId) {
        if (targetId == null) {
            return null;
        }
        Registry<?> reg = BuiltInRegistries.REGISTRY.get(getRegistryId());
        return reg != null && reg.containsKey(targetId) ? reg : null;
    }
}
