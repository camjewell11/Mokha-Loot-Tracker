package com.camjewell;

import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.PluginPanel;

public class MokhaLootPanel extends PluginPanel {

    private final MokhaLootTrackerConfig config;
    private final Runnable onDebugLocation;

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onDebugLocation) {
        this.config = config;
        this.onDebugLocation = onDebugLocation;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Create content panel with debug button
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BorderLayout());

        // Add button to print location and arena detection
        JButton debugButton = new JButton("Debug Location");
        debugButton.addActionListener(e -> {
            if (onDebugLocation != null) {
                onDebugLocation.run();
            }
        });
        contentPanel.add(debugButton, BorderLayout.NORTH);

        add(contentPanel, BorderLayout.CENTER);
    }
}
