package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public abstract class MixinLevelChunk {

    @Shadow
    private void removeBlockEntityTicker(BlockPos pos) {
    }

    @Inject(
            method = "updateBlockEntityTicker(Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T extends BlockEntity> void ringolib$preventSuppressedTickerRegistration(T blockEntity, CallbackInfo ci) {
        if (!ContentSuppressionRegistry.hasTickerRules() || blockEntity == null) {
            return;
        }
        if (ContentSuppressionRegistry.isBlockTickerSuppressed(blockEntity.getBlockState(), blockEntity.getType())) {
            this.removeBlockEntityTicker(blockEntity.getBlockPos());
            ci.cancel();
        }
    }

    @Inject(
            method = "addGameEventListener(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/server/level/ServerLevel;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private <T extends BlockEntity> void ringolib$preventSuppressedGameEventListener(
            T blockEntity,
            ServerLevel level,
            CallbackInfo ci
    ) {
        if (!ContentSuppressionRegistry.hasGameEventRules()) {
            return;
        }
        if (ContentSuppressionRegistry.isBlockEntityGameEventSuppressed(blockEntity)) {
            ci.cancel();
        }
    }
}
