package com.ringoway.ringolib.compat;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

/**
 * Duck interface implemented by Architectury's {@code RegistrarManagerImpl$Data} mixin
 * to prune redirected entries before their registration suppliers execute.
 */
public interface ArchitecturyDataAccess {
    void ringolib$pruneRedirected(ResourceKey<? extends Registry<?>> registryKey);
}
