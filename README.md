# Mokha Loot Tracker

A RuneLite plugin that tracks lost loot, claimed loot, supplies, and deaths during Doom of Mokhaiotl encounters in Old School RuneScape.

<img width="1887" height="1009" alt="image" src="https://github.com/user-attachments/assets/5394fc2c-d219-4f1d-8a98-62dd2dab5747" />

## Features

- **Death Tracking**: Automatically tracks each death in the Mokha arena
- **Lost Loot Monitoring**: Records the value of unclaimed loot when you die
- **Claimed Loot Tracking**: Tracks loot successfully claimed via "Claim and Leave" button
- **Per-Wave Breakdown**: View both lost and claimed loot organized by wave (expandable sections)
- **Profit/Loss Calculation**: Shows total claimed minus supply cost with color-coded results (green for profit, red for loss)
- **Supplies Tracking**: Tracks supplies consumed per run (live) and across runs (historical), including potions/runes with dose normalization; paused while dead to avoid gravestone wipes
- **Item Value Exclusion**: Optional toggles to exclude Sun-kissed Bones and Spirit Seeds from value calculations (both untradable items)
- **Item Value Threshold Filter**: Hide items below a configured GP threshold from panel display
- **Real-time Overlay**: Shows potential loot loss during active runs
- **Persistent Statistics**: All data is saved per-account and persists across sessions
- **Comprehensive Side Panel**: Detailed view including profit/loss, claimed loot by wave, unclaimed loot by wave, current run value, and supplies used

## How It Works

The plugin automatically detects when you're in the Mokha arena and monitors your unclaimed loot value.

**When you die:**

1. Records the wave you died on
2. Calculates the total value of your unclaimed loot
3. Saves individual items with quantities per wave
4. Pauses supplies tracking while dead to avoid gravestone wipes
5. Updates your statistics in the side panel

**When you claim loot:**

1. Detects when you click "Claim and Leave" → "Leave"
2. Records the value of loot claimed from each wave
3. Saves itemized breakdown per wave
4. Updates claimed loot statistics and profit/loss calculation

## Usage

### Side Panel

Click the Mokha Loot icon in the RuneLite sidebar to view:

- **Total Claimed Value**: Combined GP value of all successfully claimed loot
- **Supply Cost**: Total GP spent on supplies across all runs
- **Profit/Loss**: Claimed value minus supply cost (color-coded: green for profit, red for loss)
- **Total Unclaimed**: Combined GP value of all unclaimed loot from deaths
- **Current Run**: Real-time value of unclaimed loot during active runs
- **Claimed Loot by Wave**: Expandable sections showing itemized loot claimed per wave
- **Unclaimed Loot by Wave**: Expandable sections showing itemized loot lost per wave
- **Supplies Used (Current Run)**: Live supplies consumed during the current run
- **Supplies Used**: Historical supplies consumed across all runs

All sections can be expanded/collapsed for convenient browsing.

### In-Game Overlay

During active Mokha runs, an overlay appears showing:

- Current potential loot loss if you die
- Updates in real-time as you progress

### Side Panel Controls

- **Recalculate Totals**: Recalculates all statistics and reapplies ignore settings
- **Clear All Data**: Removes all tracked data for the current account

## Configuration

The plugin offers several settings:

- **Ignore Sun-kissed Bones Value**: Removes 8,000 GP per bone from loot calculations (they're untradable)
- **Ignore Spirit Seeds Value**: Removes 140,000 GP per seed from loot calculations (they're untradable)

Access settings via the RuneLite Configuration panel → Mokha Loot Tracker

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
- Old School RuneScape membership (Mokha is members content)

## Data Storage

All statistics are stored locally in your RuneLite configuration and are tied to your account. Each account has separate tracking, so you can use the plugin on multiple accounts without data overlap.

## Support

For bugs, feature requests, or questions:

- Open an issue on the [GitHub repository](https://github.com/camjewell11/Mokha-Loot-Tracker)
- Contact the developer

## License

This plugin is open source and available under standard RuneLite plugin licensing.

## Changelog

### Unreleased / Latest

- Added supplies tracking (live run + historical), with rune pouch and potion dose normalization
- Added per-item value threshold filter for panel display
- Refactored panel rendering into collapsible sections for stability and faster updates
- Fixed supplies valuation to price potion doses correctly
- Added Spirit Seeds value exclusion toggle
- Implemented profit/loss calculation with color-coded display
- Enhanced loot tracking by wave with expandable/collapsible sections

### Version 1.1.0

- Initial release
- Death tracking in Mokha arena
- Lost loot value calculation and tracking
- Claimed loot tracking via "Claim and Leave" button
- Per-wave itemized breakdown for both lost and claimed loot
- Sun-kissed Bones value exclusion toggle
- Real-time overlay
- Side panel with comprehensive statistics
- Per-account data storage
