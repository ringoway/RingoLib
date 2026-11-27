package com.ringoway.ringolib.api.content;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.jetbrains.annotations.Nullable;

public record StructureBlockContext(
        @Nullable ResourceLocation templateId,
        ServerLevelAccessor level,
        BlockPos worldPos,
        BlockPos structureOrigin,
        BlockState originalState,
        @Nullable CompoundTag originalNbt,
        StructurePlaceSettings settings,
        RandomSource random
) {
    public BlockState copyMatchingProperties(BlockState targetState) {
        BlockState result = targetState;
        for (Property<?> prop : originalState.getProperties()) {
            if (result.hasProperty(prop)) {
                result = copyProperty(originalState, result, prop);
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> BlockState copyProperty(
            BlockState source,
            BlockState target,
            Property<T> property
    ) {
        return target.setValue(property, source.getValue(property));
    }
}
