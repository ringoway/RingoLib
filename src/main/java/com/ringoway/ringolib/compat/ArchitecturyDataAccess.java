package com.ringoway.ringolib.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Duck interface implemented via Mixin on Architectury's {@code RegistrarManagerImpl$Data}.
 */
public interface ArchitecturyDataAccess {
    void ringolib$pruneRedirected(ResourceKey<? extends Registry<?>> registryKey);
}
