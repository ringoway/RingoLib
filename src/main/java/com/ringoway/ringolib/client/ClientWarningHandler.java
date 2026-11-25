package com.ringoway.ringolib.client;

import com.ringoway.ringolib.RingoLib;
import com.ringoway.ringolib.api.RingoWarningAPI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = RingoLib.MOD_ID, value = Dist.CLIENT)
public class ClientWarningHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof TitleScreen) {
            for (RingoWarningAPI.WarningCondition warn : RingoWarningAPI.getTitleWarnings()) {
                if (warn.shouldShow().getAsBoolean() && !RingoWarningData.isShown("title_" + warn.id())) {
                    Minecraft mc = Minecraft.getInstance();
                    AlertScreen alert = new AlertScreen(
                            () -> {
                                RingoWarningData.markShown("title_" + warn.id());
                                mc.setScreen(new TitleScreen());
                            },
                            warn.title(),
                            warn.message(),
                            CommonComponents.GUI_PROCEED,
                            false
                    );
                    mc.tell(() -> mc.setScreen(alert));
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        for (RingoWarningAPI.WarningCondition warn : RingoWarningAPI.getWorldWarnings()) {
            if (warn.shouldShow().getAsBoolean() && !RingoWarningData.isShown("world_" + warn.id())) {
                Minecraft mc = Minecraft.getInstance();
                mc.tell(() -> {
                    AlertScreen alert = new AlertScreen(
                            () -> {
                                RingoWarningData.markShown("world_" + warn.id());
                                if (mc.screen != null) {
                                    mc.setScreen(null);
                                }
                            },
                            warn.title(),
                            warn.message(),
                            CommonComponents.GUI_PROCEED,
                            true
                    );
                    mc.setScreen(alert);
                });
                break;
            }
        }
    }
}
