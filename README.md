# Mokha Lost Loot Tracker

A RuneLite plugin that tracks unclaimed loot and deaths during Doom of Mokhaiotl encounters in Old School RuneScape.

## Features

- **Death Tracking**: Automatically tracks each death in the Mokha arena
- **Lost Loot Monitoring**: Records the value of unclaimed loot when you die
- **Per-Wave Breakdown**: View lost loot organized by the wave you died on
- **Real-time Overlay**: Shows potential loot loss during active runs
- **Persistent Statistics**: All data is saved per-account and persists across sessions
- **Side Panel**: Detailed view of total lost value, death count, and itemized loot per wave

## How It Works

The plugin automatically detects when you're in the Mokha arena and monitors your inventory/equipment value. When you die, it:

1. Records the wave you died on (Wave 1-9+)
2. Calculates the total value of your unclaimed loot
3. Saves individual items with quantities
4. Updates your statistics in the side panel

## Usage

### Side Panel

Click the Mokha Lost Loot icon in the RuneLite sidebar to view:

- **Total Lost Value**: Combined GP value of all lost loot across all deaths
- **Deaths**: Total number of times you've died in Mokha
- **Wave Breakdown**: Expandable sections showing:
  - GP lost on each wave
  - Itemized list of what was lost

### In-Game Overlay

During active Mokha runs, an overlay appears in the top-left showing:

- Current potential loot loss if you die
- Updates in real-time as you progress

### Reset Statistics

Use the "Reset Stats" button in the side panel to clear all tracked data for the current account.

## Configuration

The plugin offers two simple settings:

- **Show Overlay**: Toggle the in-game overlay during runs (default: enabled)
- **Show Chat Notifications**: Display messages in chat when loot is lost (default: enabled)

Access settings via the RuneLite Configuration panel → Mokha Lost Loot Tracker

## Installation

### From Plugin Hub

1. Open the RuneLite Plugin Hub
2. Search for "Mokha Lost Loot Tracker"
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

### Version 1.0.0

- Initial release
- Death tracking in Mokha arena
- Lost loot value calculation
- Per-wave itemized breakdown
- Real-time overlay
- Side panel with statistics
- Reset functionality
- Per-account data storage
