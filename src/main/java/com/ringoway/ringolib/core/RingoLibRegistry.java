package com.ringoway.ringolib.core;

import com.ringoway.ringolib.RingoLib;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class RingoLibRegistry {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, RingoLib.MOD_ID);

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(BuiltInRegistries.BLOCK, RingoLib.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, RingoLib.MOD_ID);

    public static final DeferredHolder<Item, Item> DUMMY_ITEM = ITEMS.register(
            "dummy_item",
            () -> new DummyItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant())
    );

    public static final DeferredHolder<Block, Block> DUMMY_BLOCK = BLOCKS.register(
            "dummy_block",
            () -> new DummyBlock(BlockBehaviour.Properties.of()
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .noOcclusion())
    );

    public static final DeferredHolder<Item, Item> DUMMY_BLOCK_ITEM = ITEMS.register(
            "dummy_block",
            () -> new BlockItem(DUMMY_BLOCK.get(), new Item.Properties().rarity(Rarity.EPIC).fireResistant())
    );

    public static final ResourceLocation DISABLED_BLOCK_ENTITY_ID =
            ResourceLocation.fromNamespaceAndPath(RingoLib.MOD_ID, "disabled_block_entity");

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DummyBlockEntity>> DUMMY_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "dummy_block",
                    () -> BlockEntityType.Builder.of(DummyBlockEntity::new, DUMMY_BLOCK.get()).build(null)
            );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DisabledBlockEntity>> DISABLED_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register(
                    "disabled_block_entity",
                    () -> BlockEntityType.Builder.of(DisabledBlockEntity::new).build(null)
            );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
        BLOCKS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
    }
}
