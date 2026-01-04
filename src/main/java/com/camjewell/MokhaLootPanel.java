package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class MokhaLootPanel extends PluginPanel {

    private final MokhaLootTrackerConfig config;
    private final Runnable onDebugLocation;

    // Profit/Loss section
    private JLabel totalClaimedLabel;
    private JLabel supplyCostLabel;
    private JLabel profitLossLabel;
    private JLabel totalUnclaimedLabel;

    // Current Run section
    private JLabel potentialValueLabel;

    // Claimed Loot by Wave
    private JLabel[] claimedWaveLabels = new JLabel[10]; // Wave 1-9+

    // Unclaimed Loot by Wave
    private JLabel[] unclaimedWaveLabels = new JLabel[10]; // Wave 1-9+

    // Supplies Used Current Run
    private JLabel suppliesCurrentRunLabel;

    // Supplies Used (All Time)
    private JLabel suppliesTotalValueLabel;
    private JLabel potionsTotalLabel;
    private JLabel foodTotalLabel;
    private JLabel runesTotalLabel;
    private JLabel ammoTotalLabel;

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onDebugLocation) {
        this.config = config;
        this.onDebugLocation = onDebugLocation;

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Create main content panel
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Profit/Loss Section
        contentPanel.add(createProfitLossSection());
        contentPanel.add(Box.createVerticalStrut(10));

        // Current Run Section
        contentPanel.add(createCurrentRunSection());
        contentPanel.add(Box.createVerticalStrut(10));

        // Claimed Loot by Wave Section
        contentPanel.add(createClaimedLootSection());
        contentPanel.add(Box.createVerticalStrut(10));

        // Unclaimed Loot by Wave Section
        contentPanel.add(createUnclaimedLootSection());
        contentPanel.add(Box.createVerticalStrut(10));

        // Supplies Used Current Run Section
        contentPanel.add(createSuppliesCurrentRunSection());
        contentPanel.add(Box.createVerticalStrut(10));

        // Supplies Used (All Time) Section
        contentPanel.add(createSuppliesTotalSection());
        contentPanel.add(Box.createVerticalStrut(10));

        // Debug button at bottom
        JButton debugButton = new JButton("Debug Location");
        debugButton.addActionListener(e -> {
            if (onDebugLocation != null) {
                onDebugLocation.run();
            }
        });
        debugButton.setAlignmentX(JButton.CENTER_ALIGNMENT);
        contentPanel.add(debugButton);

        add(contentPanel, BorderLayout.NORTH);
    }

    private JPanel createProfitLossSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
                new EmptyBorder(8, 8, 8, 8)));

        JLabel title = new JLabel("Profit/Loss");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(5));

        totalClaimedLabel = createDataLabel("Total Claimed:", "0 gp");
        supplyCostLabel = createDataLabel("Supply Cost:", "0 gp");

        panel.add(totalClaimedLabel);
        panel.add(supplyCostLabel);
        panel.add(Box.createVerticalStrut(3));
        panel.add(createSeparator());
        panel.add(Box.createVerticalStrut(3));

        profitLossLabel = createDataLabel("Profit/Loss:", "0 gp");
        profitLossLabel.setFont(FontManager.getRunescapeBoldFont());
        totalUnclaimedLabel = createDataLabel("Total Unclaimed:", "0 gp");

        panel.add(profitLossLabel);
        panel.add(totalUnclaimedLabel);

        return panel;
    }

    private JPanel createCurrentRunSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
                new EmptyBorder(8, 8, 8, 8)));

        JLabel title = new JLabel("Current Run");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(5));

        potentialValueLabel = createDataLabel("Potential Value:", "0 gp");
        panel.add(potentialValueLabel);

        return panel;
    }

    private JPanel createClaimedLootSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
                new EmptyBorder(8, 8, 8, 8)));

        JLabel title = new JLabel("Claimed Loot by Wave");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(5));

        for (int i = 0; i < 9; i++) {
            claimedWaveLabels[i] = createDataLabel("Wave " + (i + 1) + ":", "0 gp");
            panel.add(claimedWaveLabels[i]);
        }
        claimedWaveLabels[9] = createDataLabel("Wave 9+:", "0 gp");
        panel.add(claimedWaveLabels[9]);

        return panel;
    }

    private JPanel createUnclaimedLootSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
                new EmptyBorder(8, 8, 8, 8)));

        JLabel title = new JLabel("Unclaimed Loot by Wave");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(5));

        for (int i = 0; i < 9; i++) {
            unclaimedWaveLabels[i] = createDataLabel("Wave " + (i + 1) + ":", "0 gp");
            panel.add(unclaimedWaveLabels[i]);
        }
        unclaimedWaveLabels[9] = createDataLabel("Wave 9+:", "0 gp");
        panel.add(unclaimedWaveLabels[9]);

        return panel;
    }

    private JPanel createSuppliesCurrentRunSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
                new EmptyBorder(8, 8, 8, 8)));

        JLabel title = new JLabel("Supplies Used (Current Run)");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(5));

        suppliesCurrentRunLabel = createDataLabel("Total Value:", "0 gp");
        panel.add(suppliesCurrentRunLabel);

        return panel;
    }

    private JPanel createSuppliesTotalSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE),
                new EmptyBorder(8, 8, 8, 8)));

        JLabel title = new JLabel("Supplies Used");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(5));

        suppliesTotalValueLabel = createDataLabel("Total Value:", "0 gp");
        panel.add(suppliesTotalValueLabel);
        panel.add(Box.createVerticalStrut(5));

        potionsTotalLabel = createDataLabel("Potions:", "0 gp");
        foodTotalLabel = createDataLabel("Food:", "0 gp");
        runesTotalLabel = createDataLabel("Runes:", "0 gp");
        ammoTotalLabel = createDataLabel("Ammo:", "0 gp");

        panel.add(potionsTotalLabel);
        panel.add(foodTotalLabel);
        panel.add(runesTotalLabel);
        panel.add(ammoTotalLabel);

        return panel;
    }

    private JLabel createDataLabel(String prefix, String value) {
        JLabel label = new JLabel(prefix + " " + value);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel createSeparator() {
        JPanel separator = new JPanel();
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separator.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
        return separator;
    }

    // Update methods to be called from plugin
    public void updateProfitLoss(long totalClaimed, long supplyCost, long totalUnclaimed) {
        totalClaimedLabel.setText("Total Claimed: " + formatGp(totalClaimed));
        supplyCostLabel.setText("Supply Cost: " + formatGp(supplyCost));

        long profitLoss = totalClaimed - supplyCost;
        profitLossLabel.setText("Profit/Loss: " + formatGp(profitLoss));
        profitLossLabel.setForeground(profitLoss >= 0 ? new Color(0, 200, 0) : new Color(200, 0, 0));

        totalUnclaimedLabel.setText("Total Unclaimed: " + formatGp(totalUnclaimed));
    }

    public void updateCurrentRun(long potentialValue) {
        potentialValueLabel.setText("Potential Value: " + formatGp(potentialValue));
    }

    public void updateClaimedWave(int wave, long value) {
        int index = wave > 9 ? 9 : wave - 1;
        if (index >= 0 && index < claimedWaveLabels.length) {
            String label = wave > 9 ? "Wave 9+:" : "Wave " + wave + ":";
            claimedWaveLabels[index].setText(label + " " + formatGp(value));
        }
    }

    public void updateUnclaimedWave(int wave, long value) {
        int index = wave > 9 ? 9 : wave - 1;
        if (index >= 0 && index < unclaimedWaveLabels.length) {
            String label = wave > 9 ? "Wave 9+:" : "Wave " + wave + ":";
            unclaimedWaveLabels[index].setText(label + " " + formatGp(value));
        }
    }

    public void updateSuppliesCurrentRun(long totalValue) {
        suppliesCurrentRunLabel.setText("Total Value: " + formatGp(totalValue));
    }

    public void updateSuppliesTotal(long totalValue, long potions, long food, long runes, long ammo) {
        suppliesTotalValueLabel.setText("Total Value: " + formatGp(totalValue));
        potionsTotalLabel.setText("Potions: " + formatGp(potions));
        foodTotalLabel.setText("Food: " + formatGp(food));
        runesTotalLabel.setText("Runes: " + formatGp(runes));
        ammoTotalLabel.setText("Ammo: " + formatGp(ammo));
    }

    private String formatGp(long value) {
        if (value >= 1_000_000) {
            return String.format("%.2fM gp", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK gp", value / 1_000.0);
        } else {
            return value + " gp";
        }
    }
}
