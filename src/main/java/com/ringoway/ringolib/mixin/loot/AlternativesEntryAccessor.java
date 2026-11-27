package com.ringoway.ringolib.mixin.loot;

import net.minecraft.world.level.storage.loot.entries.AlternativesEntry;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(AlternativesEntry.class)
public interface AlternativesEntryAccessor {

    @Invoker("<init>")
    static AlternativesEntry ringolib$create(
            List<LootPoolEntryContainer> children,
            List<LootItemCondition> conditions
    ) {
        throw new AssertionError();
    }
}
