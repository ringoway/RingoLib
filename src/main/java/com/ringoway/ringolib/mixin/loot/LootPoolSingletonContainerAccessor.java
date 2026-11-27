package com.ringoway.ringolib.mixin.loot;

import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(LootPoolSingletonContainer.class)
public interface LootPoolSingletonContainerAccessor {

    @Accessor("weight")
    int ringolib$getWeight();

    @Accessor("weight")
    @Mutable
    void ringolib$setWeight(int weight);

    @Accessor("quality")
    int ringolib$getQuality();

    @Accessor("quality")
    @Mutable
    void ringolib$setQuality(int quality);

    @Accessor("functions")
    List<LootItemFunction> ringolib$getFunctions();
}
