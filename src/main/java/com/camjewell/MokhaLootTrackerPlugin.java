package com.camjewell;

import java.awt.image.BufferedImage;

import javax.inject.Inject;

import com.google.inject.Provides;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(
	name = "Mokha Loot Tracker",
	description = "Tracks loot obtained from Mokhaiotl encounters",
	tags = { "mokha", "loot", "tracker", "mokhaiotl" })
public class MokhaLootTrackerPlugin extends Plugin {

	@Inject
	private MokhaLootTrackerConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	private MokhaLootPanel panel;
	private NavigationButton navButton;

	@Provides
	MokhaLootTrackerConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(MokhaLootTrackerConfig.class);
	}

	@Override
	protected void startUp() throws Exception {
		panel = new MokhaLootPanel(config);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/48icon.png");

		navButton = NavigationButton.builder()
				.tooltip("Mokha Loot Tracker")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception {
		clientToolbar.removeNavigation(navButton);
	}
}
