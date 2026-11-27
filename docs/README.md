# RingoLib documentation

Documentation for RingoLib on Minecraft 1.21.1 (NeoForge, Java 21).

RingoLib handles two jobs that usually turn into messy glue code in modded Minecraft: deduplicating overlapping items/blocks/fluids across mods, and migrating old IDs so existing worlds do not lose items when a modpack changes.

## Guides for modpack authors

Everything here works through plain JSON files inside `config/ringolib/`. You do not need to write Java code or build a custom addon.

1. [Quick start](modpack_makers/01_quick_start.md) - config layout and the basic five-minute setup.
2. [Migrations and redirects (`migrations.json`)](modpack_makers/02_migrations_and_redirects.md) - merging ingots, blocks, fluids, and arbitrary registries.
3. [Recipe filtering (`disabled_recipes.json`)](modpack_makers/03_recipe_filtering.md) - removing leftover recipes with exact IDs, `*` wildcards, and `/reload`.
4. [World preservation and placeholders](modpack_makers/04_world_preservation.md) - what happens to items in chests when a target mod is missing.
5. [Modpack cookbook](modpack_makers/05_cookbook_examples.md) - tested configs for TFMG, Oreganized, and Create Big Cannons.
6. [Debug commands, lag diagnostics, and item bans](modpack_makers/06_commands_and_ban.md) - `/rl hand`, `/rl look`, `/rl recipes`, `/rl audit`, `/rl dump`, `/rl lag tiles`, `/locate ore`, and `banned_items.json`.

## Guides for mod developers

Internal mechanics, Mixin hooks, and the public Java API if you are calling RingoLib directly from your own mod.

1. [Architecture and lifecycle](developers/01_architecture_internals.md) - when aliases, registration cancels, creative tab cleanup, loot rewriting, structure processors, and NBT hooks run.
2. [Java API reference](developers/02_java_api.md) - `RingoMigrationAPI`, `RegistryRedirectManager`, `RecipeFilterManager`, `RingoContentAPI`, and `RingoWarningAPI`.
3. [Create Registrate compatibility](developers/03_registrate_compatibility.md) - how `@Pseudo RegistrateRegistrationMixin` prevents unregistered intrusive holder crashes.

## Feature overview

| Area | What RingoLib does | Entry point |
| :--- | :--- | :--- |
| Material deduplication | Cancels redundant registrations and points the old ID at your chosen mod's item | `migrations.json` / `RingoMigrationAPI` |
| Content suppression | Disables blocks, items, fluids, block entities, tabs, advancements, and trades | `RingoContentAPI` |
| Registrate support | Stops Registrate entries before `creator.get()` creates an intrusive holder | `RegistrateRegistrationMixin` |
| Registry aliases | Routes recipes, tags, and saved IDs through NeoForge's `Registry#addAlias` | `RegistryRedirectManager.applyAliases` |
| Fluid redirects | Redirects still fluid, `flowing_` fluid, liquid block, and `neoforge:fluid_type` in one rule | `registerFluidRedirect` |
| Creative tab cleanup | Strips redirected or suppressed items out of creative tabs | `BuildCreativeModeTabContentsEvent` |
| Recipe and viewer filtering | Drops disabled recipes before `RecipeManager` parses them and hides JEI/EMI categories | `disabled_recipes.json` / `RecipeFilterManager` / `RingoContentAPI` |
| Loot and structure rewriting | Rewrites static loot tables (with EMI Loot sync) and replaces `.nbt`/jigsaw structure blocks | `RingoContentAPI` (`.loot(...)`, `.structure(...)`) |
| Save preservation | Holds items from missing mods in `ringolib:dummy_item` and restores them later | `DummyItem` / `DummyBlock` |
| Warning screens | Shows one-time `AlertScreen` notices on the title screen or on world join | `RingoWarningAPI` |
