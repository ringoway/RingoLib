package com.ringoway.ringolib.mixin.client.emi;

import com.ringoway.ringolib.client.compat.EmiLootClientSerializerHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Uses client {@code RegistryAccess} in EMI Loot's {@code LootReceiver#readItemStack} instead of bare {@code NbtOps.INSTANCE}.
 */
@Pseudo
@Mixin(targets = "fzzyhmstrs.emi_loot.client.LootReceiver", remap = false)
public interface EmiLootReceiverMixin {

    @Inject(
            method = "readItemStack(Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$readItemStackWithRegistries(
            FriendlyByteBuf buf,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        cir.setReturnValue(EmiLootClientSerializerHelper.readItemStackWithRegistries(buf));
    }
}
