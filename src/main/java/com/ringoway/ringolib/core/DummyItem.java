package com.ringoway.ringolib.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DummyItem extends Item {
    public static final String TAG_ORIGINAL_ID = "RingoLib_OriginalId";
    public static final String TAG_TARGET_ID = "RingoLib_TargetId";
    public static final String TAG_REQUIRED_MOD = "RingoLib_RequiredMod";

    public DummyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && entity instanceof Player player) {
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.contains(TAG_TARGET_ID)) {
                CompoundTag tag = customData.copyTag();
                String reqMod = tag.getString(TAG_REQUIRED_MOD);
                boolean available = reqMod.isEmpty() || ModList.get().isLoaded(reqMod);

                if (available) {
                    String targetIdStr = tag.getString(TAG_TARGET_ID);
                    ResourceLocation targetLoc = ResourceLocation.tryParse(targetIdStr);
                    if (targetLoc != null && BuiltInRegistries.ITEM.containsKey(targetLoc)) {
                        Item targetItem = BuiltInRegistries.ITEM.get(targetLoc);
                        ItemStack restored = new ItemStack(targetItem, stack.getCount());
                        restored.applyComponents(stack.getComponents());

                        // Strip internal RingoLib preservation tags so restored items stack cleanly
                        tag.remove(TAG_ORIGINAL_ID);
                        tag.remove(TAG_TARGET_ID);
                        tag.remove(TAG_REQUIRED_MOD);
                        if (tag.isEmpty()) {
                            restored.remove(DataComponents.CUSTOM_DATA);
                        } else {
                            restored.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                        }

                        Inventory inv = player.getInventory();
                        if (slotId >= 0 && slotId < inv.getContainerSize()) {
                            inv.setItem(slotId, restored);
                        }
                    }
                }
            }
        }
        super.inventoryTick(stack, level, entity, slotId, isSelected);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(TAG_ORIGINAL_ID)) {
                String original = tag.getString(TAG_ORIGINAL_ID);
                tooltip.add(Component.translatable("tooltip.ringolib.dummy_item.original", original)
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            if (tag.contains(TAG_REQUIRED_MOD)) {
                String reqMod = tag.getString(TAG_REQUIRED_MOD);
                if (!reqMod.isEmpty()) {
                    tooltip.add(Component.translatable("tooltip.ringolib.dummy_item.required_mod", reqMod)
                            .withStyle(ChatFormatting.RED));
                }
            }
        }

        tooltip.add(Component.translatable("tooltip.ringolib.dummy_item.hint")
                .withStyle(ChatFormatting.YELLOW));
    }
}
