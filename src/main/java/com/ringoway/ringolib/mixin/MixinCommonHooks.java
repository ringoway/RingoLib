package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CommonHooks.class, remap = false)
public abstract class MixinCommonHooks {

    @Inject(
            method = "modifyLoot(Lnet/minecraft/resources/ResourceLocation;Lit/unimi/dsi/fastutil/objects/ObjectArrayList;Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At("RETURN")
    )
    private static void ringolib$filterSuppressedLoot(
            ResourceLocation lootTableId,
            ObjectArrayList<ItemStack> generatedLoot,
            LootContext context,
            CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
    ) {
        if (!ContentSuppressionRegistry.hasLootRules()) {
            return;
        }
        ContentSuppressionRegistry.filterLoot(lootTableId, cir.getReturnValue(), context);
    }
}
