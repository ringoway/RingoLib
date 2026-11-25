package com.ringoway.ringolib.client;

import com.ringoway.ringolib.RingoLib;
import com.ringoway.ringolib.core.ban.BannedItemsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Physically client-isolated tooltip renderer and session cache cleanup for banned items.
 */
@EventBusSubscriber(modid = RingoLib.MOD_ID, value = Dist.CLIENT)
public final class ClientBannedItemsHandler {

    private ClientBannedItemsHandler() {
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        if (BannedItemsManager.isBannedClient(stack.getItem())) {
            int insertIdx = Math.min(1, event.getToolTip().size());
            event.getToolTip().add(insertIdx, Component.empty());
            event.getToolTip().add(insertIdx + 1, Component.translatable("tooltip.ringolib.banned.warning")
                    .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            event.getToolTip().add(insertIdx + 2, Component.translatable("tooltip.ringolib.banned.hint")
                    .withStyle(ChatFormatting.DARK_RED));
        }
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BannedItemsManager.clearClientList();
    }
}
