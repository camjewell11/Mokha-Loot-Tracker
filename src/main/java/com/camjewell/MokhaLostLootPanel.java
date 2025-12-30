package com.camjewell;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

@Slf4j
public class MokhaLostLootPanel extends PluginPanel {
    private final MokhaLostLootTrackerPlugin plugin;
    private final ItemManager itemManager;

    private final JPanel statsPanel = new JPanel();
    private final JLabel totalLostLabel = new JLabel();
    private final JLabel deathCountLabel = new JLabel();

    @Inject
    public MokhaLostLootPanel(MokhaLostLootTrackerPlugin plugin,
            ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;

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
                // updateStats() is called by resetStats() via SwingUtilities.invokeLater
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
                addWaveSection(wave, waveLost);
            }
        }

        // Wave 9+
        long wave9PlusLost = plugin.getWaveLostValue(9);
        if (wave9PlusLost > 0) {
            addWaveSection(9, wave9PlusLost);
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

        // Add total death costs (always show)
        long deathCosts = plugin.getTotalDeathCosts();
        JLabel deathCostsLabel = new JLabel();
        deathCostsLabel.setFont(FontManager.getRunescapeFont());
        deathCostsLabel.setText(QuantityFormatter.quantityToStackSize(deathCosts) + " gp");
        deathCostsLabel.setForeground(new Color(255, 100, 100)); // Light red
        statsPanel.add(createStatRow("Death Costs:", deathCostsLabel));

        statsPanel.revalidate();
        statsPanel.repaint();
        revalidate();
        repaint();
    }

    private void addWaveSection(int wave, long waveLost) {
        // Create wave header
        JPanel waveHeaderPanel = new JPanel(new BorderLayout());
        waveHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        String waveName = wave == 9 ? "Wave 9+" : "Wave " + wave;
        JLabel waveLabel = new JLabel("  " + waveName + ":");
        waveLabel.setFont(FontManager.getRunescapeFont());
        waveLabel.setForeground(Color.LIGHT_GRAY);

        JLabel waveValueLabel = new JLabel(QuantityFormatter.quantityToStackSize(waveLost) + " gp");
        waveValueLabel.setFont(FontManager.getRunescapeFont());
        waveValueLabel.setForeground(Color.YELLOW);

        waveHeaderPanel.add(waveLabel, BorderLayout.WEST);
        waveHeaderPanel.add(waveValueLabel, BorderLayout.EAST);

        // Create items panel (always visible)
        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.setBorder(new EmptyBorder(2, 20, 2, 5));

        // Get items for this wave
        List<com.camjewell.LootItem> items = plugin.getWaveLostItems(wave);
        if (items != null && !items.isEmpty()) {
            for (com.camjewell.LootItem item : items) {
                // Skip invalid items
                if (item.getId() <= 0) {
                    log.warn("Skipping invalid item with ID: {}", item.getId());
                    continue;
                }

                ItemComposition itemComp = itemManager.getItemComposition(item.getId());
                String itemName = itemComp.getName();

                // Skip if item name is null or "null"
                if (itemName == null || itemName.equalsIgnoreCase("null")) {
                    log.warn("Skipping item with null name, ID: {}", item.getId());
                    continue;
                }

                JLabel itemLabel = new JLabel(
                        "  • " + itemName + " x" + QuantityFormatter.quantityToStackSize(item.getQuantity()));
                itemLabel.setFont(FontManager.getRunescapeSmallFont());
                itemLabel.setForeground(Color.WHITE);
                itemsPanel.add(itemLabel);
            }
        }

        statsPanel.add(waveHeaderPanel);
        statsPanel.add(itemsPanel);
    }
}
