# Java API reference

All public API classes live under `com.ringoway.ringolib.api`. Call registration methods from your mod's `@Mod` constructor so rules are in place before NeoForge dispatches `RegisterEvent`.

---

## `RingoMigrationAPI`

High-level entry point for item, block, and fluid migrations and redirects.

- **`register*Migration(...)`** only rewrites IDs when worlds/inventories deserialize NBT (`MixinItemStack` / `MixinNbtUtils`). It does not cancel registry registration.
- **`register*Redirect(...)`** registers both the NBT migration **and** the full registry interception + NeoForge registry alias.

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

---

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

---

## `RecipeFilterManager`

Programmatic recipe removal. Note that `RingoLibConfig.loadDisabledRecipes()` clears and repopulates this manager from `config/ringolib/disabled_recipes.json` when `RecipeManager.apply` runs; if you want purely code-driven filters that persist across `/reload`, register them after config reload or put them in `disabled_recipes.json`.

```java
// Supports exact IDs ("modid:path") or wildcards ("modid:category/*")
public static void disableRecipe(String recipePattern);
public static void disableRecipe(ResourceLocation recipeId);

public static boolean isRecipeDisabled(ResourceLocation recipeId);
public static void reload();
public static void clear();
```

---

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
