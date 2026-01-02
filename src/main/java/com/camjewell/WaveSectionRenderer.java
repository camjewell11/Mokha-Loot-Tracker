package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import java.util.Map;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

class WaveSectionRenderer {
    private WaveSectionRenderer() {
    }

    static void renderLost(JPanel statsPanel, List<WaveData> waves, Map<Integer, Long> priceCache,
            MokhaLootTrackerPlugin plugin) {
        JLabel waveHeader = new JLabel("Lost Loot by Wave:");
        waveHeader.setFont(FontManager.getRunescapeBoldFont());
        waveHeader.setForeground(Color.LIGHT_GRAY);
        waveHeader.setToolTipText(
                "Lost loot is the value of items lost on death in each wave, filtered by your value threshold. Hover item names for price per item.");
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerPanel.setBorder(new EmptyBorder(1, 0, 0, 0));
        headerPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 18));
        headerPanel.add(waveHeader, BorderLayout.WEST);
        statsPanel.add(headerPanel);

        for (WaveData wave : waves) {
            addLostWaveSection(statsPanel, wave, priceCache, plugin);
        }

        statsPanel.add(PanelSectionUtil.createSeparator(10));
    }

    static void renderClaimed(JPanel statsPanel, List<WaveData> waves, Map<Integer, Long> priceCache,
            MokhaLootTrackerPlugin plugin) {
        JLabel claimedHeader = new JLabel("Claimed Loot by Wave:");
        claimedHeader.setFont(FontManager.getRunescapeBoldFont());
        claimedHeader.setForeground(new Color(100, 255, 100));
        claimedHeader.setToolTipText(
                "Claimed loot is the value of items you successfully claimed for each wave. Hover item names for price per item.");
        JPanel claimedHeaderPanel = new JPanel(new BorderLayout());
        claimedHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        claimedHeaderPanel.setBorder(new EmptyBorder(1, 0, 0, 0));
        claimedHeaderPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 18));
        claimedHeaderPanel.add(claimedHeader, BorderLayout.WEST);
        statsPanel.add(claimedHeaderPanel);

        for (WaveData wave : waves) {
            addClaimedWaveSection(statsPanel, wave, priceCache, plugin);
        }

        statsPanel.add(PanelSectionUtil.createSeparator(10));
    }

    private static void addLostWaveSection(JPanel statsPanel, WaveData wave, Map<Integer, Long> priceCache,
            MokhaLootTrackerPlugin plugin) {
        JPanel waveHeaderPanel = new JPanel(new BorderLayout());
        waveHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        waveHeaderPanel.setBorder(new EmptyBorder(1, 0, 1, 0));
        waveHeaderPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 18));

        String waveName = wave.getWaveNumber() == 9 ? "Wave 9+" : "Wave " + wave.getWaveNumber();
        JLabel waveLabel = new JLabel("  " + waveName + ":");
        waveLabel.setFont(FontManager.getRunescapeFont());
        waveLabel.setForeground(Color.LIGHT_GRAY);

        JPanel valuePanel = new JPanel(new BorderLayout(2, 0));
        valuePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel filteredLabel = new JLabel(QuantityFormatter.quantityToStackSize(wave.getFilteredValue()) + " gp");
        filteredLabel.setFont(FontManager.getRunescapeFont());
        filteredLabel.setForeground(Color.RED);

        if (wave.getFullValue() != wave.getFilteredValue()) {
            JLabel excludedLabel = new JLabel(
                    "(" + QuantityFormatter.quantityToStackSize(wave.getFullValue()) + ")");
            excludedLabel.setFont(FontManager.getRunescapeFont());
            excludedLabel.setForeground(new Color(255, 165, 0));
            valuePanel.add(filteredLabel, BorderLayout.WEST);
            valuePanel.add(excludedLabel, BorderLayout.EAST);
        } else {
            valuePanel.add(filteredLabel, BorderLayout.WEST);
        }

        waveHeaderPanel.add(waveLabel, BorderLayout.WEST);
        waveHeaderPanel.add(valuePanel, BorderLayout.EAST);

        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.setBorder(new EmptyBorder(0, 10, 0, 5));
        itemsPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 1000));

        List<LootItem> items = wave.getItems();
        if (items != null && !items.isEmpty()) {
            for (LootItem item : items) {
                if (item.getId() <= 0) {
                    continue;
                }
                String itemName = item.getName() != null ? item.getName() : "Item " + item.getId();
                boolean isHighValue = plugin.isHighValueItem(item.getId());
                JLabel itemLabel = new JLabel(
                        "• " + itemName + " x" + QuantityFormatter.quantityToStackSize(item.getQuantity()));
                itemLabel.setFont(
                        isHighValue ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
                itemLabel.setForeground(isHighValue ? new Color(255, 215, 0) : Color.WHITE);
                long priceEach = priceCache.getOrDefault(item.getId(), 0L);
                itemLabel.setToolTipText("Price per item: " + QuantityFormatter.quantityToStackSize(priceEach) + " gp");

                JPanel itemContainer = new JPanel(new BorderLayout());
                itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
                itemContainer.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 20));
                itemContainer.add(itemLabel, BorderLayout.WEST);
                itemsPanel.add(itemContainer);
            }
        }

        statsPanel.add(waveHeaderPanel);
        statsPanel.add(itemsPanel);
    }

    private static void addClaimedWaveSection(JPanel statsPanel, WaveData wave, Map<Integer, Long> priceCache,
            MokhaLootTrackerPlugin plugin) {
        JPanel waveHeaderPanel = new JPanel(new BorderLayout());
        waveHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        waveHeaderPanel.setBorder(new EmptyBorder(0, 0, 0, 0));
        waveHeaderPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 18));

        String waveName = wave.getWaveNumber() == 9 ? "Wave 9+" : "Wave " + wave.getWaveNumber();
        JLabel waveLabel = new JLabel("  " + waveName + ":");
        waveLabel.setFont(FontManager.getRunescapeFont());
        waveLabel.setForeground(Color.LIGHT_GRAY);

        JPanel valuePanel = new JPanel(new BorderLayout(2, 0));
        valuePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel filteredLabel = new JLabel(QuantityFormatter.quantityToStackSize(wave.getFilteredValue()) + " gp");
        filteredLabel.setFont(FontManager.getRunescapeFont());
        filteredLabel.setForeground(new Color(100, 200, 255));

        if (wave.getFullValue() != wave.getFilteredValue()) {
            JLabel fullLabel = new JLabel(
                    "(" + QuantityFormatter.quantityToStackSize(wave.getFullValue()) + ")");
            fullLabel.setFont(FontManager.getRunescapeFont());
            fullLabel.setForeground(new Color(100, 255, 100));
            valuePanel.add(filteredLabel, BorderLayout.WEST);
            valuePanel.add(fullLabel, BorderLayout.EAST);
        } else {
            valuePanel.add(filteredLabel, BorderLayout.WEST);
        }

        waveHeaderPanel.add(waveLabel, BorderLayout.WEST);
        waveHeaderPanel.add(valuePanel, BorderLayout.EAST);

        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.setBorder(new EmptyBorder(0, 10, 0, 5));
        itemsPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 1000));

        List<LootItem> items = wave.getItems();
        if (items != null && !items.isEmpty()) {
            for (LootItem item : items) {
                if (item.getId() <= 0) {
                    continue;
                }
                String itemName = item.getName() != null ? item.getName() : "Item " + item.getId();
                boolean isHighValue = plugin.isHighValueItem(item.getId());
                JLabel itemLabel = new JLabel(
                        "• " + itemName + " x" + QuantityFormatter.quantityToStackSize(item.getQuantity()));
                itemLabel.setFont(
                        isHighValue ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
                itemLabel.setForeground(isHighValue ? new Color(255, 215, 0) : Color.WHITE);
                long priceEach = priceCache.getOrDefault(item.getId(), 0L);
                itemLabel.setToolTipText("Price per item: " + QuantityFormatter.quantityToStackSize(priceEach) + " gp");

                JPanel itemContainer = new JPanel(new BorderLayout());
                itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
                itemContainer.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 20));
                itemContainer.add(itemLabel, BorderLayout.WEST);
                itemsPanel.add(itemContainer);
            }
        }

        statsPanel.add(waveHeaderPanel);
        statsPanel.add(itemsPanel);
    }
}
