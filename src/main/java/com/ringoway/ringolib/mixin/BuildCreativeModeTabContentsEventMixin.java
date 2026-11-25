package com.ringoway.ringolib.mixin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.InsertableLinkedOpenCustomHashSet;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guards {@link BuildCreativeModeTabContentsEvent} mutations against duplicate stacks
 * and missing anchor stacks caused by registry aliases merging multiple mods' items.
 */
@Mixin(value = BuildCreativeModeTabContentsEvent.class, remap = false)
public abstract class BuildCreativeModeTabContentsEventMixin {
    @Unique
    private static final Logger ringolib$LOGGER = LogUtils.getLogger();

    @Unique
    private static final int ringolib$MODE_APPEND = 0;
    @Unique
    private static final int ringolib$MODE_FIRST = 1;
    @Unique
    private static final int ringolib$MODE_AFTER = 2;
    @Unique
    private static final int ringolib$MODE_BEFORE = 3;

    @Shadow
    @Final
    private InsertableLinkedOpenCustomHashSet<ItemStack> parentEntries;

    @Shadow
    @Final
    private InsertableLinkedOpenCustomHashSet<ItemStack> searchEntries;

    @Inject(
            method = "accept(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/CreativeModeTab$TabVisibility;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ringolib$guardAccept(
            ItemStack newEntry,
            CreativeModeTab.TabVisibility visibility,
            CallbackInfo ci
    ) {
        if (ringolib$guardAndCancelIfNeeded(ringolib$MODE_APPEND, null, newEntry, visibility)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "insertFirst(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/CreativeModeTab$TabVisibility;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ringolib$guardInsertFirst(
            ItemStack newEntry,
            CreativeModeTab.TabVisibility visibility,
            CallbackInfo ci
    ) {
        if (ringolib$guardAndCancelIfNeeded(ringolib$MODE_FIRST, null, newEntry, visibility)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "insertAfter(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/CreativeModeTab$TabVisibility;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ringolib$guardInsertAfter(
            ItemStack existingEntry,
            ItemStack newEntry,
            CreativeModeTab.TabVisibility visibility,
            CallbackInfo ci
    ) {
        if (ringolib$guardAndCancelIfNeeded(ringolib$MODE_AFTER, existingEntry, newEntry, visibility)) {
            ci.cancel();
        }
    }

    @Inject(
            method = "insertBefore(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/CreativeModeTab$TabVisibility;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void ringolib$guardInsertBefore(
            ItemStack existingEntry,
            ItemStack newEntry,
            CreativeModeTab.TabVisibility visibility,
            CallbackInfo ci
    ) {
        if (ringolib$guardAndCancelIfNeeded(ringolib$MODE_BEFORE, existingEntry, newEntry, visibility)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean ringolib$guardAndCancelIfNeeded(
            int mode,
            @Nullable ItemStack existingEntry,
            @Nullable ItemStack newEntry,
            CreativeModeTab.TabVisibility visibility
    ) {
        if (newEntry == null || newEntry.isEmpty()) {
            return true;
        }
        if (newEntry.getCount() != 1) {
            ringolib$LOGGER.debug("RingoLib: Normalized creative tab stack count from {} to 1 for {}", newEntry.getCount(), newEntry);
            newEntry.setCount(1);
        }

        boolean parentTarget = visibility == CreativeModeTab.TabVisibility.PARENT_TAB_ONLY
                || visibility == CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
        boolean searchTarget = visibility == CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY
                || visibility == CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS;
        boolean needsAnchor = mode == ringolib$MODE_AFTER || mode == ringolib$MODE_BEFORE;

        boolean parentConflict = parentTarget && ringolib$wouldThrow(this.parentEntries, needsAnchor, existingEntry, newEntry);
        boolean searchConflict = searchTarget && ringolib$wouldThrow(this.searchEntries, needsAnchor, existingEntry, newEntry);

        if (!parentConflict && !searchConflict) {
            return false;
        }

        ringolib$LOGGER.debug("RingoLib: Handled duplicate/missing-anchor creative tab entry (mode {}) for {}", mode, newEntry);
        if (parentTarget) {
            ringolib$insertSafely(this.parentEntries, mode, existingEntry, newEntry);
        }
        if (searchTarget) {
            ringolib$insertSafely(this.searchEntries, mode, existingEntry, newEntry);
        }
        return true;
    }

    @Unique
    private static boolean ringolib$wouldThrow(
            InsertableLinkedOpenCustomHashSet<ItemStack> entries,
            boolean needsAnchor,
            @Nullable ItemStack existingEntry,
            ItemStack newEntry
    ) {
        if (entries.contains(newEntry)) {
            return true;
        }
        return needsAnchor && (existingEntry == null || existingEntry.isEmpty() || !entries.contains(existingEntry));
    }

    @Unique
    private static void ringolib$insertSafely(
            InsertableLinkedOpenCustomHashSet<ItemStack> entries,
            int mode,
            @Nullable ItemStack existingEntry,
            ItemStack newEntry
    ) {
        if (entries.contains(newEntry)) {
            return;
        }
        if (mode == ringolib$MODE_FIRST) {
            entries.addFirst(newEntry);
            return;
        }
        if ((mode == ringolib$MODE_AFTER || mode == ringolib$MODE_BEFORE)
                && existingEntry != null && !existingEntry.isEmpty() && entries.contains(existingEntry)) {
            if (mode == ringolib$MODE_AFTER) {
                entries.addAfter(existingEntry, newEntry);
            } else {
                entries.addBefore(existingEntry, newEntry);
            }
            return;
        }
        entries.add(newEntry);
    }
}
