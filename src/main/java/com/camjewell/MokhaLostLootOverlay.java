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
                .text("Mokha Lost Loot")
                .color(Color.RED)
                .build());

        // Show current run info if in arena or delve interface is visible
        if (plugin.isInMokhaArena() || plugin.isDelveInterfaceVisible()) {
            int currentLevel = plugin.getCurrentDelveNumber();
            if (currentLevel > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Current Level:")
                        .right(String.valueOf(currentLevel))
                        .rightColor(Color.CYAN)
                        .build());
            }

            long currentValue = plugin.getCurrentLootValue();
            if (currentValue > 0) {
                String formattedValue = QuantityFormatter.quantityToStackSize(currentValue) + " gp";
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Potential Loot Loss:")
                        .right(formattedValue)
                        .rightColor(Color.YELLOW)
                        .build());
            }
        }

        // Add total lost value
        if (config.showTotalValue()) {
            long totalLost = plugin.getTotalLostValue();
            if (totalLost > 0) {
                String formattedValue = QuantityFormatter.quantityToStackSize(totalLost) + " gp";

                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Total Lost:")
                        .right(formattedValue)
                        .rightColor(Color.RED)
                        .build());
            }
        }

        // Add death count
        if (config.showDeathCount()) {
            int deaths = plugin.getTimesDied();
            if (deaths > 0) {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Deaths:")
                        .right(String.valueOf(deaths))
                        .rightColor(Color.ORANGE)
                        .build());
            }
        }

        // Show the overlay if in arena, at delve interface, or have historical stats
        if (plugin.isInMokhaArena() || plugin.isDelveInterfaceVisible() || plugin.getTotalLostValue() > 0
                || plugin.getTimesDied() > 0) {
            return panelComponent.render(graphics);
        }

        return null;
    }
}
