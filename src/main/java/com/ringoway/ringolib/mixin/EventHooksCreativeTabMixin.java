package com.ringoway.ringolib.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.event.EventHooks;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EventHooks.class, remap = false)
public abstract class EventHooksCreativeTabMixin {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Inject(
            method = "lambda$onCreativeModeTabBuildContents$0",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private static void ringolib$ignoreInvalidCreativeTabStacks(
            InsertableLinkedOpenCustomHashSet parentEntries,
            InsertableLinkedOpenCustomHashSet searchEntries,
            ItemStack stack,
            CreativeModeTab.TabVisibility visibility,
            CallbackInfo ci
    ) {
        if (stack == null || stack.isEmpty()) {
            ci.cancel();
            return;
        }

        if (stack.getCount() != 1) {
            LOGGER.debug("RingoLib: Normalized creative tab stack count from {} to 1 for {}", stack.getCount(), stack);
            stack.setCount(1);
        }
    }
}
