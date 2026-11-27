# RingoLib

[![License: All Rights Reserved](https://img.shields.io/badge/License-All%20Rights%20Reserved-red.svg)](LICENSE)
[![Minecraft: 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-green.svg)](https://neoforged.net/)
[![NeoForge](https://img.shields.io/badge/Loader-NeoForge-orange.svg)](https://neoforged.net/)

RingoLib is a NeoForge 1.21.1 library built by Ringoway to deduplicate overlapping mod content, suppress unwanted features, filter recipes, and keep existing worlds intact when mods change.

Put two or three Create addons together and you immediately get two kinds of steel, two kinds of lead, duplicate 9-to-1 compacting recipes, and broken chests whenever you swap one addon out for another. I built RingoLib for my own mods and setups so I could stop writing throwaway KubeJS scripts and empty datapack overrides for every single ingot.

- Merges duplicate items, blocks, and fluids at registration time (including mods built on Create's Registrate) and wires up native NeoForge registry aliases so tags and recipes point to the item you kept.
- Suppresses unwanted blocks, items, fluids, block entities, creative tabs, villager trades, advancements, and JEI/EMI recipe categories through `RingoContentAPI`.
- Rewrites static loot tables and NBT/jigsaw structure blocks without shipping `.json` or `.nbt` datapack overrides, with EMI Loot support for datapack-registry item components.
- Strips unwanted recipes out of `RecipeManager` by exact ID, `*` / `?` wildcards, output item, or JSON predicates before JEI, EMI, or the vanilla recipe book load them.
- Protects existing saves by wrapping missing migrated items into placeholder objects instead of letting Minecraft delete them into `minecraft:air`, then restores them once the target mod is back.

## Documentation

Full guides live in [`docs/`](docs/README.md).

### Modpack configuration (JSON, no Java required)
- [01. Quick start](docs/modpack_makers/01_quick_start.md) - where the config files live and how a typical cleanup workflow looks.
- [02. Migrations and registry redirects](docs/modpack_makers/02_migrations_and_redirects.md) - syntax and behavior of `config/ringolib/migrations.json`.
- [03. Recipe filtering](docs/modpack_makers/03_recipe_filtering.md) - hiding duplicate recipes in `config/ringolib/disabled_recipes.json` and reloading live with `/reload`.
- [04. World preservation](docs/modpack_makers/04_world_preservation.md) - how `DummyItem` and `DummyBlock` keep player chests safe.
- [05. Modpack cookbook](docs/modpack_makers/05_cookbook_examples.md) - ready-to-use configs for TFMG, Oreganized, and Create Big Cannons.
- [06. Debug commands, lag diagnostics, and item bans](docs/modpack_makers/06_commands_and_ban.md) - `/rl hand` (`/ct hand` replacement), `/rl look`, `/rl recipes`, `/rl audit duplicates`, `/rl dump`, `/rl lag tiles`, `/locate ore`, and `banned_items.json`.

### Developer docs (Java API & internals)
- [01. Architecture and lifecycle](docs/developers/01_architecture_internals.md) - NeoForge event order and all Mixin entry points.
- [02. Java API reference](docs/developers/02_java_api.md) - `RingoMigrationAPI`, `RegistryRedirectManager`, `RecipeFilterManager`, `RingoContentAPI`, and `RingoWarningAPI`.
- [03. Registrate compatibility](docs/developers/03_registrate_compatibility.md) - why cancelling Registrate entries normally crashes NeoForge with intrusive holder errors, and how RingoLib avoids it.

## Quick config example

### `config/ringolib/migrations.json`
```json
{
  "_comment": "Redirect Create Big Cannons steel to TFMG",
  "items": [
    {
      "from": "createbigcannons:steel_ingot",
      "to": "tfmg:steel_ingot",
      "required_mod": "tfmg",
      "redirect_registry": true
    }
  ],
  "blocks": [
    {
      "from": "createbigcannons:steel_block",
      "to": "tfmg:steel_block",
      "required_mod": "tfmg",
      "redirect_registry": true
    }
  ],
  "fluids": [
    {
      "from": "createbigcannons:molten_steel",
      "to": "tfmg:molten_steel",
      "required_mod": "tfmg",
      "redirect_registry": true
    }
  ]
}
```

### `config/ringolib/disabled_recipes.json`
```json
{
  "disabled_recipes": [
    "createbigcannons:steel_block",
    "createbigcannons:steel_ingot_from_*"
  ]
}
```

## Quick Java API example

```java
// Redirect a single item
RingoMigrationAPI.registerItemRedirect("oldmod:old_item", "newmod:new_item", "newmod");

// Redirect both a block and its BlockItem
RingoMigrationAPI.registerBlockAndItemRedirect("oldmod:old_block", "newmod:new_block", "newmod");

// Redirect still fluid, flowing fluid, liquid block, and neoforge:fluid_type together
RingoMigrationAPI.registerFluidRedirect("oldmod:oil", "newmod:oil", "newmod");
```

## Building from source

Requires JDK 21:

```bash
./gradlew clean build
```

The compiled mod jar is written to `build/libs/ringolib-<version>.jar`.

## License

All Rights Reserved. See [LICENSE](LICENSE) for details.
