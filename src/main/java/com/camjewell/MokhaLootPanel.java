package com.camjewell;

import java.awt.BorderLayout;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.PluginPanel;

public class MokhaLootPanel extends PluginPanel {

	private final MokhaLootTrackerConfig config;

	public MokhaLootPanel(MokhaLootTrackerConfig config) {
		this.config = config;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));

		// Panel is intentionally blank - ready for future content
		JPanel contentPanel = new JPanel();
		add(contentPanel, BorderLayout.CENTER);
	}
}
