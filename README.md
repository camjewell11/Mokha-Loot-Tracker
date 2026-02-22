# Mokha Loot Tracker

A comprehensive RuneLite plugin for tracking loot, supplies, and deaths during Doom of Mokhaiotl encounters in Old School RuneScape. Features detailed persistent statistics, customizable loot alerts, automatic Mokhaiotl Cloth valuation, and a modern interactive UI for maximizing your Mokhaiotl profits.

<img width="1890" height="1028" alt="image" src="https://github.com/user-attachments/assets/1cc10533-5a75-4a3c-ac46-fd398a2aee5c" />

## Features

- **Death Tracking**: Automatically records each death in the Mokha arena, including wave and lost loot.
- **Lost Loot Monitoring**: Tracks the value and item breakdown of unclaimed loot lost on death, per wave.
- **Claimed Loot Tracking**: Tracks loot successfully claimed, with per-wave and combined breakdowns.
- **Per-Wave & Combined Views**: Expandable/collapsible sections for claimed and unclaimed loot by wave, plus a combined all-waves view with bullet-style formatting and tooltips.
- **Profit/Loss Calculation**: Shows total claimed minus supply cost, color-coded (green for profit, red for loss).
- **Supplies Tracking**: Tracks supplies consumed per run (live) and across all runs (historical), including potions (dose-normalized), runes (including rune pouch and Dizana's quiver), and other consumables.
- **Configurable Item Value Exclusion**: Toggles to ignore Sun-kissed Bones and Spirit Seeds (untradable) in calculations.
- **Mokhaiotl Cloth Value Calculation**: Automatically calculates and updates Cloth value based on current GE prices of component items.
- **Loot Alerts**: Configurable notifications for specific items with customizable quantity thresholds (chat message + sound effect).
- **Ultra-Valuable Item Filter**: Optionally exclude items worth more than 20 million GP from totals (uniques filter).
- **Modern UI**: Collapsible/expandable sections, left-padded bullet formatting, hover tooltips for price per item, and color-coded highlights for ultra-valuable items.
- **Persistent Statistics**: All data is saved per-account and persists across sessions, including historical claimed/unclaimed loot, supplies, and deaths.
- **Comprehensive Side Panel**: Summary, current run, claimed/unclaimed loot by wave, supplies (current/historical), and interactive controls.
- **Data Migration**: Automatically migrates old config-based data to new file-based storage.

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

- **Summary**: Total claimed, supply cost, profit/loss, total unclaimed, claim/unclaim ratio, total claims, total deaths.
- **Current Run**: Real-time value and item breakdown of unclaimed loot for the current run.
- **Claimed Loot by Wave**: Expandable/collapsible sections for each wave (1-8, 9+), with itemized loot and values. Combined all-waves view available.
- **Unclaimed Loot by Wave**: Same as above, for loot lost on death. Combined all-waves view available.
- **Supplies Used (Current Run)**: Live supplies consumed, with dose/rune normalization and values.
- **Supplies Used (All Time)**: Historical supplies consumed across all runs.

All sections support:

- Expand/collapse (▾/▸) for per-wave or combined views.
- Bullet-style formatting with left padding for items.
- Hover tooltips showing price per item.
- Color-coding for ultra-valuable items (gold if >20M GP or "Dom").

## Controls

- **Recalculate Totals**: Recalculates all statistics and reapplies ignore/exclude settings. Disabled during active runs.
- **Clear All Data**: Removes all tracked data for the current account (with confirmation).

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

## Configuration Options

Accessible via RuneLite Configuration panel → Mokha Loot Tracker:

- **Ignore Sun-kissed Bones Value**: Set value to 0 for Sun-kissed Bones (untradable, base value 8,000 GP).
- **Ignore Spirit Seeds Value**: Set value to 0 for Spirit Seeds (untradable, base value 140,000 GP).
- **Exclude Uniques**: Exclude items worth more than 20 million GP from claimed/unclaimed totals.
- **Mokhaiotl Cloth Value**: Automatically calculates Cloth value based on component prices (Confliction Gauntlets - 10000×Demon Tear - Tormented Bracelet). Updates dynamically with GE prices.
- **Loot Alerts**: Configure custom notifications for specific loot items. Format: `Item Name, Minimum Quantity` (one per line). Triggers chat message and sound when threshold is met.

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

### Latest (v2.0)

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
