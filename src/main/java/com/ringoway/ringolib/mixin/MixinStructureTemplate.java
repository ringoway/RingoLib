package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.core.content.ContentSuppressionRegistry;
import com.ringoway.ringolib.core.content.StructureTemplateIdAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(StructureTemplate.class)
public abstract class MixinStructureTemplate implements StructureTemplateIdAccess {

    @Unique
    @Nullable
    private ResourceLocation ringolib$templateId;

    @Override
    @Nullable
    public ResourceLocation ringolib$getTemplateId() {
        return this.ringolib$templateId;
    }

    @Override
    public void ringolib$setTemplateId(@Nullable ResourceLocation id) {
        this.ringolib$templateId = id;
    }

    @Inject(
            method = "processBlockInfos(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructurePlaceSettings;Ljava/util/List;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void ringolib$processStructureBlocks(
            ServerLevelAccessor level,
            BlockPos offset,
            BlockPos pos,
            StructurePlaceSettings settings,
            List<StructureTemplate.StructureBlockInfo> blockInfos,
            @Nullable StructureTemplate template,
            CallbackInfoReturnable<List<StructureTemplate.StructureBlockInfo>> cir
    ) {
        if (!ContentSuppressionRegistry.hasStructureRules()) {
            return;
        }
        ResourceLocation templateId = template instanceof StructureTemplateIdAccess access
                ? access.ringolib$getTemplateId()
                : null;
        List<StructureTemplate.StructureBlockInfo> original = cir.getReturnValue();
        List<StructureTemplate.StructureBlockInfo> updated = ContentSuppressionRegistry.processStructureBlocks(
                templateId, level, offset, pos, settings, original, template
        );
        if (updated != original) {
            cir.setReturnValue(updated);
        }
    }
}
