package com.ringoway.ringolib.client.compat;

import com.mojang.serialization.DynamicOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

/**
 * Decodes EMI Loot item stacks with the client's synced {@code RegistryAccess}.
 */
public final class EmiLootClientSerializerHelper {

    private EmiLootClientSerializerHelper() {
    }

    public static ItemStack readItemStackWithRegistries(FriendlyByteBuf buf) {
        Holder<Item> itemHolder = BuiltInRegistries.ITEM.wrapAsHolder(
                BuiltInRegistries.ITEM.byId(buf.readVarInt())
        );
        int count = buf.readVarInt();
        if (!buf.readBoolean()) {
            return new ItemStack(itemHolder, count);
        }
        CompoundTag tag = buf.readNbt();
        if (tag == null) {
            return new ItemStack(itemHolder, count);
        }
        DynamicOps<Tag> ops = resolveClientOps();
        DataComponentPatch patch = DataComponentPatch.CODEC.parse(ops, tag)
                .mapOrElse(Function.identity(), err -> DataComponentPatch.EMPTY);
        return patch.isEmpty()
                ? new ItemStack(itemHolder, count)
                : new ItemStack(itemHolder, count, patch);
    }

    private static DynamicOps<Tag> resolveClientOps() {
        Minecraft mc = Minecraft.getInstance();
        RegistryAccess access = null;
        if (mc.getConnection() != null) {
            access = mc.getConnection().registryAccess();
        } else if (mc.level != null) {
            access = mc.level.registryAccess();
        }
        return access != null ? access.createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
    }
}
