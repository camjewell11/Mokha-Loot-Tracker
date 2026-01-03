package com.camjewell;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.function.BiFunction;

import javax.inject.Inject;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.border.EmptyBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.QuantityFormatter;

public class MokhaLootPanel extends PluginPanel {
    private static final Logger log = LoggerFactory.getLogger(MokhaLootPanel.class);
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
        PanelData data = PanelDataBuilder.build(plugin);

        javax.swing.SwingUtilities.invokeLater(() -> {
            try {
                statsPanel.removeAll();
                SummarySectionRenderer.render(statsPanel, data.getSummary(), data.isShowSuppliesUsed(),
                        createRowFactory());
                CurrentRunSectionRenderer.render(statsPanel, data.getCurrentRun(), data.getItemPriceCache(), plugin,
                        createRowFactory());
                statsPanel.add(PanelSectionUtil.createSeparator(10));
                WaveSectionRenderer.renderClaimed(statsPanel, data.getClaimedWaves(), data.getItemPriceCache(), plugin);
                statsPanel.add(PanelSectionUtil.createSeparator(10));
                WaveSectionRenderer.renderLost(statsPanel, data.getLostWaves(), data.getItemPriceCache(), plugin);
                if (data.isShowSuppliesUsed() && data.getSupplies() != null) {
                    SuppliesSectionRenderer.render(statsPanel, data.getSupplies(), data.getItemPriceCache(),
                            createRowFactory());
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

    private BiFunction<String, JLabel, JPanel> createRowFactory() {
        return this::createStatRow;
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
