package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.function.BiFunction;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.QuantityFormatter;

class SummarySectionRenderer {
    private SummarySectionRenderer() {
    }

    static void render(JPanel statsPanel, SummaryData summary, boolean showSupplies,
            BiFunction<String, JLabel, JPanel> rowFactory) {
        JLabel profitLossHeader = new JLabel("Profit/Loss:");
        profitLossHeader.setFont(FontManager.getRunescapeBoldFont());
        profitLossHeader.setForeground(Color.LIGHT_GRAY);
        JPanel profitHeaderPanel = new JPanel(new BorderLayout());
        profitHeaderPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        profitHeaderPanel.setBorder(new EmptyBorder(1, 0, 0, 0));
        profitHeaderPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 18));
        profitHeaderPanel.add(profitLossHeader, BorderLayout.WEST);
        statsPanel.add(profitHeaderPanel);

        JLabel totalClaimedLabel = new JLabel(QuantityFormatter.quantityToStackSize(summary.getTotalClaimed()) + " gp");
        totalClaimedLabel.setFont(FontManager.getRunescapeFont());
        totalClaimedLabel
                .setForeground(summary.getTotalClaimed() > 0 ? new Color(100, 255, 100) : Color.WHITE);
        statsPanel.add(rowFactory.apply("  Total Claimed:", totalClaimedLabel));

        if (showSupplies) {
            JLabel supplyCostsLabel = new JLabel(
                    QuantityFormatter.quantityToStackSize(summary.getSupplyCosts()) + " gp");
            supplyCostsLabel.setFont(FontManager.getRunescapeFont());
            supplyCostsLabel
                    .setForeground(summary.getSupplyCosts() > 0 ? new Color(255, 165, 0) : Color.WHITE);
            statsPanel.add(rowFactory.apply("  Supply Costs:", supplyCostsLabel));
        }

        JLabel deathCostsLabel = new JLabel(QuantityFormatter.quantityToStackSize(summary.getDeathCosts()) + " gp");
        deathCostsLabel.setFont(FontManager.getRunescapeFont());
        deathCostsLabel.setForeground(summary.getDeathCosts() > 0 ? new Color(255, 100, 100) : Color.WHITE);
        statsPanel.add(rowFactory.apply("  Death Costs:", deathCostsLabel));

        if (showSupplies) {
            JSeparator summationLine = new JSeparator();
            summationLine.setForeground(Color.GRAY);
            JPanel summationPanel = new JPanel(new BorderLayout());
            summationPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
            summationPanel.setBorder(new EmptyBorder(5, 0, 5, 0));
            summationPanel.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 11));
            summationPanel.add(summationLine, BorderLayout.CENTER);
            statsPanel.add(summationPanel);

            JLabel profitLossLabel = new JLabel(QuantityFormatter.quantityToStackSize(summary.getProfitLoss()) + " gp");
            profitLossLabel.setFont(FontManager.getRunescapeBoldFont());
            if (summary.getProfitLoss() > 0) {
                profitLossLabel.setForeground(new Color(100, 255, 100));
            } else if (summary.getProfitLoss() < 0) {
                profitLossLabel.setForeground(Color.RED);
            } else {
                profitLossLabel.setForeground(Color.WHITE);
            }
            statsPanel.add(rowFactory.apply("  Profit/Loss:", profitLossLabel));
        }

        JLabel totalLostLabel = new JLabel(QuantityFormatter.quantityToStackSize(summary.getTotalLost()) + " gp");
        totalLostLabel.setFont(FontManager.getRunescapeFont());
        totalLostLabel.setForeground(summary.getTotalLost() > 0 ? Color.RED : Color.WHITE);
        statsPanel.add(rowFactory.apply("  Total Lost:", totalLostLabel));

        JLabel deathCountLabel = new JLabel(
                String.format("%s", QuantityFormatter.quantityToStackSize(summary.getDeathCount())));
        deathCountLabel.setFont(FontManager.getRunescapeFont());
        deathCountLabel.setForeground(summary.getDeathCount() > 0 ? Color.ORANGE : Color.WHITE);
        statsPanel.add(rowFactory.apply("  Deaths:", deathCountLabel));

        statsPanel.add(PanelSectionUtil.createSeparator(10));
    }
}
