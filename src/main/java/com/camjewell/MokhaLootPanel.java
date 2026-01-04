package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class MokhaLootPanel extends PluginPanel {

    /**
     * Represents item data for display (name, quantity, price per item, total
     * value)
     */
    public static class ItemData {
        public String name;
        public int quantity;
        public int pricePerItem;
        public long totalValue;

        public ItemData(String name, int quantity, int pricePerItem, long totalValue) {
            this.name = name;
            this.quantity = quantity;
            this.pricePerItem = pricePerItem;
            this.totalValue = totalValue;
        }
    }

    private final MokhaLootTrackerConfig config;
    private final Runnable onDebugLocation;

    // Profit/Loss section
    private JLabel totalClaimedLabel;
    private JLabel supplyCostLabel;
    private JLabel profitLossLabel;
    private JLabel totalUnclaimedLabel;

    // Current Run section
    private JLabel potentialValueLabel;

    // Claimed Loot by Wave - now stores panels for dynamic item lists
    private JPanel[] claimedWavePanels = new JPanel[10]; // Wave 1-9+

    // Unclaimed Loot by Wave - now stores panels for dynamic item lists
    private JPanel[] unclaimedWavePanels = new JPanel[10]; // Wave 1-9+

    // Supplies Used Current Run
    private JPanel suppliesCurrentRunPanel;

    // Supplies Used (All Time)
    private JLabel suppliesTotalValueLabel;
    private JPanel suppliesTotalItemsPanel;

    private final JPanel statsPanel = new JPanel();

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onDebugLocation) {
        this.config = config;
        this.onDebugLocation = onDebugLocation;

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

        // Create main content panel
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statsPanel.setBorder(new EmptyBorder(3, 10, 3, 10));

        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        // buttonPanel.add(resetButton, BorderLayout.CENTER);

        // Wrap stats panel in a container with button at bottom
        JPanel contentWrapper = new JPanel(new BorderLayout());
        contentWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        contentWrapper.add(statsPanel, BorderLayout.CENTER);
        contentWrapper.add(buttonPanel, BorderLayout.SOUTH);

        // Add all panels
        add(titlePanel, BorderLayout.NORTH);
        add(contentWrapper, BorderLayout.CENTER);

        // Profit/Loss Section
        statsPanel.add(createProfitLossSection());
        statsPanel.add(createSeparator(5));

        // Current Run Section
        statsPanel.add(createCurrentRunSection());
        statsPanel.add(createSeparator(5));

        // Claimed Loot by Wave Section
        statsPanel.add(createClaimedLootSection());
        statsPanel.add(createSeparator(5));
        // Unclaimed Loot by Wave Section
        statsPanel.add(createUnclaimedLootSection());
        statsPanel.add(createSeparator(5));

        // Supplies Used Current Run Section
        statsPanel.add(createSuppliesCurrentRunSection());
        statsPanel.add(createSeparator(5));
        // Supplies Used (All Time) Section
        statsPanel.add(createSuppliesTotalSection());
        statsPanel.add(createSeparator(5));

        // Debug button at bottom
        JButton debugButton = new JButton("Debug Location");
        debugButton.addActionListener(e -> {
            if (onDebugLocation != null) {
                onDebugLocation.run();
            }
        });
        debugButton.setAlignmentX(JButton.CENTER_ALIGNMENT);
        statsPanel.add(debugButton);

        add(statsPanel, BorderLayout.NORTH);
    }

    private JPanel createProfitLossSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Profit/Loss");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);

        totalClaimedLabel = new JLabel("Total Claimed: 0 gp");
        totalClaimedLabel.setFont(FontManager.getRunescapeFont());
        totalClaimedLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Total Claimed:", totalClaimedLabel));

        supplyCostLabel = new JLabel("0 gp");
        supplyCostLabel.setFont(FontManager.getRunescapeFont());
        supplyCostLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Supply Cost:", supplyCostLabel));

        panel.add(createInternalSeparator());

        profitLossLabel = new JLabel("0 gp");
        profitLossLabel.setFont(FontManager.getRunescapeBoldFont());
        profitLossLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Profit/Loss:", profitLossLabel));

        totalUnclaimedLabel = new JLabel("0 gp");
        totalUnclaimedLabel.setFont(FontManager.getRunescapeFont());
        totalUnclaimedLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Total Unclaimed:", totalUnclaimedLabel));

        return panel;
    }

    private JPanel createCurrentRunSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Current Run");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(0, 200, 255)); // Cyan
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);

        potentialValueLabel = new JLabel("0 gp");
        potentialValueLabel.setFont(FontManager.getRunescapeFont());
        potentialValueLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Potential Value:", potentialValueLabel));

        return panel;
    }

    private JPanel createClaimedLootSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Claimed Loot by Wave");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(0, 200, 0)); // Green
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);

        for (int i = 0; i < 10; i++) {
            claimedWavePanels[i] = createWavePanel("Wave " + (i == 9 ? "9+" : i + 1));
            panel.add(claimedWavePanels[i]);
        }

        return panel;
    }

    private JPanel createUnclaimedLootSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Unclaimed Loot by Wave");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(200, 0, 0)); // Red
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);

        for (int i = 0; i < 10; i++) {
            unclaimedWavePanels[i] = createWavePanel("Wave " + (i == 9 ? "9+" : i + 1));
            panel.add(unclaimedWavePanels[i]);
        }

        return panel;
    }

    private JPanel createSuppliesCurrentRunSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Supplies Used (Current Run)");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(255, 165, 0)); // Orange
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel totalLabel = new JLabel("0 gp");
        totalLabel.setFont(FontManager.getRunescapeFont());
        totalLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Total Value:", totalLabel));

        suppliesCurrentRunPanel = new JPanel();
        suppliesCurrentRunPanel.setLayout(new BoxLayout(suppliesCurrentRunPanel, BoxLayout.Y_AXIS));
        suppliesCurrentRunPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.add(suppliesCurrentRunPanel);

        return panel;
    }

    private JPanel createSuppliesTotalSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Supplies Used");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(255, 165, 0)); // Orange
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        panel.add(title);

        suppliesTotalValueLabel = new JLabel("0 gp");
        suppliesTotalValueLabel.setFont(FontManager.getRunescapeFont());
        suppliesTotalValueLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Total Value:", suppliesTotalValueLabel));

        suppliesTotalItemsPanel = new JPanel();
        suppliesTotalItemsPanel.setLayout(new BoxLayout(suppliesTotalItemsPanel, BoxLayout.Y_AXIS));
        suppliesTotalItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.add(suppliesTotalItemsPanel);

        return panel;
    }

    private JLabel createDataLabel(String prefix, String value) {
        JLabel label = new JLabel(prefix + " " + value);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel createStatRow(String label, JLabel valueLabel) {
        JPanel row = new JPanel(new BorderLayout());
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setBorder(new EmptyBorder(0, 0, 0, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        JLabel labelComponent = new JLabel(label);
        labelComponent.setFont(FontManager.getRunescapeFont());
        labelComponent.setForeground(Color.LIGHT_GRAY);

        row.add(labelComponent, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);

        return row;
    }

    private JPanel createWavePanel(String waveTitle) {
        JLabel valueLabel = new JLabel("0 gp");
        valueLabel.setFont(FontManager.getRunescapeFont());
        valueLabel.setForeground(Color.WHITE);
        return createStatRow(waveTitle + ":", valueLabel);
    }

    private JPanel createSeparator(int paddingTopBottom) {
        JSeparator separator = new JSeparator();
        separator.setForeground(Color.DARK_GRAY);
        JPanel separatorPanel = new JPanel(new BorderLayout());
        separatorPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        separatorPanel.setBorder(new EmptyBorder(paddingTopBottom, 0, paddingTopBottom, 0));
        separatorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, paddingTopBottom * 2 + 1));
        separatorPanel.add(separator, BorderLayout.CENTER);
        return separatorPanel;
    }

    private JPanel createInternalSeparator() {
        JSeparator separator = new JSeparator();
        separator.setForeground(Color.DARK_GRAY);
        JPanel separatorPanel = new JPanel(new BorderLayout());
        separatorPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        separatorPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        separatorPanel.add(separator, BorderLayout.CENTER);
        return separatorPanel;
    }

    // Update methods to be called from plugin
    public void updateProfitLoss(long totalClaimed, long supplyCost, long totalUnclaimed) {
        totalClaimedLabel.setText("Total Claimed: " + formatGp(totalClaimed));
        totalClaimedLabel.setForeground(new Color(0, 200, 0)); // Green

        supplyCostLabel.setText("Supply Cost: " + formatGp(supplyCost));
        supplyCostLabel.setForeground(new Color(255, 165, 0)); // Orange

        long profitLoss = totalClaimed - supplyCost;
        profitLossLabel.setText("Profit/Loss: " + formatGp(profitLoss));
        profitLossLabel.setForeground(profitLoss >= 0 ? new Color(0, 200, 0) : new Color(200, 0, 0));

        totalUnclaimedLabel.setText("Total Unclaimed: " + formatGp(totalUnclaimed));
        totalUnclaimedLabel.setForeground(new Color(200, 0, 0)); // Red for unclaimed
    }

    public void updateCurrentRun(long potentialValue) {
        potentialValueLabel.setText("Potential Value: " + formatGp(potentialValue));
    }

    public void updateClaimedWave(int wave, Map<String, ItemData> itemData) {
        int index = wave > 9 ? 9 : wave - 1;
        if (index >= 0 && index < claimedWavePanels.length) {
            updateWavePanel(claimedWavePanels[index], wave, itemData);
        }
    }

    public void updateUnclaimedWave(int wave, Map<String, ItemData> itemData) {
        int index = wave > 9 ? 9 : wave - 1;
        if (index >= 0 && index < unclaimedWavePanels.length) {
            updateWavePanel(unclaimedWavePanels[index], wave, itemData);
        }
    }

    public void updateSuppliesCurrentRun(long totalValue, Map<String, ItemData> itemData) {
        // Update total value in the section header (first label)
        // itemData contains the supplies used in current run
        updateSuppliesPanel(suppliesCurrentRunPanel, itemData);
    }

    public void updateSuppliesTotal(long totalValue, Map<String, ItemData> itemData) {
        suppliesTotalValueLabel.setText("Total Value: " + formatGp(totalValue));
        updateSuppliesPanel(suppliesTotalItemsPanel, itemData);
    }

    private void updateWavePanel(JPanel wavePanel, int wave, Map<String, ItemData> itemData) {
        // Clear existing items from panel (keep only header label)
        while (wavePanel.getComponentCount() > 1) {
            wavePanel.remove(1);
        }

        if (itemData == null || itemData.isEmpty()) {
            // Update header to show 0 gp
            JLabel headerLabel = (JLabel) wavePanel.getComponent(0);
            String waveStr = wave > 9 ? "Wave 9+" : "Wave " + wave;
            headerLabel.setText(waveStr + ": 0 gp");
            return;
        }

        // Calculate total value
        long totalValue = 0;
        for (ItemData item : itemData.values()) {
            totalValue += item.totalValue;
        }

        // Update header label with total
        JLabel headerLabel = (JLabel) wavePanel.getComponent(0);
        String waveStr = wave > 9 ? "Wave 9+" : "Wave " + wave;
        headerLabel.setText(waveStr + ": " + formatGp(totalValue));

        // Add item entries
        for (ItemData item : itemData.values()) {
            JLabel itemLabel = new JLabel(item.name + " x" + item.quantity + " (" + formatGp(item.totalValue) + ")");
            itemLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            itemLabel.setFont(FontManager.getRunescapeSmallFont());
            itemLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
            itemLabel.setBorder(new EmptyBorder(0, 15, 0, 0)); // Indent items
            wavePanel.add(itemLabel);
        }

        wavePanel.revalidate();
        wavePanel.repaint();
    }

    private void updateSuppliesPanel(JPanel suppliesPanel, Map<String, ItemData> itemData) {
        // Clear existing items
        suppliesPanel.removeAll();

        if (itemData == null || itemData.isEmpty()) {
            return;
        }

        // Add item entries
        for (ItemData item : itemData.values()) {
            JLabel itemLabel = new JLabel(item.name + " x" + item.quantity + " (" + formatGp(item.totalValue) + ")");
            itemLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            itemLabel.setFont(FontManager.getRunescapeSmallFont());
            itemLabel.setAlignmentX(JLabel.LEFT_ALIGNMENT);
            itemLabel.setBorder(new EmptyBorder(0, 10, 0, 0)); // Indent items
            suppliesPanel.add(itemLabel);
        }

        suppliesPanel.revalidate();
        suppliesPanel.repaint();
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
