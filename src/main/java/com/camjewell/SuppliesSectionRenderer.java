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

        addSuppliesList(statsPanel, supplies.getLiveItems(), priceCache, "Live");

        JLabel suppliesAllTimeHeader = new JLabel("Supplies Used:");
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

        addSuppliesList(statsPanel, supplies.getHistoricalItems(), priceCache, "");
    }

    private static void addSuppliesList(JPanel statsPanel, List<LootItem> items, Map<Integer, Long> priceCache,
            String labelSuffix) {
        if (items == null || items.isEmpty()) {
            return;
        }

        java.util.LinkedHashMap<String, List<LootItem>> grouped = groupSupplies(items);

        for (java.util.Map.Entry<String, List<LootItem>> entry : grouped.entrySet()) {
            List<LootItem> groupItems = entry.getValue();
            if (groupItems.isEmpty()) {
                continue;
            }

            String suffix = (labelSuffix != null && !labelSuffix.isEmpty()) ? " (" + labelSuffix + ")" : "";
            JLabel groupHeader = new JLabel(entry.getKey() + suffix + ":");
            groupHeader.setFont(FontManager.getRunescapeFont());
            groupHeader.setForeground(Color.LIGHT_GRAY);
            JPanel headerPanel = new JPanel(new BorderLayout());
            headerPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            headerPanel.setBorder(new EmptyBorder(4, 0, 0, 0));
            headerPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 18));
            headerPanel.add(groupHeader, BorderLayout.WEST);
            statsPanel.add(headerPanel);

            JPanel suppliesItemsPanel = new JPanel();
            suppliesItemsPanel.setLayout(new BoxLayout(suppliesItemsPanel, BoxLayout.Y_AXIS));
            suppliesItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

            for (LootItem item : groupItems) {
                String itemName = item.getName() != null ? item.getName() : "Item " + item.getId();
                JLabel itemLabel = new JLabel();
                itemLabel.setFont(FontManager.getRunescapeSmallFont());
                itemLabel.setText("    " + itemName + " x" + item.getQuantity());
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

    private static java.util.LinkedHashMap<String, List<LootItem>> groupSupplies(List<LootItem> items) {
        java.util.LinkedHashMap<String, List<LootItem>> groups = new java.util.LinkedHashMap<>();
        groups.put("Runes", new java.util.ArrayList<>());
        groups.put("Potions", new java.util.ArrayList<>());
        groups.put("Food", new java.util.ArrayList<>());
        groups.put("Ammo", new java.util.ArrayList<>());
        groups.put("Other", new java.util.ArrayList<>());

        for (LootItem item : items) {
            String name = item.getName() != null ? item.getName() : "";
            String lower = name.toLowerCase();

            if (lower.contains("rune")) {
                groups.get("Runes").add(item);
            } else if (lower.contains("(")) {
                groups.get("Potions").add(item);
            } else if (isFood(lower)) {
                groups.get("Food").add(item);
            } else if (isAmmo(lower)) {
                groups.get("Ammo").add(item);
            } else {
                groups.get("Other").add(item);
            }
        }
        return groups;
    }

    private static boolean isFood(String lower) {
        return lower.contains("shark") || lower.contains("angler") || lower.contains("karambwan")
                || lower.contains("manta") || lower.contains("ray") || lower.contains("monkfish")
                || lower.contains("lobster") || lower.contains("swordfish") || lower.contains("tuna")
                || lower.contains("salmon") || lower.contains("trout") || lower.contains("bass")
                || lower.contains("cake") || lower.contains("pie") || lower.contains("pizza")
                || lower.contains("potato") || lower.contains("stew") || lower.contains("curry")
                || lower.contains("soup") || lower.contains("meat") || lower.contains("bread")
                || lower.contains("roll") || lower.contains("egg") || lower.contains("jug of wine")
                || lower.contains("wine") || lower.contains("baguette");
    }

    private static boolean isAmmo(String lower) {
        return lower.contains("arrow") || lower.contains("bolt") || lower.contains("dart")
                || lower.contains("knife") || lower.contains("javelin") || lower.contains("chinchompa")
                || lower.contains("cannonball") || lower.contains("throwing");
    }
}
