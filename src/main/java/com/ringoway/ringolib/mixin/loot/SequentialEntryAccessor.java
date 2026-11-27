package com.ringoway.ringolib.mixin.loot;

import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.entries.SequentialEntry;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(SequentialEntry.class)
public interface SequentialEntryAccessor {

    @Invoker("<init>")
    static SequentialEntry ringolib$create(
            List<LootPoolEntryContainer> children,
            List<LootItemCondition> conditions
    ) {
        throw new AssertionError();
    }
}
