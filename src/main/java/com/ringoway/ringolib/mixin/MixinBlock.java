package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.api.RegistryRedirectManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class MixinBlock {

    @Shadow
    @Nullable
    private Item item;

    @Inject(method = "asItem()Lnet/minecraft/world/item/Item;", at = @At("RETURN"), cancellable = true)
    private void ringolib$resolveRedirectedBlockItem(CallbackInfoReturnable<Item> cir) {
        if (cir.getReturnValue() != Items.AIR) {
            return;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey((Block) (Object) this);
        ResourceLocation targetItemId = RegistryRedirectManager.getRedirectTarget(Registries.ITEM, blockId);
        if (targetItemId != null && BuiltInRegistries.ITEM.containsKey(targetItemId)) {
            Item targetItem = BuiltInRegistries.ITEM.get(targetItemId);
            if (targetItem != Items.AIR) {
                this.item = targetItem;
                cir.setReturnValue(targetItem);
            }
        }
    }
}
