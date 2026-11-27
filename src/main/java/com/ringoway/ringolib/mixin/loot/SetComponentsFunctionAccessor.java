package com.ringoway.ringolib.mixin.loot;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.level.storage.loot.functions.SetComponentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(SetComponentsFunction.class)
public interface SetComponentsFunctionAccessor {

    @Invoker("<init>")
    static SetComponentsFunction ringolib$create(
            List<LootItemCondition> conditions,
            DataComponentPatch components
    ) {
        throw new AssertionError();
    }
}
