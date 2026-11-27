package com.ringoway.ringolib.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inert alias target for unregistered {@code BlockEntityType} entries.
 */
public final class DisabledBlockEntity extends BlockEntity {

    public DisabledBlockEntity(BlockPos pos, BlockState state) {
        super(RingoLibRegistry.DISABLED_BLOCK_ENTITY.get(), pos, state);
    }
}
