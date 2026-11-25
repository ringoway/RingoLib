package com.ringoway.ringolib.client;

import com.ringoway.ringolib.RingoLib;
import com.ringoway.ringolib.core.network.CopyToClipboardPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

/**
 * Physically isolated client entrypoint for RingoLib.
 */
@Mod(value = RingoLib.MOD_ID, dist = Dist.CLIENT)
public final class RingoLibClient {

    public RingoLibClient() {
        CopyToClipboardPayload.setClientSink(text -> Minecraft.getInstance().keyboardHandler.setClipboard(text));
    }
}
