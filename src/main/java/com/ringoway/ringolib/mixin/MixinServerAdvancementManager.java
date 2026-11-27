package com.ringoway.ringolib.mixin;

import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.Map;

@Mixin(ServerAdvancementManager.class)
public abstract class MixinServerAdvancementManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD")
    )
    private void ringolib$filterDisabledAdvancements(
            Map<ResourceLocation, JsonElement> map,
            ResourceManager resourceManager,
            ProfilerFiller profiler,
            CallbackInfo ci
    ) {
        if (!ContentSuppressionRegistry.hasAdvancementRules()) {
            return;
        }
        int removed = 0;
        Iterator<Map.Entry<ResourceLocation, JsonElement>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<ResourceLocation, JsonElement> entry = it.next();
            if (ContentSuppressionRegistry.isAdvancementDisabled(entry.getKey(), entry.getValue())) {
                LOGGER.debug("RingoLib: Disabled advancement: {}", entry.getKey());
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOGGER.info("RingoLib: Filtered out {} disabled advancement(s)", removed);
        }
    }
}
