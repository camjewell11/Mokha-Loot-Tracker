package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

final class LootPanelCombinedSectionRenderer {
    private LootPanelCombinedSectionRenderer() {
    }

    @FunctionalInterface
    interface HistoricalInteractionBinder {
        void bind(JPanel itemRow, String itemName);
    }

    static void renderCombinedWaveItems(
            JPanel targetPanel,
            Map<Integer, Map<String, ItemAggregate>> historicalByWave,
            MokhaDisplaySortMode sortMode,
            boolean displayHaValueOnHover,
            boolean usePricePerItemForUniqueColor,
            Color totalTextColor,
            boolean enableHistoricalEdit,
            HistoricalInteractionBinder historicalInteractionBinder) {
        targetPanel.removeAll();

        HistoricalAggregateCombiner.CombinedAggregateResult combinedResult = HistoricalAggregateCombiner
                .combine(historicalByWave);
        long totalValue = combinedResult.getTotalValue();
        long totalHaValue = combinedResult.getTotalHaValue();

        for (ItemAggregate aggregate : LootPanelDisplayUtils
                .sortAggregatesForDisplay(combinedResult.getCombined().values(), sortMode)) {
            JPanel itemRow = new JPanel(new BorderLayout());
            itemRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            itemRow.setBorder(new EmptyBorder(2, 5, 2, 0));

            JLabel itemLabel = new JLabel("- " + aggregate.name + " x" + aggregate.totalQuantity);
            Color itemColor = isUltraValuable(aggregate, usePricePerItemForUniqueColor)
                    ? new Color(218, 165, 32)
                    : ColorScheme.LIGHT_GRAY_COLOR;
            itemLabel.setForeground(itemColor);
            itemLabel.setFont(FontManager.getRunescapeSmallFont());

            String pricePerItemText = LootPanelDisplayUtils.formatPricePerItemTooltip(
                    aggregate.pricePerItem,
                    aggregate.haPricePerItem,
                    displayHaValueOnHover);
            itemRow.setToolTipText("Price per item: " + pricePerItemText);
            itemRow.add(itemLabel, BorderLayout.WEST);

            JLabel itemValueLabel = new JLabel(LootPanelDisplayUtils.formatGp(aggregate.totalValue));
            itemValueLabel.setForeground(itemColor);
            itemValueLabel.setFont(FontManager.getRunescapeSmallFont());
            itemValueLabel.setToolTipText(
                    LootPanelDisplayUtils.formatGeHaTotalText(aggregate.totalValue, aggregate.totalHaValue));
            itemRow.add(itemValueLabel, BorderLayout.EAST);

            if (enableHistoricalEdit && historicalInteractionBinder != null) {
                historicalInteractionBinder.bind(itemRow, aggregate.name);
            }

            targetPanel.add(itemRow);
        }

        targetPanel.add(Box.createVerticalStrut(8));

        if (!displayHaValueOnHover) {
            JLabel totalLabel = new JLabel("Total: " + LootPanelDisplayUtils.formatGp(totalValue));
            totalLabel.setFont(FontManager.getRunescapeBoldFont());
            totalLabel.setForeground(totalTextColor);
            targetPanel.add(totalLabel);
        } else {
            JPanel totalRow = new JPanel(new BorderLayout());
            totalRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
            totalRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

            JLabel totalText = new JLabel("Total:");
            totalText.setFont(FontManager.getRunescapeFont());
            totalText.setForeground(Color.LIGHT_GRAY);
            totalRow.add(totalText, BorderLayout.WEST);

            JPanel valuesPanel = new JPanel();
            valuesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            valuesPanel.setLayout(new BoxLayout(valuesPanel, BoxLayout.X_AXIS));

            JLabel geTotalLabel = new JLabel("GE: " + LootPanelDisplayUtils.formatGp(totalValue));
            geTotalLabel.setFont(FontManager.getRunescapeFont());
            geTotalLabel.setForeground(Color.WHITE);

            JLabel haTotalLabel = new JLabel(" | HA: " + LootPanelDisplayUtils.formatGp(totalHaValue));
            haTotalLabel.setFont(FontManager.getRunescapeFont());
            haTotalLabel.setForeground(new Color(0, 200, 0));

            valuesPanel.add(geTotalLabel);
            valuesPanel.add(haTotalLabel);
            totalRow.add(valuesPanel, BorderLayout.EAST);
            targetPanel.add(totalRow);
        }

        targetPanel.revalidate();
        targetPanel.repaint();
    }

    private static boolean isUltraValuable(ItemAggregate aggregate,
            boolean usePricePerItemForUniqueColor) {
        if (LootPanelDisplayUtils.UNIQUE_ITEM_NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(aggregate.name))) {
            return true;
        }
        long value = usePricePerItemForUniqueColor ? aggregate.pricePerItem : aggregate.totalValue;
        return value > 20_000_000;
    }
}
