# Architecture and lifecycle

RingoLib hooks into NeoForge 1.21.1 at four points in the game's lifecycle: mod construction, registry events, creative tab building, and resource/world loading.

---

## Execution order

```text
[Mod Construction: @Mod("ringolib")]
       │
       ├── Registers ringolib:dummy_item, ringolib:dummy_block, and its BlockEntityType
       └── Reads config/ringolib/migrations.json and disabled_recipes.json
       │
       ▼
[RegisterEvent]
       ├── EventPriority.HIGHEST -> RegistryRedirectManager.applyAliases (Registry#addAlias)
       ├── RegisterEventMixin    -> Cancels redirected DeferredRegister suppliers
       ├── RegistrateRegistrationMixin (@Pseudo) -> Cancels redirected Registrate entries
       └── ArchitecturyData/EventListener/RegistrySupplierMixins (@Pseudo) -> Prunes & resolves Architectury registrations
       │
       ▼
[BuildCreativeModeTabContentsEvent]
       ├── EventHooksCreativeTabMixin             -> Drops empty stacks and normalizes count != 1
       ├── BuildCreativeModeTabContentsEventMixin -> Deduplicates aliased tab insertions and guards missing anchors
       └── EventPriority.LOWEST                   -> RegistryRedirectManager.sanitizeCreativeTab
       │
       ▼
[RecipeManager.apply (@At("HEAD"))]
       └── MixinRecipeManager -> Reloads disabled_recipes.json and strips matching entries
       │
       ▼
[Chunk & Inventory Deserialization]
       ├── MixinItemStack -> Rewrites migrated item IDs in ItemStack.parse
       └── MixinNbtUtils  -> Rewrites migrated block IDs in NbtUtils.readBlockState
```

*(If another mod registers redirects through `RingoMigrationAPI` in its own `@Mod` constructor, those maps are backed by `ConcurrentHashMap`, so parallel mod construction is thread-safe and finishes before `RegisterEvent` fires.)*

---

## Mixin hooks (`mixins.ringolib.json`)

### 1. `RegistrateRegistrationMixin`
- **Target:** `com.tterrag.registrate.AbstractRegistrate$Registration` (`@Pseudo`, `remap = false`)
- **Method:** `register(RegisterEvent)` at `HEAD` (cancellable)
- **Why it exists:** Cancels registration before Registrate invokes `creator.get()`, avoiding orphan intrusive holders when registries freeze. Because it is marked `@Pseudo`, Mixin skips it quietly when Registrate is not installed.

### 2. `RegisterEventMixin`
- **Target:** `net.neoforged.neoforge.registries.RegisterEvent`
- **Method:** `register(ResourceKey, ResourceLocation, Supplier)` at `HEAD` (cancellable)
- **Why it exists:** Stops standard NeoForge `DeferredRegister` entries from invoking their factory supplier when `RegistryRedirectManager.shouldCancel` returns `true`.

### 3. `EventHooksCreativeTabMixin` and `BuildCreativeModeTabContentsEventMixin`
- **Targets:** `net.neoforged.neoforge.event.EventHooks` (`remap = false`, `require = 0`, method `lambda$onCreativeModeTabBuildContents$0`) and `net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent` (`remap = false`, methods `accept`, `insertFirst`, `insertAfter`, `insertBefore`)
- **Why they exist:** When an item is redirected or aliased, a mod's creative tab builder may pass an empty stack, a stack with count != 1, a duplicate stack whose alias already exists in the tab (inserted by another mod earlier in the event), or an anchor stack that was aliased/removed. `EventHooksCreativeTabMixin` guards the base tab generator, while `BuildCreativeModeTabContentsEventMixin` normalizes counts, skips already-present duplicates, and falls back cleanly when an anchor stack is missing so NeoForge does not throw an `IllegalArgumentException`. `EventHooksCreativeTabMixin` is marked `require = 0` so a future synthetic lambda renumbering in NeoForge cannot crash startup.

### 4. `MixinRecipeManager`
- **Target:** `net.minecraft.world.item.crafting.RecipeManager`
- **Method:** `apply(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)` at `HEAD`
- **Why it exists:** Reloads `disabled_recipes.json` and mutates the raw JSON map before `RecipeManager` deserializes recipes, which also keeps `/reload` working live.

### 5. `MixinItemStack` and `MixinNbtUtils`
- **Targets:** `ItemStack.parse` and `NbtUtils.readBlockState` (`@ModifyVariable` at `HEAD`)
- **Why they exist:** Copy and rewrite incoming `CompoundTag` instances when saved worlds, inventories, or containers contain old item or block IDs, routing them either to the new target ID or to `ringolib:dummy_item` / `ringolib:dummy_block`. (`ItemStack.parseOptional` delegates directly to `ItemStack.parse`.)

### 6. Architectury compatibility (`ArchitecturyDataMixin`, `ArchitecturyEventListenerMixin`, `ArchitecturyRegistrySupplierMixin`)
- **Targets:** `dev.architectury.registry.registries.forge.RegistrarManagerImpl$Data`, `$RegistryProviderImpl$EventListener`, and `$RegistrarImpl$1` (`@Pseudo`, `remap = false`)
- **Why they exist:** Architectury registers items and blocks through `RegisterEvent.register(ResourceKey, Consumer<RegisterHelper>)` instead of the `Supplier` overload, bypassing `RegisterEventMixin`. These mixins prune redirected entries before `RegisterHelper.register` runs and fall back `RegistrySupplier.get()`, `isPresent()`, and `getHolder()` to the canonical target in `BuiltInRegistries`.

### 7. `MixinBlock`
- **Target:** `net.minecraft.world.level.block.Block`
- **Method:** `asItem()Lnet/minecraft/world/item/Item;` at `RETURN` (cancellable)
- **Why it exists:** When a block stays registered (e.g. due to a custom `Block` subclass) while its `BlockItem` is redirected in `Registries.ITEM`, `Block#asItem()` would otherwise cache `Items.AIR` and crash calls such as `DeferredBlock#toStack()`. This hook resolves and caches the redirected target item instead.

---

## Client-server boundary

All registry, recipe, config, and NBT classes avoid importing `net.minecraft.client.*` so RingoLib loads cleanly on dedicated servers. Screen warnings live in `com.ringoway.ringolib.client.ClientWarningHandler`, subscribed exclusively via `@EventBusSubscriber(modid = "ringolib", value = Dist.CLIENT)`.
