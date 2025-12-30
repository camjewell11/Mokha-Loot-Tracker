package com.camjewell;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("mokhaloot")
public interface MokhaLootTrackerConfig extends Config {
	@ConfigItem(keyName = "showOverlay", name = "Show Overlay", description = "Display an overlay with potential loot loss during runs", position = 0)
	default boolean showOverlay() {
		return true;
	}

	@ConfigItem(keyName = "showChatNotifications", name = "Chat Notifications", description = "Show a chat message when loot is lost", position = 1)
	default boolean showChatNotifications() {
		return true;
	}

	@ConfigItem(keyName = "excludeSunKissedBonesValue", name = "Exclude Sun-kissed Bones Value", description = "Exclude Sun-kissed Bones value (8000 gp each) from loot tracking", position = 2)
	default boolean excludeSunKissedBonesValue() {
		return true;
	}
}

