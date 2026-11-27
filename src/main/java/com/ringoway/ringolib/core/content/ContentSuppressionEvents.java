package com.ringoway.ringolib.core.content;

import com.mojang.logging.LogUtils;
import com.ringoway.ringolib.RingoLib;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = RingoLib.MOD_ID)
public final class ContentSuppressionEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation WANDERING_TRADER_ID = ResourceLocation.withDefaultNamespace("wandering_trader");

    private ContentSuppressionEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        boolean checkBlock = ContentSuppressionRegistry.hasBlockInteractionOrPlacementRules();
        boolean checkItem = ContentSuppressionRegistry.hasItemRules();
        if (!checkBlock && !checkItem) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        if (checkBlock) {
            BlockState state = level.getBlockState(pos);
            if (ContentSuppressionRegistry.isBlockInteractionBlocked(state)) {
                event.setUseBlock(TriState.FALSE);
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                if (!level.isClientSide()) {
                    ContentSuppressionRegistry.notifyBlockedInteract(state, event.getEntity(), pos);
                }
                return;
            }
        }

        if (checkItem) {
            ItemStack stack = event.getItemStack();
            if (ContentSuppressionRegistry.isItemUsageBlocked(stack)) {
                event.setUseItem(TriState.FALSE);
                if (!level.isClientSide()) {
                    ContentSuppressionRegistry.notifyBlockedUse(stack, event.getEntity());
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!ContentSuppressionRegistry.hasItemRules()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (ContentSuppressionRegistry.isItemUsageBlocked(stack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            if (!event.getLevel().isClientSide()) {
                ContentSuppressionRegistry.notifyBlockedUse(stack, event.getEntity());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!ContentSuppressionRegistry.hasBlockInteractionOrPlacementRules()) {
            return;
        }
        if (ContentSuppressionRegistry.isBlockPlacementBlocked(event.getPlacedBlock())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (!ContentSuppressionRegistry.hasItemRules() || event.getPlayer().level().isClientSide()) {
            return;
        }
        ItemStack stack = event.getItemEntity().getItem();
        if (ContentSuppressionRegistry.isItemPickupBlocked(stack)) {
            event.setCanPickup(TriState.FALSE);
            event.getItemEntity().discard();
        }
    }

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!ContentSuppressionRegistry.hasItemRules() || event.getEntity().level().isClientSide()) {
            return;
        }
        ItemStack crafted = event.getCrafting();
        if (ContentSuppressionRegistry.isItemCraftingBlocked(crafted)) {
            crafted.setCount(0);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!ContentSuppressionRegistry.hasPurgeRules() || event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }

        boolean changed = false;
        int size = player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && ContentSuppressionRegistry.isItemPurgedFromInventory(stack)) {
                player.getInventory().setItem(i, ItemStack.EMPTY);
                changed = true;
            }
        }

        if (changed) {
            player.getInventory().setChanged();
            player.inventoryMenu.broadcastChanges();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (!ContentSuppressionRegistry.hasTradeRules()) {
            return;
        }
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(event.getType());
        for (List<VillagerTrades.ItemListing> listings : event.getTrades().values()) {
            wrapListings(professionId, listings);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onWandererTrades(WandererTradesEvent event) {
        if (!ContentSuppressionRegistry.hasTradeRules()) {
            return;
        }
        wrapListings(WANDERING_TRADER_ID, event.getGenericTrades());
        wrapListings(WANDERING_TRADER_ID, event.getRareTrades());
    }

    private static void wrapListings(
            @Nullable ResourceLocation professionId,
            @Nullable List<VillagerTrades.ItemListing> listings
    ) {
        if (listings == null || listings.isEmpty()) {
            return;
        }
        for (int i = 0; i < listings.size(); i++) {
            VillagerTrades.ItemListing original = listings.get(i);
            if (original != null) {
                listings.set(i, wrapListing(professionId, original));
            }
        }
    }

    private static VillagerTrades.ItemListing wrapListing(
            @Nullable ResourceLocation professionId,
            VillagerTrades.ItemListing delegate
    ) {
        return (trader, random) -> {
            MerchantOffer offer = delegate.getOffer(trader, random);
            if (offer == null) {
                return null;
            }
            if (ContentSuppressionRegistry.isTradeItemSuppressed(professionId, offer.getResult())
                    || ContentSuppressionRegistry.isTradeItemSuppressed(professionId, offer.getBaseCostA())
                    || ContentSuppressionRegistry.isTradeItemSuppressed(professionId, offer.getCostB())) {
                return null;
            }
            return offer;
        };
    }

    // Runs before EMI Loot parses loot tables
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!ContentSuppressionRegistry.hasLootRules()) {
            return;
        }
        try {
            LootTable rewritten = LootTableRewriter.rewrite(
                    event.getName(),
                    event.getTable(),
                    event.getRegistries()
            );
            if (rewritten != null) {
                event.setTable(rewritten);
            }
        } catch (Throwable t) {
            LOGGER.error("RingoLib: Error statically rewriting loot table {}", event.getName(), t);
        }
    }

    public static void sanitizeCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!ContentSuppressionRegistry.hasItemRules()) {
            return;
        }
        try {
            Set<ItemStack> toRemove = new HashSet<>();
            for (ItemStack stack : event.getParentEntries()) {
                if (ContentSuppressionRegistry.isItemHiddenFromCreativeTab(stack)) {
                    toRemove.add(stack);
                }
            }
            for (ItemStack stack : event.getSearchEntries()) {
                if (ContentSuppressionRegistry.isItemHiddenFromCreativeTab(stack)) {
                    toRemove.add(stack);
                }
            }
            for (ItemStack stack : toRemove) {
                event.remove(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        } catch (Throwable t) {
            LOGGER.error("RingoLib: Error sanitizing creative tab {} for suppressed items", event.getTabKey(), t);
        }
    }
}
