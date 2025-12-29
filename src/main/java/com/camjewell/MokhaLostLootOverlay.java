package com.camjewell;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Color;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

public class MokhaLostLootOverlay extends Overlay {
    private final Client client;
    private final MokhaLostLootTrackerPlugin plugin;
    private final MokhaLostLootTrackerConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    private MokhaLostLootOverlay(Client client, MokhaLostLootTrackerPlugin plugin, MokhaLostLootTrackerConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setPriority(OverlayPriority.LOW);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            return null;
        }

        panelComponent.getChildren().clear();

        // Add title
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Potential Loot Loss")
                .color(Color.RED)
                .build());

        // Show current run info if in arena or delve interface is visible
        if (plugin.isInMokhaArena() || plugin.isDelveInterfaceVisible()) {
            long currentValue = plugin.getCurrentLootValue();
            if (currentValue > 0) {
                String formattedValue = QuantityFormatter.quantityToStackSize(currentValue) + " gp";
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Loss Value:")
                        .right(formattedValue)
                        .rightColor(Color.YELLOW)
                        .build());
            }
        }

        // Show the overlay when delve interface is visible (completion screen), in
        // arena on wave 2+ with loot value, or have historical stats
        int currentLevel = plugin.getCurrentDelveNumber();
        long currentValue = plugin.getCurrentLootValue();
        boolean showCurrentRun = plugin.isDelveInterfaceVisible()
                || (plugin.isInMokhaArena() && currentLevel > 1 && currentValue > 0);
        boolean showHistoricalStats = plugin.getTotalLostValue() > 0;

        if (showCurrentRun || showHistoricalStats) {
            return panelComponent.render(graphics);
        }

        return null;
    }
}
