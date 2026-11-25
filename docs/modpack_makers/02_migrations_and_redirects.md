# Migrations and redirects (`migrations.json`)

`config/ringolib/migrations.json` controls both save-file ID migrations and full registry redirects.

---

## File structure

The root JSON object has four optional arrays:
- `"items"` for `minecraft:item` entries
- `"blocks"` for `minecraft:block` entries
- `"fluids"` for fluids (automatically covers four related registries)
- `"registry_redirects"` for any other NeoForge or vanilla registry

```json
{
  "_comment": "Define custom modpack migrations and registry redirects",
  "items": [],
  "blocks": [],
  "fluids": [],
  "registry_redirects": []
}
```

If one entry in the file has a typo or malformed ID, RingoLib logs a warning and skips that single rule instead of aborting the whole config.

---

## Entry fields

Every object inside `"items"`, `"blocks"`, and `"fluids"` uses the same fields:

| Field | Type | Required | What it does |
| :--- | :--- | :--- | :--- |
| `from` | String | Yes | Source `ResourceLocation` to replace (for example, `"tfmg:lead_ingot"`). |
| `to` | String | Yes | Target `ResourceLocation` to keep (for example, `"oreganized:lead_ingot"`). |
| `required_mod` | String | No | Mod ID that must be loaded for this rule to take effect. If that mod is missing, RingoLib leaves the original entry alone during registration (or wraps saved items in a placeholder). |
| `redirect_registry` | Boolean | No | Defaults to `false`. When `false`, RingoLib only rewrites NBT when loading worlds. When `true`, it also cancels registration of `from`, registers a NeoForge registry alias, and strips duplicates out of creative tabs. |
| `description` | String | No | Optional note for yourself or other pack maintainers. Ignored by the game. |

---

## Merging items (`items`)

Use `"items"` for ingots, nuggets, raw ores, sheets, tools, and buckets:

```json
"items": [
  {
    "from": "tfmg:raw_lead",
    "to": "oreganized:raw_lead",
    "required_mod": "oreganized",
    "redirect_registry": true,
    "description": "Keep Oreganized raw lead"
  },
  {
    "from": "tfmg:lead_ingot",
    "to": "oreganized:lead_ingot",
    "required_mod": "oreganized",
    "redirect_registry": true
  }
]
```

When `redirect_registry` is `true` and `oreganized` is installed:
1. RingoLib intercepts the `DeferredRegister` or Registrate call for `tfmg:lead_ingot` and cancels it.
2. It calls `registry.addAlias("tfmg:lead_ingot", "oreganized:lead_ingot")` on `minecraft:item`, so recipes and tags referencing the old ID resolve to Oreganized's ingot.
3. It removes the redirected stack from TFMG's creative tab so players do not see two identical ingots in TFMG and Oreganized tabs.
4. Any `tfmg:lead_ingot` already saved in a player's world loads as `oreganized:lead_ingot`.

---

## Merging blocks (`blocks`)

In Minecraft, a placed block (`minecraft:block`) and the item you hold in your hand (`minecraft:item`) live in two separate registries. When you merge a storage block or ore block, list it in **both** `"blocks"` and `"items"`:

```json
"items": [
  {
    "from": "createbigcannons:steel_block",
    "to": "tfmg:steel_block",
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
]
```

---

## Merging fluids (`fluids`)

Fluids in NeoForge are annoying to redirect by hand because a single fluid is split across multiple registries: the still fluid, the flowing fluid, the placed liquid block, the `neoforge:fluid_type`, and the bucket item.

Adding one entry to `"fluids"` handles the four fluid/block registries at once:
- `minecraft:fluid`: `<ns>:<name>` -> `<target_ns>:<target_name>`
- `minecraft:fluid`: `<ns>:flowing_<name>` -> `<target_ns>:flowing_<target_name>`
- `neoforge:fluid_type`: `<ns>:<name>` -> `<target_ns>:<target_name>`
- `minecraft:block`: `<ns>:<name>` -> `<target_ns>:<target_name>`

Bucket names are not standardized across mods (some use `_bucket`, others `bucket_of_`), so redirect the bucket explicitly in `"items"`:

```json
"fluids": [
  {
    "from": "createbigcannons:molten_steel",
    "to": "tfmg:molten_steel",
    "required_mod": "tfmg",
    "redirect_registry": true
  }
],
"items": [
  {
    "from": "createbigcannons:molten_steel_bucket",
    "to": "tfmg:molten_steel_bucket",
    "required_mod": "tfmg",
    "redirect_registry": true
  }
]
```

---

## Other registries (`registry_redirects`)

If you need to alias entries in another registry (mob effects, entity types, block entity types, sound events), use `"registry_redirects"` and pass the target `"registry"` key explicitly:

```json
"registry_redirects": [
  {
    "registry": "minecraft:mob_effect",
    "from": "oldmod:bleeding",
    "to": "newmod:bleeding",
    "required_mod": "newmod"
  }
]
```
