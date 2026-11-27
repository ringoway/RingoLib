# Java API reference

All public API classes live under `com.ringoway.ringolib.api`. Call registration methods from your mod's `@Mod` constructor so rules are in place before NeoForge dispatches `RegisterEvent`.

## `RingoMigrationAPI`

High-level entry point for item, block, and fluid migrations and redirects.

- `register*Migration(...)` rewrites IDs when worlds or inventories deserialize NBT (`MixinItemStack` and `MixinNbtUtils`). It does not cancel registry registration.
- `register*Redirect(...)` registers both the NBT migration and registry interception plus a NeoForge registry alias.

### Item methods
```java
// Save-level NBT migration only
RingoMigrationAPI.registerItemMigration(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description);
RingoMigrationAPI.registerItemMigration(String oldId, String targetId, @Nullable String requiredMod);

// Save-level migration + registry interception + NeoForge alias + creative tab cleanup
RingoMigrationAPI.registerItemRedirect(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description);
RingoMigrationAPI.registerItemRedirect(String oldId, String targetId, @Nullable String requiredMod);
```

### Block methods
```java
// Save-level block state migration only
RingoMigrationAPI.registerBlockMigration(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description);
RingoMigrationAPI.registerBlockMigration(String oldId, String targetId, @Nullable String requiredMod);
RingoMigrationAPI.registerBlockAndItem(String oldId, String targetId, @Nullable String requiredMod);

// Full block redirect (use registerBlockAndItemRedirect if the block also has a matching BlockItem)
RingoMigrationAPI.registerBlockRedirect(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description);
RingoMigrationAPI.registerBlockRedirect(String oldId, String targetId, @Nullable String requiredMod);
RingoMigrationAPI.registerBlockAndItemRedirect(String oldId, String targetId, @Nullable String requiredMod);
```

### Fluid methods
`registerFluidRedirect` registers four registry redirects at once: still fluid (`minecraft:fluid`), flowing fluid (`flowing_<path>` in `minecraft:fluid`), `neoforge:fluid_type`, and the liquid block (`minecraft:block`). Redirect the bucket separately via `registerItemRedirect` if needed.

```java
RingoMigrationAPI.registerFluidMigration(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description);
RingoMigrationAPI.registerFluidMigration(String oldId, String targetId, @Nullable String requiredMod);

RingoMigrationAPI.registerFluidRedirect(String oldId, String targetId, @Nullable String requiredMod, @Nullable String description);
RingoMigrationAPI.registerFluidRedirect(String oldId, String targetId, @Nullable String requiredMod);
```

### Lookup methods
```java
boolean RingoMigrationAPI.hasItemMigration(String oldId);
boolean RingoMigrationAPI.hasBlockMigration(String oldId);
boolean RingoMigrationAPI.hasFluidMigration(String oldId);

@Nullable MigrationEntry RingoMigrationAPI.getItemEntry(String oldId);
@Nullable MigrationEntry RingoMigrationAPI.getBlockEntry(String oldId);
@Nullable MigrationEntry RingoMigrationAPI.getFluidEntry(String oldId);

Map<String, MigrationEntry> RingoMigrationAPI.getAllItemMigrations();
Map<String, MigrationEntry> RingoMigrationAPI.getAllBlockMigrations();
Map<String, MigrationEntry> RingoMigrationAPI.getAllFluidMigrations();
```

## `RegistryRedirectManager`

Low-level registry redirect and alias manager. Use this directly when redirecting registries other than items, blocks, or fluids (such as enchantments, mob effects, or block entity types).

```java
public static void registerRedirect(
        ResourceKey<? extends Registry<?>> registryKey,
        ResourceLocation fromId,
        ResourceLocation toId,
        @Nullable String requiredMod,
        @Nullable String description
);

public static void registerRedirect(
        ResourceKey<? extends Registry<?>> registryKey,
        ResourceLocation fromId,
        ResourceLocation toId,
        @Nullable String requiredMod
);

public static boolean shouldCancel(ResourceKey<? extends Registry<?>> registryKey, ResourceLocation id);
public static boolean isItemRedirected(ResourceLocation id);
public static Map<ResourceKey<? extends Registry<?>>, Map<ResourceLocation, RedirectEntry>> getAllRedirects();
```

## `RecipeFilterManager`

Programmatic recipe removal. Config rules from `config/ringolib/disabled_recipes.json` are reloaded on `RecipeManager.apply` via `RingoLibConfig.loadDisabledRecipes()`, while programmatic API rules (`disableRecipe`, `disableRecipes(...)`, `disableRecipesByOutput(...)`, `disableRecipesMatchingJson(...)`) persist across `/reload`.

```java
// Exact ID ("modid:path"), glob wildcard ("modid:category/*"), or functional predicates
public static void disableRecipe(String recipePattern);
public static void disableRecipe(ResourceLocation recipeId);
public static void disableRecipes(Predicate<ResourceLocation> idPredicate);
public static void disableRecipes(BooleanSupplier condition, Predicate<ResourceLocation> idPredicate);
public static void disableRecipesByOutput(String itemId);
public static void disableRecipesByOutput(Predicate<ResourceLocation> outputItemPredicate);
public static void disableRecipesMatchingJson(BiPredicate<ResourceLocation, JsonElement> jsonPredicate);

public static boolean isRecipeDisabled(ResourceLocation recipeId);
public static void reload();
public static void clear();
```

## `RingoContentAPI` & `RegisterContentSuppressionsEvent`

Functional, lambda-driven API in `com.ringoway.ringolib.api.content` for disabling blocks (including zero-tick `BlockEntity` elimination and world/structure block replacement), items, fluids, creative tabs, and JEI/EMI recipe categories.

Call `RingoContentAPI` from your `@Mod` constructor or subscribe to `RegisterContentSuppressionsEvent` on the mod event bus:

```java
RingoContentAPI.whenModLoaded("dwarfs_tyranny", scope -> scope
        .block("dwarfs_tyranny:infusion_cauldron", block -> block
                .unregister()
                .unregisterBlockEntity()
                .replaceInWorld("minecraft:cauldron")
                .purgeFromPlayers()
                .alsoDisableRecipes("dwarfs_tyranny:infusion_cauldron_recipe")
                .alsoDisableAdvancements("dwarfs_tyranny:recipes/infusion_cauldron_recipe"))
        .item("dwarfs_tyranny:deepbrew_bottle", item -> item
                .unregister()
                .purgeFromPlayers())
        .recipeCategory("dwarfs_tyranny:deepbrew")
);
```

### Capabilities
- Zero hidden `BlockEntity` tick load: `.block(...)`, `.blockEntity(...)`, and `.blockEntityTicker(Predicate<BlockState>)` prevent `BlockStateBase#getTicker` and `LevelChunk#updateBlockEntityTicker` from attaching tickers and block `GameEvent` listener registration. `.unregisterBlockEntity()` redirects the `BlockEntityType` to `ringolib:disabled_block_entity` so capability and renderer lookups do not crash.
- JEI and EMI category and stack removal: `.recipeCategory(idOrGlob)` and `.recipeCategories(Predicate<ResourceLocation>)` hide categories in JEI, EMI, and JEMI, filter unregistered empty stacks and catalysts, and cancel dynamic runtime recipe and ingredient additions.
- Contextual and weighted loot replacement: `.loot(rule -> rule.inTables(...).item(...).replaceWithWeighted(pool -> ...))` passes immutable `LootReplaceContext` (`lootTableId`, `originalStack`, `random()`, `level()`, `registries()`, `isStaticResolution()`, `luck()`, `count()`) and `WeightedLootBuilder` (`add`, `addPreservingCount`, `addFixed`, `addEmpty`, `addMany`), with support for probabilities (`.chance(...)`), `.preserveCount()`, `.runtimeOnly()`, and 1-to-N replacements alongside `disableLootItem`, `disableLoot`, and `replaceLoot`. Compatible rules rewrite vanilla `LootTable` pools during `LootTableLoadEvent` (so EMI Loot shows updated weights and stacks with datapack-registry `DataComponents`), and skip double-rolling in runtime hooks.
- Structure NBT block replacement and `StructureProcessor`s: `.structure(rule -> rule.inTemplates(...).block(...).replaceWith("minecraft:magma_block"))` (or `RingoContentAPI.configureStructure(...)` and `.addProcessor(...)`) intercepts `StructureTemplate#processBlockInfos` per `.nbt` or Jigsaw template ID with `StructureBlockContext` (`copyMatchingProperties(...)`, `preserveNbt()`, `chance(...)`), so unrelated structures and Ponder assets stay untouched.
- Advancements and villager trades: disabled items and blocks are stripped from loot tables and villager or wandering trader offers (`VillagerTradesEvent` and `WandererTradesEvent`) unless `.keepInLootTables()` or `.keepInTrades()` is set. Direct APIs include `disableAdvancement`, `disableAdvancements`, `disableAdvancementsMatchingJson`, `disableTradeItem`, and `disableTrades`.
- Functional filtering: combine `Predicate<ResourceLocation>`, `Predicate<BlockState>`, `Predicate<ItemStack>`, `BiPredicate<ResourceLocation, JsonElement>`, `Function<ResourceLocation, ResourceLocation>`, `Function<LootReplaceContext, ItemStack>`, `Function<StructureBlockContext, BlockState>`, `BooleanSupplier`, `Consumer`, and `BiConsumer` callbacks with `.when(...)`, `.ifModLoaded(...)`, and `.ifModMissing(...)`.

## `RingoWarningAPI`

Registers one-time client `AlertScreen` notices. Once a player clicks **Proceed**, the warning ID (`title_<id>` or `world_<id>`) is written to `config/ringolib/shown_warnings.json` and will not pop up again.

`RingoWarningAPI` itself only references `Component` and `BooleanSupplier`, so calling it from common setup code is safe on dedicated servers (the server simply stores the condition, while `ClientWarningHandler` evaluates and renders it on `Dist.CLIENT`).

```java
public static void registerTitleWarning(
        String id,
        BooleanSupplier shouldShow,
        Component title,
        Component message
);

public static void registerWorldWarning(
        String id,
        BooleanSupplier shouldShow,
        Component title,
        Component message
);
```
