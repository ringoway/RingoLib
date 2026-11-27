package com.ringoway.ringolib.core.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class LookCommand {
    private static final double RANGE = 16.0;

    private LookCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildNode() {
        return Commands.literal("look")
                .executes(LookCommand::inspectLookTarget);
    }

    private static int inspectLookTarget(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.translatable("commands.ringolib.hand.player_only")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        HitResult blockHit = player.pick(RANGE, 0.0F, false);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(RANGE));

        double maxEntityDistSq = blockHit.getType() != HitResult.Type.MISS
                ? blockHit.getLocation().distanceToSqr(eye)
                : RANGE * RANGE;

        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level,
                player,
                eye,
                end,
                player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0),
                e -> !e.isSpectator() && e.isPickable(),
                0.0F
        );

        if (entityHit != null && entityHit.getLocation().distanceToSqr(eye) <= maxEntityDistSq) {
            return inspectEntity(source, entityHit.getEntity());
        }

        if (blockHit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                return inspectBlock(source, level, pos, state);
            }
        }

        source.sendFailure(Component.translatable("commands.ringolib.look.nothing")
                .withStyle(ChatFormatting.YELLOW));
        return 0;
    }

    private static int inspectBlock(CommandSourceStack source, ServerLevel level, BlockPos pos, BlockState state) {
        ResourceLocation blockLoc = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String blockId = blockLoc.toString();
        String dimId = level.dimension().location().toString();
        CommandChatHelper.copyToClipboard(source, blockId);

        source.sendSuccess(() -> Component.literal("=== ")
                .append(Component.translatable("commands.ringolib.look.block_header"))
                .append(" ===")
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.translatable("commands.ringolib.look.id")
                .withStyle(ChatFormatting.GRAY)
                .append(CommandChatHelper.copyable(blockId, blockId, ChatFormatting.GREEN))
                .append(Component.translatable("commands.ringolib.hand.copied")
                        .withStyle(ChatFormatting.DARK_GRAY)), false);

        source.sendSuccess(() -> Component.translatable("commands.ringolib.look.pos")
                .withStyle(ChatFormatting.GRAY)
                .append(CommandChatHelper.tpCoords(pos.getX(), pos.getY(), pos.getZ(), dimId)), false);

        if (!state.getValues().isEmpty()) {
            String propsStr = state.getValues().entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getKey().getName()))
                    .map(LookCommand::formatPropertyEntry)
                    .collect(Collectors.joining(", "));
            String fullState = blockId + "[" + propsStr.replace(", ", ",") + "]";

            source.sendSuccess(() -> Component.translatable("commands.ringolib.look.state")
                    .withStyle(ChatFormatting.GRAY)
                    .append(CommandChatHelper.copyable(propsStr, fullState, ChatFormatting.YELLOW)), false);
        }

        if (!state.getFluidState().isEmpty()) {
            String fluidId = BuiltInRegistries.FLUID.getKey(state.getFluidState().getType()).toString();
            source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.fluid")
                    .withStyle(ChatFormatting.GRAY)
                    .append(CommandChatHelper.copyable(fluidId, fluidId, ChatFormatting.AQUA)), false);
        }

        Holder<Block> holder = BuiltInRegistries.BLOCK.wrapAsHolder(state.getBlock());
        List<String> tags = holder.tags()
                .map(TagKey::location)
                .sorted(Comparator.<ResourceLocation, Integer>comparing(l -> l.getNamespace().equals("c") ? 0 : 1)
                        .thenComparing(ResourceLocation::toString))
                .map(ResourceLocation::toString)
                .toList();

        if (!tags.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.ringolib.hand.block_tags", tags.size())
                    .withStyle(ChatFormatting.DARK_AQUA), false);
            for (String tag : tags) {
                source.sendSuccess(() -> Component.literal("  - ").withStyle(ChatFormatting.YELLOW)
                        .append(CommandChatHelper.copyable(tag, tag, ChatFormatting.AQUA))
                        .append(CommandChatHelper.copyBadge("#", "#" + tag, ChatFormatting.GRAY)), false);
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            ResourceLocation beKey = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            String beId = beKey != null ? beKey.toString() : be.getClass().getSimpleName();
            String snbt = be.saveWithoutMetadata(level.registryAccess()).toString();

            source.sendSuccess(() -> Component.translatable("commands.ringolib.look.block_entity")
                    .withStyle(ChatFormatting.GRAY)
                    .append(CommandChatHelper.copyable(beId, beId, ChatFormatting.LIGHT_PURPLE))
                    .append(CommandChatHelper.copyBadge("Copy SNBT", snbt, ChatFormatting.GOLD)), false);
        }

        return 1;
    }

    private static int inspectEntity(CommandSourceStack source, Entity entity) {
        ResourceLocation entityLoc = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String entityId = entityLoc.toString();
        String uuidStr = entity.getUUID().toString();
        BlockPos pos = entity.blockPosition();
        String dimId = entity.level().dimension().location().toString();

        CommandChatHelper.copyToClipboard(source, entityId);

        source.sendSuccess(() -> Component.literal("=== ")
                .append(Component.translatable("commands.ringolib.look.entity_header"))
                .append(" ===")
                .withStyle(ChatFormatting.GOLD), false);

        source.sendSuccess(() -> Component.translatable("commands.ringolib.look.id")
                .withStyle(ChatFormatting.GRAY)
                .append(CommandChatHelper.copyable(entityId, entityId, ChatFormatting.GREEN))
                .append(Component.translatable("commands.ringolib.hand.copied")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .append(CommandChatHelper.copyBadge("UUID", uuidStr, ChatFormatting.YELLOW)), false);

        source.sendSuccess(() -> Component.translatable("commands.ringolib.look.pos")
                .withStyle(ChatFormatting.GRAY)
                .append(CommandChatHelper.tpCoords(pos.getX(), pos.getY(), pos.getZ(), dimId)), false);

        if (entity instanceof LivingEntity living) {
            source.sendSuccess(() -> Component.translatable(
                    "commands.ringolib.look.health",
                    String.format("%.1f", living.getHealth()),
                    String.format("%.1f", living.getMaxHealth()),
                    living.getArmorValue()
            ).withStyle(ChatFormatting.GRAY), false);

            if (!living.getActiveEffects().isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.ringolib.look.effects")
                        .withStyle(ChatFormatting.DARK_AQUA), false);
                for (MobEffectInstance effect : living.getActiveEffects()) {
                    ResourceLocation effKey = BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value());
                    String effId = effKey != null ? effKey.toString() : "?";
                    source.sendSuccess(() -> Component.literal("  - ").withStyle(ChatFormatting.YELLOW)
                            .append(CommandChatHelper.copyable(effId, effId, ChatFormatting.RED))
                            .append(Component.literal(" amp=" + effect.getAmplifier() + " (" + (effect.getDuration() / 20) + "s)")
                                    .withStyle(ChatFormatting.DARK_GRAY)), false);
                }
            }

            List<AttributeInstance> modified = living.getAttributes().getSyncableAttributes().stream()
                    .filter(inst -> !inst.getModifiers().isEmpty())
                    .toList();
            if (!modified.isEmpty()) {
                source.sendSuccess(() -> Component.translatable("commands.ringolib.look.modifiers")
                        .withStyle(ChatFormatting.DARK_AQUA), false);
                for (AttributeInstance inst : modified) {
                    ResourceLocation attrId = BuiltInRegistries.ATTRIBUTE.getKey(inst.getAttribute().value());
                    String attrName = attrId != null ? attrId.toString() : "?";
                    source.sendSuccess(() -> Component.literal("  " + attrName + " = ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(String.format("%.3f", inst.getValue())).withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(" (base " + String.format("%.3f", inst.getBaseValue()) + ")")
                                    .withStyle(ChatFormatting.DARK_GRAY)), false);
                    for (AttributeModifier mod : inst.getModifiers()) {
                        String modId = mod.id().toString();
                        source.sendSuccess(() -> Component.literal("    - ").withStyle(ChatFormatting.YELLOW)
                                .append(CommandChatHelper.copyable(modId, modId, ChatFormatting.AQUA))
                                .append(Component.literal(" " + mod.operation().name() + " " + String.format("%+.3f", mod.amount()))
                                        .withStyle(ChatFormatting.GRAY)), false);
                    }
                }
            }
        }

        return 1;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String formatPropertyEntry(Map.Entry<Property<?>, Comparable<?>> entry) {
        Property prop = entry.getKey();
        return prop.getName() + "=" + prop.getName(entry.getValue());
    }
}
