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
}
