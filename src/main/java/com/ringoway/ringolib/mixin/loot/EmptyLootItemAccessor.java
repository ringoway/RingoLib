package com.ringoway.ringolib.mixin.loot;

import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(EmptyLootItem.class)
public interface EmptyLootItemAccessor {

    @Invoker("<init>")
    static EmptyLootItem ringolib$create(
            int weight,
            int quality,
            List<LootItemCondition> conditions,
            List<LootItemFunction> functions
    ) {
        throw new AssertionError();
    }
}
