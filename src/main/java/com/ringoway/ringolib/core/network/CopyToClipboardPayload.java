package com.ringoway.ringolib.core.network;

import com.ringoway.ringolib.RingoLib;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public record CopyToClipboardPayload(String text) implements CustomPacketPayload {

    public static final Type<CopyToClipboardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RingoLib.MOD_ID, "copy_to_clipboard"));

    public static final StreamCodec<ByteBuf, CopyToClipboardPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CopyToClipboardPayload::text,
            CopyToClipboardPayload::new
    );

    private static volatile Consumer<String> clientClipboardSink = s -> {};

    public static void setClientSink(Consumer<String> sink) {
        clientClipboardSink = sink != null ? sink : s -> {};
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CopyToClipboardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientClipboardSink.accept(payload.text()));
    }
}
