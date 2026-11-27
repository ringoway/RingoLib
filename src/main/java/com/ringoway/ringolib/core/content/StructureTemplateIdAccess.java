package com.ringoway.ringolib.core.content;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Duck interface implemented via Mixin on {@code StructureTemplate} to store its template ID.
 */
public interface StructureTemplateIdAccess {

    @Nullable
    ResourceLocation ringolib$getTemplateId();

    void ringolib$setTemplateId(@Nullable ResourceLocation id);
}
