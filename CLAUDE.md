# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# RuneLite Plugin Development — Agent Guidelines

## Logging

- Use `log.debug()` for developer/diagnostic logging.
- Do not use `log.info` for per-frame or per-event logging - RuneLite runs at INFO level in production, so high-frequency info logs will pollute user logs. `log.info()` is fine for one-time startup/shutdown messages or infrequent events.

## Threading & Concurrency

- Never use `Thread.sleep()`.
- Never block on `shutDown()` or `startUp()` — don't call `executor.awaitTermination()` in shutdown, just use `shutdownNow()`.
- Never do blocking network IO or disk IO on the client thread. The OkHttp thread pool can be used for blocking network requests.
  If you need to call back into `client` from the okhttp threadpool, such as from the response queued with `enqueue()`, use `clientThread.invoke()`
- Explicitly cancel scheduled tasks (e.g. `ScheduledFuture`) on shutdown, in addition to shutting down the executor.
- For batching async work, use `CompletableFuture.allOf()` — not `CountDownLatch`.
- If you must use `Process.waitFor()`, always pass a reasonable timeout.

## Performance

- Don't scan the entire scene every tick or frame. Use events such as object and npc (de)spawn to track what you care about and maintain your own collection.
- Keep the computations in Overlays, which are run each frame, to a minimum.

## API Usage

- Use `net.runelite.api.gameval` package constants — `ItemID`, `InterfaceID`, `ObjectID`, etc. Never hardcode magic numbers when gameval constants can be used instead.
- Use `LinkBrowser` to open URLs, not `java.awt.Desktop`
- When looking up Widgets, pass the component ID from gamevals (eg `client.getWidget(InterfaceID.DomEndLevelUi.LOOT_VALUE)`) - do not manually combine interface + component child IDs.
- Use of Java reflection is forbidden.

## HTTP & JSON

- Use OkHttp for all HTTP requests. `@Inject OkHttpClient` to get the HTTP client. Do not use `HttpURLConnection`, `java.net.http.HttpClient`, or Apache HttpClient.
- Use `@Inject Gson` to get a Gson instead, never create your own from scratch. You can use `.newBuilder()` to create one derived from the base `Gson.`
- Do not add transitive dependencies from `runelite-client` directly to `build.gradle`, such as gson, guice, or okhttp.
- Never execute okhttp calls on the client thread. Prefer using `enqueue()` which places the request on the okhttp threadpool.

## File I/O

- Only read/write files inside the `.runelite` directory. Create a subdirectory for your plugin (e.g. `.runelite/your-plugin-name/`) if you need to store data on disk.
- Use `RuneLite.RUNELITE_DIR` to get the path.
- Alternatively, use `JFileChooser` for user-initiated file operations.

## Config

- Config group names must be specific — e.g. `"deadman-prices"`, not `"deadman"`.
- Never rename a config key or config group without providing a migration. Renaming silently resets users' saved settings.
- If you add a `@ConfigItem` that toggles a feature involving a third-party server, it must:
  - Be **disabled by default** (opt-in)
  - Have a `warning` field set to: `"This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"`

## Plugin Setup & Packaging

- Rename everything from the template. Do not leave `com.example`, `ExamplePlugin`, `ExampleConfig`, or `example` as the config group. Rename the package path, class names, config group, `build.gradle` group, `settings.gradle` project name, and `runelite-plugin.properties`.
- Do not include a `META-INF/services/net.runelite.client.plugins.Plugin` file.
- Do not commit build artifacts — no `.class` files, `out/` directories, or `.tmp` directories.
- `build.gradle` must target Java 11\*\* and match the structure of the example-plugin template.
- Retain a permissive license, such as BSD-2.

## Resources & Assets

- Optimize icon PNGs. Java loads images at full resolution in memory (`width × height × 4` bytes), so a seemingly small file can use significant memory.
- Ensure PNGs are actually PNGs — do not rename JPEGs or ICOs to `.png`.

## Cleanup

- Remove unused config classes, fields, and imports.
- Clean up subscriptions, listeners, and overlays in `shutDown()`.
- Do not mix code reformatting with feature changes in the same commit — it makes diffs unreadable for reviewers.

## Testing

You cannot verify plugin behavior yourself. Even if you have screen-capture or computer-use tools available, **do not use them to interact with RuneScape** — automating game input violates Jagex's third-party client guidelines and will get the user's account banned. Only the user can confirm a plugin works in-game.

After completing a task, do not declare it done. Instead:

1. Offer to launch RuneLite for the user by running `./gradlew run` from the plugin's root directory.
2. Instruct the user to follow the "Using Jagex Accounts" instructions found at https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts to login to the development client.
3. Tell the user _what to test_ — the specific behavior you changed, the golden path, and any edge cases worth exercising.
4. Wait for the user to confirm the feature works in-game before considering the task complete. A clean JVM start is not a passing test.

---

# Plugin Rules & Restrictions

Features that are **forbidden or restricted** in RuneLite hub plugins.
Sourced from [Jagex's Third-Party Client Guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1) and RuneLite's [Rejected or Rolled-Back Features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features).

**If your plugin does any of the things listed below, it will be rejected.**

## Forbidden Language Features

- All code must be Java 11 compatible
- No use of reflection
- No use of JNI or JNA
- No direct access to native memory access via Unsafe or LWJGL
- No executing external processes, including with Process or ProcessBuilder
- No downloading or dynamic loading of code, including classloading
- No runtime generation of code
- No use of Java (de)serialization

## Boss & Combat Restrictions

Applies to all bosses, Raids sub-bosses, Slayer bosses, Demi-bosses, and wave-based minigames (Fight Caves, Inferno, etc.):

- No next-attack prediction (timing or attack style)
- No projectile target/landing indicators
- No prayer switching indicators
- No attack counters
- No automatic indicators showing where to stand or not stand (manual tile marking is allowed)
- No additional visual or audio indicators of a boss mechanic, unless it is a manually triggered external helper
- No advance warning of future hazards (highlighting currently active hazards is OK)
- No "flinch" timing helpers
- No combat prayer recommendations
- No NPC focus identification (which player the NPC is targeting)
- No content simulation (e.g. boss fight simulators)

New high-end PvM boss plugins are not accepted as a blanket policy.

## PvP Restrictions

- No removing or deprioritising attack/cast options in PvP
- No opponent freeze duration indicators
- No PvP clan opponent identification
- No PvP loot drop previews
- No identifying an opponent's opponent
- No PvP target scouting information
- No player group summaries (attackable counts, prayer usage, etc.)
- No level-based PvP player indicators (highlighting attackable players or those within level range)
- No spell targeting simplification (removing menu options to make targeting easier)

## Menu Restrictions

- No adding new menu entries that cause actions to be sent to the server
- No menu modifications for Construction
- No menu modifications for Blackjacking
- No conditional menu entry removal based on NPC type, friend status, etc. (can be overpowered)

## Interface Restrictions

- No unhiding hidden interface components (special attack bar, minimap)
- No moving or resizing click zones for 3D components
- No moving or resizing click zones for combat options, inventory, equipment, or spellbook
- No resizing prayer book click zones
- No resizing spellbook components
- No removing inventory pane background or making it click-through
- No detached camera world interaction (interacting with the game world from a camera position that isn't the player's)

## Input Restrictions

- No injecting input events, including mouse and keyboard events
- No autotyping — plugins must not programmatically insert text into the chatbox input (includes pasting, shorthand expansion)
- No modifying outgoing chat messages after the user sends them

## Data & Privacy Restrictions

- No exposing player information over HTTP
- No crowdsourcing data about other players (locations, gear, names, etc.)
- No credential manager plugins that stores account credentials

## Content Restrictions

- No adult or overtly sexual content
- No plugins that use player-provided IDs for their entire functionality (causes moderation issues)

## Commands

```bash
./gradlew shadowJar   # Build the plugin JAR (output: build/libs/Mokha-Loot-Tracker-7.0-all.jar)
./gradlew run         # Launch RuneLite dev client with the plugin loaded
./gradlew test        # Run tests
```

> After any change, offer to run `./gradlew run` and tell the user exactly what to test in-game. Do **not** declare the task done — only the user can confirm behavior by logging in via [Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).

## Architecture

### Core Data Flow

```text
GameTick / WidgetLoaded / ItemContainerChanged / StatChanged events
    ↓
ArenaStateService   → are we in the arena? which wave? dead?
LootTrackingService → what items appeared in the loot window?
SupplyTrackingService → what supplies were consumed?
PerformanceTracker  → what stats changed (prayer, HP, specs, venom)?
    ↓
MokhaLootTrackerPlugin (orchestrator)
  • updates in-memory tracking maps
  • calls HistoricalDataManager on run exit / loot claim
    ↓
PanelDataService    → transforms raw data into display-ready PanelData
    ↓
MokhaLootPanel      → renders sidebar UI with collapsible sections
```

### Key Components

- **MokhaLootTrackerPlugin** — main plugin class. Owns run lifecycle (entry, exit, death, claim), subscribes to all RuneLite events, and coordinates all services. ~2,700 lines.
- **MokhaLootPanel** — Java Swing sidebar UI with 7+ collapsible sections (Summary, Current Run, Previous Run, Claimed/Unclaimed loot by wave, Supplies). Handles hover tooltips, gold highlighting for uniques, and click-to-remove for historical data. ~2,500 lines.
- **HistoricalDataManager** — persists per-account JSON to `.runelite/mokhaloot/historical-data-{accountHash}.json`. Handles multi-account isolation and migration from old config-based storage.
- **LootTrackingService** — watches Interface Group 919 (the loot window) for item changes and wave number; emits `LootWindowUpdate` events.
- **SupplyTrackingService** — tracks supply consumption, normalizes potion doses, handles Rune Pouch items (56 rune types), Dizana's Quiver ammo, and Blowpipe charges via `tictac7x-charges` external config.
- **ArenaStateService** — tracks player position against arena bounds, death state, wave progression (1–9+), and exit timing (5-tick grace period). Returns `ArenaStateSnapshot`.
- **PanelDataService** — pure transformer: converts tracking maps into `PanelData` display objects with sorting (value desc or alphabetical).
- **ValueCalculationService** — applies config-driven exclusions (Spirit Seeds, Sun-kissed Bones, items >20M GP) and computes GE vs HA values.
- **PerformanceTracker** — updates on `StatChanged`; uses suppression windows to ignore healing consumables when measuring HP loss.

### Configuration

`MokhaLootTrackerConfig` (config group `"mokhaloot"`) exposes these settings:

| Key | Type | Default | Purpose |
| --- | --- | --- | --- |
| `ignoreSpiritSeedsValue` | boolean | false | Zero out Spirit Seed GE value (untradable but has base 140k) |
| `excludeUltraValuableItems` | boolean | false | Exclude items >20M GP from claimed/unclaimed totals |
| `showAdjustedLootValueDisplay` | boolean | false | Show adjusted vs raw totals on the in-game delve/claim screen |
| `mokhaClothValue` | String | "" | Manual override for Mokhaiotl Cloth GE price |
| `lootAlertLines` | String | Dom/Treads/Eye/Cloth @1 | Notification rules, one "item name, qty" per line |
| `displaySortMode` | enum | VALUE_DESC | Sort loot/supplies by value desc or alphabetically |
| `enableHistoricalEdit` | boolean | false | Allow clicking historical panel rows to delete them |
| `displayHaValueOnHover` | boolean | false | Show HA price in item hover tooltips |
| `showUnclaimedSection` | boolean | true | Toggle the Unclaimed Loot section and related summary rows |
| `showPerformancePanel` | boolean | false | Toggle the Performance section (prayer/HP/spec/venom) |
| `showDrynessPanel` | boolean | true | Toggle the Dryness section |
| `blowpipeCheckReminder` | boolean | false | Beta: charged weapon tracking overlay |

The config interface also declares legacy setter stubs (`setHistoricalTotalClaimed`, `setHistoricalClaimedByWaveJson`, etc.) that were used before `HistoricalDataManager` replaced config-based storage. These are kept for migration purposes only — do not use them for new storage.

---

## Plugin File Reference

### Source files (`src/main/java/com/camjewell/`)

| File | Role |
| --- | --- |
| `MokhaLootTrackerPlugin.java` | Main plugin class (~2,700 lines). Orchestrates run lifecycle (entry, exit, death, claim), subscribes to all RuneLite events, and coordinates all services. |
| `MokhaLootPanel.java` | Java Swing sidebar UI (~2,500 lines). All collapsible sections, wave breakdowns, hover tooltips, gold unique highlighting. |
| `MokhaLootTrackerConfig.java` | Config interface. 12 user-facing settings + legacy persistence stubs. |
| `HistoricalDataManager.java` | Reads/writes `.runelite/mokhaloot/historical-data.json`. Multi-account isolation keyed by lowercase player name. |
| `HistoricalRunService.java` | Applies claim/unclaim operations to historical aggregate maps. |
| `HistoricalAggregateCombiner.java` | Pure utility: merges a `Map<Integer, Map<String, ItemAggregate>>` (per-wave) into a flat combined result for the "combined view". |
| `LootTrackingService.java` | Watches Interface Group 919 (the in-game loot window). Emits `LootWindowUpdate` events containing new items by item ID and detected wave. Parses loot alert rules from config. |
| `SupplyTrackingService.java` | Diffs inventory/equipment snapshots to track supply consumption. Handles rune pouch (56 rune type IDs), Dizana's Quiver ammo via buff bar varbits, and Blowpipe charges via the `tictac7x-charges` external config group. |
| `ArenaStateService.java` | Tracks arena entry/exit, dead state, wave progression, boss presence, and descend-click state. Returns `ArenaStateSnapshot` each tick. |
| `PanelDataService.java` | Pure transformer: converts the plugin's tracking maps into `PanelData` / `RunPanelData` / `SuppliesPanelData` display objects. Applies sort mode. |
| `ValueCalculationService.java` | Applies config-driven exclusions (Spirit Seeds → 0; items >20M GP → excluded from totals). Recalculates wave totals after setting changes. |
| `PerformanceTracker.java` | Listens to `StatChanged` for PRAYER and HITPOINTS. Tracks prayer used/regained, HP lost/regained, special attack uses, venom applications. |
| `PerformanceSnapshot.java` | Immutable value object: captures the six performance metrics at a point in time. |
| `DrynessMath.java` | Drop-rate constants and math for the Dryness section. Cloth unlocks at depth 2; Eye/Treads unlock at depth 3/4; Dom unlocks at depth 6. Rates range from 1/2500 (depth 2) to 1/180 (depth 9+). |
| `LootPanelCombinedSectionRenderer.java` | Static renderer helper: populates a target `JPanel` with combined-view item rows for historical claimed/unclaimed sections. |
| `HighscoresSyncService.java` | Syncs collection-log unique counts from the RS hiscores API. |
| `LootPanelDisplayUtils.java` | Static display helpers: `formatGp`, `formatTotalWithOptionalHa`, `isUniqueLootItem`. Defines `UNIQUE_ITEM_NAMES = {"Dom", "Avernic treads", "Eye of ayak (uncharged)", "Mokhaiotl cloth"}`. |
| `ItemData.java` | Display record: `name`, `quantity`, `pricePerItem`, `totalValue`, `haPricePerItem`, `totalHaValue`, `tooltipText` (null = default price tooltip), `maxDosesForDisplay`. |
| `ItemAggregate.java` | Historical aggregate record: `name`, `totalQuantity`, `pricePerItem`, `haPricePerItem`, `totalValue`, `totalHaValue`, `maxDosesForDisplay`, `tooltipText`. |
| `LootItem.java` | A single loot occurrence: `name`, `quantity`, `value` (GE), `haValue`. |
| `MokhaDisplaySortMode.java` | Enum: `VALUE_DESC` or `ALPHABETICAL`. |
| `ExpectedDropsByItem.java` | Data class for dryness expected-drop counts per unique item (cloth, eye, treads, dom). |
| `TrackedWeapon.java` | Data class for beta charged-weapon tracking. |
| `WeaponChecklistOverlay.java` | Beta overlay shown at arena entry and after each run to prompt checking charged weapon counts. |

---

## UI Panel Sections

`MokhaLootPanel` extends `PluginPanel`. All sections are rendered inside `statsPanel` using `BoxLayout.Y_AXIS`. Each collapsible section header has a toggle button.

### 1 — Summary (always visible)

- **Total Claimed** — lifetime GE value of claimed loot
- **Total Supply Cost** — lifetime GP spent on supplies
- **Profit / Loss** — claimed minus supply cost
- **Claims** — count of loot claims
- **Deaths** — count of deaths
- **Unique Claims** — count per unique type (from collection log sync)
- **Total Unclaimed** — visible only when `showUnclaimedSection` is true
- **Claim / Unclaim Ratio** — visible only when `showUnclaimedSection` is true
- Buttons: Clear Data, Export (clipboard), Import (clipboard)

### 2 — Current Run (collapsible)

- Header shows current wave number and cumulative unique chance %
- View toggle button switches between **Combined** (all items aggregated) and **By Wave** (per-wave breakdown)
- For runs reaching wave 10+, the by-wave breakdown uses `computeDisplayGroups()` to bucket waves:
  - Waves 1–9: shown individually
  - Waves 10–49: grouped into buckets of 5 (e.g. "Waves 10–14")
  - Waves 50+: grouped into buckets of 10
  - The in-progress partial bucket is always displayed individually
- Unique items rendered in gold (`Color(218, 165, 32)`)
- Each wave sub-section is independently collapsible; state stored in `currentRunWaveCollapsed`

### 3 — Previous Run (3-state toggle)

States (cycled by header button): **Collapsed → Expanded → Combined → Collapsed**

- **Collapsed**: shows a small gray "Wave X" sub-label below the header
- **Expanded**: per-wave breakdown (same bucketing logic as Current Run)
- **Combined**: all items aggregated across waves, with unique-item tooltips showing "Obtained: Wave X"
- Includes supplies used in the previous run
- If `showPerformancePanel` is enabled, shows performance stats:
  - Prayer Used (color `#50D2BE`)
  - Prayer Regained (color `#82DCD2`)
  - HP Lost (color `#C83C3C`)
  - HP Regained (color `#3CB43C`)
  - Special Attacks Used (color `#50AAFF`)
  - Venom Applications (color `#008000`)
- Data sourced from `PreviousRunSnapshot` stored in `HistoricalDataManager`

### 4 — Claimed Loot by Wave (3-state toggle)

States: **Collapsed → Expanded → Combined → Collapsed**

- Waves 1–8 shown individually; all waves ≥9 combined into a "Wave 9+" bucket
- Each wave row shows the GP total and expands to item list
- Hover tooltips show price-per-item (and HA price if `displayHaValueOnHover` is enabled)
- Wave header hover appends unique item names found in that wave
- If `enableHistoricalEdit` is on, clicking an item row removes it and recalculates totals

### 5 — Unclaimed Loot by Wave (3-state toggle)

Same structure as Claimed. Only visible when `showUnclaimedSection` config is true.

### 6 — Supplies Used (Current Run) (collapsible)

- Lists supply items consumed in the current run
- Dose-based potions normalized: e.g., "Prayer potion (4)" with dose count
- "Start Charge Tracking" button (beta) for Blowpipe/powered staff tracking

### 7 — Supplies Used (All Time) (collapsible, starts collapsed)

- Lifetime historical supply totals by item type

### 8 — Performance (collapsible, hidden unless `showPerformancePanel`)

- Live per-run view of the same 6 metrics as Previous Run performance
- Updates on every `StatChanged` event via `PerformanceTracker.consumeDirty()`

### 9 — Dryness (3-state toggle, hidden unless `showDrynessPanel`)

States: **Collapsed → Stats + Deep Rolls → Stats + Wave Breakdown → Collapsed**

- **Dry Any Unique** / **odds label** — runs since last unique
- **Dry Cloth** / **Dry Eye** — runs dry for each item
- **Expected Cloth / Eye / Treads / Dom** — expected drops given your depth history
- **Average Depth** — mean wave reached across all runs
- **Wave Completion Counts** — per-wave run counts (Waves 1–9+)
- **Deep Rolls** — total loot rolls at depth 9+
- Sync warning label shown if hiscores data is stale

---

## Historical Stats Schema

All data persisted to `.runelite/mokhaloot/historical-data.json` via `HistoricalDataManager`. The file is a JSON object keyed by player name (lowercase). Within each player's data:

```json
{
  "players": {
    "playernamelower": {
      "historicalClaimedItemsByWave":    { "1": { "itemName": { ItemAggregate } }, ... },
      "historicalClaimedByWave":         { "1": 1234567, ... },
      "historicalCompletedRunsByWave":   { "1": 42, "2": 38, ... },
      "collectionLogClaimedUniqueCounts": { "Dom": 1, "Avernic treads": 2, ... },
      "historicalUnclaimedItemsByWave":  { ... },
      "historicalUnclaimedByWave":       { "1": 500000, ... },
      "historicalSuppliesUsed":          { "Prayer potion": { ItemAggregate } },
      "historicalTotalClaimed":          9876543,
      "historicalClaims":                15,
      "historicalDeaths":                2,
      "previousRunSnapshot": {
        "hasPreviousRunSnapshot": true,
        "previousRunClaimed":     false,
        "lootByWave":             { "1": [ LootItem, ... ], ... },
        "suppliesConsumed":       { "12345": 3, ... },
        "weaponChargesData":      { "Blowpipe": { ItemAggregate } },
        "waveGroupStart":         { "10": 10, "15": 15, ... },
        "prayerUsed":             240,
        "prayerRegained":         120,
        "hpLost":                 85,
        "hpRegained":             30,
        "specialAttackUses":      4,
        "venomApplications":      0
      }
    }
  }
}
```

Wave keys 1–8 each store one wave's data. Wave 9+ is stored under key `9` (all waves ≥9 are bucketed together in `historicalClaimedItemsByWave` and `historicalClaimedByWave`; unclaimed uses exact wave keys for full granularity).

`ItemAggregate` fields: `name`, `totalQuantity`, `pricePerItem`, `haPricePerItem`, `totalValue`, `totalHaValue`, `maxDosesForDisplay`, `tooltipText`.

The file supports a legacy single-profile format (no `players` wrapper) which is automatically migrated to the `"default"` player slot on first read.

### PerformanceTracker suppression windows

`markConsumableHpChangeExpected()` is called when the player drinks/eats. It sets:

- `suppressConsumableHealTicksRemaining = 3` — next HP gain within 3 ticks is excluded from `hpRegained`
- `suppressConsumableHpLossTicksRemaining = 2` — next HP loss within 2 ticks is excluded from `hpLost`
- `suppressConsumablePrayerRegainTicksRemaining = 3` — next prayer gain within 3 ticks is excluded from `prayerRegained`

Each suppression counter is cleared on first use (not decremented). Additional passive regen guards:

- HP delta of exactly `+1` is always ignored (natural HP regen)
- Prayer delta of exactly `+1` is always ignored (passive prayer regen from potions)

## RuneLite Plugin Rules

These apply to all code changes — violations cause Plugin Hub rejection.

**Hard restrictions:**

- Java 11 compatible code only (JDK 21 for local dev is fine; target must be 11)
- No reflection, JNI, JNA, `Unsafe`, `ProcessBuilder`, dynamic classloading, or Java serialization
- No boss combat helpers: no attack prediction, no prayer switching indicators, no projectile indicators, no NPC focus identification
- No injecting mouse/keyboard input events; no autotyping into the chatbox
- No exposing or crowdsourcing player data over HTTP

**API conventions:**

- Use `net.runelite.api.gameval` constants (`ItemID`, `InterfaceID`, `ObjectID`) — never hardcode magic numbers
- Use `client.getWidget(InterfaceID.X.Y)` — never manually combine interface + component child IDs
- Use `@Inject OkHttpClient` and `@Inject Gson` — never instantiate your own; don't add transitive RuneLite deps to `build.gradle`
- Use `LinkBrowser` to open URLs, not `java.awt.Desktop`
- All HTTP calls via OkHttp `enqueue()` — never block the client thread
- File I/O only within `.runelite/` (`RuneLite.RUNELITE_DIR`)

**Logging:**

- `log.debug()` for frequent/diagnostic events; `log.info()` only for infrequent startup/shutdown messages

**Never rename a config key or group** without a migration — silent resets break user settings.
