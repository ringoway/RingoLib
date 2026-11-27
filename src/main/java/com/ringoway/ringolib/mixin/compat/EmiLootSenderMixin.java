package com.ringoway.ringolib.mixin.compat;

import com.ringoway.ringolib.core.compat.EmiLootSerializerHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Uses level {@code RegistryAccess} in EMI Loot's {@code LootSender#writeItemStack} instead of bare {@code NbtOps.INSTANCE}.
 */
@Pseudo
@Mixin(targets = "fzzyhmstrs.emi_loot.server.LootSender", remap = false)
public interface EmiLootSenderMixin {

    @Inject(
            method = "writeItemStack(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0
    )
    private void ringolib$writeItemStackWithRegistries(
            FriendlyByteBuf buf,
            ItemStack stack,
            Level level,
            CallbackInfo ci
    ) {
        EmiLootSerializerHelper.writeItemStackWithRegistries(buf, stack, level);
        ci.cancel();
    }
}
