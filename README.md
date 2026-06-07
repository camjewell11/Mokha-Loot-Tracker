# Mokha Loot Tracker

[![Donate](https://img.shields.io/badge/Donate-PayPal-blue?logo=paypal)](https://paypal.me/camjewell)

A comprehensive RuneLite plugin for tracking loot, supplies, and deaths during Doom of Mokhaiotl encounters in Old School RuneScape. Features detailed persistent statistics, customizable loot alerts, automatic Mokhaiotl Cloth valuation, charged weapon cost tracking, and a modern interactive UI for maximizing your Mokhaiotl profits.

<img width="1918" height="1032" alt="image" src="https://github.com/user-attachments/assets/d59aadda-bba9-45bf-839a-4d8df18add25" />

<img width="242" height="914" alt="image" src="https://github.com/user-attachments/assets/e205c36f-a14d-4664-a5a7-cfb9e6c14c86" />

## Features

- **Death Tracking**: Automatically records each death in the Mokha arena, including wave and lost loot.
- **Lost Loot Monitoring**: Tracks the value and item breakdown of unclaimed loot lost on death, per wave.
- **Claimed Loot Tracking**: Tracks loot successfully claimed, with per-wave and combined breakdowns.
- **Per-Wave & Combined Views**: Expandable/collapsible sections for claimed and unclaimed loot by wave, plus a combined all-waves view with bullet-style formatting and tooltips.
- **Current Run View Toggle**: Current Run supports both summary and by-wave item views, with an arrow-style toggle in the section header (defaults to summary).
- **Previous Run Wave Breakdown**: Previous Run now supports detailed by-wave breakdown with collapsible wave rows and combined section behavior.
- **Display Sorting Options**: Sort displayed loot and supplies alphabetically or by total value, including per-wave and combined views (default: value-descending).
- **Profit/Loss Calculation**: Shows total claimed minus supply cost, color-coded (green for profit, red for loss).
- **Supplies Tracking**: Tracks supplies consumed per run (live) and across all runs (historical), including potions (dose-normalized), runes (including rune pouch and Dizana's quiver), and other consumables.
- **Historical Entry Editing**: Optional edit mode allows removing historical claimed/unclaimed/supply entries directly from the side panel with confirmation and immediate total recalculation.
- **Configurable Item Value Exclusion**: Toggles to ignore Sun-kissed Bones and Spirit Seeds (untradable) in calculations.
- **Adjusted Loot Screen Display**: Optional loot-window value rewrite showing both totals: `original gp (adjusted gp)`.
- **Mokhaiotl Cloth Value Override**: Supports manual cloth value from settings, with automatic component-based calculation as fallback when left blank.
- **Loot Alerts**: Configurable notifications for specific items with customizable quantity thresholds (chat message + sound effect).
- **Ultra-Valuable Item Filter**: Optionally exclude items worth more than 20 million GP from totals (uniques filter).
- **Modern UI**: Collapsible/expandable sections, left-padded bullet formatting, hover tooltips for price per item, and color-coded highlights for ultra-valuable items.
- **Persistent Statistics**: All data is saved per-account and persists across sessions, including historical claimed/unclaimed loot, supplies, and deaths.
- **Clipboard Export/Import for Historical Data**: Export the active character's historical profile to clipboard and import from clipboard with overwrite support.
- **Player-Safe Historical Import**: Import validates the payload player key and only allows overwrite for the currently logged-in character.
- **Comprehensive Side Panel**: Summary, current run, claimed/unclaimed loot by wave, supplies (current/historical), performance metrics, and interactive controls.
- **Data Migration**: Automatically migrates old config-based data to new file-based storage.
- **Performance Metrics**: Tracks prayer points used, HP lost/regained, special attack uses, and venom applications per run. Consumable healing and passive regen are excluded for accuracy.
- **Dryness Tracking**: Shows expected vs. actual unique drops based on your historical wave completions, with cumulative probability calculations.
- **Highscores & Collection Log Sync**: Automatically syncs wave completion counts from the Dom Scoreboard and unique item counts from the Collection Log.
- **Blowpipe Live Ammo Tracking**: Tracks blowpipe ammo consumption using server-pushed varps — no longer requires manually opening the blowpipe interface to register usage.
- **Charged Weapon Tracking (Beta)**: Tracks charges consumed by powered staves, crystal equipment, blowpipes, and other charged weapons. Calculates per-charge supply cost using each weapon's charge recipe and displays a full ingredient breakdown on hover. See [Charged Weapon Tracking](#charged-weapon-tracking-beta) for details.

## How It Works

Mokha Loot Tracker automatically detects when you enter the Mokha arena and tracks all loot, supplies, and deaths:

**On Death:**

- Records the wave and all unclaimed loot (itemized, with quantities and values).
- Adds lost loot to historical unclaimed statistics.
- Pauses supplies tracking while dead to avoid gravestone wipes.
- Increments death count and updates the side panel.

**On Claiming Loot:**

- Detects "Claim and Leave" → "Leave" sequence.
- Records all loot claimed per wave (itemized, with quantities and values).
- Updates historical claimed statistics and profit/loss.
- Tracks supplies consumed and adds to historical totals.

**During Runs:**

- Tracks supplies consumed in real time (potions, runes, etc.), with dose normalization for potions.
- Monitors loot window for new loot per wave.
- Provides a real-time overlay (if enabled) showing potential loot loss.

## Side Panel Overview

Click the Mokha Loot icon in the RuneLite sidebar to view:

- **Summary**: Total claimed, supply cost, profit/loss, total unclaimed, claim/unclaim ratio, total claims, total deaths, dryness statistics.
- **Current Run**: Real-time value and item breakdown of unclaimed loot for the current run, with summary/by-wave toggle (summary by default).
- **Previous Run**: Last run status/value plus loot and supplies, with collapsible section states and by-wave breakdown.
- **Claimed Loot by Wave**: Expandable/collapsible sections for each wave (1-8, 9+), with itemized loot and values. Combined all-waves view available.
- **Unclaimed Loot by Wave**: Same as above, for loot lost on death. Combined all-waves view available.
- **Supplies Used (Current Run)**: Live supplies consumed, with dose/rune normalization and values.
- **Supplies Used (All Time)**: Historical supplies consumed across all runs.
- **Performance Metrics**: Per-run display of prayer used, HP lost/regained, special attack uses, and venom applications.

All sections support:

- Expand/collapse (▾/▸) for per-wave or combined views.
- Bullet-style formatting with left padding for items.
- Hover tooltips showing price per item.
- Color-coding for ultra-valuable items (gold if >20M GP or "Dom").

## Controls

- **Recalculate Totals**: Recalculates all statistics and reapplies ignore/exclude settings. Disabled during active runs.
- **Clear Claimed Historical Data**: Clears claimed historical loot only (with confirmation), then refreshes panel values.
- **Clear Unclaimed Historical Data**: Clears unclaimed historical loot only (with confirmation), then refreshes panel values.
- **Clear Supplies Historical Data**: Clears historical supplies usage only (with confirmation), then refreshes panel values.
- **Import Stats**: Imports historical stats from clipboard and overwrites the currently logged-in character's historical data after confirmation.
- **Export Stats**: Exports the currently logged-in character's historical stats to clipboard.
- **Clear All Data**: Removes all tracked data for the current account (with confirmation).

When **Enable Historical Edit Mode** is on in plugin settings:

- Click historical item rows (left or right click) in claimed/unclaimed wave breakdowns, combined views, or historical supplies.
- Confirm removal in the prompt.
- The plugin removes the historical entry, updates affected totals/values, refreshes the panel, and persists changes immediately.

## Loot Alerts

Configure custom notifications to alert you when specific loot items meet or exceed a quantity threshold:

1. Open RuneLite Configuration → Mokha Loot Tracker
2. Find the "Loot Alerts" text area
3. Add one alert per line in format: `Item Name, Minimum Quantity`
4. Examples:
   - `Dragon Pickaxe, 1` - Alerts on any Dragon Pickaxe drop
   - `Blood Rune, 1000` - Alerts when 1000+ Blood Runes are visible
   - `Mokhaiotl Cloth, 5` - Alerts when 5+ Cloth pieces are found

When triggered, alerts display a chat message and play a notification sound. Alerts check against the total visible loot in the current wave window.

## Charged Weapon Tracking (Beta)

> **Beta feature** — Enable via *Charged Weapon Tracking (Beta)* in plugin settings. Accuracy depends on checking your weapons before and after every run. Results may be approximate for the Eye of Ayak (see note below).

### How it works

When this feature is enabled, an overlay appears at the arena entrance prompting you to check each detected charged weapon. Right-click your weapon and choose **Check** — this records the current charge count. After the run ends, the overlay reappears so you can check again. The plugin calculates the difference between your starting and ending charge counts, then uses each weapon's known charge recipe to work out exactly what it cost to run those charges.

The result appears in the **Supplies Used (All Time)** panel as a line item such as:

> `Trident of the Swamp Charge ×10   4,910 gp`

Hovering over the entry shows the full ingredient breakdown (e.g. *10× Death rune, 10× Chaos rune, 50× Fire rune, 10× Zulrah's scale*).

### Supported weapons and charge costs

| Weapon | Cost per charge |
| --- | --- |
| Toxic Blowpipe | Exact dart + scale consumption tracked directly |
| Blazing Blowpipe | Exact dart + scale consumption tracked directly |
| Camphor Blowpipe | Exact dart + scale consumption tracked directly |
| Ironwood Blowpipe | Exact dart + scale consumption tracked directly |
| Rosewood Blowpipe | Exact dart + scale consumption tracked directly |
| Venator Bow | 1 ancient essence |
| Trident of the Seas | 1 death rune + 1 chaos rune + 5 fire runes |
| Trident of the Swamp | 1 death rune + 1 chaos rune + 5 fire runes + 1 Zulrah's scale |
| Sanguinesti Staff | 3 blood runes |
| Tumeken's Shadow | 2 soul runes + 5 chaos runes |
| Eye of Ayak | 1 demon tear *(see note)* |
| Scythe of Vitur | 2 blood runes + 1 vial of blood per 100 charges |
| Blade of Saeldor | 1 crystal shard per 100 charges *(untradeable — quantity only)* |
| Bow of Faerdhinen | 1 crystal shard per 100 charges *(untradeable — quantity only)* |
| Crystal Bow | 1 crystal shard per 100 charges *(untradeable — quantity only)* |
| Crystal Halberd | 1 crystal shard per 100 charges *(untradeable — quantity only)* |
| Serpentine Helm | 1 Zulrah's scale |

Crystal shard weapons (Blade of Saeldor, Bow of Faerdhinen, Crystal Bow, Crystal Halberd) are recharged with untradeable crystal shards — the shard count is tracked and shown in the tooltip, but contributes 0 gp to your supply cost.

**Eye of Ayak note**: the Eye can be recharged with either 2 death runes + 1 chaos rune *or* 1 demon tear. The plugin defaults to demon tear since it cannot detect which method you used. If you recharge with runes, the displayed cost will not match your actual spend.

### Tips for accurate tracking

- Check every detected weapon before crossing into the arena, then again immediately after leaving. Skipping a check cancels tracking for that run.
- The overlay lists each weapon with a checkbox — only when all are ticked does the overlay dismiss and tracking begin/end.
- Charges consumed between games (e.g. from other content) will be counted if you do not check directly at the arena entrance. For best accuracy, check only at the arena.

## Configuration Options

Accessible via RuneLite Configuration panel → Mokha Loot Tracker:

- **Ignore Sun-kissed Bones Value**: Set value to 0 for Sun-kissed Bones (untradable, base value 8,000 GP).
- **Ignore Spirit Seeds Value**: Set value to 0 for Spirit Seeds (untradable, base value 140,000 GP).
- **Exclude Uniques**: Exclude items worth more than 20 million GP from claimed/unclaimed totals.
- **Show Adjusted Loot Value Display**: Rewrites the collect-loot window value text to show `original gp (adjusted gp)` using ignore toggles.
- **Mokhaiotl Cloth Value**: Manual override for cloth value. Leave blank to use automatic calculation based on component prices (Confliction Gauntlets - 10000×Demon Tear - Tormented Bracelet).
- **Loot Alerts**: Configure custom notifications for specific loot items. Format: `Item Name, Minimum Quantity` (one per line). Triggers chat message and sound when threshold is met.
- **Display Sort Mode**: Choose how displayed loot/supplies are ordered (`By Value` or `Alphabetical`). Default is `By Value`.
- **Enable Historical Edit Mode**: Enables click-to-remove for historical entries in the side panel with confirmation and immediate recalculation.
- **Charged Weapon Tracking (Beta)**: Enables the charged weapon checklist overlay and per-charge cost tracking. See [Charged Weapon Tracking](#charged-weapon-tracking-beta) for full details.

## Data Storage & Persistence

- All statistics are stored locally in dedicated JSON files per account in your RuneLite directory
- File location: `.runelite/mokhaloot/historical-data-{accountHash}.json`
- Data includes: historical claimed/unclaimed loot (by wave and item), supplies used, total claimed, supply cost, claims, deaths, and more
- Data is automatically migrated from old config-based storage if present on first load
- Each account's data is completely separate and persistent across sessions

## Technical Details

- **Loot & Supplies Tracking**: Uses in-game events and widget parsing to track loot and supplies, including rune pouch, equipment, and Dizana's quiver.
- **Data Structures**:
  - `Map<Integer, Map<String, ItemAggregate>>` for claimed/unclaimed loot by wave.
  - `Map<String, ItemAggregate>` for supplies used.
  - `ItemAggregate` holds name, total quantity, price per item, total value.
- **UI**: Java Swing-based panel with custom styling, collapsible sections, tooltips, and color-coding.
- **Persistence**: Data is saved in `mokhaloot/historical-data-{accountHash}.json` under your RuneLite directory, with automatic migration from old config-based storage.
- **Price Calculations**:
  - Uses RuneLite ItemManager for GE prices
  - Normalizes potion prices to per-dose values
  - Dynamically calculates Mokhaiotl Cloth value from component prices
- **Configurable**: All major toggles and filters are exposed in the config panel.

## Installation

### From Plugin Hub

1. Open the RuneLite Plugin Hub
2. Search for "Mokha Loot Tracker"
3. Click Install

### Manual Installation (Development)

1. Clone this repository
2. Build the plugin: `./gradlew shadowJar`
3. Place the JAR from `build/libs/` into your `.runelite/sideloaded-plugins/` folder
4. Restart RuneLite

## Requirements

- RuneLite client
- Old School RuneScape membership (Mokhaiotl is members content)

## Support

For bugs, feature requests, or questions:

- Open an issue on the [GitHub repository](https://github.com/camjewell11/Mokha-Loot-Tracker/issues)
- Submit a pull request for contributions
- Check existing issues before creating new ones

Please include:

- RuneLite version
- Plugin version
- Steps to reproduce (for bugs)
- Screenshots or logs if applicable

## License

This plugin is open source and available under standard RuneLite plugin licensing.

## Changelog

### v8.1

- **Charged Weapon Tracking (Beta)**: New opt-in feature that tracks charge consumption for all major charged weapons usable at Doom of Mokhaiotl. An overlay prompts you to check your weapons before and after each run; the plugin records the charge delta and calculates supply cost using each weapon's known charge recipe. Results appear in the historical supplies panel with a hover tooltip showing the full ingredient breakdown.
  - Blowpipe variants (Toxic, Blazing, Camphor, Ironwood, Rosewood): exact darts and scales tracked per run.
  - Powered staves (Trident of the Seas, Trident of the Swamp, Sanguinesti Staff, Tumeken's Shadow, Eye of Ayak): cost calculated per charge from rune/reagent recipes.
  - Scythe of Vitur: blood runes and vials of blood calculated per charge.
  - Crystal equipment (Blade of Saeldor, Bow of Faerdhinen, Crystal Bow, Crystal Halberd): crystal shard usage tracked; no GP cost since shards are untradeable.
  - Venator Bow: ancient essence tracked per shot.
  - Serpentine Helm: Zulrah's scales tracked per charge.
- **PayPal Donate button** added to the README.

### v8.0

- **Performance Metrics**: New panel section tracking prayer used, HP lost/regained, special attack uses, and venom applications per run. Consumable healing and passive +1 HP regen ticks are excluded to keep metrics action-focused. Food consumed outside the arena is not counted.
- **Dryness Tracking**: Displays expected vs. actual unique drops using cumulative probability math across your historical wave completions. Shows dry streak across all tracked runs.
- **Highscores & Collection Log Sync**: Automatically reads wave completion counts from the Dom Scoreboard widget and unique item counts from the Collection Log when they are opened in-game. Supports both "Personal Completions" and "Universe" scoreboard formats.
- **Blowpipe Live Ammo Tracking**: Blowpipe ammo and dart consumption now use server-pushed `BUFF_BAR` varps (updated every tick without player interaction). Eliminates false positives from outside-arena usage — no longer requires manually opening the blowpipe interface to register or baseline ammo counts.
- **Loot Alerts use System Notifications**: Alert triggers now fire a RuneLite system notification in addition to the in-game chat message.
- **Performance Improvements**: Event-based NPC tracking via `NpcSpawned`/`NpcDespawned` (replaces O(n) scan every tick); supply tracking gated to arena-only; alert rule parsing cached and reused until config changes; dose regex compiled once at class load.
- **Code Refactor**: Extracted `DrynessMath`, `HighscoresSyncService`, `ItemAggregate`, `ItemData`, `LootItem`, and `ExpectedDropsByItem` from the main plugin class, reducing it from ~2,700 to ~2,100 lines.

### v7.0

- Added Current Run summary/by-wave display toggle in the section header using arrow-style controls
- Set Current Run default mode to summary view
- Added by-wave breakdown rendering for Current Run with per-wave collapse state
- Expanded Previous Run presentation with improved by-wave/collapsed/combined behavior
- Added clipboard Export Stats for active-player historical data
- Added clipboard Import Stats with overwrite support for historical data
- Added strict player-key validation so imports only overwrite the currently logged-in character profile

### v6.0

- Added display sort mode for loot/supplies views (default: by value)
- Added historical edit mode toggle for click-to-remove entry correction in panel
- Added per-entry historical deletion flow with immediate totals/value refresh and persistence

### v2.0

- Added optional collect-loot value rewrite showing `original gp (adjusted gp)`
- Added setting toggle to enable/disable adjusted collect-loot display
- Added manual Mokhaiotl Cloth value override with automatic fallback when blank
- Added section-specific historical clear buttons (claimed, unclaimed, supplies)
- **Loot Alerts**: Added configurable notifications for specific items with custom quantity thresholds
- **Mokhaiotl Cloth Value**: Automatic calculation based on component GE prices (Confliction Gauntlets, Demon Tear, Tormented Bracelet)
- **Dizana's Quiver Support**: Tracks ammunition from Dizana's quiver in addition to inventory/equipment
- **Enhanced Price Calculations**: Dynamic GE price updates with proper per-dose normalization for all potions
- **Improved File Storage**: Per-account data files with automatic account hash detection
- **Teleport Detection**: Better handling of arena exits via teleport vs "Leave" button
- **Data Migration**: Automatic upgrade from config-based to file-based storage
- Combined all-waves view for claimed/unclaimed loot with bullet formatting and tooltips
- Improved UI: left padding, color-coding, tooltips, and modern collapsible sections
- Added ultra-valuable item exclusion filter (20M+ GP)
- Enhanced supplies tracking (dose/rune normalization, rune pouch support)
- Bug fixes and performance improvements

### Version 1.1.0

- Initial release: death tracking, lost loot, claimed loot, per-wave breakdown, Sun-kissed Bones exclusion, overlay, side panel, per-account data storage
