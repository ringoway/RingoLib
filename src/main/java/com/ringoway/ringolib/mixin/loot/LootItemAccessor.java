package com.ringoway.ringolib.mixin.loot;

import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(LootItem.class)
public interface LootItemAccessor {

    @Accessor("item")
    Holder<Item> ringolib$getItem();

    @Invoker("<init>")
    static LootItem ringolib$create(
            Holder<Item> item,
            int weight,
            int quality,
            List<LootItemCondition> conditions,
            List<LootItemFunction> functions
    ) {
        throw new AssertionError();
    }
}
