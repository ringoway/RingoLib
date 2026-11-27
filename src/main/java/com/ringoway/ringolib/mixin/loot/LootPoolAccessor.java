package com.ringoway.ringolib.mixin.loot;

import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.Optional;

@Mixin(LootPool.class)
public interface LootPoolAccessor {

    @Accessor("entries")
    List<LootPoolEntryContainer> ringolib$getEntries();

    @Accessor("conditions")
    List<LootItemCondition> ringolib$getConditions();

    @Accessor("functions")
    List<LootItemFunction> ringolib$getFunctions();

    @Accessor("rolls")
    NumberProvider ringolib$getRolls();

    @Accessor("bonusRolls")
    NumberProvider ringolib$getBonusRolls();

    @Accessor("name")
    @Nullable
    String ringolib$getName();

    @Invoker("<init>")
    static LootPool ringolib$create(
            List<LootPoolEntryContainer> entries,
            List<LootItemCondition> conditions,
            List<LootItemFunction> functions,
            NumberProvider rolls,
            NumberProvider bonusRolls,
            Optional<String> name
    ) {
        throw new AssertionError();
    }
}
