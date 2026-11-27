package com.ringoway.ringolib.mixin;

import com.ringoway.ringolib.core.content.StructureTemplateIdAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(StructureTemplateManager.class)
public abstract class MixinStructureTemplateManager {

    @Inject(
            method = "tryLoad(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            at = @At("RETURN")
    )
    private void ringolib$attachTemplateId(
            ResourceLocation id,
            CallbackInfoReturnable<Optional<StructureTemplate>> cir
    ) {
        cir.getReturnValue().ifPresent(template -> {
            if (template instanceof StructureTemplateIdAccess access) {
                access.ringolib$setTemplateId(id);
            }
        });
    }
}
