# Quick start for modpack authors

If you build industrial or magic-tech modpacks, you already know the headache: three different mods register their own steel ingot, zinc sheet, or lead block. Players craft one version, find out a machine only accepts the other, and see duplicate 9-to-1 block recipes in JEI and EMI.

Normally you end up disabling recipes with KubeJS or datapacks, rewriting tags, and hoping nobody already stored the old ingots in a chest. RingoLib handles that directly at the NeoForge registry and NBT layers:

1. If `redirect_registry` is enabled, the duplicate item or block is never registered in the first place, and NeoForge aliases its ID to the item you decided to keep.
2. Leftover recipes are dropped before `RecipeManager` loads them, so JEI and EMI never index them.
3. If an existing world already had the old item in a chest or inventory, RingoLib rewrites the ID when the stack loads.

---

## Config file layout

On first launch, RingoLib creates a `ringolib/` folder inside `config/` with two JSON files:

```text
minecraft/
└── config/
    └── ringolib/
        ├── migrations.json        <-- Item, block, fluid, and registry redirect rules
        └── disabled_recipes.json  <-- Exact recipe IDs or wildcard patterns to remove
```

*(If an older pack installation still has `ringolib_migrations.json` or `ringolib_disabled_recipes.json` in the root `config/` folder, RingoLib moves them into `config/ringolib/` automatically on startup.)*

---

## Typical workflow

Suppose your pack includes both *Create Big Cannons* and *TFMG*, and both mods add steel (`createbigcannons:steel_ingot` and `tfmg:steel_ingot`).

1. **Pick the winner.** Decide which mod's item stays in the game. Here we keep `tfmg:steel_ingot` because TFMG has the full blast furnace chain.
2. **Add a redirect in `config/ringolib/migrations.json`:**
   ```json
   {
     "items": [
       {
         "from": "createbigcannons:steel_ingot",
         "to": "tfmg:steel_ingot",
         "required_mod": "tfmg",
         "redirect_registry": true
       }
     ]
   }
   ```
3. **Remove the duplicate compacting recipes in `config/ringolib/disabled_recipes.json`:**
   ```json
   {
     "disabled_recipes": [
       "createbigcannons:steel_block",
       "createbigcannons:steel_ingot_from_block"
     ]
   }
   ```
4. **Launch the game.** CBC's steel ingot is gone from creative tabs and JEI, while any CBC cannon recipe that asks for `createbigcannons:steel_ingot` resolves straight to `tfmg:steel_ingot`.

Note that `migrations.json` touches game registries, so editing it requires a full game restart. `disabled_recipes.json` only touches recipes, so you can edit it while the game is running and type `/reload`.
