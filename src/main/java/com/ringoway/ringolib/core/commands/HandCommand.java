package com.ringoway.ringolib.core.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class HandCommand {

    private HandCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildNode() {
        return Commands.literal("hand")
                .executes(HandCommand::inspectAll)
                .then(Commands.literal("id")
                        .executes(HandCommand::inspectIdOnly)
                )
                .then(Commands.literal("tags")
                        .executes(HandCommand::inspectTagsOnly)
                )
                .then(Commands.literal("components")
                        .executes(HandCommand::inspectComponentsOnly)
                )
                .then(Commands.literal("recipes")
                        .executes(HandCommand::inspectRecipesOnly)
                );
    }

    @Nullable
    static ItemStack getHeldStack(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("commands.ringolib.hand.player_only")
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            stack = player.getOffhandItem();
        }
        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable("commands.ringolib.hand.empty")
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        return stack;
    }

    private static int inspectAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack stack = getHeldStack(source);
        if (stack == null) return 0;

        ServerPlayer player = (ServerPlayer) source.getEntity();
        HolderLookup.Provider registries = player.registryAccess();
        Item item = stack.getItem();
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

        CommandChatHelper.copyToClipboard(source, itemId);

        source.sendSuccess(() -> Component.literal("=== ")
                .append(Component.translatable("commands.ringolib.hand.header"))
                .append(" ===")
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.item")
                .withStyle(ChatFormatting.GRAY)
                .append(CommandChatHelper.copyable(itemId, itemId, ChatFormatting.GREEN))
                .append(Component.translatable("commands.ringolib.hand.copied")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(CommandChatHelper.copyBadge("JSON", "\"item\": \"" + itemId + "\"", ChatFormatting.AQUA)),
                false);

        if (item instanceof BlockItem blockItem) {
            String blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
            source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.block")
                    .withStyle(ChatFormatting.GRAY)
                    .append(CommandChatHelper.copyable(blockId, blockId, ChatFormatting.GREEN)), false);
        }

        sendFluidLines(source, stack, item);

        sendItemTags(source, stack);
        if (item instanceof BlockItem blockItem) {
            sendBlockTags(source, blockItem.getBlock());
        }

        sendDataComponents(source, stack, registries);

        List<RecipeHolder<?>> outputs = RecipeDebugCommands.findOutputRecipes(source.getServer(), registries, item);
        if (!outputs.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.recipes_header", outputs.size())
                    .withStyle(ChatFormatting.DARK_AQUA), false);

            int shown = 0;
            for (RecipeHolder<?> holder : outputs) {
                if (shown >= 15) {
                    int remaining = outputs.size() - shown;
                    source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.recipes_more", remaining)
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(CommandChatHelper.runBadge(
                                    "All",
                                    "/rl hand recipes",
                                    Component.translatable("commands.ringolib.hand.recipes_show_all"),
                                    ChatFormatting.YELLOW
                            )), false);
                    break;
                }
                RecipeDebugCommands.sendOutputRecipeLine(source, holder);
                shown++;
            }
        } else {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.no_output_recipes")
                    .withStyle(ChatFormatting.DARK_GRAY), false);
        }

        return 1;
    }

    private static int inspectIdOnly(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack stack = getHeldStack(source);
        if (stack == null) return 0;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        CommandChatHelper.copyToClipboard(source, itemId);

        source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.item")
                .withStyle(ChatFormatting.GRAY)
                .append(CommandChatHelper.copyable(itemId, itemId, ChatFormatting.GREEN))
                .append(Component.translatable("commands.ringolib.hand.copied")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(CommandChatHelper.copyBadge("JSON", "\"item\": \"" + itemId + "\"", ChatFormatting.AQUA)),
                false);
        return 1;
    }

    private static int inspectTagsOnly(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack stack = getHeldStack(source);
        if (stack == null) return 0;

        List<String> itemTags = sendItemTags(source, stack);
        List<String> blockTags = stack.getItem() instanceof BlockItem blockItem
                ? sendBlockTags(source, blockItem.getBlock())
                : List.of();

        List<String> all = new ArrayList<>(itemTags);
        all.addAll(blockTags);

        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.no_tags")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }

        CommandChatHelper.copyToClipboard(source, all.get(0));
        return all.size();
    }

    private static int inspectComponentsOnly(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack stack = getHeldStack(source);
        if (stack == null) return 0;

        ServerPlayer player = (ServerPlayer) source.getEntity();
        String patchSnbt = sendDataComponents(source, stack, player.registryAccess());
        if (patchSnbt == null) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.no_components")
                    .withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        CommandChatHelper.copyToClipboard(source, patchSnbt);
        return 1;
    }

    private static int inspectRecipesOnly(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ItemStack stack = getHeldStack(source);
        if (stack == null) return 0;

        return RecipeDebugCommands.inspectStackRecipes(source, stack);
    }

    private static void sendFluidLines(CommandSourceStack source, ItemStack stack, Item item) {
        IFluidHandlerItem fluidHandler = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (fluidHandler != null) {
            boolean printed = false;
            for (int i = 0; i < fluidHandler.getTanks(); i++) {
                FluidStack fluidStack = fluidHandler.getFluidInTank(i);
                if (!fluidStack.isEmpty()) {
                    String fluidId = BuiltInRegistries.FLUID.getKey(fluidStack.getFluid()).toString();
                    int amount = fluidStack.getAmount();
                    source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.fluid")
                            .withStyle(ChatFormatting.GRAY)
                            .append(CommandChatHelper.copyable(fluidId, fluidId, ChatFormatting.AQUA))
                            .append(Component.literal(" (" + amount + " mB)").withStyle(ChatFormatting.DARK_GRAY)),
                            false);
                    printed = true;
                }
            }
            if (printed) return;
        }

        if (item instanceof BucketItem bucket) {
            Fluid content = bucket.content;
            if (content != Fluids.EMPTY) {
                String fluidId = BuiltInRegistries.FLUID.getKey(content).toString();
                source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.fluid")
                        .withStyle(ChatFormatting.GRAY)
                        .append(CommandChatHelper.copyable(fluidId, fluidId, ChatFormatting.AQUA)), false);
            }
        }
    }

    private static List<String> sendItemTags(CommandSourceStack source, ItemStack stack) {
        List<String> tags = stack.getTags()
                .map(TagKey::location)
                .sorted(tagComparator())
                .map(ResourceLocation::toString)
                .toList();

        if (!tags.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.item_tags", tags.size())
                    .withStyle(ChatFormatting.DARK_AQUA), false);
            tags.forEach(tag -> sendTagLine(source, tag));
        }
        return tags;
    }

    private static List<String> sendBlockTags(CommandSourceStack source, Block block) {
        Holder<Block> holder = BuiltInRegistries.BLOCK.wrapAsHolder(block);
        List<String> tags = holder.tags()
                .map(TagKey::location)
                .sorted(tagComparator())
                .map(ResourceLocation::toString)
                .toList();

        if (!tags.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.block_tags", tags.size())
                    .withStyle(ChatFormatting.DARK_AQUA), false);
            tags.forEach(tag -> sendTagLine(source, tag));
        }
        return tags;
    }

    private static void sendTagLine(CommandSourceStack source, String tagId) {
        source.sendSuccess(() -> Component.literal("  - ").withStyle(ChatFormatting.YELLOW)
                .append(CommandChatHelper.copyable(tagId, tagId, ChatFormatting.AQUA))
                .append(CommandChatHelper.copyBadge("#", "#" + tagId, ChatFormatting.GRAY))
                .append(CommandChatHelper.copyBadge("JSON", "\"tag\": \"" + tagId + "\"", ChatFormatting.DARK_AQUA)),
                false);
    }

    @Nullable
    private static String sendDataComponents(
            CommandSourceStack source,
            ItemStack stack,
            HolderLookup.Provider registries
    ) {
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return null;
        }

        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        String patchSnbt = DataComponentPatch.CODEC.encodeStart(ops, patch)
                .result()
                .map(Tag::toString)
                .orElse(patch.toString());

        MutableComponent header = Component.translatable("commands.ringolib.hand.components", patch.size())
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(CommandChatHelper.copyBadge("Copy SNBT", patchSnbt, ChatFormatting.GOLD));
        source.sendSuccess(() -> header, false);

        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            DataComponentType<?> type = entry.getKey();
            ResourceLocation typeId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            String typeKey = typeId != null ? typeId.toString() : type.toString();

            if (entry.getValue().isEmpty()) {
                source.sendSuccess(() -> Component.literal("  !").withStyle(ChatFormatting.RED)
                        .append(CommandChatHelper.copyable(typeKey, "!" + typeKey, ChatFormatting.RED)), false);
                continue;
            }

            Object rawVal = entry.getValue().get();
            String valueStr = encodeComponentValue(type, rawVal, ops);
            String fullLine = typeKey + "=" + valueStr;

            source.sendSuccess(() -> Component.literal("  - ").withStyle(ChatFormatting.YELLOW)
                    .append(CommandChatHelper.copyable(typeKey, fullLine, ChatFormatting.GOLD))
                    .append(Component.literal(" = ").withStyle(ChatFormatting.GRAY))
                    .append(CommandChatHelper.copyable(truncate(valueStr, 90), valueStr, ChatFormatting.WHITE)),
                    false);
        }

        return patchSnbt;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String encodeComponentValue(DataComponentType<?> type, Object value, RegistryOps<Tag> ops) {
        Codec codec = type.codec();
        if (codec != null) {
            Optional<Tag> encoded = codec.encodeStart(ops, value).result();
            if (encoded.isPresent()) {
                return encoded.get().toString();
            }
        }
        return String.valueOf(value);
    }

    private static Comparator<ResourceLocation> tagComparator() {
        return Comparator.<ResourceLocation, Integer>comparing(loc -> loc.getNamespace().equals("c") ? 0 : 1)
                .thenComparing(ResourceLocation::toString);
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
