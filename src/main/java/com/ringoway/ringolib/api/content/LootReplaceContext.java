package com.ringoway.ringolib.api.content;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import org.jetbrains.annotations.Nullable;

/**
 * Context passed to loot replacement callbacks.
 * {@link #lootContext()} is {@code null} during static {@code LootTableLoadEvent} rewriting
 * while {@link #registries()} remains available.
 */
public record LootReplaceContext(
        @Nullable ResourceLocation lootTableId,
        ItemStack originalStack,
        @Nullable LootContext lootContext,
        @Nullable HolderLookup.Provider registries
) {
    public LootReplaceContext(
            @Nullable ResourceLocation lootTableId,
            ItemStack originalStack,
            @Nullable LootContext lootContext
    ) {
        this(
                lootTableId,
                originalStack,
                lootContext,
                lootContext != null && lootContext.getLevel() != null
                        ? lootContext.getLevel().registryAccess()
                        : null
        );
    }

    public boolean isStaticResolution() {
        return lootContext == null;
    }

    public RandomSource random() {
        return lootContext != null ? lootContext.getRandom() : RandomSource.create();
    }

    @Nullable
    public ServerLevel level() {
        return lootContext != null ? lootContext.getLevel() : null;
    }

    public float luck() {
        return lootContext != null ? lootContext.getLuck() : 0.0f;
    }

    public int count() {
        return originalStack.getCount();
    }

    public ItemStack copyWithCount(@Nullable ItemStack template) {
        if (template == null || template.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = template.copy();
        int desired = Math.min(Math.max(1, originalStack.getCount()), copy.getMaxStackSize());
        copy.setCount(desired);
        return copy;
    }
}
