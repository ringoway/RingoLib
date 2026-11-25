package com.ringoway.ringolib.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ringoway.ringolib.RingoLib;
import com.ringoway.ringolib.client.RingoWarningData;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers RingoLib's unified operator command tree under /ringolib and /rl,
 * and extends vanilla /locate with /locate ore.
 */
@EventBusSubscriber(modid = RingoLib.MOD_ID)
public final class RingoLibCommands {

    private RingoLibCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(buildRootCommand("ringolib"));
        dispatcher.register(buildRootCommand("rl"));

        // Merge /locate ore <block_or_tag> [radius] into vanilla /locate
        dispatcher.register(
                Commands.literal("locate")
                        .requires(source -> source.hasPermission(2))
                        .then(LocateOreCommand.buildOreNode())
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildRootCommand(String literal) {
        return Commands.literal(literal)
                .requires(source -> source.hasPermission(2))
                .then(HandCommand.buildNode())
                .then(LookCommand.buildNode())
                .then(RecipeDebugCommands.buildNode())
                .then(AuditCommands.buildNode())
                .then(DumpCommands.buildDumpNode())
                .then(DumpCommands.buildIngotsNode())
                .then(DumpCommands.buildEffectsNode())
                .then(LagCommand.buildCommand())
                .then(BanCommands.buildNode())
                .then(PlayerDebugCommands.buildPlayerNode())
                .then(PlayerDebugCommands.buildDropInventoryNode())
                .then(PlayerDebugCommands.buildForceRespawnNode())
                .then(PlayerDebugCommands.buildReviveNode())
                .then(LocateOreCommand.buildLocateNode())
                .then(StructurePlaceCommand.buildNode())
                .then(Commands.literal("warnings")
                        .then(Commands.literal("reset")
                                .executes(context -> {
                                    RingoWarningData.reset();
                                    context.getSource().sendSuccess(() ->
                                            Component.translatable("commands.ringolib.warnings.reset")
                                                    .withStyle(ChatFormatting.GREEN), true);
                                    return 1;
                                })
                        )
                );
    }
}
