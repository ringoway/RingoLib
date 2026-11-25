package com.ringoway.ringolib.core.ban;

import com.ringoway.ringolib.RingoLib;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Server-side enforcement for banned items (pickup, inventory purge, crafting block, and login sync).
 */
@EventBusSubscriber(modid = RingoLib.MOD_ID)
public final class BannedItemsEvents {

    private BannedItemsEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        BannedItemsManager.init();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer().level().isClientSide()) return;

        ItemStack stack = event.getItemEntity().getItem();
        if (BannedItemsManager.isBanned(stack.getItem())) {
            event.setCanPickup(TriState.FALSE);
            event.getItemEntity().discard();
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.tickCount % 20 != 0) return;

        boolean foundBanned = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && BannedItemsManager.isBanned(stack.getItem())) {
                ItemEntity itemEntity = player.drop(stack.copy(), false);
                if (itemEntity != null) {
                    itemEntity.discard();
                }
                player.getInventory().setItem(i, ItemStack.EMPTY);
                foundBanned = true;
            }
        }

        if (foundBanned) {
            player.getInventory().setChanged();
            player.displayClientMessage(
                    Component.translatable("tooltip.ringolib.banned.removed")
                            .withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        ItemStack crafted = event.getCrafting();
        if (!crafted.isEmpty() && BannedItemsManager.isBanned(crafted.getItem())) {
            crafted.setCount(0);
            if (event.getEntity() instanceof ServerPlayer player) {
                player.displayClientMessage(
                        Component.translatable("tooltip.ringolib.banned.craft_blocked")
                                .withStyle(ChatFormatting.RED),
                        true
                );
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BannedItemsManager.syncToPlayer(player);
        }
    }
}
