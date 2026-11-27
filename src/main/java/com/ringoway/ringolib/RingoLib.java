package com.ringoway.ringolib;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.api.RegistryRedirectManager;
import com.ringoway.ringolib.api.content.RegisterContentSuppressionsEvent;
import com.ringoway.ringolib.core.RingoLibConfig;
import com.ringoway.ringolib.core.RingoLibRegistry;
import com.ringoway.ringolib.core.ban.SyncBannedItemsPayload;
import com.ringoway.ringolib.core.content.ContentSuppressionEvents;
import com.ringoway.ringolib.core.network.CopyToClipboardPayload;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

@Mod(RingoLib.MOD_ID)
public class RingoLib {
    public static final String MOD_ID = "ringolib";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean SUPPRESSIONS_POSTED = new AtomicBoolean(false);

    public RingoLib(IEventBus eventBus, ModContainer modContainer) {
        LOGGER.info("Initializing RingoLib - Universal Mod Migration & Preservation API");
        RingoLibRegistry.register(eventBus);
        RingoLibConfig.loadModpackRules();

        eventBus.addListener(EventPriority.HIGHEST, RingoLib::onRegisterEventHighest);
        eventBus.addListener(EventPriority.LOWEST, RegistryRedirectManager::sanitizeCreativeTab);
        eventBus.addListener(EventPriority.LOWEST, ContentSuppressionEvents::sanitizeCreativeTab);
        eventBus.addListener(RingoLib::registerPayloads);
    }

    public static void ensureSuppressionsPosted() {
        if (SUPPRESSIONS_POSTED.compareAndSet(false, true)) {
            ModLoader.postEvent(new RegisterContentSuppressionsEvent());
        }
    }

    private static void onRegisterEventHighest(RegisterEvent event) {
        ensureSuppressionsPosted();
        RegistryRedirectManager.applyAliases(event);
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
