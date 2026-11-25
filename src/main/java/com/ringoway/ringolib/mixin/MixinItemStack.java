package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.RingoLib;
import com.ringoway.ringolib.api.MigrationEntry;
import com.ringoway.ringolib.api.RingoMigrationAPI;
import com.ringoway.ringolib.core.DummyItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemStack.class)
public class MixinItemStack {

    @ModifyVariable(
            method = "parse(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/Tag;)Ljava/util/Optional;",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private static Tag ringolib$interceptMigratedItems(Tag rawTag, HolderLookup.Provider registries) {
        if (!(rawTag instanceof CompoundTag tag) || !tag.contains("id", 8)) {
            return rawTag;
        }

        String idStr = tag.getString("id");
        if (RingoMigrationAPI.hasItemMigration(idStr)) {
            MigrationEntry entry = RingoMigrationAPI.getItemEntry(idStr);
            if (entry != null) {
                CompoundTag mutated = tag.copy();
                if (entry.isTargetAvailable()) {
                    mutated.putString("id", entry.targetId());
                } else {
                    mutated.putString("id", RingoLib.MOD_ID + ":dummy_item");
                    CompoundTag componentsTag = mutated.getCompound("components");
                    CompoundTag customDataTag = componentsTag.getCompound("minecraft:custom_data");

                    customDataTag.putString(DummyItem.TAG_ORIGINAL_ID, idStr);
                    customDataTag.putString(DummyItem.TAG_TARGET_ID, entry.targetId());
                    if (entry.requiredTargetMod() != null) {
                        customDataTag.putString(DummyItem.TAG_REQUIRED_MOD, entry.requiredTargetMod());
                    }

                    componentsTag.put("minecraft:custom_data", customDataTag);
                    mutated.put("components", componentsTag);
                }
                return mutated;
            }
        }

        return rawTag;
    }
}
