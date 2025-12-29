package com.camjewell;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

@Slf4j
public class MokhaLostLootPanel extends PluginPanel {
    private final MokhaLostLootTrackerPlugin plugin;
    private final MokhaLostLootTrackerConfig config;

    private final JPanel statsPanel = new JPanel();
    private final JLabel totalLostLabel = new JLabel();
    private final JLabel deathCountLabel = new JLabel();

    @Inject
    public MokhaLostLootPanel(MokhaLostLootTrackerPlugin plugin, MokhaLostLootTrackerConfig config) {
        this.plugin = plugin;
        this.config = config;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Title panel
        JPanel titlePanel = new JPanel();
        titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titlePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        titlePanel.setLayout(new BorderLayout());

        JLabel title = new JLabel("Mokha Lost Loot Tracker");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        titlePanel.add(title, BorderLayout.CENTER);

        // Stats panel
        statsPanel.setLayout(new GridLayout(0, 1, 0, 5));
        statsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statsPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        totalLostLabel.setFont(FontManager.getRunescapeFont());
        totalLostLabel.setForeground(Color.WHITE);
        deathCountLabel.setFont(FontManager.getRunescapeFont());
        deathCountLabel.setForeground(Color.WHITE);

        statsPanel.add(createStatRow("Total Lost:", totalLostLabel));
        statsPanel.add(createStatRow("Deaths:", deathCountLabel));

        // Reset button
        JButton resetButton = new JButton("Reset Stats");
        resetButton.setFont(FontManager.getRunescapeSmallFont());
        resetButton.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to reset all tracked stats?",
                    "Reset Stats",
                    JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                plugin.resetStats();
                updateStats();
            }
        });

        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        buttonPanel.add(resetButton);

        // Add all panels
        add(titlePanel, BorderLayout.NORTH);
        add(statsPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        updateStats();
    }

    private JPanel createStatRow(String label, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(FontManager.getRunescapeFont());
        labelComponent.setForeground(Color.LIGHT_GRAY);

        row.add(labelComponent, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);

        return row;
    }

    public void updateStats() {
        // Clear existing stats
        statsPanel.removeAll();

        long totalLost = plugin.getTotalLostValue();
        int deaths = plugin.getTimesDied();

        // Add summary stats
        totalLostLabel.setText(QuantityFormatter.quantityToStackSize(totalLost) + " gp");
        totalLostLabel.setForeground(totalLost > 0 ? Color.RED : Color.WHITE);
        statsPanel.add(createStatRow("Total Lost:", totalLostLabel));

        deathCountLabel.setText(String.valueOf(deaths));
        deathCountLabel.setForeground(deaths > 0 ? Color.ORANGE : Color.WHITE);
        statsPanel.add(createStatRow("Deaths:", deathCountLabel));

        // Add separator
        JSeparator separator = new JSeparator();
        statsPanel.add(separator);

        // Add wave-specific stats
        JLabel waveHeader = new JLabel("Lost Loot by Wave:");
        waveHeader.setFont(FontManager.getRunescapeBoldFont());
        waveHeader.setForeground(Color.LIGHT_GRAY);
        statsPanel.add(waveHeader);

        // Waves 1-8 individually
        for (int wave = 1; wave <= 8; wave++) {
            long waveLost = plugin.getWaveLostValue(wave);
            if (waveLost > 0) {
                JLabel waveValueLabel = new JLabel();
                waveValueLabel.setFont(FontManager.getRunescapeFont());
                waveValueLabel.setText(QuantityFormatter.quantityToStackSize(waveLost) + " gp");
                waveValueLabel.setForeground(Color.YELLOW);
                statsPanel.add(createStatRow("  Wave " + wave + ":", waveValueLabel));
            }
        }

        // Wave 9+
        long wave9PlusLost = plugin.getWaveLostValue(9);
        if (wave9PlusLost > 0) {
            JLabel wave9ValueLabel = new JLabel();
            wave9ValueLabel.setFont(FontManager.getRunescapeFont());
            wave9ValueLabel.setText(QuantityFormatter.quantityToStackSize(wave9PlusLost) + " gp");
            wave9ValueLabel.setForeground(Color.YELLOW);
            statsPanel.add(createStatRow("  Wave 9+:", wave9ValueLabel));
        }

        // Add separator before current run stats
        JSeparator separator2 = new JSeparator();
        statsPanel.add(separator2);

        // Add current/previous run total
        long currentRunValue = plugin.getCurrentLootValue();
        if (currentRunValue > 0) {
            JLabel currentRunLabel = new JLabel();
            currentRunLabel.setFont(FontManager.getRunescapeFont());
            currentRunLabel.setText(QuantityFormatter.quantityToStackSize(currentRunValue) + " gp");
            currentRunLabel.setForeground(Color.CYAN);
            statsPanel.add(createStatRow("Current Run:", currentRunLabel));
        }

        revalidate();
        repaint();
    }
}
