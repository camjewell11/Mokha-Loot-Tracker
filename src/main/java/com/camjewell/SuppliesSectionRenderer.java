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

class SuppliesSectionRenderer {
    private SuppliesSectionRenderer() {
    }

    static void render(JPanel statsPanel, SuppliesData supplies, Map<Integer, Long> priceCache,
            BiFunction<String, JLabel, JPanel> rowFactory) {
        statsPanel.add(PanelSectionUtil.createSeparator(10));

        JLabel suppliesHeader = new JLabel("Supplies Used (Current Run):");
        suppliesHeader.setFont(FontManager.getRunescapeBoldFont());
        suppliesHeader.setForeground(new Color(255, 165, 0));
        suppliesHeader.setToolTipText(
                "Live, monotonic view of supplies consumed during the ongoing run. Hover item names for price per item.");
        JPanel suppliesHeaderPanel = new JPanel(new BorderLayout());
        suppliesHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        suppliesHeaderPanel.setBorder(new EmptyBorder(1, 0, 0, 0));
        suppliesHeaderPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 18));
        suppliesHeaderPanel.add(suppliesHeader, BorderLayout.WEST);
        statsPanel.add(suppliesHeaderPanel);

        JLabel suppliesValueLabel = new JLabel();
        suppliesValueLabel.setFont(FontManager.getRunescapeFont());
        suppliesValueLabel.setText(QuantityFormatter.quantityToStackSize(supplies.getLiveValue()) + " gp");
        suppliesValueLabel.setForeground(supplies.getLiveValue() > 0 ? new Color(255, 165, 0) : Color.WHITE);
        statsPanel.add(rowFactory.apply("  Total Value:", suppliesValueLabel));

        addSuppliesList(statsPanel, supplies.getLiveItems(), priceCache);

        JLabel suppliesAllTimeHeader = new JLabel("Supplies Used (All Time):");
        suppliesAllTimeHeader.setFont(FontManager.getRunescapeBoldFont());
        suppliesAllTimeHeader.setForeground(new Color(255, 165, 0));
        suppliesAllTimeHeader.setToolTipText(
                "Total supplies consumed across all runs. Hover item names for price per item.");
        JPanel suppliesAllTimeHeaderPanel = new JPanel(new BorderLayout());
        suppliesAllTimeHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        suppliesAllTimeHeaderPanel.setBorder(new EmptyBorder(6, 0, 0, 0));
        suppliesAllTimeHeaderPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 18));
        suppliesAllTimeHeaderPanel.add(suppliesAllTimeHeader, BorderLayout.WEST);
        statsPanel.add(suppliesAllTimeHeaderPanel);

        JLabel suppliesAllTimeValueLabel = new JLabel();
        suppliesAllTimeValueLabel.setFont(FontManager.getRunescapeFont());
        suppliesAllTimeValueLabel
                .setText(QuantityFormatter.quantityToStackSize(supplies.getHistoricalValue()) + " gp");
        suppliesAllTimeValueLabel
                .setForeground(supplies.getHistoricalValue() > 0 ? new Color(255, 165, 0) : Color.WHITE);
        statsPanel.add(rowFactory.apply("  Total Value:", suppliesAllTimeValueLabel));

        addSuppliesList(statsPanel, supplies.getHistoricalItems(), priceCache);
    }

    private static void addSuppliesList(JPanel statsPanel, List<LootItem> items, Map<Integer, Long> priceCache) {
        if (items == null || items.isEmpty()) {
            return;
        }

        JPanel suppliesItemsPanel = new JPanel();
        suppliesItemsPanel.setLayout(new BoxLayout(suppliesItemsPanel, BoxLayout.Y_AXIS));
        suppliesItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        for (LootItem item : items) {
            JLabel itemLabel = new JLabel();
            itemLabel.setFont(FontManager.getRunescapeSmallFont());
            itemLabel.setText("    " + item.getName() + " x" + item.getQuantity());
            itemLabel.setForeground(Color.LIGHT_GRAY);
            long priceEach = priceCache.getOrDefault(item.getId(), 0L);
            itemLabel.setToolTipText(
                    "Price per item: " + QuantityFormatter.quantityToStackSize(priceEach) + " gp");

            JPanel itemContainer = new JPanel(new BorderLayout());
            itemContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
            itemContainer.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 20));
            itemContainer.add(itemLabel, BorderLayout.WEST);
            suppliesItemsPanel.add(itemContainer);
        }

        statsPanel.add(suppliesItemsPanel);
    }
}
