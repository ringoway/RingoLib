package com.ringoway.ringolib.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class CuriosCompatImpl {

    private CuriosCompatImpl() {
    }

    static int countEquippedCurios(ServerPlayer player) {
        int[] count = {0};
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                    handler.getCurios().forEach((slotType, stacksHandler) -> {
                        var stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (!stacks.getStackInSlot(i).isEmpty()) {
                                count[0]++;
                            }
                        }
                    })
            );
        } catch (Exception ignored) {
        }
        return count[0];
    }

    static Map<String, List<String>> getEquippedCuriosByMod(ServerPlayer player) {
        Map<String, List<String>> byMod = new TreeMap<>();
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                    handler.getCurios().forEach((slotType, stacksHandler) -> {
                        var stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            ItemStack stack = stacks.getStackInSlot(i);
                            if (stack.isEmpty()) continue;

                            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                            if (itemId == null) continue;

                            String modId = itemId.getNamespace();
                            String itemName = itemId.getPath();
                            if (stack.getCount() > 1) {
                                itemName += " x" + stack.getCount();
                            }
                            byMod.computeIfAbsent(modId, k -> new ArrayList<>()).add(itemName);
                        }
                    })
            );
        } catch (Exception ignored) {
        }
        return byMod;
    }

    static int dropAllCurios(ServerPlayer player) {
        int[] dropped = {0};
        try {
            CuriosApi.getCuriosInventory(player).ifPresent(handler ->
                    handler.getCurios().forEach((type, stacksHandler) -> {
                        var stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            ItemStack stack = stacks.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                player.drop(stack.copy(), true, false);
                                stacks.setStackInSlot(i, ItemStack.EMPTY);
                                dropped[0]++;
                            }
                        }
                        var cosmetic = stacksHandler.getCosmeticStacks();
                        for (int i = 0; i < cosmetic.getSlots(); i++) {
                            ItemStack stack = cosmetic.getStackInSlot(i);
                            if (!stack.isEmpty()) {
                                player.drop(stack.copy(), true, false);
                                cosmetic.setStackInSlot(i, ItemStack.EMPTY);
                                dropped[0]++;
                            }
                        }
                    })
            );
        } catch (Exception ignored) {
        }
        return dropped[0];
    }
}
