package com.camjewell;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("mokhalostloot")
public interface MokhaLostLootTrackerConfig extends Config
{
	@ConfigSection(
		name = "Display Settings",
		description = "Configure what information is displayed",
		position = 0
	)
	String displaySection = "display";

	@ConfigSection(
		name = "Notifications",
		description = "Configure notifications",
		position = 1
	)
	String notificationSection = "notifications";

	@ConfigSection(
		name = "Debug",
		description = "Debug and development settings",
		position = 2
	)
	String debugSection = "debug";

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show Overlay",
		description = "Display an overlay with lost loot statistics",
		section = displaySection,
		position = 0
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTotalValue",
		name = "Show Total Lost Value",
		description = "Display the total value of all lost loot",
		section = displaySection,
		position = 1
	)
	default boolean showTotalValue()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDeathCount",
		name = "Show Death Count",
		description = "Display the number of times you've died with unclaimed loot",
		section = displaySection,
		position = 2
	)
	default boolean showDeathCount()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showChatNotifications",
		name = "Chat Notifications",
		description = "Show a chat message when loot is lost",
		section = notificationSection,
		position = 0
	)
	default boolean showChatNotifications()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableDebugMode",
		name = "Enable Debug Logging",
		description = "Log all visible widgets to help find Mokhaihitl interface IDs (check RuneLite logs)",
		section = debugSection,
		position = 0
	)
	default boolean enableDebugMode()
	{
		return false;
	}
}
