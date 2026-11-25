package com.ringoway.ringolib.mixin;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import com.ringoway.ringolib.compat.ArchitecturyDataAccess;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.Iterator;
import java.util.Map;

@Pseudo
@Mixin(targets = "dev.architectury.registry.registries.forge.RegistrarManagerImpl$Data", remap = false)
public abstract class ArchitecturyDataMixin implements ArchitecturyDataAccess {
    @Unique
    private static final Logger ringolib$LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    private Map<ResourceLocation, ?> objects;

    @Override
    public void ringolib$pruneRedirected(ResourceKey<? extends Registry<?>> registryKey) {
        Iterator<ResourceLocation> it = this.objects.keySet().iterator();
        while (it.hasNext()) {
            ResourceLocation id = it.next();
            if (RegistryRedirectManager.shouldCancel(registryKey, id)) {
                it.remove();
                ringolib$LOGGER.info("RingoLib: Intercepted and cancelled Architectury registration for [{}] {}",
                        registryKey.location(), id);
            }
        }
    }
}
