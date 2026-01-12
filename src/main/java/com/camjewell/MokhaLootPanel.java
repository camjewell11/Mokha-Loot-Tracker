package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
    private java.util.function.BooleanSupplier isInRun;

    // Profit/Loss section
    private JLabel totalClaimedLabel;
    private JLabel supplyCostLabel;
    private JLabel profitLossLabel;
    private JLabel totalUnclaimedLabel;
    private JLabel claimUnclaimRatioLabel;
    private JLabel claimedCountLabel;
    private JLabel deathCountLabel;

    // Current Run section
    private JLabel potentialValueLabel;
    private JPanel currentRunItemsPanel;

    // Claimed Loot by Wave - now stores panels for dynamic item lists
    private JPanel claimedWavesContainer; // Container for all waves
    private JPanel[] claimedWavePanels = new JPanel[9]; // Wave 1-8 and 9+
    private JLabel[] claimedWaveValueLabels = new JLabel[9];
    private JPanel[] claimedWaveItemPanels = new JPanel[9];
    private boolean[] claimedWaveCollapsed = new boolean[9];
    // 0 = expanded, 1 = collapsed, 2 = combined
    private int claimedSectionState = 1; // Start collapsed
    private JLabel claimedSectionTotalLabel; // Total value label for collapsed view
    private JPanel claimedCombinedPanel; // Panel for combined all-waves view

    // Unclaimed Loot by Wave - now stores panels for dynamic item lists
    private JPanel unclaimedWavesContainer; // Container for all waves
    private JPanel[] unclaimedWavePanels = new JPanel[9]; // Wave 1-8 and 9+
    private JLabel[] unclaimedWaveValueLabels = new JLabel[9];
    private JPanel[] unclaimedWaveItemPanels = new JPanel[9];
    private boolean[] unclaimedWaveCollapsed = new boolean[9];
    // 0 = expanded, 1 = collapsed, 2 = combined
    private int unclaimedSectionState = 1; // Start collapsed
    private JLabel unclaimedSectionTotalLabel; // Total value label for collapsed view
    private JPanel unclaimedCombinedPanel; // Panel for combined all-waves view

    // Supplies Used Current Run
    private JLabel suppliesCurrentRunTotalLabel;
    private JPanel suppliesCurrentRunPanel;
    private JPanel suppliesCurrentRunContainer; // Container for all supplies content
    private boolean suppliesCurrentRunCollapsed = false; // Track collapse state
    private JLabel suppliesCurrentRunHeaderLabel; // Collapsed view total label

    // Supplies Used (All Time)
    private JLabel suppliesTotalValueLabel;
    private JPanel suppliesTotalItemsPanel;
    private JPanel suppliesTotalContainer; // Container for all supplies content
    private boolean suppliesTotalCollapsed = true; // Track collapse state
    private JLabel suppliesTotalHeaderLabel; // Collapsed view total label

    private final JPanel statsPanel = new JPanel();
    private Runnable onClearData;
    private Runnable onRecalculateTotals;

    // Add these fields to hold references to the historical data maps (set via
    // setter or constructor)
    private Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalClaimedItemsByWave;
    private Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalUnclaimedItemsByWave;

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onDebugLocation) {
        this(config, onDebugLocation, null, null, null);
    }

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onDebugLocation, Runnable onClearData) {
        this(config, onDebugLocation, onClearData, null, null);
    }

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onDebugLocation, Runnable onClearData,
            Runnable onRecalculateTotals) {
        this(config, onDebugLocation, onClearData, onRecalculateTotals, null);
    }

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onDebugLocation, Runnable onClearData,
            Runnable onRecalculateTotals, java.util.function.BooleanSupplier isInRun) {
        this.config = config;
        this.onDebugLocation = onDebugLocation;
        this.onClearData = onClearData;
        this.onRecalculateTotals = onRecalculateTotals;
        this.isInRun = isInRun;

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
        statsPanel.setBorder(new EmptyBorder(3, 0, 3, 0));

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        buttonPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JButton recalculateButton = new JButton("Recalculate Totals");
        recalculateButton.setFocusPainted(false);
        recalculateButton.setBackground(new Color(0, 100, 50)); // Muted green
        recalculateButton.setForeground(Color.WHITE);
        recalculateButton.setFont(FontManager.getRunescapeSmallFont());
        recalculateButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        recalculateButton.addActionListener(e -> {
            // Check if currently in a run
            if (isInRun != null && isInRun.getAsBoolean()) {
                // Show error dialog - cannot recalculate during run
                JOptionPane.showMessageDialog(this,
                        "Will not recalculate during a run.\nFinish your run and try again.",
                        "Cannot Recalculate",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                // Show confirmation dialog
                int response = JOptionPane.showConfirmDialog(this,
                        "Are you sure you want to recalculate all totals?",
                        "Confirm Recalculate Totals",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (response == JOptionPane.YES_OPTION && onRecalculateTotals != null) {
                    onRecalculateTotals.run();
                }
            }
        });
        buttonPanel.add(recalculateButton);
        buttonPanel.add(Box.createVerticalStrut(10));

        JButton clearButton = new JButton("Clear All Data");
        clearButton.setFocusPainted(false);
        clearButton.setBackground(new Color(120, 0, 0)); // Dark red background
        clearButton.setForeground(Color.WHITE);
        clearButton.setFont(FontManager.getRunescapeSmallFont());
        clearButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        clearButton.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to clear ALL current and historical data?\nThis action cannot be undone.",
                    "Confirm Clear All Data",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (response == JOptionPane.YES_OPTION && onClearData != null) {
                onClearData.run();
            }
        });
        buttonPanel.add(clearButton);

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
    }

    private JPanel createProfitLossSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Summary:");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(218, 165, 32)); // Gold
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setBorder(new EmptyBorder(1, 0, 0, 0));
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
        titleRow.add(title, BorderLayout.WEST);
        panel.add(titleRow);

        totalClaimedLabel = new JLabel("0 gp");
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

        claimUnclaimRatioLabel = new JLabel("0.00x");
        claimUnclaimRatioLabel.setFont(FontManager.getRunescapeFont());
        claimUnclaimRatioLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Claim/Unclaim Ratio:", claimUnclaimRatioLabel));

        panel.add(createInternalSeparator());

        claimedCountLabel = new JLabel("0");
        claimedCountLabel.setFont(FontManager.getRunescapeFont());
        claimedCountLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Total Claims:", claimedCountLabel));

        deathCountLabel = new JLabel("0");
        deathCountLabel.setFont(FontManager.getRunescapeFont());
        deathCountLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Total Deaths:", deathCountLabel));

        return panel;
    }

    private JPanel createCurrentRunSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        JLabel title = new JLabel("Current Run");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(0, 200, 255)); // Cyan
        titleRow.add(title, BorderLayout.WEST);
        panel.add(titleRow);

        potentialValueLabel = new JLabel("0 gp");
        potentialValueLabel.setFont(FontManager.getRunescapeFont());
        potentialValueLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Potential Value:", potentialValueLabel));

        // Current run items panel
        currentRunItemsPanel = new JPanel();
        currentRunItemsPanel.setLayout(new BoxLayout(currentRunItemsPanel, BoxLayout.Y_AXIS));
        currentRunItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.add(currentRunItemsPanel);

        return panel;
    }

    private JPanel createClaimedLootSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header with collapse button and title
        JButton collapseButton = new JButton("▸"); // Start collapsed
        collapseButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        collapseButton.setForeground(Color.WHITE);
        collapseButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        collapseButton.setBorderPainted(false);
        collapseButton.setFocusPainted(false);
        collapseButton.setPreferredSize(new Dimension(18, 18));
        collapseButton.setMaximumSize(new Dimension(18, 18));

        JLabel title = new JLabel("Claimed Loot");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(0, 200, 0)); // Green

        claimedSectionTotalLabel = new JLabel("0 gp");
        claimedSectionTotalLabel.setFont(FontManager.getRunescapeFont());
        claimedSectionTotalLabel.setForeground(Color.WHITE);
        claimedSectionTotalLabel.setVisible(false); // Hidden when expanded

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rightPanel.add(claimedSectionTotalLabel, BorderLayout.CENTER);
        rightPanel.add(collapseButton, BorderLayout.EAST);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        titleRow.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(rightPanel, BorderLayout.EAST);
        panel.add(titleRow);

        // Container for all wave panels
        claimedWavesContainer = new JPanel();
        claimedWavesContainer.setLayout(new BoxLayout(claimedWavesContainer, BoxLayout.Y_AXIS));
        claimedWavesContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        for (int i = 0; i < 9; i++) {
            claimedWavePanels[i] = createWavePanel(
                    "Wave " + (i == 8 ? "9+" : i + 1),
                    claimedWaveValueLabels,
                    claimedWaveItemPanels,
                    claimedWaveCollapsed,
                    i);
            claimedWavesContainer.add(claimedWavePanels[i]);
        }

        // Combined all-waves panel
        claimedCombinedPanel = new JPanel();
        claimedCombinedPanel.setLayout(new BoxLayout(claimedCombinedPanel, BoxLayout.Y_AXIS));
        claimedCombinedPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        claimedCombinedPanel.setVisible(false);

        panel.add(claimedWavesContainer);
        panel.add(claimedCombinedPanel);

        // Set initial state: 0 = expanded, 1 = collapsed, 2 = combined
        updateClaimedSectionView();

        // Collapse/expand/combined logic
        collapseButton.addActionListener(e -> {
            claimedSectionState = (claimedSectionState + 1) % 3;
            updateClaimedSectionView();
            panel.revalidate();
            panel.repaint();
        });

        return panel;
    }

    private JPanel createUnclaimedLootSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header with collapse button and title
        JButton collapseButton = new JButton("▸"); // Start collapsed
        collapseButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        collapseButton.setForeground(Color.WHITE);
        collapseButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        collapseButton.setBorderPainted(false);
        collapseButton.setFocusPainted(false);
        collapseButton.setPreferredSize(new Dimension(18, 18));
        collapseButton.setMaximumSize(new Dimension(18, 18));

        JLabel title = new JLabel("Unclaimed Loot");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(200, 0, 0)); // Red

        unclaimedSectionTotalLabel = new JLabel("0 gp");
        unclaimedSectionTotalLabel.setFont(FontManager.getRunescapeFont());
        unclaimedSectionTotalLabel.setForeground(Color.WHITE);
        unclaimedSectionTotalLabel.setVisible(false); // Hidden when expanded

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rightPanel.add(unclaimedSectionTotalLabel, BorderLayout.CENTER);
        rightPanel.add(collapseButton, BorderLayout.EAST);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        titleRow.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(rightPanel, BorderLayout.EAST);
        panel.add(titleRow);

        // Container for all wave panels
        unclaimedWavesContainer = new JPanel();
        unclaimedWavesContainer.setLayout(new BoxLayout(unclaimedWavesContainer, BoxLayout.Y_AXIS));
        unclaimedWavesContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        for (int i = 0; i < 9; i++) {
            unclaimedWavePanels[i] = createWavePanel(
                    "Wave " + (i == 8 ? "9+" : i + 1),
                    unclaimedWaveValueLabels,
                    unclaimedWaveItemPanels,
                    unclaimedWaveCollapsed,
                    i);
            unclaimedWavesContainer.add(unclaimedWavePanels[i]);
        }

        // Combined all-waves panel
        unclaimedCombinedPanel = new JPanel();
        unclaimedCombinedPanel.setLayout(new BoxLayout(unclaimedCombinedPanel, BoxLayout.Y_AXIS));
        unclaimedCombinedPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        unclaimedCombinedPanel.setVisible(false);

        panel.add(unclaimedWavesContainer);
        panel.add(unclaimedCombinedPanel);

        // Set initial state: 0 = expanded, 1 = collapsed, 2 = combined
        updateUnclaimedSectionView();

        // Collapse/expand/combined logic
        collapseButton.addActionListener(e -> {
            unclaimedSectionState = (unclaimedSectionState + 1) % 3;
            updateUnclaimedSectionView();
            panel.revalidate();
            panel.repaint();
        });

        return panel;
        // Helper to update claimed section view based on state
    }

    private void updateClaimedSectionView() {
        switch (claimedSectionState) {
            case 0: // expanded
                claimedWavesContainer.setVisible(true);
                claimedCombinedPanel.setVisible(false);
                claimedSectionTotalLabel.setVisible(false);
                setClaimedCollapseButtonText("▾");
                break;
            case 1: // collapsed
                claimedWavesContainer.setVisible(false);
                claimedCombinedPanel.setVisible(false);
                claimedSectionTotalLabel.setVisible(true);
                setClaimedCollapseButtonText("▸");
                break;
            default: // combined
                claimedWavesContainer.setVisible(false);
                claimedCombinedPanel.setVisible(true);
                claimedSectionTotalLabel.setVisible(false);
                setClaimedCollapseButtonText("◂");
                populateClaimedCombinedPanel();
                break;
        }
    }

    // Helper to update unclaimed section view based on state
    private void updateUnclaimedSectionView() {
        switch (unclaimedSectionState) {
            case 0: // expanded
                unclaimedWavesContainer.setVisible(true);
                unclaimedCombinedPanel.setVisible(false);
                unclaimedSectionTotalLabel.setVisible(false);
                setUnclaimedCollapseButtonText("▾");
                break;
            case 1: // collapsed
                unclaimedWavesContainer.setVisible(false);
                unclaimedCombinedPanel.setVisible(false);
                unclaimedSectionTotalLabel.setVisible(true);
                setUnclaimedCollapseButtonText("▸");
                break;
            default: // combined
                unclaimedWavesContainer.setVisible(false);
                unclaimedCombinedPanel.setVisible(true);
                unclaimedSectionTotalLabel.setVisible(false);
                setUnclaimedCollapseButtonText("◂");
                populateUnclaimedCombinedPanel();
                break;
        }
    }

    // Helper to set collapse button text for claimed section
    private void setClaimedCollapseButtonText(String text) {
        // Find the collapse button in claimed section and set text
        // (Assumes only one collapse button in claimed section header)
        JPanel header = (JPanel) ((JPanel) claimedWavesContainer.getParent()).getComponent(0);
        JPanel rightPanel = (JPanel) header.getComponent(1);
        JButton collapseButton = (JButton) rightPanel.getComponent(rightPanel.getComponentCount() - 1);
        collapseButton.setText(text);
    }

    /**
     * Combines all claimed wave items for display in the combined panel.
     * This does not change how data is stored, only how it is shown.
     */
    private void populateClaimedCombinedPanel() {
        claimedCombinedPanel.removeAll();
        Map<String, MokhaLootTrackerPlugin.ItemAggregate> combined = new java.util.HashMap<>();
        long totalValue = 0;
        if (historicalClaimedItemsByWave != null) {
            for (Map.Entry<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> waveEntry : historicalClaimedItemsByWave
                    .entrySet()) {
                Map<String, MokhaLootTrackerPlugin.ItemAggregate> waveMap = waveEntry.getValue();
                if (waveMap != null) {
                    for (Map.Entry<String, MokhaLootTrackerPlugin.ItemAggregate> itemEntry : waveMap.entrySet()) {
                        MokhaLootTrackerPlugin.ItemAggregate agg = itemEntry.getValue();
                        if (agg == null)
                            continue;
                        MokhaLootTrackerPlugin.ItemAggregate existing = combined.get(agg.name);
                        if (existing == null) {
                            combined.put(agg.name, new MokhaLootTrackerPlugin.ItemAggregate(agg.name, agg.totalQuantity,
                                    agg.pricePerItem));
                            combined.get(agg.name).totalValue = agg.totalValue;
                        } else {
                            existing.totalQuantity += agg.totalQuantity;
                            existing.totalValue += agg.totalValue;
                        }
                        totalValue += agg.totalValue;
                    }
                }
            }
        }
        for (MokhaLootTrackerPlugin.ItemAggregate agg : combined.values()) {
            JPanel itemRow = new JPanel(new java.awt.BorderLayout());
            itemRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            itemRow.setBorder(new EmptyBorder(2, 5, 2, 0));
            String left = "- " + agg.name + " x" + agg.totalQuantity;
            JLabel itemLabel = new JLabel(left);
            // Color gold if value > 20m or if item is Dom
            Color itemColor = (agg.totalValue > 20_000_000 || "Dom".equalsIgnoreCase(agg.name))
                    ? new Color(218, 165, 32)
                    : ColorScheme.LIGHT_GRAY_COLOR;
            itemLabel.setForeground(itemColor);
            itemLabel.setFont(FontManager.getRunescapeSmallFont());
            String pricePerItemText = agg.pricePerItem > 0 ? (formatGp(agg.pricePerItem) + "/ea") : "N/A";
            itemRow.setToolTipText("Price per item: " + pricePerItemText);
            itemRow.add(itemLabel, java.awt.BorderLayout.WEST);
            JLabel itemValueLabel = new JLabel(formatGp(agg.totalValue));
            itemValueLabel.setForeground(itemColor);
            itemValueLabel.setFont(FontManager.getRunescapeSmallFont());
            itemRow.add(itemValueLabel, java.awt.BorderLayout.EAST);
            claimedCombinedPanel.add(itemRow);
        }
        JLabel totalLabel = new JLabel("Total: " + formatGp(totalValue));
        totalLabel.setFont(FontManager.getRunescapeBoldFont());
        totalLabel.setForeground(new Color(0, 200, 0));
        claimedCombinedPanel.add(Box.createVerticalStrut(8));
        claimedCombinedPanel.add(totalLabel);
        claimedCombinedPanel.revalidate();
        claimedCombinedPanel.repaint();
    }

    /**
     * Combines all unclaimed wave items for display in the combined panel.
     * This does not change how data is stored, only how it is shown.
     */
    private void populateUnclaimedCombinedPanel() {
        unclaimedCombinedPanel.removeAll();
        Map<String, MokhaLootTrackerPlugin.ItemAggregate> combined = new java.util.HashMap<>();
        long totalValue = 0;
        if (historicalUnclaimedItemsByWave != null) {
            for (Map.Entry<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> waveEntry : historicalUnclaimedItemsByWave
                    .entrySet()) {
                Map<String, MokhaLootTrackerPlugin.ItemAggregate> waveMap = waveEntry.getValue();
                if (waveMap != null) {
                    for (Map.Entry<String, MokhaLootTrackerPlugin.ItemAggregate> itemEntry : waveMap.entrySet()) {
                        MokhaLootTrackerPlugin.ItemAggregate agg = itemEntry.getValue();
                        if (agg == null)
                            continue;
                        MokhaLootTrackerPlugin.ItemAggregate existing = combined.get(agg.name);
                        if (existing == null) {
                            combined.put(agg.name, new MokhaLootTrackerPlugin.ItemAggregate(agg.name, agg.totalQuantity,
                                    agg.pricePerItem));
                            combined.get(agg.name).totalValue = agg.totalValue;
                        } else {
                            existing.totalQuantity += agg.totalQuantity;
                            existing.totalValue += agg.totalValue;
                        }
                        totalValue += agg.totalValue;
                    }
                }
            }
        }
        for (MokhaLootTrackerPlugin.ItemAggregate agg : combined.values()) {
            JPanel itemRow = new JPanel(new java.awt.BorderLayout());
            itemRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            itemRow.setBorder(new EmptyBorder(2, 5, 2, 0));
            String left = "- " + agg.name + " x" + agg.totalQuantity;
            JLabel itemLabel = new JLabel(left);
            Color itemColor = (agg.totalValue > 20_000_000 || "Dom".equalsIgnoreCase(agg.name))
                    ? new Color(218, 165, 32)
                    : ColorScheme.LIGHT_GRAY_COLOR;
            itemLabel.setForeground(itemColor);
            itemLabel.setFont(FontManager.getRunescapeSmallFont());
            String pricePerItemText = agg.pricePerItem > 0 ? (formatGp(agg.pricePerItem) + "/ea") : "N/A";
            itemRow.setToolTipText("Price per item: " + pricePerItemText);
            itemRow.add(itemLabel, java.awt.BorderLayout.WEST);
            JLabel itemValueLabel = new JLabel(formatGp(agg.totalValue));
            itemValueLabel.setForeground(itemColor);
            itemValueLabel.setFont(FontManager.getRunescapeSmallFont());
            itemRow.add(itemValueLabel, java.awt.BorderLayout.EAST);
            unclaimedCombinedPanel.add(itemRow);
        }
        JLabel totalLabel = new JLabel("Total: " + formatGp(totalValue));
        totalLabel.setFont(FontManager.getRunescapeBoldFont());
        totalLabel.setForeground(new Color(200, 0, 0));
        unclaimedCombinedPanel.add(Box.createVerticalStrut(8));
        unclaimedCombinedPanel.add(totalLabel);
        unclaimedCombinedPanel.revalidate();
        unclaimedCombinedPanel.repaint();
    }

    private void setUnclaimedCollapseButtonText(String text) {
        JPanel header = (JPanel) ((JPanel) unclaimedWavesContainer.getParent()).getComponent(0);
        JPanel rightPanel = (JPanel) header.getComponent(1);
        JButton collapseButton = (JButton) rightPanel.getComponent(rightPanel.getComponentCount() - 1);
        collapseButton.setText(text);
    }

    private JPanel createSuppliesCurrentRunSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header with collapse button and title
        JButton collapseButton = new JButton("▾"); // Down triangle for expanded
        collapseButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        collapseButton.setForeground(Color.WHITE);
        collapseButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        collapseButton.setBorderPainted(false);
        collapseButton.setFocusPainted(false);
        collapseButton.setPreferredSize(new Dimension(18, 18));
        collapseButton.setMaximumSize(new Dimension(18, 18));

        JLabel title = new JLabel("Supplies Used (Current Run)");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(255, 165, 0)); // Orange

        suppliesCurrentRunHeaderLabel = new JLabel("0 gp");
        suppliesCurrentRunHeaderLabel.setFont(FontManager.getRunescapeFont());
        suppliesCurrentRunHeaderLabel.setForeground(Color.WHITE);
        suppliesCurrentRunHeaderLabel.setVisible(false); // Hidden when expanded

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rightPanel.add(suppliesCurrentRunHeaderLabel, BorderLayout.CENTER);
        rightPanel.add(collapseButton, BorderLayout.EAST);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        titleRow.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(rightPanel, BorderLayout.EAST);
        panel.add(titleRow);

        // Container for supplies content
        suppliesCurrentRunContainer = new JPanel();
        suppliesCurrentRunContainer.setLayout(new BoxLayout(suppliesCurrentRunContainer, BoxLayout.Y_AXIS));
        suppliesCurrentRunContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        suppliesCurrentRunTotalLabel = new JLabel("0 gp");
        suppliesCurrentRunTotalLabel.setFont(FontManager.getRunescapeFont());
        suppliesCurrentRunTotalLabel.setForeground(Color.WHITE);
        suppliesCurrentRunContainer.add(createStatRow("Total Value:", suppliesCurrentRunTotalLabel));

        suppliesCurrentRunPanel = new JPanel();
        suppliesCurrentRunPanel.setLayout(new BoxLayout(suppliesCurrentRunPanel, BoxLayout.Y_AXIS));
        suppliesCurrentRunPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        suppliesCurrentRunContainer.add(suppliesCurrentRunPanel);

        panel.add(suppliesCurrentRunContainer);

        // Collapse/expand logic
        collapseButton.addActionListener(e -> {
            suppliesCurrentRunCollapsed = !suppliesCurrentRunCollapsed;
            suppliesCurrentRunContainer.setVisible(!suppliesCurrentRunCollapsed);
            suppliesCurrentRunHeaderLabel.setVisible(suppliesCurrentRunCollapsed); // Show when collapsed
            collapseButton.setText(suppliesCurrentRunCollapsed ? "▸" : "▾");
            panel.revalidate();
            panel.repaint();
        });

        return panel;
    }

    private JPanel createSuppliesTotalSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header with collapse button and title
        JButton collapseButton = new JButton("▾"); // Down triangle for expanded
        collapseButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        collapseButton.setForeground(Color.WHITE);
        collapseButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        collapseButton.setBorderPainted(false);
        collapseButton.setFocusPainted(false);
        collapseButton.setPreferredSize(new Dimension(18, 18));
        collapseButton.setMaximumSize(new Dimension(18, 18));

        JLabel title = new JLabel("Supplies Used");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(255, 165, 0)); // Orange

        suppliesTotalHeaderLabel = new JLabel("0 gp");
        suppliesTotalHeaderLabel.setFont(FontManager.getRunescapeFont());
        suppliesTotalHeaderLabel.setForeground(Color.WHITE);
        suppliesTotalHeaderLabel.setVisible(false); // Hidden when expanded

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rightPanel.add(suppliesTotalHeaderLabel, BorderLayout.CENTER);
        rightPanel.add(collapseButton, BorderLayout.EAST);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        titleRow.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(rightPanel, BorderLayout.EAST);
        panel.add(titleRow);

        // Container for supplies content
        suppliesTotalContainer = new JPanel();
        suppliesTotalContainer.setLayout(new BoxLayout(suppliesTotalContainer, BoxLayout.Y_AXIS));
        suppliesTotalContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        suppliesTotalValueLabel = new JLabel("0 gp");
        suppliesTotalValueLabel.setFont(FontManager.getRunescapeFont());
        suppliesTotalValueLabel.setForeground(Color.WHITE);
        suppliesTotalContainer.add(createStatRow("Total Value:", suppliesTotalValueLabel));

        suppliesTotalItemsPanel = new JPanel();
        suppliesTotalItemsPanel.setLayout(new BoxLayout(suppliesTotalItemsPanel, BoxLayout.Y_AXIS));
        suppliesTotalItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        suppliesTotalContainer.add(suppliesTotalItemsPanel);

        panel.add(suppliesTotalContainer);

        // Set initial collapsed/expanded state based on suppliesTotalCollapsed
        suppliesTotalContainer.setVisible(!suppliesTotalCollapsed);
        suppliesTotalHeaderLabel.setVisible(suppliesTotalCollapsed);
        collapseButton.setText(suppliesTotalCollapsed ? "▸" : "▾");

        // Collapse/expand logic
        collapseButton.addActionListener(e -> {
            suppliesTotalCollapsed = !suppliesTotalCollapsed;
            suppliesTotalContainer.setVisible(!suppliesTotalCollapsed);
            suppliesTotalHeaderLabel.setVisible(suppliesTotalCollapsed); // Show when collapsed
            collapseButton.setText(suppliesTotalCollapsed ? "▸" : "▾");
            panel.revalidate();
            panel.repaint();
        });

        return panel;
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

    private JPanel createWavePanel(String waveTitle, JLabel[] valueLabels, JPanel[] itemPanels,
            boolean[] collapsedStates,
            int index) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header with collapse button, wave title, and value
        JLabel valueLabel = new JLabel("0 gp");
        valueLabel.setFont(FontManager.getRunescapeFont());
        valueLabel.setForeground(Color.WHITE);

        JButton collapseButton = new JButton("▾");
        collapseButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        collapseButton.setForeground(Color.WHITE);
        collapseButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        collapseButton.setBorderPainted(false);
        collapseButton.setFocusPainted(false);
        collapseButton.setPreferredSize(new Dimension(18, 18));
        collapseButton.setMaximumSize(new Dimension(18, 18));

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        headerRow.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        JLabel labelComponent = new JLabel(waveTitle + ":");
        labelComponent.setFont(FontManager.getRunescapeFont());
        labelComponent.setForeground(Color.LIGHT_GRAY);

        headerRow.add(collapseButton, BorderLayout.WEST);
        headerRow.add(labelComponent, BorderLayout.CENTER);
        headerRow.add(valueLabel, BorderLayout.EAST);

        panel.add(headerRow);

        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.add(itemsPanel);

        // Store references for update methods
        valueLabels[index] = valueLabel;
        itemPanels[index] = itemsPanel;
        collapsedStates[index] = false;

        collapseButton.addActionListener(e -> {
            collapsedStates[index] = !collapsedStates[index];
            itemsPanel.setVisible(!collapsedStates[index]);
            collapseButton.setText(collapsedStates[index] ? "▸" : "▾");
            panel.revalidate();
            panel.repaint();
        });

        return panel;
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
    public void updateProfitLoss(long totalClaimed, long supplyCost, long totalUnclaimed, long claimedCount,
            long deathCount) {
        totalClaimedLabel.setText(formatGp(totalClaimed));
        totalClaimedLabel.setForeground(new Color(0, 200, 0)); // Green

        supplyCostLabel.setText(formatGp(supplyCost));
        supplyCostLabel.setForeground(new Color(255, 165, 0)); // Orange

        long profitLoss = totalClaimed - supplyCost;
        profitLossLabel.setText(formatGp(profitLoss));
        profitLossLabel.setForeground(profitLoss >= 0 ? new Color(0, 200, 0) : new Color(200, 0, 0));

        totalUnclaimedLabel.setText(formatGp(totalUnclaimed));
        totalUnclaimedLabel.setForeground(new Color(200, 0, 0)); // Red

        claimedCountLabel.setText(String.valueOf((int) claimedCount));
        deathCountLabel.setText(String.valueOf((int) deathCount));

        // Calculate and display claim/unclaim ratio with muted colors
        if (totalUnclaimed > 0) {
            double ratio = (double) totalClaimed / totalUnclaimed;
            claimUnclaimRatioLabel.setText(String.format("%.2fx", ratio));
            // Use muted green for positive, muted red for negative
            claimUnclaimRatioLabel.setForeground(ratio >= 1.0 ? new Color(0, 170, 0) : new Color(170, 0, 0));
        } else {
            claimUnclaimRatioLabel.setText("0.00x");
            claimUnclaimRatioLabel.setForeground(Color.WHITE);
        }

    }

    public void updateCurrentRun(long potentialValue, Map<String, ItemData> itemData) {
        potentialValueLabel.setText(formatGp(potentialValue));
        updateSuppliesPanel(currentRunItemsPanel, itemData);
    }

    public void updateClaimedWave(int wave, Map<String, ItemData> itemData) {
        updateClaimedWave(wave, itemData, -1);
    }

    public void updateClaimedWave(int wave, Map<String, ItemData> itemData, long explicitTotal) {
        int index = wave >= 9 ? 8 : wave - 1;
        if (index >= 0 && index < claimedWavePanels.length) {
            updateWavePanel(claimedWaveValueLabels[index], claimedWaveItemPanels[index],
                    claimedWaveCollapsed[index], itemData, explicitTotal);
            updateClaimedSectionTotal();
        }
    }

    public void updateUnclaimedWave(int wave, Map<String, ItemData> itemData) {
        updateUnclaimedWave(wave, itemData, -1);
    }

    public void updateUnclaimedWave(int wave, Map<String, ItemData> itemData, long explicitTotal) {
        int index = wave >= 9 ? 8 : wave - 1;
        if (index >= 0 && index < unclaimedWavePanels.length) {
            updateWavePanel(unclaimedWaveValueLabels[index], unclaimedWaveItemPanels[index],
                    unclaimedWaveCollapsed[index], itemData, explicitTotal);
            updateUnclaimedSectionTotal();
        }
    }

    public void updateSuppliesCurrentRun(long totalValue, Map<String, ItemData> itemData) {
        // Update total value label
        suppliesCurrentRunTotalLabel.setText(formatGp(totalValue));
        suppliesCurrentRunHeaderLabel.setText(formatGp(totalValue)); // Also update header label
        // Update items
        updateSuppliesPanel(suppliesCurrentRunPanel, itemData);
    }

    public void updateSuppliesTotal(long totalValue, Map<String, ItemData> itemData) {
        suppliesTotalValueLabel.setText(formatGp(totalValue));
        suppliesTotalHeaderLabel.setText(formatGp(totalValue)); // Also update header label
        updateSuppliesPanel(suppliesTotalItemsPanel, itemData);
    }

    /**
     * Update the claimed loot section total for collapsed view to match the summary
     * value
     */
    private void updateClaimedSectionTotal() {
        claimedSectionTotalLabel.setText(totalClaimedLabel.getText());
    }

    /**
     * Update the unclaimed loot section total for collapsed view to match the
     * summary value
     */
    private void updateUnclaimedSectionTotal() {
        unclaimedSectionTotalLabel.setText(totalUnclaimedLabel.getText());
    }

    public void clearAllPanelData() {
        // Clear Profit/Loss section
        totalClaimedLabel.setText("0 gp");
        totalClaimedLabel.setForeground(Color.WHITE);
        supplyCostLabel.setText("0 gp");
        supplyCostLabel.setForeground(Color.WHITE);
        profitLossLabel.setText("0 gp");
        profitLossLabel.setForeground(Color.WHITE);
        totalUnclaimedLabel.setText("0 gp");
        claimUnclaimRatioLabel.setText("0.00x");
        claimedCountLabel.setText("0");
        deathCountLabel.setText("0");

        // Clear Current Run section
        potentialValueLabel.setText("0 gp");
        currentRunItemsPanel.removeAll();

        // Clear all claimed wave panels
        for (int i = 0; i < claimedWavePanels.length; i++) {
            claimedWaveValueLabels[i].setText("0 gp");
            claimedWaveItemPanels[i].removeAll();
        }
        claimedSectionTotalLabel.setText("0 gp");

        // Clear all unclaimed wave panels
        for (int i = 0; i < unclaimedWavePanels.length; i++) {
            unclaimedWaveValueLabels[i].setText("0 gp");
            unclaimedWaveItemPanels[i].removeAll();
        }
        unclaimedSectionTotalLabel.setText("0 gp");

        // Clear supplies sections
        suppliesCurrentRunTotalLabel.setText("0 gp");
        suppliesCurrentRunHeaderLabel.setText("0 gp");
        suppliesCurrentRunPanel.removeAll();
        suppliesTotalValueLabel.setText("0 gp");
        suppliesTotalHeaderLabel.setText("0 gp");
        suppliesTotalItemsPanel.removeAll();

        // Refresh the panel
        statsPanel.revalidate();
        statsPanel.repaint();
    }

    private void updateWavePanel(JLabel valueLabel, JPanel itemsPanel, boolean isCollapsed,
            Map<String, ItemData> itemData, long explicitTotal) {
        itemsPanel.removeAll();

        if (itemData == null || itemData.isEmpty()) {
            valueLabel.setText("0 gp");
            itemsPanel.setVisible(!isCollapsed);
            itemsPanel.revalidate();
            itemsPanel.repaint();
            return;
        }

        long totalValue = explicitTotal >= 0 ? explicitTotal : 0;
        if (explicitTotal < 0) {
            for (ItemData item : itemData.values()) {
                totalValue += item.totalValue;
            }
        }

        valueLabel.setText(formatGp(totalValue));

        for (ItemData item : itemData.values()) {
            String pricePerItemText = item.pricePerItem > 0 ? (formatGp(item.pricePerItem) + "/ea") : "N/A";

            JPanel itemRow = new JPanel(new BorderLayout());
            itemRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            itemRow.setBorder(new EmptyBorder(2, 15, 2, 0));
            itemRow.setToolTipText("Price per item: " + pricePerItemText);

            JLabel itemLabel = new JLabel("- " + item.name + " x" + item.quantity);
            // Color gold if value > 20m or if item is Dom
            Color itemColor = (item.totalValue > 20_000_000 || "Dom".equalsIgnoreCase(item.name))
                    ? new Color(218, 165, 32)
                    : ColorScheme.LIGHT_GRAY_COLOR;
            itemLabel.setForeground(itemColor);
            itemLabel.setFont(FontManager.getRunescapeSmallFont());
            itemRow.add(itemLabel, BorderLayout.WEST);

            JLabel itemValueLabel = new JLabel(formatGp(item.totalValue));
            itemValueLabel.setForeground(itemColor);
            itemValueLabel.setFont(FontManager.getRunescapeSmallFont());
            itemRow.add(itemValueLabel, BorderLayout.EAST);

            itemsPanel.add(itemRow);
        }

        itemsPanel.setVisible(!isCollapsed);
        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    private void updateSuppliesPanel(JPanel suppliesPanel, Map<String, ItemData> itemData) {
        // Clear existing items
        suppliesPanel.removeAll();

        if (itemData == null || itemData.isEmpty()) {
            return;
        }

        // Add item entries
        for (ItemData item : itemData.values()) {
            String pricePerItemText = item.pricePerItem > 0 ? (formatGp(item.pricePerItem) + "/ea") : "N/A";

            // Create item row with BorderLayout for left/right alignment
            JPanel itemRow = new JPanel(new BorderLayout());
            itemRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            itemRow.setBorder(new EmptyBorder(2, 5, 2, 0));
            itemRow.setToolTipText("Price: " + pricePerItemText);

            // Left side: item name and quantity
            JLabel itemLabel = new JLabel("- " + item.name + " x" + item.quantity);
            itemLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            itemLabel.setFont(FontManager.getRunescapeSmallFont());
            itemRow.add(itemLabel, BorderLayout.WEST);

            // Right side: value
            JLabel itemValueLabel = new JLabel(formatGp(item.totalValue));
            itemValueLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
            itemValueLabel.setFont(FontManager.getRunescapeSmallFont());
            itemRow.add(itemValueLabel, BorderLayout.EAST);

            suppliesPanel.add(itemRow);
        }

        suppliesPanel.revalidate();
        suppliesPanel.repaint();
    }

    private String formatGp(long value) {
        if (value >= 1_000_000 || value <= -1_000_000) {
            return String.format("%.2fM gp", value / 1_000_000.0);
        } else if (value >= 1_000 || value <= -1_000) {
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

    // Add setters for these maps
    public void setHistoricalClaimedItemsByWave(Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> map) {
        this.historicalClaimedItemsByWave = map;
    }

    public void setHistoricalUnclaimedItemsByWave(Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> map) {
        this.historicalUnclaimedItemsByWave = map;
    }
}
