package com.camjewell;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("mokhaloot")
public interface MokhaLootTrackerConfig extends Config {

	@ConfigItem(keyName = "ignoreSpiritSeedsValue", name = "Ignore Spirit Seeds Value", description = "Set spirit seeds value to 0 (they are untradable but have 140000 gp base value)", position = 12)
	default boolean ignoreSpiritSeedsValue() {
		return false;
	}

	@ConfigItem(keyName = "excludeUltraValuableItems", name = "Exclude Uniques", description = "Exclude items worth more than 20 million gp from claimed/unclaimed totals (uniques)", position = 11)
	default boolean excludeUltraValuableItems() {
		return false;
	}

	@ConfigItem(keyName = "showAdjustedLootValueDisplay", name = "Show Adjusted Loot Value Display", description = "Show loot value as original and adjusted totals in the delve/claim loot screen", position = 13)
	default boolean showAdjustedLootValueDisplay() {
		return false;
	}

	@ConfigItem(keyName = "mokhaClothValue", name = "Mokhaiotl Cloth Value", description = "Manual override for Mokhaiotl Cloth value. Leave blank to use automatic calculation.", position = 17)
	default String mokhaClothValue() {
		return "";
	}

	@ConfigItem(keyName = "lootAlertLines", name = "Loot alerts", description = "One per line: item name, quantity (e.g. steel cannonball, 300)", position = 18)
	default String lootAlertLines() {
		return "Dom, 1\nAvernic treads, 1\nEye of ayak (uncharged), 1\nMokhaiotl cloth, 1";
	}

	@ConfigItem(keyName = "displaySortMode", name = "Display Sort Mode", description = "Sort displayed loot and supplies alphabetically or by total value", position = 10)
	default MokhaDisplaySortMode displaySortMode() {
		return MokhaDisplaySortMode.VALUE_DESC;
	}

	@ConfigItem(keyName = "enableHistoricalEdit", name = "Enable Historical Edit Mode", description = "Allow clicking historical entries in the panel to remove them and recalculate totals", position = 16)
	default boolean enableHistoricalEdit() {
		return false;
	}

	@ConfigItem(keyName = "displayHaValueOnHover", name = "Display HA Value On Hover", description = "Show GE and HA price per item in loot hover tooltips. Historical loot is not backfilled automatically; enable this before collecting data if you want HA values there.", position = 14)
	default boolean displayHaValueOnHover() {
		return false;
	}

	@ConfigItem(keyName = "showPerformancePanel", name = "Show Performance Panel", description = "Show the Performance section in the panel (tracks prayer used, HP lost/regained, special attacks used, and times venomed during a run)", position = 15)
	default boolean showPerformancePanel() {
		return false;
	}

	@ConfigItem(keyName = "showDrynessPanel", name = "Show Dryness Panel", description = "Show the Dryness section in the panel", position = 19)
	default boolean showDrynessPanel() {
		return true;
	}

	// Internal persistence properties (not exposed in settings UI)
	// These use direct ConfigManager access, not @ConfigItem

	default long historicalTotalClaimed() {
		return 0;
	}

	void setHistoricalTotalClaimed(long value);

	default long historicalSupplyCost() {
		return 0;
	}

	void setHistoricalSupplyCost(long value);

	default String historicalClaimedByWaveJson() {
		return "{}";
	}

	void setHistoricalClaimedByWaveJson(String value);

	default String historicalClaimedItemsByWaveJson() {
		return "{}";
	}

	void setHistoricalClaimedItemsByWaveJson(String value);

	default String historicalSuppliesUsedJson() {
		return "{}";
	}

	void setHistoricalSuppliesUsedJson(String value);

	default String historicalUnclaimedByWaveJson() {
		return "{}";
	}

	void setHistoricalUnclaimedByWaveJson(String value);

	default String historicalUnclaimedItemsByWaveJson() {
		return "{}";
	}

	void setHistoricalUnclaimedItemsByWaveJson(String value);

	default String currentRunLootByWaveJson() {
		return "{}";
	}

	void setCurrentRunLootByWaveJson(String value);
}
