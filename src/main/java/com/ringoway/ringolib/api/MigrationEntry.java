package com.ringoway.ringolib.api;

import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

public record MigrationEntry(
        String oldId,
        String targetId,
        @Nullable String requiredTargetMod,
        @Nullable String description
) {
    public boolean isTargetAvailable() {
        if (requiredTargetMod == null || requiredTargetMod.isEmpty()) {
            return true;
        }
        return ModList.get().isLoaded(requiredTargetMod);
    }
}
