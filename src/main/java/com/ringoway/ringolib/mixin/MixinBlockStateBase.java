package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockStateBase {

    @Inject(
            method = "getTicker(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/entity/BlockEntityType;)Lnet/minecraft/world/level/block/entity/BlockEntityTicker;",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T extends BlockEntity> void ringolib$suppressBlockTicker(
            Level level,
            BlockEntityType<T> blockEntityType,
            CallbackInfoReturnable<BlockEntityTicker<T>> cir
    ) {
        if (!ContentSuppressionRegistry.hasTickerRules()) {
            return;
        }
        if ((Object) this instanceof BlockState state
                && ContentSuppressionRegistry.isBlockTickerSuppressed(state, blockEntityType)) {
            cir.setReturnValue(null);
        }
    }
}
