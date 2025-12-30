package com.camjewell;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("mokhalostloot")
public interface MokhaLostLootTrackerConfig extends Config {
	@ConfigItem(keyName = "showOverlay", name = "Show Overlay", description = "Display an overlay with potential loot loss during runs", position = 0)
	default boolean showOverlay() {
		return true;
	}

	@ConfigItem(keyName = "showChatNotifications", name = "Chat Notifications", description = "Show a chat message when loot is lost", position = 1)
	default boolean showChatNotifications() {
		return true;
	}
}
