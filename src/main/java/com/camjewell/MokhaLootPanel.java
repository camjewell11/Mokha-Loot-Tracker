package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

public class MokhaLootPanel extends PluginPanel {
    private static final int ACTION_BUTTON_HEIGHT = 30;
    private static final Color UNIQUE_GOLD_COLOR = new Color(218, 165, 32);
    private static final Color HP_LOST_COLOR = new Color(200, 60, 60);
    private static final Color PRAYER_USED_COLOR = new Color(80, 210, 190);
    private static final Color PRAYER_REGAINED_COLOR = new Color(130, 220, 210);
    private static final Color SPECIAL_ATTACKS_COLOR = new Color(80, 170, 255);
    private static final Color VENOM_COLOR = new Color(0, 128, 0);
    private static final Color HP_REGAINED_COLOR = new Color(60, 180, 60);

    private final MokhaLootTrackerConfig config;
    private final java.util.function.BooleanSupplier isInRun;
    private boolean displayHaValueOnHover;

    // Profit/Loss section
    private JLabel totalClaimedLabel;
    private JLabel supplyCostLabel;
    private JLabel profitLossLabel;
    private JLabel totalUnclaimedLabel;
    private JLabel claimUnclaimRatioLabel;
    private JLabel claimedCountLabel;
    private JLabel deathCountLabel;
    private JLabel uniqueClaimsCountLabel;
    private long summaryTotalClaimedGe;
    private long summarySupplyCostGe;
    private long summaryTotalUnclaimedGe;

    // Current Run section
    private JLabel potentialValueLabel;
    private JLabel cumulativeUniqueChanceLabel;
    private JLabel currentRunWaveLabel;
    private JButton currentRunViewToggleButton;
    private JPanel currentRunItemsPanel;
    private long currentRunGeTotal;
    private long currentRunHaTotal;
    private Map<String, ItemData> currentRunItemData = new HashMap<>();
    private Map<Integer, Map<String, ItemData>> currentRunItemsByWave = new TreeMap<>();
    private Map<Integer, Long> currentRunTotalsByWave = new TreeMap<>();
    private Map<Integer, Long> currentRunHaTotalsByWave = new TreeMap<>();
    private Map<Integer, Boolean> currentRunWaveCollapsed = new HashMap<>();
    private boolean currentRunShowByWave = false;

    // Previous Run section
    private JLabel previousRunStatusLabel;
    private JButton previousRunCollapseButton;
    private JLabel previousRunSectionTotalLabel;
    private JLabel previousRunValueLabel;
    private JLabel previousRunSuppliesValueLabel;
    private JPanel previousRunContainer;
    private JPanel previousRunSuppliesPanel;
    private JPanel previousRunSuppliesSeparator;
    private JPanel previousRunPerformanceSeparator;
    private JLabel previousRunPrayerUsedLabel;
    private JLabel previousRunPrayerRegainedLabel;
    private JLabel previousRunHpLostLabel;
    private JLabel previousRunHpRegainedLabel;
    private JLabel previousRunSpecialAttacksUsedLabel;
    private JLabel previousRunVenomApplicationsLabel;
    private JPanel previousRunWavesContainer;
    private JPanel previousRunCombinedPanel;
    private long previousRunGeTotal;
    private long previousRunHaTotal;
    private long previousRunSuppliesTotal;
    private Map<String, ItemData> previousRunItemData = new HashMap<>();
    private Map<String, ItemData> previousRunSuppliesItemData = new HashMap<>();
    private Map<Integer, Map<String, ItemData>> previousRunItemsByWave = new TreeMap<>();
    private Map<Integer, Long> previousRunTotalsByWave = new TreeMap<>();
    private Map<Integer, Long> previousRunHaTotalsByWave = new TreeMap<>();
    private int previousRunPrayerUsed;
    private int previousRunPrayerRegained;
    private int previousRunHpLost;
    private int previousRunHpRegained;
    private int previousRunSpecialAttacksUsed;
    private int previousRunVenomApplications;
    private Map<Integer, Boolean> previousRunWaveCollapsed = new HashMap<>();
    // 0 = expanded, 1 = collapsed, 2 = combined
    private int previousRunSectionState = 1; // Start collapsed
    private boolean hasPreviousRunData;

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
    private final long[] claimedWaveHaTotals = new long[9];
    private long claimedSectionHaTotal;

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
    private final long[] unclaimedWaveHaTotals = new long[9];
    private long unclaimedSectionHaTotal;

    // Supplies Used Current Run
    private JLabel suppliesCurrentRunTotalLabel;
    private JPanel suppliesCurrentRunPanel;
    private JPanel suppliesCurrentRunContainer; // Container for all supplies content
    private boolean suppliesCurrentRunCollapsed = false; // Track collapse state
    private JLabel suppliesCurrentRunHeaderLabel; // Collapsed view total label
    private JButton startChargeTrackingButton;
    private JPanel startChargeTrackingButtonWrapper;

    // Supplies Used (All Time)
    private JLabel suppliesTotalValueLabel;
    private JPanel suppliesTotalItemsPanel;
    private JPanel suppliesTotalContainer; // Container for all supplies content
    private boolean suppliesTotalCollapsed = true; // Track collapse state
    private JLabel suppliesTotalHeaderLabel; // Collapsed view total label

    // Performance section
    private JPanel performanceContainer;
    private boolean performanceCollapsed = false;
    private JPanel performanceSectionPanel;
    private JPanel performanceSeparatorPanel;
    private JLabel performancePrayerUsedLabel;
    private JLabel performancePrayerRegainedLabel;
    private JLabel performanceHpLostLabel;
    private JLabel performanceHpRegainedLabel;
    private JLabel performanceSpecialAttacksUsedLabel;
    private JLabel performanceVenomApplicationsLabel;

    // Dryness section
    private JPanel drynessContainer;
    private JPanel drynessWaveCompletionsPanel;
    private JLabel[] drynessWaveCompletionLabels = new JLabel[9];
    private JLabel dryDeepRollsLabel;
    private JPanel dryDeepRollsRow;
    private JButton drynessCollapseButton;
    // 0 = collapsed, 1 = expanded (dryness stats + deep rolls), 2 = expanded with wave breakdown
    private int drynessSectionState = 0;
    private JPanel drynessSectionPanel;
    private JPanel drynessSeparatorPanel;
    private JLabel dryAnyUniqueLabel;
    private JLabel dryAnyUniqueOddsLabel;
    private JLabel dryClothLabel;
    private JLabel dryEyeLabel;
    private JLabel dryExpectedClothLabel;
    private JLabel dryExpectedEyeLabel;
    private JLabel dryExpectedTreadsLabel;
    private JLabel dryExpectedDomLabel;
    private JLabel drySyncWarningLabel;

    private final JPanel statsPanel = new JPanel();
    private final Runnable onClearData;
    private final Runnable onRecalculateTotals;
    private final Runnable onClearClaimedHistoricalData;
    private final Runnable onClearUnclaimedHistoricalData;
    private final Runnable onClearSuppliesHistoricalData;
    private final Runnable onExportHistoricalData;
    private final Runnable onImportHistoricalData;
    private final Runnable onStartChargeTracking;
    private java.util.function.BiConsumer<Integer, String> onRemoveClaimedHistoricalItem;
    private java.util.function.BiConsumer<Integer, String> onRemoveUnclaimedHistoricalItem;
    private java.util.function.Consumer<String> onRemoveClaimedHistoricalItemAllWaves;
    private java.util.function.Consumer<String> onRemoveUnclaimedHistoricalItemAllWaves;
    private java.util.function.Consumer<String> onRemoveHistoricalSupplyItem;

    // Add these fields to hold references to the historical data maps (set via
    // setter or constructor)
    private Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave;
    private Map<Integer, Map<String, ItemAggregate>> historicalUnclaimedItemsByWave;

    public MokhaLootPanel(MokhaLootTrackerConfig config) {
        this(config, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onClearData) {
        this(config, onClearData, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onClearData,
            Runnable onRecalculateTotals) {
        this(config, onClearData, onRecalculateTotals, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onClearData,
            Runnable onRecalculateTotals, java.util.function.BooleanSupplier isInRun) {
        this(config, onClearData, onRecalculateTotals, isInRun, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public MokhaLootPanel(MokhaLootTrackerConfig config, Runnable onClearData,
            Runnable onRecalculateTotals, java.util.function.BooleanSupplier isInRun,
            Runnable onClearClaimedHistoricalData, Runnable onClearUnclaimedHistoricalData,
            Runnable onClearSuppliesHistoricalData,
            java.util.function.BiConsumer<Integer, String> onRemoveClaimedHistoricalItem,
            java.util.function.BiConsumer<Integer, String> onRemoveUnclaimedHistoricalItem,
            java.util.function.Consumer<String> onRemoveClaimedHistoricalItemAllWaves,
            java.util.function.Consumer<String> onRemoveUnclaimedHistoricalItemAllWaves,
            java.util.function.Consumer<String> onRemoveHistoricalSupplyItem,
            Runnable onExportHistoricalData,
            Runnable onImportHistoricalData,
            Runnable onStartChargeTracking) {
        this.config = config;
        this.onClearData = onClearData;
        this.onRecalculateTotals = onRecalculateTotals;
        this.isInRun = isInRun;
        this.onClearClaimedHistoricalData = onClearClaimedHistoricalData;
        this.onClearUnclaimedHistoricalData = onClearUnclaimedHistoricalData;
        this.onClearSuppliesHistoricalData = onClearSuppliesHistoricalData;
        this.onExportHistoricalData = onExportHistoricalData;
        this.onRemoveClaimedHistoricalItem = onRemoveClaimedHistoricalItem;
        this.onRemoveUnclaimedHistoricalItem = onRemoveUnclaimedHistoricalItem;
        this.onRemoveClaimedHistoricalItemAllWaves = onRemoveClaimedHistoricalItemAllWaves;
        this.onRemoveUnclaimedHistoricalItemAllWaves = onRemoveUnclaimedHistoricalItemAllWaves;
        this.onRemoveHistoricalSupplyItem = onRemoveHistoricalSupplyItem;
        this.displayHaValueOnHover = config.displayHaValueOnHover();
        this.onImportHistoricalData = onImportHistoricalData;
        this.onStartChargeTracking = onStartChargeTracking;

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
        configureActionButton(recalculateButton, new Color(0, 100, 50));
        recalculateButton.addActionListener(e -> {
            // Check if currently in a run
            if (this.isInRun != null && this.isInRun.getAsBoolean()) {
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
                if (response == JOptionPane.YES_OPTION && this.onRecalculateTotals != null) {
                    this.onRecalculateTotals.run();
                }
            }
        });
        buttonPanel.add(recalculateButton);
        buttonPanel.add(createButtonDivider());

        JButton clearClaimedButton = new JButton("Clear Claimed Historical Data");
        configureActionButton(clearClaimedButton, new Color(0, 140, 0));
        clearClaimedButton.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to clear claimed historical data?\nThis action cannot be undone.",
                    "Confirm Clear Claimed Historical",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (response == JOptionPane.YES_OPTION && this.onClearClaimedHistoricalData != null) {
                this.onClearClaimedHistoricalData.run();
            }
        });
        buttonPanel.add(clearClaimedButton);
        buttonPanel.add(Box.createVerticalStrut(6));

        JButton clearUnclaimedButton = new JButton("Clear Unclaimed Historical Data");
        configureActionButton(clearUnclaimedButton, new Color(140, 35, 35));
        clearUnclaimedButton.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to clear unclaimed historical data?\nThis action cannot be undone.",
                    "Confirm Clear Unclaimed Historical",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (response == JOptionPane.YES_OPTION && this.onClearUnclaimedHistoricalData != null) {
                this.onClearUnclaimedHistoricalData.run();
            }
        });
        buttonPanel.add(clearUnclaimedButton);
        buttonPanel.add(Box.createVerticalStrut(6));

        JButton clearSuppliesButton = new JButton("Clear Supplies Historical Data");
        configureActionButton(clearSuppliesButton, new Color(170, 115, 0));
        clearSuppliesButton.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to clear supplies historical data?\nThis action cannot be undone.",
                    "Confirm Clear Supplies Historical",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (response == JOptionPane.YES_OPTION && this.onClearSuppliesHistoricalData != null) {
                this.onClearSuppliesHistoricalData.run();
            }
        });
        buttonPanel.add(clearSuppliesButton);
        buttonPanel.add(createButtonDivider());

        JButton importHistoricalButton = new JButton("Import Stats");
        configureActionButton(importHistoricalButton, new Color(0, 95, 140));
        importHistoricalButton.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(this,
                    "Import historical stats from clipboard and overwrite the current historical data?\nThis cannot be undone.",
                    "Confirm Import Historical Stats",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (response == JOptionPane.YES_OPTION && this.onImportHistoricalData != null) {
                this.onImportHistoricalData.run();
            }
        });

        JButton exportHistoricalButton = new JButton("Export Stats");
        configureActionButton(exportHistoricalButton, new Color(0, 95, 140));
        exportHistoricalButton.addActionListener(e -> {
            if (this.onExportHistoricalData != null) {
                this.onExportHistoricalData.run();
            }
        });

        buttonPanel.add(createHorizontalActionRow(importHistoricalButton, exportHistoricalButton));
        buttonPanel.add(createButtonDivider());

        JButton clearButton = new JButton("Clear All Data");
        configureActionButton(clearButton, new Color(120, 0, 0));
        clearButton.addActionListener(e -> {
            int response = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to clear ALL current and historical data?\nThis action cannot be undone.",
                    "Confirm Clear All Data",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (response == JOptionPane.YES_OPTION && this.onClearData != null) {
                this.onClearData.run();
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

        // Supplies Used Current Run Section
        statsPanel.add(createSuppliesCurrentRunSection());
        statsPanel.add(createSeparator(5));

        // Performance Section (config-gated)
        performanceSectionPanel = createPerformanceSection();
        performanceSeparatorPanel = createSeparator(5);
        performanceSectionPanel.setVisible(config.showPerformancePanel());
        performanceSeparatorPanel.setVisible(config.showPerformancePanel());
        statsPanel.add(performanceSectionPanel);
        statsPanel.add(performanceSeparatorPanel);

        // Previous Run Section
        statsPanel.add(createPreviousRunSection());
        statsPanel.add(createSeparator(5));

        // Claimed Loot by Wave Section
        statsPanel.add(createClaimedLootSection());
        statsPanel.add(createSeparator(5));
        // Unclaimed Loot by Wave Section
        statsPanel.add(createUnclaimedLootSection());
        statsPanel.add(createSeparator(5));

        // Supplies Used (All Time) Section
        statsPanel.add(createSuppliesTotalSection());
        statsPanel.add(createSeparator(5));

        // Dryness Section
        drynessSectionPanel = createDrynessSection();
        drynessSeparatorPanel = createSeparator(5);
        drynessSectionPanel.setVisible(config.showDrynessPanel());
        drynessSeparatorPanel.setVisible(config.showDrynessPanel());
        statsPanel.add(drynessSectionPanel);
        statsPanel.add(drynessSeparatorPanel);
    }

    private void configureActionButton(JButton button, Color backgroundColor) {
        button.setFocusPainted(false);
        button.setBackground(backgroundColor);
        button.setForeground(Color.WHITE);
        button.setFont(FontManager.getRunescapeSmallFont());
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.setMargin(new Insets(2, 8, 2, 8));
        button.setMinimumSize(new Dimension(120, ACTION_BUTTON_HEIGHT));
        button.setPreferredSize(new Dimension(Short.MAX_VALUE, ACTION_BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, ACTION_BUTTON_HEIGHT));
    }

    private JPanel createHorizontalActionRow(JButton leftButton, JButton rightButton) {
        JPanel row = new JPanel(new GridLayout(1, 2, 6, 0));
        row.setBackground(ColorScheme.DARK_GRAY_COLOR);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ACTION_BUTTON_HEIGHT));

        leftButton.setMinimumSize(new Dimension(0, ACTION_BUTTON_HEIGHT));
        rightButton.setMinimumSize(new Dimension(0, ACTION_BUTTON_HEIGHT));

        row.add(leftButton);
        row.add(rightButton);
        return row;
    }

    private JPanel createButtonDivider() {
        JPanel divider = new JPanel(new BorderLayout());
        divider.setBackground(ColorScheme.DARK_GRAY_COLOR);
        divider.setBorder(new EmptyBorder(5, 0, 5, 0));
        divider.setAlignmentX(LEFT_ALIGNMENT);
        divider.setMinimumSize(new Dimension(0, 11));
        divider.setPreferredSize(new Dimension(1, 11));
        divider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 11));

        JSeparator separator = new JSeparator();
        separator.setForeground(Color.DARK_GRAY);
        separator.setPreferredSize(new Dimension(1, 1));
        divider.add(separator, BorderLayout.CENTER);

        return divider;
    }

    private JPanel createProfitLossSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Summary:");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(UNIQUE_GOLD_COLOR); // Gold
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

        panel.add(createInternalSeparator());

        uniqueClaimsCountLabel = new JLabel("0");
        uniqueClaimsCountLabel.setFont(FontManager.getRunescapeFont());
        uniqueClaimsCountLabel.setForeground(UNIQUE_GOLD_COLOR);
        panel.add(createStatRow("Uniques Claimed:", uniqueClaimsCountLabel));

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

        currentRunViewToggleButton = new JButton(getArrowOrFallback(currentRunShowByWave ? "▾" : "▸",
                currentRunShowByWave ? "↓" : "→"));
        currentRunViewToggleButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        currentRunViewToggleButton.setForeground(Color.WHITE);
        currentRunViewToggleButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        currentRunViewToggleButton.setBorderPainted(false);
        currentRunViewToggleButton.setFocusPainted(false);
        currentRunViewToggleButton.setPreferredSize(new Dimension(18, 18));
        currentRunViewToggleButton.setMaximumSize(new Dimension(18, 18));

        currentRunWaveLabel = new JLabel("");
        currentRunWaveLabel.setFont(FontManager.getRunescapeFont());
        currentRunWaveLabel.setForeground(Color.LIGHT_GRAY);

        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.X_AXIS));
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        rightPanel.add(currentRunWaveLabel);
        rightPanel.add(Box.createHorizontalStrut(6));
        rightPanel.add(currentRunViewToggleButton);
        titleRow.add(rightPanel, BorderLayout.EAST);

        currentRunViewToggleButton.addActionListener(e -> {
            currentRunShowByWave = !currentRunShowByWave;
            updateCurrentRunViewToggleText();
            renderCurrentRunWaveBreakdown();
            panel.revalidate();
            panel.repaint();
        });
        panel.add(titleRow);

        cumulativeUniqueChanceLabel = new JLabel("N/A");
        cumulativeUniqueChanceLabel.setFont(FontManager.getRunescapeFont());
        cumulativeUniqueChanceLabel.setForeground(Color.LIGHT_GRAY);
        panel.add(createStatRow("Unique Chance:", cumulativeUniqueChanceLabel));

        potentialValueLabel = new JLabel("0 gp");
        potentialValueLabel.setFont(FontManager.getRunescapeFont());
        potentialValueLabel.setForeground(Color.WHITE);
        panel.add(createStatRow("Potential:", potentialValueLabel));

        // Current run items panel
        currentRunItemsPanel = new JPanel();
        currentRunItemsPanel.setLayout(new BoxLayout(currentRunItemsPanel, BoxLayout.Y_AXIS));
        currentRunItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.add(currentRunItemsPanel);

        updateCurrentRunViewToggleText();

        return panel;
    }

    private JPanel createPreviousRunSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        previousRunCollapseButton = new JButton(getArrowOrFallback("▸", "→"));
        previousRunCollapseButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        previousRunCollapseButton.setForeground(Color.WHITE);
        previousRunCollapseButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        previousRunCollapseButton.setBorderPainted(false);
        previousRunCollapseButton.setFocusPainted(false);
        previousRunCollapseButton.setPreferredSize(new Dimension(18, 18));
        previousRunCollapseButton.setMaximumSize(new Dimension(18, 18));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        titleRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel title = new JLabel("Previous Run");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(120, 180, 255));
        titleRow.add(title, BorderLayout.WEST);

        previousRunSectionTotalLabel = new JLabel("0 gp");
        previousRunSectionTotalLabel.setFont(FontManager.getRunescapeFont());
        previousRunSectionTotalLabel.setForeground(Color.WHITE);
        previousRunSectionTotalLabel.setVisible(false);

        previousRunStatusLabel = new JLabel("");
        previousRunStatusLabel.setFont(FontManager.getRunescapeFont());
        previousRunStatusLabel.setForeground(Color.LIGHT_GRAY);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel statusAndTotalPanel = new JPanel();
        statusAndTotalPanel.setLayout(new BoxLayout(statusAndTotalPanel, BoxLayout.X_AXIS));
        statusAndTotalPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statusAndTotalPanel.add(previousRunStatusLabel);
        statusAndTotalPanel.add(Box.createHorizontalStrut(6));
        statusAndTotalPanel.add(previousRunSectionTotalLabel);

        rightPanel.add(statusAndTotalPanel, BorderLayout.CENTER);
        rightPanel.add(previousRunCollapseButton, BorderLayout.EAST);
        titleRow.add(rightPanel, BorderLayout.EAST);
        panel.add(titleRow);

        previousRunContainer = new JPanel();
        previousRunContainer.setLayout(new BoxLayout(previousRunContainer, BoxLayout.Y_AXIS));
        previousRunContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        previousRunValueLabel = new JLabel("0 gp");
        previousRunValueLabel.setFont(FontManager.getRunescapeFont());
        previousRunValueLabel.setForeground(Color.WHITE);
        previousRunContainer.add(createStatRow("Loot:", previousRunValueLabel));

        previousRunWavesContainer = new JPanel();
        previousRunWavesContainer.setLayout(new BoxLayout(previousRunWavesContainer, BoxLayout.Y_AXIS));
        previousRunWavesContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        previousRunContainer.add(previousRunWavesContainer);

        previousRunSuppliesSeparator = createSeparator(2);
        previousRunContainer.add(previousRunSuppliesSeparator);

        previousRunSuppliesValueLabel = new JLabel("0 gp");
        previousRunSuppliesValueLabel.setFont(FontManager.getRunescapeFont());
        previousRunSuppliesValueLabel.setForeground(Color.WHITE);
        previousRunContainer.add(createStatRow("Supplies Used:", previousRunSuppliesValueLabel));

        previousRunSuppliesPanel = new JPanel();
        previousRunSuppliesPanel.setLayout(new BoxLayout(previousRunSuppliesPanel, BoxLayout.Y_AXIS));
        previousRunSuppliesPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        previousRunContainer.add(previousRunSuppliesPanel);

        previousRunPerformanceSeparator = createSeparator(2);
        previousRunContainer.add(previousRunPerformanceSeparator);

        JLabel previousRunPerformanceHeaderLabel = new JLabel("");
        previousRunPerformanceHeaderLabel.setFont(FontManager.getRunescapeFont());
        previousRunPerformanceHeaderLabel.setForeground(Color.WHITE);
        previousRunContainer.add(createStatRow("Performance:", previousRunPerformanceHeaderLabel));

        previousRunHpLostLabel = createPerformanceMetricValueLabel(HP_LOST_COLOR);
        previousRunContainer.add(createStatRow("HP lost:", previousRunHpLostLabel));

        previousRunPrayerUsedLabel = createPerformanceMetricValueLabel(PRAYER_USED_COLOR);
        previousRunContainer.add(createStatRow("Prayer used:", previousRunPrayerUsedLabel));

        previousRunSpecialAttacksUsedLabel = createPerformanceMetricValueLabel(SPECIAL_ATTACKS_COLOR);
        previousRunContainer.add(createStatRow("Special attacks used:", previousRunSpecialAttacksUsedLabel));

        previousRunVenomApplicationsLabel = createPerformanceMetricValueLabel(VENOM_COLOR);
        previousRunContainer.add(createStatRow("Times Venomed:", previousRunVenomApplicationsLabel));

        previousRunHpRegainedLabel = createPerformanceMetricValueLabel(HP_REGAINED_COLOR);
        previousRunContainer.add(createStatRow("HP regained:", previousRunHpRegainedLabel));

        previousRunPrayerRegainedLabel = createPerformanceMetricValueLabel(PRAYER_REGAINED_COLOR);
        previousRunContainer.add(createStatRow("Prayer regained:", previousRunPrayerRegainedLabel));

        previousRunCombinedPanel = new JPanel();
        previousRunCombinedPanel.setLayout(new BoxLayout(previousRunCombinedPanel, BoxLayout.Y_AXIS));
        previousRunCombinedPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        previousRunCombinedPanel.setVisible(false);

        panel.add(previousRunContainer);
        panel.add(previousRunCombinedPanel);

        previousRunCollapseButton.addActionListener(e -> {
            previousRunSectionState = (previousRunSectionState + 1) % 3;
            renderPreviousRunSection();
            panel.revalidate();
            panel.repaint();
        });

        renderPreviousRunSection();

        return panel;
    }

    private JPanel createClaimedLootSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header with collapse button and title
        JButton collapseButton = new JButton(getArrowOrFallback("▸", "→")); // Start collapsed
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
        JButton collapseButton = new JButton(getArrowOrFallback("▸", "→")); // Start collapsed
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
        JPanel header = (JPanel) ((JPanel) claimedWavesContainer.getParent()).getComponent(0);
        JPanel rightPanel = (JPanel) header.getComponent(1);
        JButton collapseButton = (JButton) rightPanel.getComponent(rightPanel.getComponentCount() - 1);
        collapseButton.setText(getArrowOrFallback(text, getArrowFallback(text)));
    }

    /**
     * Combines all claimed wave items for display in the combined panel.
     * This does not change how data is stored, only how it is shown.
     */
    private void populateClaimedCombinedPanel() {
        LootPanelCombinedSectionRenderer.renderCombinedWaveItems(
                claimedCombinedPanel,
                historicalClaimedItemsByWave,
                config.displaySortMode(),
                displayHaValueOnHover,
                true,
                new Color(0, 200, 0),
                config.enableHistoricalEdit() && onRemoveClaimedHistoricalItemAllWaves != null,
                (itemRow, itemName) -> addHistoricalRemovalInteraction(
                        itemRow,
                        itemName,
                        "claimed historical loot (all waves)",
                        () -> onRemoveClaimedHistoricalItemAllWaves.accept(itemName)));
    }

    /**
     * Combines all unclaimed wave items for display in the combined panel.
     * This does not change how data is stored, only how it is shown.
     */
    private void populateUnclaimedCombinedPanel() {
        LootPanelCombinedSectionRenderer.renderCombinedWaveItems(
                unclaimedCombinedPanel,
                historicalUnclaimedItemsByWave,
                config.displaySortMode(),
                displayHaValueOnHover,
                false,
                new Color(200, 0, 0),
                config.enableHistoricalEdit() && onRemoveUnclaimedHistoricalItemAllWaves != null,
                (itemRow, itemName) -> addHistoricalRemovalInteraction(
                        itemRow,
                        itemName,
                        "unclaimed historical loot (all waves)",
                        () -> onRemoveUnclaimedHistoricalItemAllWaves.accept(itemName)));
    }

    private void setUnclaimedCollapseButtonText(String text) {
        JPanel header = (JPanel) ((JPanel) unclaimedWavesContainer.getParent()).getComponent(0);
        JPanel rightPanel = (JPanel) header.getComponent(1);
        JButton collapseButton = (JButton) rightPanel.getComponent(rightPanel.getComponentCount() - 1);
        collapseButton.setText(getArrowOrFallback(text, getArrowFallback(text)));
    }

    private JPanel createSuppliesCurrentRunSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Header with collapse button and title
        JButton collapseButton = new JButton(getArrowOrFallback("▾", "↓")); // Down triangle for expanded
        collapseButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        collapseButton.setForeground(Color.WHITE);
        collapseButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        collapseButton.setBorderPainted(false);
        collapseButton.setFocusPainted(false);
        collapseButton.setPreferredSize(new Dimension(18, 18));
        collapseButton.setMaximumSize(new Dimension(18, 18));

        JLabel title = new JLabel("Supplies (Current Run)");
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

        startChargeTrackingButton = new JButton("Start Charge Tracking");
        startChargeTrackingButton.setFont(FontManager.getRunescapeSmallFont());
        startChargeTrackingButton.setForeground(Color.WHITE);
        startChargeTrackingButton.setBackground(new Color(60, 60, 60));
        startChargeTrackingButton.setFocusPainted(false);
        startChargeTrackingButton.setVisible(config.blowpipeCheckReminder());
        startChargeTrackingButton.addActionListener(e -> {
            if (onStartChargeTracking != null) {
                onStartChargeTracking.run();
            }
        });

        // Wrap in a BorderLayout panel so BoxLayout gives it the full row width.
        startChargeTrackingButtonWrapper = new JPanel(new BorderLayout());
        startChargeTrackingButtonWrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        startChargeTrackingButtonWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        startChargeTrackingButtonWrapper.setBorder(new EmptyBorder(4, 0, 0, 0));
        startChargeTrackingButtonWrapper.add(startChargeTrackingButton, BorderLayout.CENTER);
        startChargeTrackingButtonWrapper.setVisible(config.blowpipeCheckReminder());
        suppliesCurrentRunContainer.add(startChargeTrackingButtonWrapper);

        panel.add(suppliesCurrentRunContainer);

        // Collapse/expand logic
        collapseButton.addActionListener(e -> {
            suppliesCurrentRunCollapsed = !suppliesCurrentRunCollapsed;
            suppliesCurrentRunContainer.setVisible(!suppliesCurrentRunCollapsed);
            suppliesCurrentRunHeaderLabel.setVisible(suppliesCurrentRunCollapsed); // Show when collapsed
            collapseButton.setText(getArrowOrFallback(suppliesCurrentRunCollapsed ? "▸" : "▾",
                    suppliesCurrentRunCollapsed ? "→" : "↓"));
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
        JButton collapseButton = new JButton(getArrowOrFallback("▾", "↓")); // Down triangle for expanded
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
        collapseButton
                .setText(getArrowOrFallback(suppliesTotalCollapsed ? "▸" : "▾", suppliesTotalCollapsed ? "→" : "↓"));

        // Collapse/expand logic
        collapseButton.addActionListener(e -> {
            suppliesTotalCollapsed = !suppliesTotalCollapsed;
            suppliesTotalContainer.setVisible(!suppliesTotalCollapsed);
            suppliesTotalHeaderLabel.setVisible(suppliesTotalCollapsed); // Show when collapsed
            collapseButton.setText(
                    getArrowOrFallback(suppliesTotalCollapsed ? "▸" : "▾", suppliesTotalCollapsed ? "→" : "↓"));
            panel.revalidate();
            panel.repaint();
        });

        return panel;
    }

    private JPanel createPerformanceSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JButton collapseButton = new JButton(getArrowOrFallback("▾", "↓"));
        collapseButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        collapseButton.setForeground(Color.WHITE);
        collapseButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        collapseButton.setBorderPainted(false);
        collapseButton.setFocusPainted(false);
        collapseButton.setPreferredSize(new Dimension(18, 18));
        collapseButton.setMaximumSize(new Dimension(18, 18));

        JLabel title = new JLabel("Performance");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(Color.WHITE);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        titleRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(collapseButton, BorderLayout.EAST);
        panel.add(titleRow);

        performanceContainer = new JPanel();
        performanceContainer.setLayout(new BoxLayout(performanceContainer, BoxLayout.Y_AXIS));
        performanceContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        performanceHpLostLabel = createPerformanceMetricValueLabel(HP_LOST_COLOR);
        performanceContainer.add(createStatRow("HP lost:", performanceHpLostLabel));

        performancePrayerUsedLabel = createPerformanceMetricValueLabel(PRAYER_USED_COLOR);
        performanceContainer.add(createStatRow("Prayer used:", performancePrayerUsedLabel));

        performanceSpecialAttacksUsedLabel = createPerformanceMetricValueLabel(SPECIAL_ATTACKS_COLOR);
        performanceContainer.add(createStatRow("Special attacks used:", performanceSpecialAttacksUsedLabel));

        performanceVenomApplicationsLabel = createPerformanceMetricValueLabel(VENOM_COLOR);
        performanceContainer.add(createStatRow("Times Venomed:", performanceVenomApplicationsLabel));

        performanceHpRegainedLabel = createPerformanceMetricValueLabel(HP_REGAINED_COLOR);
        performanceContainer.add(createStatRow("HP regained:", performanceHpRegainedLabel));

        performancePrayerRegainedLabel = createPerformanceMetricValueLabel(PRAYER_REGAINED_COLOR);
        performanceContainer.add(createStatRow("Prayer regained:", performancePrayerRegainedLabel));

        panel.add(performanceContainer);

        collapseButton.addActionListener(e -> {
            performanceCollapsed = !performanceCollapsed;
            performanceContainer.setVisible(!performanceCollapsed);
            collapseButton.setText(getArrowOrFallback(performanceCollapsed ? "▸" : "▾",
                    performanceCollapsed ? "→" : "↓"));
            panel.revalidate();
            panel.repaint();
        });

        return panel;
    }

    private JPanel createDrynessSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        drynessCollapseButton = new JButton(getArrowOrFallback("▾", "↓"));
        drynessCollapseButton.setFont(FontManager.getRunescapeSmallFont().deriveFont(11f));
        drynessCollapseButton.setForeground(Color.WHITE);
        drynessCollapseButton.setBackground(ColorScheme.DARK_GRAY_COLOR);
        drynessCollapseButton.setBorderPainted(false);
        drynessCollapseButton.setFocusPainted(false);
        drynessCollapseButton.setPreferredSize(new Dimension(18, 18));
        drynessCollapseButton.setMaximumSize(new Dimension(18, 18));

        JLabel title = new JLabel("Dryness");
        title.setFont(FontManager.getRunescapeBoldFont());
        title.setForeground(new Color(230, 210, 120));

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(drynessCollapseButton, BorderLayout.EAST);
        panel.add(titleRow);

        drynessContainer = new JPanel();
        drynessContainer.setLayout(new BoxLayout(drynessContainer, BoxLayout.Y_AXIS));
        drynessContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);

        dryAnyUniqueLabel = new JLabel("N/A");
        dryAnyUniqueLabel.setFont(FontManager.getRunescapeFont());
        dryAnyUniqueLabel.setForeground(Color.WHITE);
        drynessContainer.add(createStatRow("Wave Rolls:", dryAnyUniqueLabel));

        dryDeepRollsLabel = new JLabel("N/A");
        dryDeepRollsLabel.setFont(FontManager.getRunescapeFont());
        dryDeepRollsLabel.setForeground(Color.LIGHT_GRAY);
        dryDeepRollsRow = createStatRow("Deep Rolls (8+):", dryDeepRollsLabel);
        drynessContainer.add(dryDeepRollsRow);

        drynessWaveCompletionsPanel = new JPanel();
        drynessWaveCompletionsPanel.setLayout(new BoxLayout(drynessWaveCompletionsPanel, BoxLayout.Y_AXIS));
        drynessWaveCompletionsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        drynessWaveCompletionsPanel.setVisible(false);
        for (int i = 0; i < 8; i++) {
            drynessWaveCompletionLabels[i] = new JLabel("0");
            drynessWaveCompletionLabels[i].setFont(FontManager.getRunescapeFont());
            drynessWaveCompletionLabels[i].setForeground(Color.WHITE);
            drynessWaveCompletionsPanel.add(createStatRow("Wave " + (i + 1) + ":", drynessWaveCompletionLabels[i]));
        }
        drynessWaveCompletionLabels[8] = new JLabel("0");
        drynessWaveCompletionLabels[8].setFont(FontManager.getRunescapeFont());
        drynessWaveCompletionLabels[8].setForeground(Color.WHITE);
        drynessWaveCompletionsPanel.add(createStatRow("Wave 9+:", drynessWaveCompletionLabels[8]));
        drynessContainer.add(drynessWaveCompletionsPanel);

        dryAnyUniqueOddsLabel = new JLabel("N/A");
        dryAnyUniqueOddsLabel.setFont(FontManager.getRunescapeFont());
        dryAnyUniqueOddsLabel.setForeground(Color.LIGHT_GRAY);
        drynessContainer.add(createStatRow("Expected Drops:", dryAnyUniqueOddsLabel));

        drynessContainer.add(createInternalSeparator());

        dryClothLabel = new JLabel("N/A");
        dryClothLabel.setFont(FontManager.getRunescapeFont());
        dryClothLabel.setForeground(Color.WHITE);
        drynessContainer.add(createStatRow("Drops Received:", dryClothLabel));

        dryEyeLabel = new JLabel("N/A");
        dryEyeLabel.setFont(FontManager.getRunescapeBoldFont());
        dryEyeLabel.setForeground(new Color(230, 210, 120));
        drynessContainer.add(createStatRow("Dryness Value:", dryEyeLabel));

        drynessContainer.add(createInternalSeparator());

        dryExpectedDomLabel = new JLabel("0.00 / 0");
        dryExpectedDomLabel.setFont(FontManager.getRunescapeFont());
        dryExpectedDomLabel.setForeground(Color.WHITE);
        drynessContainer.add(createStatRow("Dom Expected/Received:", dryExpectedDomLabel));

        dryExpectedTreadsLabel = new JLabel("0.00 / 0");
        dryExpectedTreadsLabel.setFont(FontManager.getRunescapeFont());
        dryExpectedTreadsLabel.setForeground(Color.WHITE);
        drynessContainer.add(createStatRow("Treads Expected/Received:", dryExpectedTreadsLabel));

        dryExpectedEyeLabel = new JLabel("0.00 / 0");
        dryExpectedEyeLabel.setFont(FontManager.getRunescapeFont());
        dryExpectedEyeLabel.setForeground(Color.WHITE);
        drynessContainer.add(createStatRow("Eye Expected/Received:", dryExpectedEyeLabel));

        dryExpectedClothLabel = new JLabel("0.00 / 0");
        dryExpectedClothLabel.setFont(FontManager.getRunescapeFont());
        dryExpectedClothLabel.setForeground(Color.WHITE);
        drynessContainer.add(createStatRow("Cloth Expected/Received:", dryExpectedClothLabel));

        drySyncWarningLabel = new JLabel(
                "Sync highscores and collection log to initialize dryness.");
        drySyncWarningLabel.setFont(FontManager.getRunescapeSmallFont());
        drySyncWarningLabel.setForeground(new Color(220, 70, 70));
        drySyncWarningLabel.setVisible(false);

        JPanel warningRow = new JPanel(new BorderLayout());
        warningRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
        warningRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        warningRow.add(drySyncWarningLabel, BorderLayout.WEST);
        drynessContainer.add(warningRow);

        panel.add(drynessContainer);

        updateDrynessSectionView();

        drynessCollapseButton.addActionListener(e -> {
            drynessSectionState = (drynessSectionState + 1) % 3;
            updateDrynessSectionView();
            panel.revalidate();
            panel.repaint();
        });

        return panel;
    }

    private void updateDrynessSectionView() {
        switch (drynessSectionState) {
            case 0: // collapsed
                drynessContainer.setVisible(false);
                drynessCollapseButton.setText(getArrowOrFallback("▸", "→"));
                break;
            case 1: // expanded — dryness stats with deep rolls
                drynessContainer.setVisible(true);
                dryDeepRollsRow.setVisible(true);
                drynessWaveCompletionsPanel.setVisible(false);
                drynessCollapseButton.setText(getArrowOrFallback("▾", "↓"));
                break;
            default: // expanded — dryness stats with wave breakdown
                drynessContainer.setVisible(true);
                dryDeepRollsRow.setVisible(false);
                drynessWaveCompletionsPanel.setVisible(true);
                drynessCollapseButton.setText(getArrowOrFallback("◂", "←"));
                break;
        }
    }

    void updatePerformance(int prayerUsed, int prayerRegained, int hpLost, int hpRegained, int specialAttacksUsed, int venomApplications) {
        SwingUtilities.invokeLater(() -> {
            if (performancePrayerUsedLabel != null)
                performancePrayerUsedLabel.setText(String.valueOf(prayerUsed));
            if (performancePrayerRegainedLabel != null)
                performancePrayerRegainedLabel.setText(String.valueOf(prayerRegained));
            if (performanceHpLostLabel != null)
                performanceHpLostLabel.setText(String.valueOf(hpLost));
            if (performanceHpRegainedLabel != null)
                performanceHpRegainedLabel.setText(String.valueOf(hpRegained));
            if (performanceSpecialAttacksUsedLabel != null)
                performanceSpecialAttacksUsedLabel.setText(String.valueOf(specialAttacksUsed));
            if (performanceVenomApplicationsLabel != null)
                performanceVenomApplicationsLabel.setText(String.valueOf(venomApplications));
        });
    }

    public void setChargeTrackingButtonVisible(boolean visible) {
        if (startChargeTrackingButtonWrapper != null) {
            startChargeTrackingButtonWrapper.setVisible(visible);
        }
    }

    void setPerformanceSectionVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            if (performanceSectionPanel != null)
                performanceSectionPanel.setVisible(visible);
            if (performanceSeparatorPanel != null)
                performanceSeparatorPanel.setVisible(visible);
            statsPanel.revalidate();
            statsPanel.repaint();
        });
    }

    void setDrynessSectionVisible(boolean visible) {
        SwingUtilities.invokeLater(() -> {
            if (drynessSectionPanel != null)
                drynessSectionPanel.setVisible(visible);
            if (drynessSeparatorPanel != null)
                drynessSeparatorPanel.setVisible(visible);
            statsPanel.revalidate();
            statsPanel.repaint();
        });
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

    private JLabel createPerformanceMetricValueLabel(Color color) {
        JLabel label = new JLabel("0");
        label.setFont(FontManager.getRunescapeFont());
        label.setForeground(color);
        return label;
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

        JButton collapseButton = new JButton(getArrowOrFallback("▾", "↓"));
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
            collapseButton.setText(
                    getArrowOrFallback(collapsedStates[index] ? "▸" : "▾", collapsedStates[index] ? "→" : "↓"));
            panel.revalidate();
            panel.repaint();
        });
        return panel;
    }

    /**
     * Returns the fallback arrow for a given arrow symbol.
     * ▸ (U+25B8) → (U+2192)
     * ▾ (U+25BE) ↓ (U+2193)
     * ◂ (U+25C2) ← (U+2190)
     * ▴ (U+25B4) ↑ (U+2191)
     */
    private String getArrowFallback(String arrow) {
        switch (arrow) {
            case "▸":
                return "→";
            case "▾":
                return "↓";
            case "◂":
                return "←";
            case "▴":
                return "↑";
            default:
                return arrow;
        }
    }

    /**
     * Returns the preferred arrow if supported, otherwise the fallback.
     * This is a stub: Java/Swing does not provide a direct way to check font glyph
     * support at runtime,
     * so this always returns the preferred arrow. If you want to implement a real
     * check, you could use Font.canDisplay().
     */
    private String getArrowOrFallback(String preferred, String fallback) {
        java.awt.Font font = FontManager.getRunescapeSmallFont();
        if (font != null && preferred != null && !preferred.isEmpty() && font.canDisplay(preferred.charAt(0))) {
            return preferred;
        } else {
            return fallback;
        }
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
            long deathCount, long uniqueClaimsCount) {
        summaryTotalClaimedGe = totalClaimed;
        summarySupplyCostGe = supplyCost;
        summaryTotalUnclaimedGe = totalUnclaimed;

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
        uniqueClaimsCountLabel.setText(String.valueOf((int) uniqueClaimsCount));

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

        refreshSummaryHaTooltips();

    }

    public void updateCurrentRun(long potentialValue,
            Map<String, ItemData> itemData,
            Map<Integer, Map<String, ItemData>> itemsByWave,
            Map<Integer, Long> totalsByWave,
            Map<Integer, Long> haTotalsByWave) {
        currentRunGeTotal = potentialValue;
        currentRunHaTotal = 0;
        currentRunItemData = itemData != null ? new HashMap<>(itemData) : new HashMap<>();
        currentRunItemsByWave = itemsByWave != null ? new TreeMap<>(itemsByWave) : new TreeMap<>();
        currentRunTotalsByWave = totalsByWave != null ? new TreeMap<>(totalsByWave) : new TreeMap<>();
        currentRunHaTotalsByWave = haTotalsByWave != null ? new TreeMap<>(haTotalsByWave) : new TreeMap<>();
        if (itemData != null) {
            for (ItemData item : itemData.values()) {
                currentRunHaTotal += item.totalHaValue;
            }
        }

        potentialValueLabel.setText(formatTotalWithOptionalHa(currentRunGeTotal, currentRunHaTotal));
        potentialValueLabel.setToolTipText(formatGeHaTotalText(currentRunGeTotal, currentRunHaTotal));
        renderCurrentRunWaveBreakdown();
    }

    public void updatePreviousRun(boolean hasPreviousRun, boolean claimed, long totalValue, long totalHaValue,
            Map<String, ItemData> itemData,
            long suppliesTotalValue,
            Map<String, ItemData> suppliesItemData,
            int prayerUsed,
            int prayerRegained,
            int hpLost,
            int hpRegained,
            int specialAttacksUsed,
            int venomApplications,
            Map<Integer, Map<String, ItemData>> itemsByWave,
            Map<Integer, Long> totalsByWave,
            Map<Integer, Long> haTotalsByWave) {
        hasPreviousRunData = hasPreviousRun;
        previousRunGeTotal = totalValue;
        previousRunHaTotal = totalHaValue;
        previousRunItemData = itemData != null ? new HashMap<>(itemData) : new HashMap<>();
        previousRunSuppliesTotal = suppliesTotalValue;
        previousRunSuppliesItemData = suppliesItemData != null ? new HashMap<>(suppliesItemData) : new HashMap<>();
        previousRunPrayerUsed = prayerUsed;
        previousRunPrayerRegained = prayerRegained;
        previousRunHpLost = hpLost;
        previousRunHpRegained = hpRegained;
        previousRunSpecialAttacksUsed = specialAttacksUsed;
        previousRunVenomApplications = venomApplications;
        previousRunItemsByWave = itemsByWave != null ? new TreeMap<>(itemsByWave) : new TreeMap<>();
        previousRunTotalsByWave = totalsByWave != null ? new TreeMap<>(totalsByWave) : new TreeMap<>();
        previousRunHaTotalsByWave = haTotalsByWave != null ? new TreeMap<>(haTotalsByWave) : new TreeMap<>();

        previousRunStatusLabel.setText(hasPreviousRun ? (claimed ? "Claimed" : "Unclaimed") : "");
        previousRunStatusLabel.setForeground(claimed ? new Color(0, 200, 0) : new Color(200, 0, 0));
        renderPreviousRunSection();
    }

    public void updateCurrentRunUniqueChance(int currentDepth, double cumulativeUniqueChancePercent,
            double clothCumulativePercent,
            double eyeCumulativePercent,
            double treadsCumulativePercent,
            double domCumulativePercent) {
        if (isInRun == null || !isInRun.getAsBoolean() || currentDepth < 2) {
            currentRunWaveLabel.setText(isInRun != null && isInRun.getAsBoolean() && currentDepth > 0
                    ? String.format("Wave %d", currentDepth)
                    : "");
            cumulativeUniqueChanceLabel.setText("N/A");
            cumulativeUniqueChanceLabel.setForeground(Color.LIGHT_GRAY);
            cumulativeUniqueChanceLabel.setToolTipText(null);
            return;
        }

        int displayDepth = Math.max(2, currentDepth);
        currentRunWaveLabel.setText(String.format("Wave %d", displayDepth));
        double clothDisplay = toFlooredOneInNDecimal(clothCumulativePercent);
        double eyeDisplay = toFlooredOneInNDecimal(eyeCumulativePercent);
        double treadsDisplay = toFlooredOneInNDecimal(treadsCumulativePercent);
        double domDisplay = toFlooredOneInNDecimal(domCumulativePercent);
        double totalExcludingDomDisplay = clothDisplay + eyeDisplay + treadsDisplay;

        cumulativeUniqueChanceLabel.setText(String.format("%.2f%%", cumulativeUniqueChancePercent));
        cumulativeUniqueChanceLabel.setForeground(UNIQUE_GOLD_COLOR);
        cumulativeUniqueChanceLabel.setToolTipText(String.format(
                "<html>Per-unique cumulative by delve %d:<br>"
                        + "Mokhaiotl cloth: %.2f<br>"
                        + "Eye of ayak: %s<br>"
                        + "Avernic treads: %s<br>"
                        + "Dom: %s<br>"
                        + "Total (excluding Dom): %.2f</html>",
                displayDepth,
                clothDisplay,
                displayDepth >= 3 ? String.format("%.2f", eyeDisplay) : "N/A (unlocks at 3)",
                displayDepth >= 4 ? String.format("%.2f", treadsDisplay) : "N/A (unlocks at 4)",
                displayDepth >= 6 ? String.format("%.2f", domDisplay) : "N/A (unlocks at 6)",
                totalExcludingDomDisplay));
    }

    void updateHistoricalDryness(long waveRollsTracked, long deepRolls, double expectedDrops, long dropsReceived,
            double expectedDom, double expectedTreads, double expectedEye, double expectedCloth,
            long receivedDom, long receivedTreads, long receivedEye, long receivedCloth,
            java.util.Map<Integer, Long> completedRunsByWave) {
        if (dryAnyUniqueLabel == null) {
            return;
        }

        double drynessValue = dropsReceived - expectedDrops;

        dryAnyUniqueLabel.setText(String.valueOf(waveRollsTracked));
        if (dryDeepRollsLabel != null) {
            dryDeepRollsLabel.setText(String.valueOf(deepRolls));
        }
        dryAnyUniqueOddsLabel.setText(String.format("%.2f", expectedDrops));
        dryClothLabel.setText(String.valueOf(dropsReceived));
        dryEyeLabel.setText(String.format("%.2f", drynessValue));

        if (dryExpectedDomLabel != null) {
            dryExpectedDomLabel.setText(String.format("%.2f / %d", expectedDom, receivedDom));
            dryExpectedDomLabel.setForeground(getDrynessGradientColor(receivedDom - expectedDom));
        }
        if (dryExpectedTreadsLabel != null) {
            dryExpectedTreadsLabel.setText(String.format("%.2f / %d", expectedTreads, receivedTreads));
            dryExpectedTreadsLabel.setForeground(getDrynessGradientColor(receivedTreads - expectedTreads));
        }
        if (dryExpectedEyeLabel != null) {
            dryExpectedEyeLabel.setText(String.format("%.2f / %d", expectedEye, receivedEye));
            dryExpectedEyeLabel.setForeground(getDrynessGradientColor(receivedEye - expectedEye));
        }
        if (dryExpectedClothLabel != null) {
            dryExpectedClothLabel.setText(String.format("%.2f / %d", expectedCloth, receivedCloth));
            dryExpectedClothLabel.setForeground(getDrynessGradientColor(receivedCloth - expectedCloth));
        }

        if (drySyncWarningLabel != null) {
            boolean noWavesLogged = waveRollsTracked == 0;
            boolean noItemsLogged = dropsReceived == 0
                    && receivedDom == 0
                    && receivedTreads == 0
                    && receivedEye == 0
                    && receivedCloth == 0;
            boolean expectedExactlyZero = Math.abs(expectedDrops) < 1e-9;
            drySyncWarningLabel.setVisible(expectedExactlyZero && noWavesLogged && noItemsLogged);
        }

        if (drynessWaveCompletionLabels != null && completedRunsByWave != null) {
            for (int i = 0; i < 8; i++) {
                if (drynessWaveCompletionLabels[i] != null) {
                    drynessWaveCompletionLabels[i].setText(
                            String.valueOf(completedRunsByWave.getOrDefault(i + 1, 0L)));
                }
            }
            if (drynessWaveCompletionLabels[8] != null) {
                long wave9Plus = completedRunsByWave.getOrDefault(9, 0L);
                drynessWaveCompletionLabels[8].setText(String.valueOf(wave9Plus));
            }
        }
    }

    private void setDrynessUnavailable() {
        if (dryAnyUniqueLabel != null) {
            dryAnyUniqueLabel.setText("0");
        }
        if (dryDeepRollsLabel != null) {
            dryDeepRollsLabel.setText("0");
        }
        if (dryAnyUniqueOddsLabel != null) {
            dryAnyUniqueOddsLabel.setText("0");
        }
        if (dryClothLabel != null) {
            dryClothLabel.setText("0");
        }
        if (dryEyeLabel != null) {
            dryEyeLabel.setText("N/A");
        }
        if (dryExpectedClothLabel != null) {
            dryExpectedClothLabel.setText("0.00 / 0");
            dryExpectedClothLabel.setForeground(Color.WHITE);
        }
        if (dryExpectedEyeLabel != null) {
            dryExpectedEyeLabel.setText("0.00 / 0");
            dryExpectedEyeLabel.setForeground(Color.WHITE);
        }
        if (dryExpectedTreadsLabel != null) {
            dryExpectedTreadsLabel.setText("0.00 / 0");
            dryExpectedTreadsLabel.setForeground(Color.WHITE);
        }
        if (dryExpectedDomLabel != null) {
            dryExpectedDomLabel.setText("0.00 / 0");
            dryExpectedDomLabel.setForeground(Color.WHITE);
        }
        if (drySyncWarningLabel != null) {
            drySyncWarningLabel.setVisible(true);
        }
    }

    private Color getDrynessGradientColor(double overUnderRate) {
        if (Math.abs(overUnderRate) < 1e-9) {
            return Color.WHITE;
        }

        // Clamp to the +/-3 range requested by design.
        double clamped = Math.max(-3.0, Math.min(3.0, overUnderRate));
        double strength = Math.abs(clamped) / 3.0;

        Color target = clamped > 0
                ? new Color(0, 200, 0)
                : new Color(200, 0, 0);

        return blendColors(Color.WHITE, target, strength);
    }

    private Color blendColors(Color start, Color end, double t) {
        double clampedT = Math.max(0.0, Math.min(1.0, t));

        int r = (int) Math.round(start.getRed() + (end.getRed() - start.getRed()) * clampedT);
        int g = (int) Math.round(start.getGreen() + (end.getGreen() - start.getGreen()) * clampedT);
        int b = (int) Math.round(start.getBlue() + (end.getBlue() - start.getBlue()) * clampedT);

        return new Color(r, g, b);
    }

    private double toFlooredOneInNDecimal(double cumulativePercent) {
        if (cumulativePercent <= 0) {
            return 0;
        }

        double probability = cumulativePercent / 100.0;
        int flooredDenominator = (int) Math.floor(1.0 / probability);
        if (flooredDenominator <= 0) {
            return 0;
        }

        return 1.0 / flooredDenominator;
    }

    public void updateClaimedWave(int wave, Map<String, ItemData> itemData) {
        updateClaimedWave(wave, itemData, -1);
    }

    public void updateClaimedWave(int wave, Map<String, ItemData> itemData, long explicitTotal) {
        int index = wave >= 9 ? 8 : wave - 1;
        if (index >= 0 && index < claimedWavePanels.length) {
            updateWavePanel(claimedWaveValueLabels[index], claimedWaveItemPanels[index],
                    claimedWaveCollapsed[index], itemData, explicitTotal, true, wave >= 9 ? 9 : wave);
            updateClaimedSectionTotal();
            if (claimedSectionState == 2) {
                populateClaimedCombinedPanel();
            }
        }
    }

    public void updateUnclaimedWave(int wave, Map<String, ItemData> itemData) {
        updateUnclaimedWave(wave, itemData, -1);
    }

    public void updateUnclaimedWave(int wave, Map<String, ItemData> itemData, long explicitTotal) {
        int index = wave >= 9 ? 8 : wave - 1;
        if (index >= 0 && index < unclaimedWavePanels.length) {
            updateWavePanel(unclaimedWaveValueLabels[index], unclaimedWaveItemPanels[index],
                    unclaimedWaveCollapsed[index], itemData, explicitTotal, false, wave >= 9 ? 9 : wave);
            updateUnclaimedSectionTotal();
            if (unclaimedSectionState == 2) {
                populateUnclaimedCombinedPanel();
            }
        }
    }

    public void updateSuppliesCurrentRun(long totalValue, Map<String, ItemData> itemData) {
        // Update total value label
        suppliesCurrentRunTotalLabel.setText(formatGp(totalValue));
        suppliesCurrentRunHeaderLabel.setText(formatGp(totalValue)); // Also update header label
        // Update items
        updateSuppliesPanel(suppliesCurrentRunPanel, itemData, false, false);
    }

    public void updateSuppliesTotal(long totalValue, Map<String, ItemData> itemData) {
        suppliesTotalValueLabel.setText(formatGp(totalValue));
        suppliesTotalHeaderLabel.setText(formatGp(totalValue)); // Also update header label
        updateSuppliesPanel(suppliesTotalItemsPanel, itemData, true, false);
    }

    public void setDisplayHaValueOnHover(boolean displayHaValueOnHover) {
        this.displayHaValueOnHover = displayHaValueOnHover;
        potentialValueLabel.setText(formatTotalWithOptionalHa(currentRunGeTotal, currentRunHaTotal));
        potentialValueLabel.setToolTipText(formatGeHaTotalText(currentRunGeTotal, currentRunHaTotal));
        previousRunValueLabel.setText(formatTotalWithOptionalHa(previousRunGeTotal, previousRunHaTotal));
        previousRunValueLabel.setToolTipText(formatGeHaTotalText(previousRunGeTotal, previousRunHaTotal));

        renderCurrentRunWaveBreakdown();
        renderPreviousRunSection();

        updateClaimedSectionTotal();
        updateUnclaimedSectionTotal();
        refreshSummaryHaTooltips();
        refreshWaveHeaderHaTooltips(claimedWaveValueLabels, claimedWaveHaTotals);
        refreshWaveHeaderHaTooltips(unclaimedWaveValueLabels, unclaimedWaveHaTotals);

        if (claimedSectionState == 2) {
            populateClaimedCombinedPanel();
        }
        if (unclaimedSectionState == 2) {
            populateUnclaimedCombinedPanel();
        }
    }

    /**
     * Update the claimed loot section total for collapsed view to match the summary
     * value
     */
    private void updateClaimedSectionTotal() {
        claimedSectionTotalLabel.setText(totalClaimedLabel.getText());
        claimedSectionHaTotal = 0;
        for (long haTotal : claimedWaveHaTotals) {
            claimedSectionHaTotal += haTotal;
        }
        claimedSectionTotalLabel.setToolTipText(
                formatGeHaTotalText(totalClaimedLabel.getText(), formatGp(claimedSectionHaTotal)));
        refreshSummaryHaTooltips();
    }

    /**
     * Update the unclaimed loot section total for collapsed view to match the
     * summary value
     */
    private void updateUnclaimedSectionTotal() {
        unclaimedSectionTotalLabel.setText(totalUnclaimedLabel.getText());
        unclaimedSectionHaTotal = 0;
        for (long haTotal : unclaimedWaveHaTotals) {
            unclaimedSectionHaTotal += haTotal;
        }
        unclaimedSectionTotalLabel.setToolTipText(
                formatGeHaTotalText(totalUnclaimedLabel.getText(), formatGp(unclaimedSectionHaTotal)));
        refreshSummaryHaTooltips();
    }

    private void refreshSummaryHaTooltips() {
        // Fall back to GE values when HA data is absent (historical items collected before
        // HA tracking was added have totalHaValue=0; showing HA:0 or HA:-cost is misleading).
        long claimedHa = claimedSectionHaTotal > 0 ? claimedSectionHaTotal : summaryTotalClaimedGe;
        long unclaimedHa = unclaimedSectionHaTotal > 0 ? unclaimedSectionHaTotal : summaryTotalUnclaimedGe;

        totalClaimedLabel.setToolTipText(formatGeHaTotalText(summaryTotalClaimedGe, claimedHa));
        totalUnclaimedLabel.setToolTipText(formatGeHaTotalText(summaryTotalUnclaimedGe, unclaimedHa));

        long geProfitLoss = summaryTotalClaimedGe - summarySupplyCostGe;
        long haProfitLoss = claimedHa - summarySupplyCostGe;
        profitLossLabel.setToolTipText(formatGeHaTotalText(geProfitLoss, haProfitLoss));
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
        potentialValueLabel.setToolTipText(null);
        currentRunGeTotal = 0;
        currentRunHaTotal = 0;
        currentRunWaveLabel.setText("");
        cumulativeUniqueChanceLabel.setText("N/A");
        cumulativeUniqueChanceLabel.setForeground(Color.LIGHT_GRAY);
        currentRunItemsPanel.removeAll();
        currentRunItemData.clear();
        currentRunItemsByWave.clear();
        currentRunTotalsByWave.clear();
        currentRunHaTotalsByWave.clear();
        currentRunWaveCollapsed.clear();
        currentRunShowByWave = false;
        updateCurrentRunViewToggleText();

        previousRunStatusLabel.setText("");
        previousRunSectionTotalLabel.setText("0 gp");
        previousRunSectionTotalLabel.setToolTipText(null);
        previousRunValueLabel.setText("0 gp");
        previousRunValueLabel.setToolTipText(null);
        previousRunSuppliesValueLabel.setText("0 gp");
        previousRunGeTotal = 0;
        previousRunHaTotal = 0;
        previousRunSuppliesTotal = 0;
        if (previousRunSuppliesPanel != null) {
            previousRunSuppliesPanel.removeAll();
        }
        previousRunWavesContainer.removeAll();
        previousRunItemData.clear();
        previousRunSuppliesItemData.clear();
        previousRunItemsByWave.clear();
        previousRunTotalsByWave.clear();
        previousRunHaTotalsByWave.clear();
        previousRunWaveCollapsed.clear();
        previousRunSectionState = 1;
        hasPreviousRunData = false;
        if (previousRunCombinedPanel != null) {
            previousRunCombinedPanel.removeAll();
        }

        // Clear all claimed wave panels
        for (int i = 0; i < claimedWavePanels.length; i++) {
            claimedWaveValueLabels[i].setText("0 gp");
            claimedWaveValueLabels[i].setToolTipText(null);
            claimedWaveHaTotals[i] = 0;
            claimedWaveItemPanels[i].removeAll();
        }
        claimedSectionTotalLabel.setText("0 gp");
        claimedSectionTotalLabel.setToolTipText(null);
        claimedSectionHaTotal = 0;

        // Clear claimed combined panel
        if (claimedCombinedPanel != null) {
            claimedCombinedPanel.removeAll();
        }

        // Clear all unclaimed wave panels
        for (int i = 0; i < unclaimedWavePanels.length; i++) {
            unclaimedWaveValueLabels[i].setText("0 gp");
            unclaimedWaveValueLabels[i].setToolTipText(null);
            unclaimedWaveHaTotals[i] = 0;
            unclaimedWaveItemPanels[i].removeAll();
        }
        unclaimedSectionTotalLabel.setText("0 gp");
        unclaimedSectionTotalLabel.setToolTipText(null);
        unclaimedSectionHaTotal = 0;

        // Clear unclaimed combined panel
        if (unclaimedCombinedPanel != null) {
            unclaimedCombinedPanel.removeAll();
        }

        // Clear supplies sections
        suppliesCurrentRunTotalLabel.setText("0 gp");
        suppliesCurrentRunHeaderLabel.setText("0 gp");
        suppliesCurrentRunPanel.removeAll();
        suppliesTotalValueLabel.setText("0 gp");
        suppliesTotalHeaderLabel.setText("0 gp");
        suppliesTotalItemsPanel.removeAll();

        setDrynessUnavailable();

        // Refresh the panel
        statsPanel.revalidate();
        statsPanel.repaint();
    }

    private void updateWavePanel(JLabel valueLabel, JPanel itemsPanel, boolean isCollapsed,
            Map<String, ItemData> itemData, long explicitTotal, boolean isClaimed, int wave) {
        itemsPanel.removeAll();
        int index = wave >= 9 ? 8 : wave - 1;

        if (itemData == null || itemData.isEmpty()) {
            valueLabel.setText("0 gp");
            if (index >= 0 && index < 9) {
                if (isClaimed) {
                    claimedWaveHaTotals[index] = 0;
                } else {
                    unclaimedWaveHaTotals[index] = 0;
                }
            }
            valueLabel.setToolTipText(null);
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

        long totalHaValue = 0;
        for (ItemData item : itemData.values()) {
            totalHaValue += item.totalHaValue;
        }

        valueLabel.setText(formatGp(totalValue));
        if (index >= 0 && index < 9) {
            if (isClaimed) {
                claimedWaveHaTotals[index] = totalHaValue;
            } else {
                unclaimedWaveHaTotals[index] = totalHaValue;
            }
        }
        valueLabel.setToolTipText(formatGeHaTotalText(totalValue, totalHaValue));

        for (ItemData item : sortItemDataForDisplay(itemData.values())) {
            String pricePerItemText = formatPricePerItemTooltip(item.pricePerItem, item.haPricePerItem,
                    displayHaValueOnHover);

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
            itemValueLabel.setToolTipText(formatGeHaTotalText(item.totalValue, resolveItemTotalHaValue(item)));
            itemRow.add(itemValueLabel, BorderLayout.EAST);

            if (config.enableHistoricalEdit()) {
                if (isClaimed && onRemoveClaimedHistoricalItem != null) {
                    addHistoricalRemovalInteraction(itemRow, item.name,
                            "claimed historical loot (Wave " + (wave >= 9 ? "9+" : wave) + ")",
                            () -> onRemoveClaimedHistoricalItem.accept(wave, item.name));
                } else if (!isClaimed && onRemoveUnclaimedHistoricalItem != null) {
                    addHistoricalRemovalInteraction(itemRow, item.name,
                            "unclaimed historical loot (Wave " + (wave >= 9 ? "9+" : wave) + ")",
                            () -> onRemoveUnclaimedHistoricalItem.accept(wave, item.name));
                }
            }

            itemsPanel.add(itemRow);
        }

        itemsPanel.setVisible(!isCollapsed);
        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    private void updateSuppliesPanel(JPanel suppliesPanel, Map<String, ItemData> itemData, boolean isHistorical,
            boolean highlightCurrentRunUniques) {
        // Clear existing items
        suppliesPanel.removeAll();

        if (itemData == null || itemData.isEmpty()) {
            // Still need to refresh UI even when clearing items
            suppliesPanel.revalidate();
            suppliesPanel.repaint();
            return;
        }

        // Add item entries
        for (ItemData item : sortItemDataForDisplay(itemData.values())) {
            String pricePerItemText = formatPricePerItemTooltip(item.pricePerItem, 0, false);

            // Create item row with BorderLayout for left/right alignment
            JPanel itemRow = new JPanel(new BorderLayout());
            itemRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            itemRow.setBorder(new EmptyBorder(2, 5, 2, 0));
            itemRow.setToolTipText(item.tooltipText != null ? item.tooltipText : "Price: " + pricePerItemText);

            // Left side: item name and quantity
            String quantityText;
            if (item.maxDosesForDisplay > 0) {
                double potions = (double) item.quantity / item.maxDosesForDisplay;
                quantityText = (potions == Math.floor(potions))
                        ? String.valueOf((long) potions)
                        : String.format("%.2f", potions);
            } else {
                quantityText = String.valueOf(item.quantity);
            }
            JLabel itemLabel = new JLabel("- " + item.name + " x" + quantityText);
            Color itemColor = highlightCurrentRunUniques && isUniqueLootItem(item)
                    ? UNIQUE_GOLD_COLOR
                    : ColorScheme.LIGHT_GRAY_COLOR;
            itemLabel.setForeground(itemColor);
            itemLabel.setFont(FontManager.getRunescapeSmallFont());
            itemRow.add(itemLabel, BorderLayout.WEST);

            // Right side: value
            JLabel itemValueLabel = new JLabel(formatGp(item.totalValue));
            itemValueLabel.setForeground(itemColor);
            itemValueLabel.setFont(FontManager.getRunescapeSmallFont());
            itemRow.add(itemValueLabel, BorderLayout.EAST);

            if (isHistorical && config.enableHistoricalEdit() && onRemoveHistoricalSupplyItem != null) {
                addHistoricalRemovalInteraction(itemRow, item.name, "historical supplies",
                        () -> onRemoveHistoricalSupplyItem.accept(item.name));
            }

            suppliesPanel.add(itemRow);
        }

        suppliesPanel.revalidate();
        suppliesPanel.repaint();
    }

    private void updateRunItemsPanel(JPanel targetPanel, Map<String, ItemData> itemData, boolean highlightUniques) {
        targetPanel.removeAll();

        if (itemData == null || itemData.isEmpty()) {
            targetPanel.revalidate();
            targetPanel.repaint();
            return;
        }

        for (ItemData item : sortItemDataForDisplay(itemData.values())) {
            String pricePerItemText = formatPricePerItemTooltip(item.pricePerItem, item.haPricePerItem,
                    displayHaValueOnHover);

            JPanel itemRow = new JPanel(new BorderLayout());
            itemRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
            itemRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
            itemRow.setBorder(new EmptyBorder(2, 5, 2, 0));
            itemRow.setToolTipText("Price per item: " + pricePerItemText);

            JLabel itemLabel = new JLabel("- " + item.name + " x" + item.quantity);
            Color itemColor = highlightUniques && isUniqueLootItem(item)
                    ? UNIQUE_GOLD_COLOR
                    : ColorScheme.LIGHT_GRAY_COLOR;
            itemLabel.setForeground(itemColor);
            itemLabel.setFont(FontManager.getRunescapeSmallFont());
            itemRow.add(itemLabel, BorderLayout.WEST);

            JLabel itemValueLabel = new JLabel(formatGp(item.totalValue));
            itemValueLabel.setForeground(itemColor);
            itemValueLabel.setFont(FontManager.getRunescapeSmallFont());
            itemValueLabel.setToolTipText(formatGeHaTotalText(item.totalValue, resolveItemTotalHaValue(item)));
            itemRow.add(itemValueLabel, BorderLayout.EAST);

            targetPanel.add(itemRow);
        }

        targetPanel.revalidate();
        targetPanel.repaint();
    }

    private void renderCurrentRunWaveBreakdown() {
        currentRunItemsPanel.removeAll();

        if (currentRunShowByWave && !currentRunItemsByWave.isEmpty()) {
            for (Map.Entry<Integer, Map<String, ItemData>> waveEntry : currentRunItemsByWave.entrySet()) {
                int wave = waveEntry.getKey();
                JPanel wavePanel = createCurrentRunWavePanel(
                        wave,
                        waveEntry.getValue(),
                        currentRunTotalsByWave.getOrDefault(wave, 0L),
                        currentRunHaTotalsByWave.getOrDefault(wave, 0L));
                currentRunItemsPanel.add(wavePanel);
            }
        } else {
            // Fallback for callers that only provide aggregated current-run items.
            updateRunItemsPanel(currentRunItemsPanel, currentRunItemData, true);
        }

        currentRunItemsPanel.revalidate();
        currentRunItemsPanel.repaint();
    }

    private void updateCurrentRunViewToggleText() {
        if (currentRunViewToggleButton != null) {
            currentRunViewToggleButton.setText(getArrowOrFallback(currentRunShowByWave ? "▾" : "▸",
                    currentRunShowByWave ? "↓" : "→"));
            currentRunViewToggleButton.setToolTipText(currentRunShowByWave
                    ? "Showing by-wave breakdown (click for summary)"
                    : "Showing summary (click for by-wave breakdown)");
        }
    }

    private JPanel createCurrentRunWavePanel(int wave, Map<String, ItemData> itemData, long totalValue,
            long totalHaValue) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        boolean collapsed = currentRunWaveCollapsed.getOrDefault(wave, false);

        JLabel valueLabel = new JLabel(formatGp(totalValue));
        valueLabel.setFont(FontManager.getRunescapeFont());
        valueLabel.setForeground(Color.WHITE);
        valueLabel.setToolTipText(formatGeHaTotalText(totalValue, totalHaValue));

        JButton collapseButton = new JButton(getArrowOrFallback(collapsed ? "▸" : "▾", collapsed ? "→" : "↓"));
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
        headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel labelComponent = new JLabel("Wave " + wave + ":");
        labelComponent.setFont(FontManager.getRunescapeFont());
        labelComponent.setForeground(Color.LIGHT_GRAY);

        headerRow.add(collapseButton, BorderLayout.WEST);
        headerRow.add(labelComponent, BorderLayout.CENTER);
        headerRow.add(valueLabel, BorderLayout.EAST);
        panel.add(headerRow);

        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.setVisible(!collapsed);
        panel.add(itemsPanel);
        updateRunItemsPanel(itemsPanel, itemData, true);
        itemsPanel.setVisible(!collapsed);

        collapseButton.addActionListener(e -> {
            boolean nextCollapsed = !currentRunWaveCollapsed.getOrDefault(wave, false);
            currentRunWaveCollapsed.put(wave, nextCollapsed);
            itemsPanel.setVisible(!nextCollapsed);
            collapseButton.setText(getArrowOrFallback(nextCollapsed ? "▸" : "▾", nextCollapsed ? "→" : "↓"));
            panel.revalidate();
            panel.repaint();
        });

        return panel;
    }

    private void renderPreviousRunSection() {
        previousRunSectionTotalLabel.setVisible(previousRunSectionState == 1);
        previousRunSectionTotalLabel.setText(formatGp(previousRunGeTotal));
        previousRunSectionTotalLabel.setToolTipText(formatGeHaTotalText(previousRunGeTotal, previousRunHaTotal));

        previousRunValueLabel.setText(formatTotalWithOptionalHa(previousRunGeTotal, previousRunHaTotal));
        previousRunValueLabel.setToolTipText(formatGeHaTotalText(previousRunGeTotal, previousRunHaTotal));
        previousRunSuppliesValueLabel.setText(formatGp(previousRunSuppliesTotal));
        updateSuppliesPanel(previousRunSuppliesPanel, previousRunSuppliesItemData, false, false);
        updatePreviousRunPerformanceLabels();

        previousRunWavesContainer.removeAll();

        if (hasPreviousRunData) {
            for (Map.Entry<Integer, Map<String, ItemData>> waveEntry : previousRunItemsByWave.entrySet()) {
                int wave = waveEntry.getKey();
                JPanel wavePanel = createPreviousRunWavePanel(
                        wave,
                        waveEntry.getValue(),
                        previousRunTotalsByWave.getOrDefault(wave, 0L),
                        previousRunHaTotalsByWave.getOrDefault(wave, 0L));
                previousRunWavesContainer.add(wavePanel);
            }
        }

        updatePreviousRunSectionView();

        previousRunWavesContainer.revalidate();
        previousRunWavesContainer.repaint();
        previousRunContainer.revalidate();
        previousRunContainer.repaint();
        previousRunCombinedPanel.revalidate();
        previousRunCombinedPanel.repaint();
    }

    private void updatePreviousRunSectionView() {
        switch (previousRunSectionState) {
            case 0: // expanded
                previousRunContainer.setVisible(true);
                previousRunCombinedPanel.setVisible(false);
                previousRunSectionTotalLabel.setVisible(false);
                setPreviousRunCollapseButtonText("▾");
                break;
            case 1: // collapsed
                previousRunContainer.setVisible(false);
                previousRunCombinedPanel.setVisible(false);
                previousRunSectionTotalLabel.setVisible(true);
                setPreviousRunCollapseButtonText("▸");
                break;
            default: // combined
                previousRunContainer.setVisible(false);
                previousRunCombinedPanel.setVisible(true);
                previousRunSectionTotalLabel.setVisible(false);
                setPreviousRunCollapseButtonText("◂");
                populatePreviousRunCombinedPanel();
                break;
        }
    }

    private void setPreviousRunCollapseButtonText(String text) {
        previousRunCollapseButton.setText(getArrowOrFallback(text, getArrowFallback(text)));
    }

    private void populatePreviousRunCombinedPanel() {
        previousRunCombinedPanel.removeAll();
        updateRunItemsPanel(previousRunCombinedPanel, previousRunItemData, true);

        if (!previousRunItemData.isEmpty() && displayHaValueOnHover) {
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

            JLabel geTotalLabel = new JLabel("GE: " + formatGp(previousRunGeTotal));
            geTotalLabel.setFont(FontManager.getRunescapeFont());
            geTotalLabel.setForeground(Color.WHITE);

            JLabel haTotalLabel = new JLabel(" | HA: " + formatGp(previousRunHaTotal));
            haTotalLabel.setFont(FontManager.getRunescapeFont());
            haTotalLabel.setForeground(new Color(0, 200, 0));

            valuesPanel.add(geTotalLabel);
            valuesPanel.add(haTotalLabel);
            totalRow.add(valuesPanel, BorderLayout.EAST);
            previousRunCombinedPanel.add(totalRow);
        }

        previousRunCombinedPanel.add(createSeparator(2));

        JLabel combinedSuppliesTotalLabel = new JLabel(formatGp(previousRunSuppliesTotal));
        combinedSuppliesTotalLabel.setFont(FontManager.getRunescapeFont());
        combinedSuppliesTotalLabel.setForeground(Color.WHITE);
        previousRunCombinedPanel.add(createStatRow("Supplies Used:", combinedSuppliesTotalLabel));

        JPanel combinedSuppliesItemsPanel = new JPanel();
        combinedSuppliesItemsPanel.setLayout(new BoxLayout(combinedSuppliesItemsPanel, BoxLayout.Y_AXIS));
        combinedSuppliesItemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        previousRunCombinedPanel.add(combinedSuppliesItemsPanel);
        updateSuppliesPanel(combinedSuppliesItemsPanel, previousRunSuppliesItemData, false, false);

        previousRunCombinedPanel.add(createSeparator(2));

        JLabel combinedPerformanceHeaderLabel = new JLabel("");
        combinedPerformanceHeaderLabel.setFont(FontManager.getRunescapeFont());
        combinedPerformanceHeaderLabel.setForeground(Color.WHITE);
        previousRunCombinedPanel.add(createStatRow("Performance:", combinedPerformanceHeaderLabel));

        addPreviousRunPerformanceRows(previousRunCombinedPanel);
    }

    private void updatePreviousRunPerformanceLabels() {
        previousRunPrayerUsedLabel.setText(String.valueOf(previousRunPrayerUsed));
        previousRunPrayerRegainedLabel.setText(String.valueOf(previousRunPrayerRegained));
        previousRunHpLostLabel.setText(String.valueOf(previousRunHpLost));
        previousRunHpRegainedLabel.setText(String.valueOf(previousRunHpRegained));
        previousRunSpecialAttacksUsedLabel.setText(String.valueOf(previousRunSpecialAttacksUsed));
        previousRunVenomApplicationsLabel.setText(String.valueOf(previousRunVenomApplications));
    }

    private void addPreviousRunPerformanceRows(JPanel targetPanel) {
        targetPanel.add(createStatRow("HP lost:",
                createPerformanceMetricValueLabelWithValue(HP_LOST_COLOR, previousRunHpLost)));
        targetPanel.add(createStatRow("Prayer used:",
                createPerformanceMetricValueLabelWithValue(PRAYER_USED_COLOR, previousRunPrayerUsed)));
        targetPanel.add(createStatRow("Special attacks used:",
                createPerformanceMetricValueLabelWithValue(SPECIAL_ATTACKS_COLOR, previousRunSpecialAttacksUsed)));
        targetPanel.add(createStatRow("Times Venomed:",
                createPerformanceMetricValueLabelWithValue(VENOM_COLOR, previousRunVenomApplications)));
        targetPanel.add(createStatRow("HP regained:",
                createPerformanceMetricValueLabelWithValue(HP_REGAINED_COLOR, previousRunHpRegained)));
        targetPanel.add(createStatRow("Prayer regained:",
                createPerformanceMetricValueLabelWithValue(PRAYER_REGAINED_COLOR, previousRunPrayerRegained)));
    }

    private JLabel createPerformanceMetricValueLabelWithValue(Color color, int value) {
        JLabel label = createPerformanceMetricValueLabel(color);
        label.setText(String.valueOf(value));
        return label;
    }

    private JPanel createPreviousRunWavePanel(int wave, Map<String, ItemData> itemData, long totalValue,
            long totalHaValue) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        boolean collapsed = previousRunWaveCollapsed.getOrDefault(wave, false);

        JLabel valueLabel = new JLabel(formatGp(totalValue));
        valueLabel.setFont(FontManager.getRunescapeFont());
        valueLabel.setForeground(Color.WHITE);
        valueLabel.setToolTipText(formatGeHaTotalText(totalValue, totalHaValue));

        JButton collapseButton = new JButton(getArrowOrFallback(collapsed ? "▸" : "▾", collapsed ? "→" : "↓"));
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
        headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel labelComponent = new JLabel("Wave " + wave + ":");
        labelComponent.setFont(FontManager.getRunescapeFont());
        labelComponent.setForeground(Color.LIGHT_GRAY);

        headerRow.add(collapseButton, BorderLayout.WEST);
        headerRow.add(labelComponent, BorderLayout.CENTER);
        headerRow.add(valueLabel, BorderLayout.EAST);
        panel.add(headerRow);

        JPanel itemsPanel = new JPanel();
        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        itemsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        itemsPanel.setVisible(!collapsed);
        panel.add(itemsPanel);
        updateRunItemsPanel(itemsPanel, itemData, true);
        itemsPanel.setVisible(!collapsed);

        collapseButton.addActionListener(e -> {
            boolean nextCollapsed = !previousRunWaveCollapsed.getOrDefault(wave, false);
            previousRunWaveCollapsed.put(wave, nextCollapsed);
            itemsPanel.setVisible(!nextCollapsed);
            collapseButton.setText(getArrowOrFallback(nextCollapsed ? "▸" : "▾", nextCollapsed ? "→" : "↓"));
            panel.revalidate();
            panel.repaint();
        });

        return panel;
    }

    private boolean isUniqueLootItem(ItemData item) {
        return LootPanelDisplayUtils.isUniqueLootItem(item);
    }

    private void addHistoricalRemovalInteraction(JPanel itemRow, String itemName, String scope, Runnable onConfirm) {
        itemRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        String baseTooltip = itemRow.getToolTipText();
        String editHint = "Click to remove from " + scope;
        itemRow.setToolTipText(
                baseTooltip == null || baseTooltip.isEmpty() ? editHint : (baseTooltip + " | " + editHint));
        itemRow.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event) && !SwingUtilities.isRightMouseButton(event)) {
                    return;
                }

                int response = JOptionPane.showConfirmDialog(
                        MokhaLootPanel.this,
                        "Remove '" + itemName + "' from " + scope + "?\nThis updates historical totals immediately.",
                        "Remove Historical Entry",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);

                if (response == JOptionPane.YES_OPTION) {
                    onConfirm.run();
                }
            }
        });
    }

    private List<ItemData> sortItemDataForDisplay(java.util.Collection<ItemData> items) {
        return LootPanelDisplayUtils.sortItemDataForDisplay(items, config.displaySortMode());
    }

    private List<ItemAggregate> sortAggregatesForDisplay(
            java.util.Collection<ItemAggregate> items) {
        return LootPanelDisplayUtils.sortAggregatesForDisplay(items, config.displaySortMode());
    }

    private String formatGp(long value) {
        return LootPanelDisplayUtils.formatGp(value);
    }

    private String formatTotalWithOptionalHa(long geTotal, long haTotal) {
        return LootPanelDisplayUtils.formatTotalWithOptionalHa(geTotal, haTotal, displayHaValueOnHover);
    }

    private String formatGeHaTotalText(long geTotal, long haTotal) {
        return LootPanelDisplayUtils.formatGeHaTotalText(geTotal, haTotal);
    }

    private String formatGeHaTotalText(String geText, String haText) {
        return LootPanelDisplayUtils.formatGeHaTotalText(geText, haText);
    }

    private long resolveItemTotalHaValue(ItemData item) {
        if (item == null) {
            return 0;
        }

        if (item.totalHaValue > 0) {
            return item.totalHaValue;
        }

        if (item.haPricePerItem > 0 && item.quantity > 0) {
            return (long) item.haPricePerItem * item.quantity;
        }

        return 0;
    }

    private void refreshWaveHeaderHaTooltips(JLabel[] waveLabels, long[] waveHaTotals) {
        for (int i = 0; i < waveLabels.length; i++) {
            JLabel waveLabel = waveLabels[i];
            if (waveLabel == null) {
                continue;
            }

            waveLabel.setToolTipText(formatGeHaTotalText(waveLabel.getText(), formatGp(waveHaTotals[i])));
        }
    }

    private String formatPricePerItemTooltip(long pricePerItem, long haPricePerItem, boolean includeHa) {
        return LootPanelDisplayUtils.formatPricePerItemTooltip(pricePerItem, haPricePerItem, includeHa);
    }

    private void customizeScrollBar() {
        getScrollPane().setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
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
    public void setHistoricalClaimedItemsByWave(Map<Integer, Map<String, ItemAggregate>> map) {
        this.historicalClaimedItemsByWave = map;
        if (claimedSectionState == 2) {
            SwingUtilities.invokeLater(this::populateClaimedCombinedPanel);
        }
    }

    public void setHistoricalUnclaimedItemsByWave(Map<Integer, Map<String, ItemAggregate>> map) {
        this.historicalUnclaimedItemsByWave = map;
        if (unclaimedSectionState == 2) {
            SwingUtilities.invokeLater(this::populateUnclaimedCombinedPanel);
        }
    }
}
