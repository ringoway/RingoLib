# World preservation and placeholders

Removing a mod from an existing world usually ends badly. The moment a player loads their base or opens a storage chest, vanilla Minecraft fails to look up the missing registry IDs, drops them to `minecraft:air`, and saves the chunk. Even if you put the mod back five minutes later, those items are gone for good.

RingoLib prevents that for any ID registered in its migration tables.

## How item preservation works

### 1. NBT interception (`MixinItemStack`)
When Minecraft deserializes an `ItemStack` in `ItemStack.parseOptional`, RingoLib checks whether the saved `"id"` matches a registered item migration:
- If the target mod is loaded, RingoLib rewrites `"id"` to the target item ID on a copy of the NBT tag before vanilla parses the stack. Enchantments, stack counts, damage values, and other Data Components carry over untouched.
- If the target mod is missing, instead of letting vanilla discard the unknown item, RingoLib swaps `"id"` to `ringolib:dummy_item` and stores the original metadata inside `minecraft:custom_data`:
  - `RingoLib_OriginalId`
  - `RingoLib_TargetId`
  - `RingoLib_RequiredMod`

### 2. In-game placeholder tooltip
While the required mod is absent, the player sees a `Preserved Item (Mod Required)` placeholder in their chest or inventory instead of an empty slot. Hovering over it shows which item it originally was and which mod ID needs to be installed to get it back.

### 3. Automatic restoration (`DummyItem#inventoryTick`)
Once the missing mod is installed again and the stack ticks inside a player's inventory on the server:
1. `DummyItem` looks up `RingoLib_TargetId` in `BuiltInRegistries.ITEM`.
2. It creates a new `ItemStack` of the real item with the saved stack size and copies all Data Components over.
3. It strips out the three internal `RingoLib_*` keys from `minecraft:custom_data` (removing `custom_data` completely if nothing else was in it), so the restored item stacks normally with freshly crafted items of the same type.

## Block state migration (`MixinNbtUtils` & `DummyBlock`)

`MixinNbtUtils` hooks `NbtUtils.readBlockState`. When a chunk loads a block ID with a registered block migration and the target mod is present, the block palette entry is rewritten directly to the target block ID.

RingoLib also registers `ringolib:dummy_block` and `DummyBlockEntity` (`RingoLib_StoredBlockId`, `RingoLib_RequiredMod`, `RingoLib_SavedTeData`), which ticks on the server and swaps itself back to the target block via `level.setBlock` once the required mod is loaded. Keep in mind that chunk section palettes (`NbtUtils.readBlockState`) only carry block state names and properties, not tile entity NBT, so full tile-entity data round-tripping only applies when a `DummyBlockEntity` has explicitly been populated via `DummyBlockEntity#setStoredBlock`.
