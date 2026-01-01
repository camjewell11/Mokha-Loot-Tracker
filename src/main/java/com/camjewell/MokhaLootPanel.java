package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.List;

import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

@Slf4j
public class MokhaLootPanel extends PluginPanel {
    private final MokhaLootTrackerPlugin plugin;
    private final ItemManager itemManager;

    private final JPanel statsPanel = new JPanel();
    private final JLabel totalLostLabel = new JLabel();
    private final JLabel totalClaimedLabel = new JLabel();
    private final JLabel deathCountLabel = new JLabel();

    @Inject
    public MokhaLootPanel(MokhaLootTrackerPlugin plugin,
            ItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Customize scrollbar appearance for modern look
        customizeScrollBar();

        // Title panel
        JPanel titlePanel = new JPanel();
        titlePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        titlePanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        titlePanel.setLayout(new BorderLayout());

        JLabel title = new JLabel("Mokha Loot Tracker");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        titlePanel.add(title, BorderLayout.CENTER);

        // Stats panel - use BoxLayout for tighter control
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statsPanel.setBorder(new EmptyBorder(3, 10, 3, 10));

        totalLostLabel.setFont(FontManager.getRunescapeFont());
        totalLostLabel.setForeground(Color.WHITE);
        totalClaimedLabel.setFont(FontManager.getRunescapeFont());
        totalClaimedLabel.setForeground(Color.WHITE);
        deathCountLabel.setFont(FontManager.getRunescapeFont());
        deathCountLabel.setForeground(Color.WHITE);

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

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        buttonPanel.add(resetButton, BorderLayout.CENTER);

        // Wrap stats panel in a container with button at bottom
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentWrapper.add(statsPanel, BorderLayout.CENTER);
        contentWrapper.add(buttonPanel, BorderLayout.SOUTH);

        // Add all panels
        add(titlePanel, BorderLayout.NORTH);
        add(contentWrapper, BorderLayout.CENTER);

        // Don't call updateStats() here - it will be called from startUp() on the
        // client thread
    }

    private JPanel createStatRow(String label, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setBorder(new EmptyBorder(0, 0, 0, 0)); // No spacing
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20)); // Limit height

        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(FontManager.getRunescapeFont());
        labelComponent.setForeground(Color.LIGHT_GRAY);

        row.add(labelComponent, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);

        return row;
    }

    public void updateStats() {
        // Fetch all data first (needs to be on client thread for ItemManager)
        long totalLost = plugin.getTotalLostValue();
        int deaths = plugin.getTimesDied();
        long totalClaimed = plugin.getTotalClaimedValue();
        long deathCosts = plugin.getTotalDeathCosts();
        int minValue = plugin.config.minItemValueThreshold();
        long currentRunValue = plugin.getFilteredLootValue(plugin.getCurrentLootItems());
        long currentRunFullValue = plugin.getTotalLootValue(plugin.getCurrentLootItems());
        List<com.camjewell.LootItem> allCurrentRunItems = plugin.getCurrentLootItems();
        List<com.camjewell.LootItem> currentRunItems = (minValue > 0) ? plugin.filterItemsByValue(allCurrentRunItems)
                : allCurrentRunItems;

        // Fetch all wave data
        long[] waveLostValues = new long[10];
        long[] waveLostFullValues = new long[10];
        long[] waveClaimedValues = new long[10];
        long[] waveClaimedFullValues = new long[10];
        List<com.camjewell.LootItem>[] waveLostItems = new List[10];
        List<com.camjewell.LootItem>[] waveClaimedItems = new List[10];

        // Create a map to cache item prices (must be fetched on client thread)
        java.util.Map<Integer, Long> itemPriceCache = new java.util.HashMap<>();

        for (int wave = 1; wave <= 9; wave++) {
            waveLostValues[wave] = plugin.getWaveLostValue(wave);
            waveLostFullValues[wave] = plugin.getWaveLostFullValue(wave);
            List<com.camjewell.LootItem> lostAll = plugin.getWaveLostItems(wave);
            waveLostItems[wave] = (minValue > 0) ? plugin.filterItemsByValue(lostAll) : lostAll;

            // Pre-cache prices for all lost items
            if (waveLostItems[wave] != null) {
                for (com.camjewell.LootItem item : waveLostItems[wave]) {
                    if (item.getId() > 0 && !itemPriceCache.containsKey(item.getId())) {
                        itemPriceCache.put(item.getId(), plugin.getItemValue(item.getId(), 1));
                    }
                }
            }

            waveClaimedValues[wave] = plugin.getWaveClaimedValue(wave);
            waveClaimedFullValues[wave] = plugin.getWaveClaimedFullValue(wave);
            List<com.camjewell.LootItem> claimedAll = plugin.getWaveClaimedItems(wave);
            waveClaimedItems[wave] = (minValue > 0) ? plugin.filterItemsByValue(claimedAll) : claimedAll;

            // Pre-cache prices for all claimed items
            if (waveClaimedItems[wave] != null) {
                for (com.camjewell.LootItem item : waveClaimedItems[wave]) {
                    if (item.getId() > 0 && !itemPriceCache.containsKey(item.getId())) {
                        itemPriceCache.put(item.getId(), plugin.getItemValue(item.getId(), 1));
                    }
                }
            }
        }

        // Debug logging for item values if enabled
        if (plugin.config.debugItemValueLogging()) {
            log.info("[MokhaLootTracker] Debug: Current Run Items (filtered):");
            for (com.camjewell.LootItem item : currentRunItems) {
                long value = plugin.getItemValue(item.getId(), item.getQuantity());
                log.info("  " + (item.getName() != null ? item.getName() : ("Item " + item.getId())) +
                        " x" + item.getQuantity() + " = " + value + " gp");
            }
            // Log excluded items for current run
            if (minValue > 0) {
                log.info("[MokhaLootTracker] Debug: Current Run Items (excluded by threshold):");
                for (com.camjewell.LootItem item : allCurrentRunItems) {
                    if (plugin.getItemValue(item.getId(), 1) < minValue) {
                        long value = plugin.getItemValue(item.getId(), item.getQuantity());
                        log.info("  " + (item.getName() != null ? item.getName() : ("Item " + item.getId())) +
                                " x" + item.getQuantity() + " = " + value + " gp");
                    }
                }
            }
            // Log by wave
            for (int wave = 1; wave <= 9; wave++) {
                log.info("[MokhaLootTracker] Debug: Wave " + (wave == 9 ? "9+" : wave) + " Items (filtered):");
                for (com.camjewell.LootItem item : waveLostItems[wave]) {
                    long value = plugin.getItemValue(item.getId(), item.getQuantity());
                    log.info("  " + (item.getName() != null ? item.getName() : ("Item " + item.getId())) +
                            " x" + item.getQuantity() + " = " + value + " gp");
                }
                if (minValue > 0) {
                    List<com.camjewell.LootItem> lostAll = plugin.getWaveLostItems(wave);
                    log.info("[MokhaLootTracker] Debug: Wave " + (wave == 9 ? "9+" : wave)
                            + " Items (excluded by threshold):");
                    for (com.camjewell.LootItem item : lostAll) {
                        if (plugin.getItemValue(item.getId(), 1) < minValue) {
                            long value = plugin.getItemValue(item.getId(), item.getQuantity());
                            log.info(
                                    "  " + (item.getName() != null ? item.getName() : ("Item " + item.getId())) +
                                            " x" + item.getQuantity() + " = " + value + " gp");
                        }
                    }
                }
            }
        }
        // Now update UI on Swing thread
        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                // Clear existing stats
                statsPanel.removeAll();

                // Add Profit/Loss header
                JLabel profitLossHeader = new JLabel("Profit/Loss:");
                profitLossHeader.setFont(FontManager.getRunescapeBoldFont());
                profitLossHeader.setForeground(Color.LIGHT_GRAY);
                JPanel profitHeaderPanel = new JPanel(new BorderLayout());
                profitHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
                profitHeaderPanel.setBorder(new EmptyBorder(1, 0, 0, 0));
                profitHeaderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                profitHeaderPanel.add(profitLossHeader, BorderLayout.WEST);
                statsPanel.add(profitHeaderPanel);

                // Add summary stats
                totalLostLabel.setText(QuantityFormatter.quantityToStackSize(totalLost) + " gp");
                totalLostLabel.setForeground(totalLost > 0 ? Color.RED : Color.WHITE);
                statsPanel.add(createStatRow("  Total Lost:", totalLostLabel));

                totalClaimedLabel.setText(QuantityFormatter.quantityToStackSize(totalClaimed) + " gp");
                totalClaimedLabel.setForeground(totalClaimed > 0 ? new Color(100, 255, 100) : Color.WHITE);
                statsPanel.add(createStatRow("  Total Claimed:", totalClaimedLabel));

                // Add death costs to summary (always show)
                JLabel deathCostsLabel = new JLabel();
                deathCostsLabel.setFont(FontManager.getRunescapeFont());
                deathCostsLabel.setText(QuantityFormatter.quantityToStackSize(deathCosts) + " gp");
                deathCostsLabel.setForeground(deathCosts > 0 ? new Color(255, 100, 100) : Color.WHITE);
                statsPanel.add(createStatRow("  Death Costs:", deathCostsLabel));

                deathCountLabel.setText(String.format("%s", QuantityFormatter.quantityToStackSize(deaths)));
                deathCountLabel.setForeground(deaths > 0 ? Color.ORANGE : Color.WHITE);
                statsPanel.add(createStatRow("  Deaths:", deathCountLabel));

                // Add separator with padding
                JSeparator separator = new JSeparator();
                separator.setForeground(Color.DARK_GRAY);
                JPanel separatorPanel = new JPanel(new BorderLayout());
                separatorPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
                separatorPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
                separatorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21)); // 1px line + 20px padding
                separatorPanel.add(separator, BorderLayout.CENTER);
                statsPanel.add(separatorPanel);

                // Add wave-specific stats
                JLabel waveHeader = new JLabel("Lost Loot by Wave:");
                waveHeader.setFont(FontManager.getRunescapeBoldFont());
                waveHeader.setForeground(Color.LIGHT_GRAY);
                waveHeader.setToolTipText(
                        "Lost loot is the value of items lost on death in each wave, filtered by your value threshold. Hover item names for price per item.");
                JPanel headerPanel = new JPanel(new BorderLayout());
                headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
                headerPanel.setBorder(new EmptyBorder(1, 0, 0, 0));
                headerPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                headerPanel.add(waveHeader, BorderLayout.WEST);
                statsPanel.add(headerPanel);

                // Waves 1-8 individually
                for (int wave = 1; wave <= 8; wave++) {
                    addWaveSection(wave, waveLostValues[wave], waveLostFullValues[wave], waveLostItems[wave],
                            itemPriceCache);
                }

                // Wave 9+
                addWaveSection(9, waveLostValues[9], waveLostFullValues[9], waveLostItems[9], itemPriceCache);
                JSeparator separator2 = new JSeparator();
                separator2.setForeground(Color.DARK_GRAY);
                JPanel separatorPanel2 = new JPanel(new BorderLayout());
                separatorPanel2.setBackground(ColorScheme.DARK_GRAY_COLOR);
                separatorPanel2.setBorder(new EmptyBorder(10, 0, 10, 0));
                separatorPanel2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21)); // 1px line + 20px padding
                separatorPanel2.add(separator2, BorderLayout.CENTER);
                statsPanel.add(separatorPanel2);

                // Add claimed loot header
                JLabel claimedHeader = new JLabel("Claimed Loot by Wave:");
                claimedHeader.setFont(FontManager.getRunescapeBoldFont());
                claimedHeader.setForeground(new Color(100, 255, 100)); // Light green
                claimedHeader.setToolTipText(
                        "Claimed loot is the value of items you successfully claimed for each wave. Hover item names for price per item.");
                JPanel claimedHeaderPanel = new JPanel(new BorderLayout());
                claimedHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
                claimedHeaderPanel.setBorder(new EmptyBorder(1, 0, 0, 0));
                claimedHeaderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                claimedHeaderPanel.add(claimedHeader, BorderLayout.WEST);
                statsPanel.add(claimedHeaderPanel);

                // Waves 1-8 individually (claimed)
                for (int wave = 1; wave <= 8; wave++) {
                    addClaimedWaveSection(wave, waveClaimedValues[wave], waveClaimedFullValues[wave],
                            waveClaimedItems[wave], itemPriceCache);
                }

                // Wave 9+ (claimed)
                addClaimedWaveSection(9, waveClaimedValues[9], waveClaimedFullValues[9], waveClaimedItems[9],
                        itemPriceCache);
                JSeparator separator3 = new JSeparator();
                separator3.setForeground(Color.DARK_GRAY);
                JPanel separatorPanel3 = new JPanel(new BorderLayout());
                separatorPanel3.setBackground(ColorScheme.DARK_GRAY_COLOR);
                separatorPanel3.setBorder(new EmptyBorder(10, 0, 10, 0));
                separatorPanel3.setMaximumSize(new Dimension(Integer.MAX_VALUE, 21));
                separatorPanel3.add(separator3, BorderLayout.CENTER);
                statsPanel.add(separatorPanel3);

                // Add current run header (always show)
                JLabel currentRunHeader = new JLabel("Current Run:");
                currentRunHeader.setFont(FontManager.getRunescapeBoldFont());
                currentRunHeader.setForeground(Color.CYAN);
                currentRunHeader.setToolTipText(
                        "Current run shows the value of items lost in your ongoing run. Hover item names for price per item.");
                JPanel currentRunHeaderPanel = new JPanel(new BorderLayout());
                currentRunHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
                currentRunHeaderPanel.setBorder(new EmptyBorder(1, 0, 0, 0));
                currentRunHeaderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
                currentRunHeaderPanel.add(currentRunHeader, BorderLayout.WEST);
                statsPanel.add(currentRunHeaderPanel);

                // Add current run value row (always show)

                JLabel currentRunLabel = new JLabel();
                currentRunLabel.setFont(FontManager.getRunescapeFont());
                String currentRunText = QuantityFormatter.quantityToStackSize(currentRunValue) + " gp";
                if (currentRunFullValue != currentRunValue) {
                    currentRunText += " (" + QuantityFormatter.quantityToStackSize(currentRunFullValue) + ")";
                }
                currentRunLabel.setText(currentRunText);
                currentRunLabel.setForeground(currentRunValue > 0 ? Color.CYAN : Color.WHITE);
                statsPanel.add(createStatRow("  Loss Value:", currentRunLabel));

                // Add current run items
                if (currentRunItems != null && !currentRunItems.isEmpty()) {
                    JPanel itemsPanel = new JPanel();
                    itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
                    itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

                    for (com.camjewell.LootItem item : currentRunItems) {
                        JLabel itemLabel = new JLabel();
                        boolean isHighValue = plugin.isHighValueItem(item.getId());
                        itemLabel.setFont(
                                isHighValue ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
                        itemLabel.setText("    " + item.getName() + " x" + item.getQuantity());
                        itemLabel.setForeground(isHighValue ? new Color(255, 215, 0) : Color.LIGHT_GRAY);
                        long priceEach = plugin.getItemValue(item.getId(), 1);
                        itemLabel.setToolTipText(
                                "Price per item: " + QuantityFormatter.quantityToStackSize(priceEach) + " gp");

                        JPanel itemContainer = new JPanel(new BorderLayout());
                        itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
                        itemContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
                        itemContainer.add(itemLabel, BorderLayout.WEST);
                        itemsPanel.add(itemContainer);
                    }

                    statsPanel.add(itemsPanel);
                }

                statsPanel.revalidate();
                statsPanel.repaint();
                revalidate();
                repaint();
            } catch (Exception e) {
                log.error("Error updating panel stats", e);
            }
        });
    }

    private void addWaveSection(int wave, long waveLost, long waveFullValue, List<com.camjewell.LootItem> items,
            java.util.Map<Integer, Long> itemPriceCache) {
        // Create wave header
        JPanel waveHeaderPanel = new JPanel(new BorderLayout());
        waveHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        waveHeaderPanel.setBorder(new EmptyBorder(1, 0, 1, 0)); // Compact spacing
        waveHeaderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        String waveName = wave == 9 ? "Wave 9+" : "Wave " + wave;
        JLabel waveLabel = new JLabel("  " + waveName + ":");
        waveLabel.setFont(FontManager.getRunescapeFont());
        waveLabel.setForeground(Color.LIGHT_GRAY);

        // Create separate labels for filtered and excluded values
        JPanel valuePanel = new JPanel(new BorderLayout(2, 0));
        valuePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Filtered value (red)
        JLabel filteredLabel = new JLabel(QuantityFormatter.quantityToStackSize(waveLost) + " gp");
        filteredLabel.setFont(FontManager.getRunescapeFont());
        filteredLabel.setForeground(Color.RED);

        if (waveFullValue != waveLost) {
            // Excluded value (orange) in parentheses
            JLabel excludedLabel = new JLabel("(" + QuantityFormatter.quantityToStackSize(waveFullValue) + ")");
            excludedLabel.setFont(FontManager.getRunescapeFont());
            excludedLabel.setForeground(new Color(255, 165, 0)); // Orange

            valuePanel.add(filteredLabel, BorderLayout.WEST);
            valuePanel.add(excludedLabel, BorderLayout.EAST);
        } else {
            valuePanel.add(filteredLabel, BorderLayout.WEST);
        }

        waveHeaderPanel.add(waveLabel, BorderLayout.WEST);
        waveHeaderPanel.add(valuePanel, BorderLayout.EAST);

        // Create items panel (always visible)
        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.setBorder(new EmptyBorder(0, 10, 0, 5)); // Reduced left padding
        itemsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1000)); // Allow growth

        // Use provided items (already fetched)
        if (items != null && !items.isEmpty()) {
            for (com.camjewell.LootItem item : items) {
                // Skip invalid items
                if (item.getId() <= 0) {
                    continue;
                }

                // Use the stored name if available, otherwise use the ID
                String itemName = item.getName() != null ? item.getName() : "Item " + item.getId();

                boolean isHighValue = plugin.isHighValueItem(item.getId());
                JLabel itemLabel = new JLabel(
                        "• " + itemName + " x" + QuantityFormatter.quantityToStackSize(item.getQuantity()));
                itemLabel.setFont(
                        isHighValue ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
                itemLabel.setForeground(isHighValue ? new Color(255, 215, 0) : Color.WHITE);
                long priceEach = itemPriceCache.getOrDefault(item.getId(), 0L);
                itemLabel.setToolTipText("Price per item: " + QuantityFormatter.quantityToStackSize(priceEach) + " gp");

                // Wrap label in container panel with BorderLayout.WEST for left alignment
                JPanel itemContainer = new JPanel(new BorderLayout());
                itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
                itemContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
                itemContainer.add(itemLabel, BorderLayout.WEST);

                itemsPanel.add(itemContainer);
            }
        }

        statsPanel.add(waveHeaderPanel);
        statsPanel.add(itemsPanel);
    }

    private void addClaimedWaveSection(int wave, long waveClaimed, long waveFullValue,
            List<com.camjewell.LootItem> items, java.util.Map<Integer, Long> itemPriceCache) {

        // Create wave header
        JPanel waveHeaderPanel = new JPanel(new BorderLayout());
        waveHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        waveHeaderPanel.setBorder(new EmptyBorder(0, 0, 0, 0)); // No spacing
        waveHeaderPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));

        String waveName = wave == 9 ? "Wave 9+" : "Wave " + wave;
        JLabel waveLabel = new JLabel("  " + waveName + ":");
        waveLabel.setFont(FontManager.getRunescapeFont());
        waveLabel.setForeground(Color.LIGHT_GRAY);

        // Create separate labels for filtered and full values with distinct colors
        JPanel valuePanel = new JPanel(new BorderLayout(2, 0));
        valuePanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Filtered value (blue)
        JLabel filteredLabel = new JLabel(QuantityFormatter.quantityToStackSize(waveClaimed) + " gp");
        filteredLabel.setFont(FontManager.getRunescapeFont());
        filteredLabel.setForeground(new Color(100, 200, 255)); // Blue

        if (waveFullValue != waveClaimed) {
            // Full value (green) in parentheses
            JLabel fullLabel = new JLabel("(" + QuantityFormatter.quantityToStackSize(waveFullValue) + ")");
            fullLabel.setFont(FontManager.getRunescapeFont());
            fullLabel.setForeground(new Color(100, 255, 100)); // Green

            valuePanel.add(filteredLabel, BorderLayout.WEST);
            valuePanel.add(fullLabel, BorderLayout.EAST);
        } else {
            valuePanel.add(filteredLabel, BorderLayout.WEST);
        }

        waveHeaderPanel.add(waveLabel, BorderLayout.WEST);
        waveHeaderPanel.add(valuePanel, BorderLayout.EAST);

        // Create items panel (always visible)
        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.setBorder(new EmptyBorder(0, 10, 0, 5)); // Reduced left padding
        itemsPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1000)); // Allow growth

        // Always show all items if threshold is 0, or show filtered, but re-fetch if
        // config changed
        List<com.camjewell.LootItem> displayItems = items;
        if (displayItems != null && !displayItems.isEmpty()) {
            for (com.camjewell.LootItem item : displayItems) {
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
                long priceEach = itemPriceCache.getOrDefault(item.getId(), 0L);
                itemLabel.setToolTipText("Price per item: " + QuantityFormatter.quantityToStackSize(priceEach) + " gp");
                JPanel itemContainer = new JPanel(new BorderLayout());
                itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
                itemContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
                itemContainer.add(itemLabel, BorderLayout.WEST);
                itemsPanel.add(itemContainer);
            }
        }

        statsPanel.add(waveHeaderPanel);
        statsPanel.add(itemsPanel);
    }

    private void customizeScrollBar() {
        // Get the scrollbar from PluginPanel's scroll pane
        JScrollBar verticalScrollBar = getScrollPane().getVerticalScrollBar();

        // Set modern styling
        verticalScrollBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        verticalScrollBar.setPreferredSize(new Dimension(8, 0)); // Thinner scrollbar
        verticalScrollBar.setUnitIncrement(16); // Smoother scrolling

        // Use modern UI with custom colors
        verticalScrollBar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = ColorScheme.DARK_GRAY_COLOR;
                this.trackColor = new Color(30, 30, 30);
            }

            @Override
            protected javax.swing.JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected javax.swing.JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private javax.swing.JButton createZeroButton() {
                javax.swing.JButton button = new javax.swing.JButton();
                button.setPreferredSize(new Dimension(0, 0));
                button.setMinimumSize(new Dimension(0, 0));
                button.setMaximumSize(new Dimension(0, 0));
                return button;
            }
        });
    }
}
