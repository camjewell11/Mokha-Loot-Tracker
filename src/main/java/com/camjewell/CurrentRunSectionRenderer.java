package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

class CurrentRunSectionRenderer {
    private CurrentRunSectionRenderer() {
    }

    static void render(JPanel statsPanel, CurrentRunData currentRun, Map<Integer, Long> priceCache,
            MokhaLootTrackerPlugin plugin, BiFunction<String, JLabel, JPanel> rowFactory) {
        JLabel currentRunHeader = new JLabel("Current Run:");
        currentRunHeader.setFont(FontManager.getRunescapeBoldFont());
        currentRunHeader.setForeground(Color.CYAN);
        currentRunHeader.setToolTipText(
                "Current run shows the value of items lost in your ongoing run. Hover item names for price per item.");
        JPanel currentRunHeaderPanel = new JPanel(new BorderLayout());
        currentRunHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        currentRunHeaderPanel.setBorder(new EmptyBorder(1, 0, 0, 0));
        currentRunHeaderPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 18));
        currentRunHeaderPanel.add(currentRunHeader, BorderLayout.WEST);
        statsPanel.add(currentRunHeaderPanel);

        JLabel currentRunLabel = new JLabel();
        currentRunLabel.setFont(FontManager.getRunescapeFont());
        String currentRunText = QuantityFormatter.quantityToStackSize(currentRun.getFilteredValue()) + " gp";
        if (currentRun.getFullValue() != currentRun.getFilteredValue()) {
            currentRunText += " (" + QuantityFormatter.quantityToStackSize(currentRun.getFullValue()) + ")";
        }
        currentRunLabel.setText(currentRunText);
        currentRunLabel.setForeground(currentRun.getFilteredValue() > 0 ? Color.CYAN : Color.WHITE);
        statsPanel.add(rowFactory.apply("  Potential Value:", currentRunLabel));

        List<LootItem> items = currentRun.getItems();
        if (items != null && !items.isEmpty()) {
            JPanel itemsPanel = new JPanel();
            itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
            itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

            for (LootItem item : items) {
                JLabel itemLabel = new JLabel();
                boolean isHighValue = plugin.isHighValueItem(item.getId());
                itemLabel.setFont(
                        isHighValue ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
                itemLabel.setText("    " + item.getName() + " x" + item.getQuantity());
                itemLabel.setForeground(isHighValue ? new Color(255, 215, 0) : Color.LIGHT_GRAY);
                long priceEach = priceCache.getOrDefault(item.getId(), 0L);
                itemLabel.setToolTipText(
                        "Price per item: " + QuantityFormatter.quantityToStackSize(priceEach) + " gp");

                JPanel itemContainer = new JPanel(new BorderLayout());
                itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
                itemContainer.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 20));
                itemContainer.add(itemLabel, BorderLayout.WEST);
                itemsPanel.add(itemContainer);
            }

            statsPanel.add(itemsPanel);
        }
    }
}
