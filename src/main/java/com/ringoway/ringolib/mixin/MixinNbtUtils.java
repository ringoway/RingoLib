package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.RingoLib;
import com.ringoway.ringolib.api.MigrationEntry;
import com.ringoway.ringolib.api.RingoMigrationAPI;
import com.ringoway.ringolib.core.DummyBlockEntity;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(NbtUtils.class)
public class MixinNbtUtils {

    @ModifyVariable(
            method = "readBlockState(Lnet/minecraft/core/HolderGetter;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/block/state/BlockState;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static CompoundTag ringolib$interceptMigratedBlocks(CompoundTag tag, HolderGetter<Block> blockGetter) {
        if (tag == null || !tag.contains("Name", 8)) {
            return tag;
        }

        String name = tag.getString("Name");
        if (RingoMigrationAPI.hasBlockMigration(name)) {
            MigrationEntry entry = RingoMigrationAPI.getBlockEntry(name);
            if (entry != null) {
                CompoundTag mutated = tag.copy();
                if (entry.isTargetAvailable()) {
                    mutated.putString("Name", entry.targetId());
                } else {
                    mutated.putString("Name", RingoLib.MOD_ID + ":dummy_block");
                    mutated.putString(DummyBlockEntity.TAG_STORED_BLOCK_ID, entry.targetId());
                    if (entry.requiredTargetMod() != null) {
                        mutated.putString(DummyBlockEntity.TAG_REQUIRED_MOD, entry.requiredTargetMod());
                    }
                }
                return mutated;
            }
        }

        return tag;
    }
}
