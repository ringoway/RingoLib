# Debug commands, modpack auditing, lag profiler, and item bans

RingoLib bundles operator commands (permission level 2) for inspecting items, blocks, entities, recipes, server tick load, and modpack configs. Every command lives under `/ringolib` and has a short `/rl` alias.

---

## Hand inspector (`/rl hand`)

Designed as a direct replacement for CraftTweaker's `/ct hand` when writing datapacks, `migrations.json`, or documentation guides. Instead of `<item:create:brass_sheet>`, `/rl hand` copies and prints the plain `create:brass_sheet` ID.

- `/rl hand` inspects the item in your main hand (or offhand if main hand is empty), automatically copies the plain `namespace:path` ID to your clipboard, and prints:
  - Plain item ID with a `[JSON]` copy badge (`"item": "create:brass_sheet"`).
  - Block ID (for `BlockItem`) and fluid ID + mB volume (for buckets or `IFluidHandler.ITEM` containers).
  - All item tags and block tags (`#c:*` sorted first). Clicking the tag copies `c:plates/brass`, `[#]` copies `#c:plates/brass`, and `[JSON]` copies `"tag": "c:plates/brass"`.
  - Non-default `DataComponents` patch entries, each clickable, plus a `[Copy SNBT]` badge on the section header.
  - All recipes producing this item (including multi-output processing recipes), each with a clickable recipe path and a one-click **`[Disable]`** button that saves the recipe to `disabled_recipes.json` and updates clients immediately.
- `/rl hand id` - prints and copies only the plain item ID.
- `/rl hand tags` - prints item/block tags and copies the first tag.
- `/rl hand components` - prints `DataComponents` and copies the full `DataComponentPatch` SNBT.
- `/rl hand recipes` - lists both output recipes and recipes using the held item as an ingredient.

---

## World target inspector (`/rl look`)

Raycasts up to 16 blocks to inspect whatever block or entity you are looking at, automatically copying its registry ID to your clipboard.

- **Block:** clickable block ID, clickable teleport coordinates, formatted `BlockState` properties (click to copy `namespace:block[prop=val]`), fluid state ID, block tags, and `BlockEntityType` ID with a `[Copy SNBT]` badge for tile data.
- **Entity:** clickable `EntityType` ID, `[UUID]` copy badge, coordinates, health/armor, active `MobEffect` IDs, and all active `AttributeModifier` entries with clickable modifier IDs.

---

## Recipe tools (`/rl recipes`)

- `/rl recipes hand` - shows recipes that output or consume the held item, with `[Disable]` buttons.
- `/rl recipes of <item>` - looks up all recipes producing any registered item ID.
- `/rl recipes disable <pattern>` - appends an exact recipe ID or `*`/`?` wildcard to `config/ringolib/disabled_recipes.json`, removes matching recipes from `RecipeManager` immediately, and pushes the updated recipe list to connected clients without waiting for `/reload`.
- `/rl recipes enable <pattern>` - removes a rule from `config/ringolib/disabled_recipes.json`.
- `/rl recipes list` - prints all active filter rules with clickable `[Enable]` buttons.
- `/rl recipes conflicts` - scans crafting recipes for overlapping inputs that produce different outputs, writes the full report to `config/ringolib/recipe_conflicts.json`, and prints conflicts with `[Disable]` buttons in chat.

---

## Modpack audit & duplicate finder (`/rl audit`)

- `/rl audit status` - prints counts of active item/block/fluid migrations, registry redirects, disabled recipe rules, unmatched rules, and banned items.
- `/rl audit validate` - checks every active `migrations.json` / `registry_redirects` target against live registries, flags `disabled_recipes.json` rules that matched zero recipes, and reports missing IDs in `banned_items.json`.
- `/rl audit duplicates` - scans `#c:*` material tags (`ingots`, `nuggets`, `storage_blocks`, `raw_materials`, `plates`, `dusts`, `gears`, `rods`, `wires`, `gems`, and fluids) plus common naming suffixes across mods, ignores already-redirected items, prints groups in chat, and writes `config/ringolib/duplicate_candidates.json` with a ready-to-edit `migrations_template`.

---

## Registry dumps (`/rl dump`)

- `/rl dump ingots` (alias `/rl ingots dump`) - scans `ingot`/`alloy`/`steel` items that have a block or nugget form, records `block_id`, `nugget_id`, and `#c:*` tags into `config/ringolib/ingots_dump.json`.
- `/rl dump effects [all|harmful|beneficial|neutral]` (alias `/rl effects dump`) - exports mob effects with translated names and hex colors.
- `/rl dump fluids` - exports non-flowing fluids, flowing IDs, fluid blocks, buckets, and `#c:*` tags to `config/ringolib/fluids_dump.json`.

---

## Worldgen & structure debugging (`/locate ore` & `/rl structure`)

- `/locate ore <block_or_tag> [radius]` (also `/rl locate ore <block_or_tag> [radius]`) - supports both single block IDs (`minecraft:iron_ore`) and block tags (`#c:ores/iron`), scans outward in spiral rings (default `512`, max `2048`, max `256` newly loaded chunks per search), and prints clickable teleport coordinates plus chunk count and elapsed milliseconds.
- `/rl structure place <namespace:path> [none|90|180|270]` - places `.nbt` templates from `assets/<namespace>/ponder/<path>.nbt` or `data/<namespace>/structure/<path>.nbt` at your position with optional rotation.

---

## Item ban system (`/rl ban` & `banned_items.json`)

Blocks item pickup, clears banned crafting results, purges banned items from player inventories once per second on the server, and syncs the list to clients for a red tooltip warning.

- `/rl ban add <modid:item_id>` - bans an item in `config/ringolib/banned_items.json` and syncs online players immediately.
- `/rl ban remove <modid:item_id>` - unbans an item.
- `/rl ban list` - lists banned items with clickable IDs and `[Unban]` buttons.
- `/rl ban clear` - empties the ban list.

---

## Server lag profiler (`/rl lag`)

- `/rl lag` - TPS, MSPT, entity count, active ticking `BlockEntity` count, dropped items, loaded chunks, trend, and per-player summaries with clickable teleport coordinates.
- `/rl lag items [count]` - top dropped items and top 5 chunk hotspots.
- `/rl lag entities [count]` - global `EntityType` breakdown.
- `/rl lag tiles [count]` - active ticking `BlockEntity` breakdown by dimension, type, and top 5 chunk hotspots with clickable teleport coordinates.
- `/rl lag players` and `/rl lag curios <player>` - per-player entity density and equipped Curios breakdown by mod.
- `/rl lag near [radius]`, `/rl lag hotspots [count]`, `/rl lag history` - local area scan, `48x48` density hotspots, and last 10 TPS/MSPT snapshots.
- `/rl lag cleanup items|mobs|type|all ...` - removes dropped items or unnamed hostile mobs while sparing `DummyItem`, enchanted items, named items, shulker/container/bundle items, `RARE`/`EPIC` items, and netherite/boss gear.
- `/rl lag auto items <threshold>` and `/rl lag alert <mspt>` - automated background item cleanup (60s interval) and operator MSPT alerts (30s interval).

---

## Player tools & warnings (`/rl player` & `/rl warnings`)

- `/rl player revive [targets]` (shorthand `/rl revive [targets]`) - clears effects and hurt/death timers, repairs `NaN`/`Infinity` max health, heals, and resends health/attribute/ability packets.
- `/rl player force_respawn [targets]` (shorthand `/rl force_respawn [targets]`) - triggers server `PlayerList#respawn` and resyncs client state.
- `/rl player drop_inventory [targets]` (shorthand `/rl drop_inventory [targets]`) - drops main inventory, armor, offhand, and Curios slots.
- `/rl player attributes [target]` - inspects non-default attributes and every active `AttributeModifier` with clickable modifier IDs.
- `/rl warnings reset` - clears `config/ringolib/shown_warnings.json` so `RingoWarningAPI` title/world alerts can be tested again.
