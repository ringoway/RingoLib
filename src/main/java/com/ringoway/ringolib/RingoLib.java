package com.ringoway.ringolib;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import com.ringoway.ringolib.core.RingoLibConfig;
import com.ringoway.ringolib.core.RingoLibRegistry;
import com.ringoway.ringolib.core.ban.SyncBannedItemsPayload;
import com.ringoway.ringolib.core.network.CopyToClipboardPayload;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(RingoLib.MOD_ID)
public class RingoLib {
    public static final String MOD_ID = "ringolib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RingoLib(IEventBus eventBus, ModContainer modContainer) {
        LOGGER.info("Initializing RingoLib - Universal Mod Migration & Preservation API");
        RingoLibRegistry.register(eventBus);
        RingoLibConfig.loadModpackRules();

        // Register registry aliasing (HIGHEST) and creative tab cleanup (LOWEST)
        eventBus.addListener(EventPriority.HIGHEST, RegistryRedirectManager::applyAliases);
        eventBus.addListener(EventPriority.LOWEST, RegistryRedirectManager::sanitizeCreativeTab);
        eventBus.addListener(RingoLib::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID).optional();
        registrar.playToClient(
                SyncBannedItemsPayload.TYPE,
                SyncBannedItemsPayload.STREAM_CODEC,
                SyncBannedItemsPayload::handle
        );
        registrar.playToClient(
                CopyToClipboardPayload.TYPE,
                CopyToClipboardPayload.STREAM_CODEC,
                CopyToClipboardPayload::handle
        );
    }
}
