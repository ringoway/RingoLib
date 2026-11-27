package com.ringoway.ringolib.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DummyBlock extends Block implements EntityBlock {

    public DummyBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DummyBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> blockEntityType) {
        if (level.isClientSide || blockEntityType != RingoLibRegistry.DUMMY_BLOCK_ENTITY.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> {
            if (be instanceof DummyBlockEntity dummyBe && !dummyBe.isRestoreChecked()) {
                dummyBe.markRestoreChecked();
                String reqMod = dummyBe.getRequiredMod();
                if (reqMod.isEmpty() || ModList.get().isLoaded(reqMod)) {
                    restoreBlock(lvl, pos, dummyBe);
                }
            }
        };
    }

    private static void restoreBlock(Level level, BlockPos pos, DummyBlockEntity dummyBe) {
        String targetIdStr = dummyBe.getStoredBlockId();
        if (targetIdStr.isEmpty()) return;

        ResourceLocation targetLoc = ResourceLocation.tryParse(targetIdStr);
        if (targetLoc != null && BuiltInRegistries.BLOCK.containsKey(targetLoc)) {
            Block realBlock = BuiltInRegistries.BLOCK.get(targetLoc);
            BlockState realState = realBlock.defaultBlockState();
            CompoundTag savedTeData = dummyBe.getSavedTeData();

            level.setBlock(pos, realState, 3);

            if (!savedTeData.isEmpty() && level instanceof ServerLevel serverLevel) {
                BlockEntity newBe = level.getBlockEntity(pos);
                if (newBe != null) {
                    newBe.loadWithComponents(savedTeData, serverLevel.registryAccess());
                    newBe.setChanged();
                }
            }
        }
    }
}
