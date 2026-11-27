package com.ringoway.ringolib.core.compat;

import com.mojang.serialization.DynamicOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Encodes EMI Loot item stacks with the server's {@code RegistryAccess} to preserve datapack-registry components.
 */
public final class EmiLootSerializerHelper {

    private EmiLootSerializerHelper() {
    }

    public static void writeItemStackWithRegistries(
            FriendlyByteBuf buf,
            ItemStack stack,
            @Nullable Level level
    ) {
        buf.writeVarInt(BuiltInRegistries.ITEM.getId(stack.getItem()));
        buf.writeVarInt(stack.getCount());
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            buf.writeBoolean(false);
            return;
        }
        DynamicOps<Tag> ops = level != null
                ? level.registryAccess().createSerializationContext(NbtOps.INSTANCE)
                : NbtOps.INSTANCE;
        DataComponentPatch.CODEC.encodeStart(ops, patch)
                .ifSuccess(tag -> {
                    buf.writeBoolean(true);
                    buf.writeNbt(tag);
                })
                .ifError(err -> buf.writeBoolean(false));
    }
}
