package com.ringoway.ringolib.core.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.ringoway.ringolib.RingoLib;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class StructurePlaceCommand {
    private static final List<String> ROTATIONS = List.of("none", "90", "180", "270");

    private StructurePlaceCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> buildNode() {
        return Commands.literal("structure")
                .then(Commands.literal("place")
                        .then(Commands.argument("path", ResourceLocationArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                        ctx.getSource().getLevel().getStructureManager().listTemplates(),
                                        builder
                                ))
                                .executes(ctx -> placeStructure(ctx, Rotation.NONE))
                                .then(Commands.argument("rotation", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ROTATIONS, builder))
                                        .executes(ctx -> placeStructure(ctx, parseRotation(StringArgumentType.getString(ctx, "rotation"))))
                                )
                        )
                );
    }

    private static Rotation parseRotation(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "90", "clockwise_90" -> Rotation.CLOCKWISE_90;
            case "180" -> Rotation.CLOCKWISE_180;
            case "270", "counterclockwise_90" -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static int placeStructure(CommandContext<CommandSourceStack> context, Rotation rotation) {
        ResourceLocation structureId = ResourceLocationArgument.getId(context, "path");
        CommandSourceStack source = context.getSource();

        try {
            ServerLevel level = source.getLevel();
            BlockPos pos = BlockPos.containing(source.getPosition());

            StructureTemplate template = resolveTemplate(level, structureId);
            if (template == null) {
                source.sendFailure(Component.translatable("commands.ringolib.structure.not_found", structureId.toString())
                        .withStyle(ChatFormatting.RED));
                return 0;
            }

            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setMirror(Mirror.NONE)
                    .setRotation(rotation)
                    .setIgnoreEntities(false);

            template.placeInWorld(level, pos, pos, settings, RandomSource.create(), 2);

            String dimId = level.dimension().location().toString();
            source.sendSuccess(() -> Component.translatable("commands.ringolib.structure.placed",
                            CommandChatHelper.copyable(structureId.toString(), structureId.toString(), ChatFormatting.YELLOW),
                            CommandChatHelper.tpCoords(pos.getX(), pos.getY(), pos.getZ(), dimId))
                    .withStyle(ChatFormatting.GREEN), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.translatable("commands.ringolib.structure.failed", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            RingoLib.LOGGER.error("RingoLib: Failed to place structure {}", structureId, e);
            return 0;
        }
    }

    @Nullable
    private static StructureTemplate resolveTemplate(ServerLevel level, ResourceLocation structureId) throws Exception {
        InputStream stream = openModResource(structureId.getNamespace(),
                "assets/" + structureId.getNamespace() + "/ponder/" + structureId.getPath() + ".nbt");

        if (stream != null) {
            try (InputStream in = stream) {
                CompoundTag nbt = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
                StructureTemplate template = new StructureTemplate();
                template.load(level.holderLookup(Registries.BLOCK), nbt);
                return template;
            }
        }

        Optional<StructureTemplate> standard = level.getStructureManager().get(structureId);
        return standard.orElse(null);
    }

    @Nullable
    private static InputStream openModResource(String namespace, String relativePath) {
        try {
            var modFileInfo = ModList.get().getModFileById(namespace);
            if (modFileInfo != null) {
                Path resource = modFileInfo.getFile().findResource(relativePath);
                if (Files.exists(resource)) {
                    return Files.newInputStream(resource);
                }
            }
        } catch (Exception ignored) {
        }
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            InputStream in = cl.getResourceAsStream(relativePath);
            if (in != null) return in;
        }
        return StructurePlaceCommand.class.getResourceAsStream("/" + relativePath);
    }
}
