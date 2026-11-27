# Changelog

All notable changes to this project will be documented in this file.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

## [1.1.0] - 2026-11-27

### Added
- Added content suppression system to disable or replace mod blocks, items, fluids, and block entities without breaking existing worlds
- Added JEI and EMI integration to hide suppressed items, fluids, recipes, catalysts, and entire recipe categories
- Added creative tab, advancement, and villager trade filtering for suppressed content
- Added loot table item stripping and weighted loot replacement with preserved stack counts and custom item data
- Added EMI Loot compatibility so replaced chest and dungeon loot displays accurately in-game, including items with custom mod data
- Added block replacement for NBT and jigsaw structures during world generation
- Added automatic replacement of suppressed blocks when existing chunks load
- Added conditional registry redirects and expanded recipe filtering by output item or custom rules
- Added content suppression stats and world replacement checks to `/ringolib status` and `/ringolib audit`

### Changed
- Separated config recipe rules from mod-registered rules so `/reload` preserves mod-defined recipe filters
- Reduced server overhead for placeholder blocks and items: blocks check restoration once on load, and inventory items check once per second
- Removed unnecessary item drop creation when clearing banned items from player inventories and synced slot updates immediately

### Fixed
- Fixed a startup race where early mod registrations could run before content suppression rules loaded
- Fixed chat color formatting in the `/ringolib audit` report

## [1.0.0] - 2026-11-25

### Added
- Initial release for NeoForge 1.21.1
