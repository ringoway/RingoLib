# Recipe filtering (`disabled_recipes.json`)

`config/ringolib/disabled_recipes.json` removes unwanted or duplicate recipes before Minecraft parses them.

## Why you still need this after a redirect

Redirecting `createbigcannons:steel_ingot` to `tfmg:steel_ingot` aliases the item ID, which is great for machine recipes. The catch is that CBC's own crafting recipe (`9 steel ingots -> 1 steel block`) also stays valid and now uses TFMG steel ingots too. Players end up seeing two identical steel block recipes in JEI and EMI.

Instead of shipping datapacks full of empty `"neoforge:false"` condition overrides, list those recipe IDs in `disabled_recipes.json`. RingoLib strips them from the recipe map at the head of `RecipeManager.apply(...)`, so neither the server nor JEI/EMI ever loads them.

## File format

```json
{
  "_comment": "Define recipes to disable by full ID or wildcard pattern (e.g. tfmg:crafting/materials/lead_*)",
  "disabled_recipes": [
    "tfmg:crafting/materials/lead_block_from_compacting",
    "tfmg:crafting/materials/lead_ingot_from_decompacting",
    "createbigcannons:steel_block",
    "createbigcannons:steel_ingot_from_block",
    "createbigcannons:steel_ingot_from_nuggets",
    "createbigcannons:steel_scrap"
  ]
}
```

## Exact IDs vs. wildcards

### Exact recipe IDs
Pass the full `namespace:path` of the recipe (press `F3 + H` in-game and hover the recipe output in JEI/EMI to copy the recipe ID):

```json
"disabled_recipes": [
  "createbigcannons:steel_block"
]
```

### Wildcards (`*` and `?`)
Use `*` to match any sequence of characters, or `?` to match a single character. This is handy when a mod puts an entire material's compacting recipes under a predictable folder or naming pattern:

```json
"disabled_recipes": [
  "unwantedmod:broken_category/*",
  "somemod:crafting/*_from_compacting"
]
```

All literal characters outside `*` and `?` are escaped automatically, so dots, slashes, and hyphens in mod paths behave as plain characters instead of regex operators.

## Live reload with `/reload`

`RingoLibConfig.loadDisabledRecipes()` runs every time `RecipeManager.apply` executes. You can keep Minecraft open, edit `config/ringolib/disabled_recipes.json`, run `/reload` in chat, and check JEI or EMI right away without restarting the client or server.
