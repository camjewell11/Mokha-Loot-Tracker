package com.camjewell;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("mokhaloot")
public interface MokhaLootTrackerConfig extends Config {

	@ConfigItem(keyName = "minItemValueThreshold", name = "Exclude Items Under Value", description = "Items individually valued under this amount will be excluded from wave value and item lists. Set to 0 to show all items. The panel will show both filtered and full values.", position = 3)
	default int minItemValueThreshold() {
		return 0;
	}

	@ConfigItem(keyName = "showOverlay", name = "Show Overlay", description = "Display an overlay with potential loot loss during runs. Only visible in the Mokha arena or delve interface.", position = 0)
	default boolean showOverlay() {
		return true;
	}

	@ConfigItem(keyName = "showChatNotifications", name = "Chat Notifications", description = "Show a chat message when loot is lost (e.g. on death or claim)", position = 1)
	default boolean showChatNotifications() {
		return true;
	}

	@ConfigItem(keyName = "excludeSunKissedBonesValue", name = "Exclude Sun-kissed Bones Value", description = "Exclude Sun-kissed Bones value (8000 gp each) from loot tracking. If enabled, bones are not counted in lost/claimed values.", position = 2)
	default boolean excludeSunKissedBonesValue() {
		return true;
	}

	@ConfigItem(keyName = "showSuppliesUsedBeta", name = "Show Supplies Used (beta)", description = "Enable supplies-used summary and item breakdown. Beta feature: tracking still occurs even when disabled.", position = 4)
	default boolean showSuppliesUsedBeta() {
		return false;
	}

	@ConfigItem(keyName = "debugItemValueLogging", name = "Debug: Log Item Values", description = "If enabled, logs the value of all earned and excluded items for each wave and current run when the panel is refreshed.", position = 99)
	default boolean debugItemValueLogging() {
		return false;
	}
}
