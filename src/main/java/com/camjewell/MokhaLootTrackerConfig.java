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

	@ConfigItem(keyName = "historicalTotalClaimed", name = "Historical Total Claimed", description = "Persisted total claimed value across sessions", position = 100)
	default long historicalTotalClaimed() {
		return 0;
	}

	void setHistoricalTotalClaimed(long value);

	@ConfigItem(keyName = "historicalSupplyCost", name = "Historical Supply Cost", description = "Persisted supply cost across sessions", position = 102)
	default long historicalSupplyCost() {
		return 0;
	}

	void setHistoricalSupplyCost(long value);

	@ConfigItem(keyName = "historicalClaimedByWaveJson", name = "Historical Claimed By Wave", description = "Persisted claimed loot by wave as JSON", position = 103)
	default String historicalClaimedByWaveJson() {
		return "{}";
	}

	void setHistoricalClaimedByWaveJson(String value);

	@ConfigItem(keyName = "historicalClaimedItemsByWaveJson", name = "Historical Claimed Items By Wave", description = "Persisted claimed item details by wave as JSON", position = 103)
	default String historicalClaimedItemsByWaveJson() {
		return "{}";
	}

	void setHistoricalClaimedItemsByWaveJson(String value);

	@ConfigItem(keyName = "historicalSuppliesUsedJson", name = "Historical Supplies Used", description = "Persisted supplies used as JSON", position = 104)
	default String historicalSuppliesUsedJson() {
		return "{}";
	}

	void setHistoricalSuppliesUsedJson(String value);

	@ConfigItem(keyName = "historicalUnclaimedByWaveJson", name = "Historical Unclaimed By Wave", description = "Persisted unclaimed loot by wave as JSON", position = 105)
	default String historicalUnclaimedByWaveJson() {
		return "{}";
	}

	void setHistoricalUnclaimedByWaveJson(String value);

	@ConfigItem(keyName = "historicalUnclaimedItemsByWaveJson", name = "Historical Unclaimed Items By Wave", description = "Persisted unclaimed item details by wave as JSON", position = 106)
	default String historicalUnclaimedItemsByWaveJson() {
		return "{}";
	}

	void setHistoricalUnclaimedItemsByWaveJson(String value);

	@ConfigItem(keyName = "currentRunLootByWaveJson", name = "Current Run Loot By Wave", description = "Persisted current run unclaimed loot by wave as JSON", position = 107)
	default String currentRunLootByWaveJson() {
		return "{}";
	}

	void setCurrentRunLootByWaveJson(String value);
}
