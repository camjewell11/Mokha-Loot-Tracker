package com.camjewell;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("mokhaloot")
public interface MokhaLootTrackerConfig extends Config {

	@ConfigItem(
		keyName = "exampleToggle",
		name = "Example Toggle",
		description = "An example configuration toggle",
		position = 1
	)
	default boolean exampleToggle() {
		return false;
	}

	@ConfigItem(
		keyName = "debugLogging",
		name = "Debug Logging",
		description = "Enable debug logging to the console",
		position = 99
	)
	default boolean debugLogging() {
		return false;
	}
}
