# Modpack cookbook

Ready-to-use configuration snippets from my own setups for common Create addons.

---

## Example 1: Merging TFMG lead into Oreganized

### Context
Both **TFMG** (Create: The Factory Must Grow) and **Oreganized** register raw lead, raw lead blocks, lead ingots, lead nuggets, and lead storage blocks. Here we keep Oreganized's lead variants and retire TFMG's duplicates.

### `config/ringolib/migrations.json`
```json
{
  "_comment": "Redirect TFMG lead items and blocks to Oreganized",
  "items": [
    {
      "from": "tfmg:raw_lead_block",
      "to": "oreganized:raw_lead_block",
      "required_mod": "oreganized",
      "redirect_registry": true
    },
    {
      "from": "tfmg:lead_block",
      "to": "oreganized:lead_block",
      "required_mod": "oreganized",
      "redirect_registry": true
    },
    {
      "from": "tfmg:lead_nugget",
      "to": "oreganized:lead_nugget",
      "required_mod": "oreganized",
      "redirect_registry": true
    },
    {
      "from": "tfmg:raw_lead",
      "to": "oreganized:raw_lead",
      "required_mod": "oreganized",
      "redirect_registry": true
    },
    {
      "from": "tfmg:lead_ingot",
      "to": "oreganized:lead_ingot",
      "required_mod": "oreganized",
      "redirect_registry": true
    }
  ],
  "blocks": [
    {
      "from": "tfmg:raw_lead_block",
      "to": "oreganized:raw_lead_block",
      "required_mod": "oreganized",
      "redirect_registry": true
    },
    {
      "from": "tfmg:lead_block",
      "to": "oreganized:lead_block",
      "required_mod": "oreganized",
      "redirect_registry": true
    }
  ]
}
```

### `config/ringolib/disabled_recipes.json`
```json
{
  "disabled_recipes": [
    "tfmg:crafting/materials/lead_block_from_compacting",
    "tfmg:crafting/materials/lead_ingot_from_decompacting",
    "tfmg:crafting/materials/lead_ingot_from_compacting",
    "tfmg:crafting/materials/lead_nugget_from_decompacting",
    "tfmg:crafting/materials/raw_lead_block",
    "tfmg:crafting/materials/raw_lead"
  ]
}
```

---

## Example 2: Merging Create Big Cannons steel into TFMG

### Context
**Create Big Cannons** (CBC) adds its own steel ingots, steel blocks, and molten steel fluid for casting cannon barrels. **TFMG** already has blast furnaces and its own steel chain. Redirecting CBC steel to TFMG lets players cast cannons out of TFMG's molten steel without dealing with two incompatible fluids in pipes and basins.

### `config/ringolib/migrations.json`
```json
{
  "_comment": "Redirect CBC steel and molten steel to TFMG",
  "items": [
    {
      "from": "createbigcannons:steel_ingot",
      "to": "tfmg:steel_ingot",
      "required_mod": "tfmg",
      "redirect_registry": true
    },
    {
      "from": "createbigcannons:steel_block",
      "to": "tfmg:steel_block",
      "required_mod": "tfmg",
      "redirect_registry": true
    },
    {
      "from": "createbigcannons:molten_steel_bucket",
      "to": "tfmg:molten_steel_bucket",
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
    "createbigcannons:steel_ingot_from_block",
    "createbigcannons:steel_ingot_from_nuggets",
    "createbigcannons:steel_scrap",
    "createbigcannons:mixing/alloy_steel"
  ]
}
```

With both files in place:
- Cannon parts and shells craft directly with `tfmg:steel_ingot`.
- Fluid pipes, tanks, and casting moulds use `tfmg:molten_steel` everywhere.
- Duplicate compacting and alloying recipes disappear from JEI and EMI.
