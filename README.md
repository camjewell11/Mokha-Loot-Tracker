# Mokha Loot Tracker

A RuneLite plugin that tracks lost loot, claimed loot, supplies, and deaths during Doom of Mokhaiotl encounters in Old School RuneScape.

<img width="1887" height="1009" alt="image" src="https://github.com/user-attachments/assets/5394fc2c-d219-4f1d-8a98-62dd2dab5747" />

## Features

- **Death Tracking**: Automatically tracks each death in the Mokha arena
- **Lost Loot Monitoring**: Records the value of unclaimed loot when you die
- **Claimed Loot Tracking**: Tracks loot successfully claimed via "Claim and Leave" button
- **Per-Wave Breakdown**: View both lost and claimed loot organized by wave
- **Supplies Tracking (beta)**: Tracks supplies consumed per run (live) and across runs (historical), including potions/runes with dose normalization; paused while dead to avoid gravestone wipes
- **Sun-kissed Bones Exclusion**: Optional toggle to exclude Sun-kissed Bones value from totals
- **Death Cost Tracking**: Records GP spent on death reclaim costs
- **Real-time Overlay**: Shows potential loot loss during active runs
- **Persistent Statistics**: All data is saved per-account and persists across sessions
- **Side Panel**: Detailed view of total lost value, total claimed value, death count, supplies, and itemized loot per wave

## How It Works

The plugin automatically detects when you're in the Mokha arena and monitors your unclaimed loot value.

**When you die:**

1. Records the wave you died on (Wave 1-12)
2. Calculates the total value of your unclaimed loot
3. Saves individual items with quantities per wave
4. Tracks death reclaim costs from chat messages
5. Pauses supplies tracking while you are dead so gravestone empties are not counted
6. Updates your statistics in the side panel

**When you claim loot:**

1. Detects when you click "Claim and Leave" → "Leave"
2. Records the value of loot claimed from each wave
3. Saves itemized breakdown per wave
4. Updates claimed loot statistics

## Usage

### Side Panel

Click the Mokha Loot icon in the RuneLite sidebar to view:

- **Total Lost Value**: Combined GP value of all lost loot across all deaths
- **Total Claimed Value**: Combined GP value of all successfully claimed loot
- **Deaths**: Total number of times you've died in Mokha
- **Lost Loot by Wave**: Expandable sections showing GP lost and itemized list per wave
- **Death Costs**: Collapsible section with individual death reclaim costs
- **Claimed Loot by Wave**: Expandable sections showing GP claimed and itemized list per wave
- **Current Run**: Real-time view of unclaimed loot value during active runs
- **Supplies Used (beta)**: Live supplies consumed this run and historical totals across runs (visible when enabled)

### In-Game Overlay

During active Mokha runs, an overlay appears in the top-left showing:

- Current potential loot loss if you die
- Updates in real-time as you progress

### Reset Statistics

Use the "Reset Stats" button in the side panel to clear all tracked data for the current account.

## Configuration

The plugin offers several settings:

- **Show Overlay**: Toggle the in-game overlay during runs (default: enabled)
- **Show Chat Notifications**: Display messages in chat when loot is lost (default: enabled)
- **Exclude Sun-kissed Bones Value**: Removes 8,000 GP per bone from loot calculations (default: enabled)
- **Exclude Items Under Value**: Filter out items below the configured GP threshold from panel lists (default: 0 = show all)
- **Show Supplies Used (beta)**: Show supplies used (live run + all time) in the panel; tracking still occurs even when hidden (default: disabled)
- **Debug: Log Item Values**: Logs item prices used for calculations when refreshing the panel (default: disabled)

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

- Added supplies tracking (live run + historical), with rune pouch and potion dose normalization, hidden behind a beta toggle; tracking pauses while dead to avoid gravestone wipes
- Added per-item value threshold filter for panel display
- Refactored panel rendering into sections for stability and faster updates
- Fixed supplies valuation to price potion doses (e.g., Prayer potion(4) counts per dose, not per full potion)

### Version 1.0.0

- Initial release
- Death tracking in Mokha arena
- Lost loot value calculation and tracking
- Claimed loot tracking via "Claim and Leave" button
- Per-wave itemized breakdown for both lost and claimed loot
- Death cost tracking from reclaim messages
- Sun-kissed Bones value exclusion toggle
- Real-time overlay
- Side panel with comprehensive statistics
- Reset functionality
- Per-account data storage
