# Architecture and lifecycle

RingoLib hooks into NeoForge 1.21.1 at four points in the game's lifecycle: mod construction, registry events, creative tab building, and resource/world loading.

## Execution order

```text
[Mod Construction: @Mod("ringolib")]
       │
       ├── Registers ringolib:dummy_item, ringolib:dummy_block, ringolib:dummy_block_entity,
       │   and inert ringolib:disabled_block_entity
       └── Reads config/ringolib/migrations.json and disabled_recipes.json
       │
       ▼
[RegisterEvent]
       ├── EventPriority.HIGHEST -> RingoLib.ensureSuppressionsPosted() + RegistryRedirectManager.applyAliases
       ├── RegisterEventMixin    -> Ensures suppressions posted & cancels redirected DeferredRegister suppliers
       ├── RegistrateRegistrationMixin (@Pseudo) -> Ensures suppressions posted & cancels redirected Registrate entries
       └── ArchitecturyData/EventListener/RegistrySupplierMixins (@Pseudo) -> Ensures suppressions posted, prunes & resolves Architectury registrations
       │
       ▼
[BuildCreativeModeTabContentsEvent & CreativeModeTab]
       ├── MixinCreativeModeTab                     -> Hides entire suppressed creative tabs
       ├── EventHooksCreativeTabMixin             -> Drops empty stacks and normalizes count != 1
       ├── BuildCreativeModeTabContentsEventMixin -> Deduplicates aliased tab insertions and guards missing anchors
       ├── EventPriority.LOWEST                   -> RegistryRedirectManager.sanitizeCreativeTab
       └── EventPriority.LOWEST                   -> ContentSuppressionEvents.sanitizeCreativeTab
       │
       ▼
[RecipeManager & ServerAdvancementManager (.apply @At("HEAD"))]
       ├── MixinRecipeManager            -> Reloads disabled_recipes.json (preserving Java API filters)
       │                                    and strips matching IDs, JSON predicates, and suppressed outputs
       └── MixinServerAdvancementManager -> Strips disabled advancements by ID, glob, or JSON predicate
       │
       ▼
[Loot Tables & Villager / Wandering Trader Offers]
       ├── LootTableLoadEvent (LOWEST)   -> LootTableRewriter rewrites static LootPool entries with exact
       │                                    GCD/LCM weight scaling before EMI Loot parses loot registries
       ├── MixinCommonHooks#modifyLoot   -> Applies runtime-only/conditional/fallback loot rules (skipping
       │                                    rules already baked statically without opaque entries)
       └── VillagerTrades / WandererTrades -> Wraps ItemListings to drop suppressed trade offers
       │
       ▼
[Chunk, Structure & BlockEntity Ticking]
       ├── MixinItemStack                -> Rewrites migrated item IDs in ItemStack.parse
       ├── MixinNbtUtils                 -> Applies RingoContentAPI world replacements + migrated block IDs
       ├── MixinStructureTemplateManager -> Attaches ResourceLocation template ID to loaded StructureTemplates
       ├── MixinStructureTemplate        -> Applies RingoContentAPI StructureRuleBuilders & StructureProcessors per template
       ├── MixinBlockStateBase           -> Returns null ticker for suppressed blocks/BlockEntityTypes
       └── MixinLevelChunk               -> Prevents adding suppressed tickers & BlockEntity game event listeners

[Client Recipe Viewers (client-only @Pseudo Mixins, zero compile/runtime dependency)]
       ├── client.jei.* -> Hides suppressed recipe categories, dynamic recipes, catalysts (Object/ItemLike[]/List),
       │                   subtypes, info pages, empty unregistered placeholders, and runtime ingredients in JEI
       └── client.emi.* -> Prunes suppressed categories, workstations, recipes (including JEMI),
                           empty/suppressed stacks in EMI before baking
```

*(If another mod registers redirects through `RingoMigrationAPI` in its own `@Mod` constructor, those maps are backed by `ConcurrentHashMap`, so parallel mod construction is thread-safe and finishes before `RegisterEvent` fires.)*

## Mixin hooks (`mixins.ringolib.json`)

### 1. `RegistrateRegistrationMixin`
Targets `com.tterrag.registrate.AbstractRegistrate$Registration` (`@Pseudo`, `remap = false`) on `register(RegisterEvent)` at `HEAD` (cancellable).

It cancels registration before Registrate invokes `creator.get()`, preventing orphan intrusive holders when registries freeze. Because it is marked `@Pseudo`, Mixin skips it when Registrate is not installed.

### 2. `RegisterEventMixin`
Targets `net.neoforged.neoforge.registries.RegisterEvent#register(ResourceKey, ResourceLocation, Supplier)` at `HEAD` (cancellable).

It stops standard NeoForge `DeferredRegister` entries from invoking their factory supplier when `RegistryRedirectManager.shouldCancel` returns `true`.

### 3. `EventHooksCreativeTabMixin` and `BuildCreativeModeTabContentsEventMixin`
Targets `net.neoforged.neoforge.event.EventHooks` (`remap = false`, `require = 0`, method `lambda$onCreativeModeTabBuildContents$0`) and `net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent` (`remap = false`, methods `accept`, `insertFirst`, `insertAfter`, `insertBefore`).

When an item is redirected or aliased, a mod's creative tab builder may pass an empty stack, a stack with count != 1, a duplicate stack whose alias already exists in the tab (inserted by another mod earlier in the event), or an anchor stack that was aliased or removed. `EventHooksCreativeTabMixin` guards the base tab generator, while `BuildCreativeModeTabContentsEventMixin` normalizes counts, skips existing duplicates, and falls back when an anchor stack is missing so NeoForge does not throw an `IllegalArgumentException`. `EventHooksCreativeTabMixin` is marked `require = 0` so a future synthetic lambda renumbering in NeoForge will not crash startup.

### 4. `MixinRecipeManager`
Targets `net.minecraft.world.item.crafting.RecipeManager#apply(Map<ResourceLocation, JsonElement>, ResourceManager, ProfilerFiller)` at `HEAD`.

It reloads `disabled_recipes.json` and mutates the raw JSON map before `RecipeManager` deserializes recipes, so `/reload` stays effective live.

### 5. `MixinItemStack` and `MixinNbtUtils`
Target `ItemStack.parse` and `NbtUtils.readBlockState` (`@ModifyVariable` at `HEAD`).

They copy and rewrite incoming `CompoundTag` instances when saved worlds, inventories, or containers contain old item or block IDs, routing them either to the new target ID or to `ringolib:dummy_item` / `ringolib:dummy_block`. (`ItemStack.parseOptional` delegates directly to `ItemStack.parse`.)

### 6. Architectury compatibility (`ArchitecturyDataMixin`, `ArchitecturyEventListenerMixin`, `ArchitecturyRegistrySupplierMixin`)
Target `dev.architectury.registry.registries.forge.RegistrarManagerImpl$Data`, `$RegistryProviderImpl$EventListener`, and `$RegistrarImpl$1` (`@Pseudo`, `remap = false`).

Architectury registers items and blocks through `RegisterEvent.register(ResourceKey, Consumer<RegisterHelper>)` instead of the `Supplier` overload, bypassing `RegisterEventMixin`. These mixins prune redirected entries before `RegisterHelper.register` runs and resolve `RegistrySupplier.get()`, `isPresent()`, and `getHolder()` to the target entry in `BuiltInRegistries`.

### 7. `MixinBlock`
Targets `net.minecraft.world.level.block.Block#asItem()Lnet/minecraft/world/item/Item;` at `RETURN` (cancellable).

When a block stays registered (for example due to a custom `Block` subclass) while its `BlockItem` is redirected in `Registries.ITEM`, `Block#asItem()` would otherwise cache `Items.AIR` and crash calls like `DeferredBlock#toStack()`. This hook resolves and caches the redirected target item instead.

### 8. Content suppression, loot, advancements, and structures (`MixinBlockStateBase`, `MixinLevelChunk`, `MixinCreativeModeTab`, `MixinCommonHooks`, `MixinServerAdvancementManager`, `MixinStructureTemplateManager`, `MixinStructureTemplate`, `loot.*`, `compat.EmiLootSenderMixin`)
Target `BlockBehaviour.BlockStateBase#getTicker`, `LevelChunk#updateBlockEntityTicker` / `addGameEventListener`, `CreativeModeTab#shouldDisplay`, `CommonHooks#modifyLoot`, `ServerAdvancementManager#apply`, `StructureTemplateManager#tryLoad`, `StructureTemplate#processBlockInfos`, vanilla `loot.*` AST accessors (`LootTable`, `LootPool`, `LootItem`, `EmptyLootItem`, composite entries, `SetComponentsFunction`), and EMI Loot's `LootSender#writeItemStack` (`@Pseudo`).

These hooks keep suppressed blocks and `BlockEntityType`s from attaching tickers to `Level.blockEntityTickers` or subscribing to `GameEvent`s, hide whole creative tabs, rewrite static `LootTable` ASTs during `LootTableLoadEvent` (so viewers like EMI Loot display updated weighted replacements and preserve dynamic-registry `DataComponentPatch` components over the network), handle runtime 1-to-N and fallback loot replacements with `LootContext`, filter disabled advancements, and replace blocks or run `StructureProcessor`s per `.nbt` or Jigsaw template ID without requiring `.nbt` file overrides.

### 9. Client-only JEI and EMI viewer mixins (`com.ringoway.ringolib.mixin.client.*`)
Target JEI (`RecipeManagerInternal`, `RecipeRegistration`, `RecipeCatalystRegistration`, `SubtypeRegistration`, `IngredientManagerBuilder`, `IngredientManager`, `IngredientVisibility`, `GuiHelper`) and EMI / EMI Loot (`EmiRecipes`, `EmiStackList`, `EmiHidden`, `EmiLootReceiverMixin`). All are `@Pseudo`, `remap = false`, `require = 0`, listed only under `"client"` in `mixins.ringolib.json`, and bridged through reflection/helpers in `RecipeViewerReflect` and `EmiLootClientSerializerHelper`.

Mods can disable JEI categories, EMI categories (including JEMI-bridged JEI categories where EMI uses `includeHidden()`), catalysts, subtypes, and dynamic ingredients without adding compile or runtime dependencies on JEI or EMI, and EMI Loot decodes `DataComponentPatch` using the client's synced `RegistryAccess`.

## Client-server boundary

All registry, recipe, config, suppression API, and NBT classes avoid importing `net.minecraft.client.*`, `mezz.jei.*`, or `dev.emi.*` so RingoLib loads on dedicated servers without error. Screen warnings live in `com.ringoway.ringolib.client.ClientWarningHandler`, subscribed via `@EventBusSubscriber(modid = "ringolib", value = Dist.CLIENT)`. JEI and EMI mixins live only in the `"client"` section of `mixins.ringolib.json`.
