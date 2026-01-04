package com.camjewell;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("mokhaloot")
public interface MokhaLootTrackerConfig extends Config {

	@ConfigItem(keyName = "ignoreSunKissedBonesValue", name = "Ignore Sun-kissed Bones Value", description = "Set sun-kissed bones value to 0 (they are untradable but have 8000 gp base value)", position = 10)
	default boolean ignoreSunKissedBonesValue() {
		return false;
	}

	@ConfigItem(keyName = "ignoreSpiritSeedsValue", name = "Ignore Spirit Seeds Value", description = "Set spirit seeds value to 0 (they are untradable but have 140000 gp base value)", position = 11)
	default boolean ignoreSpiritSeedsValue() {
		return false;
	}

	@ConfigItem(keyName = "debugLogging", name = "Debug Logging", description = "Enable debug logging to the console", position = 99)
	default boolean debugLogging() {
		return false;
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
