package com.ringoway.ringolib.core.ban;

import com.ringoway.ringolib.RingoLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record SyncBannedItemsPayload(Set<ResourceLocation> bannedItems) implements CustomPacketPayload {

    public static final Type<SyncBannedItemsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RingoLib.MOD_ID, "sync_banned_items"));

    public static final StreamCodec<ByteBuf, SyncBannedItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()),
            payload -> List.copyOf(payload.bannedItems()),
            list -> new SyncBannedItemsPayload(new HashSet<>(list))
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncBannedItemsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> BannedItemsManager.updateClientList(payload.bannedItems()));
    }
}
