package com.camjewell;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

import javax.inject.Inject;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.client.util.QuantityFormatter;

public class MokhaLootOverlay extends Overlay {
    private final MokhaLootTrackerPlugin plugin;
    private final MokhaLootTrackerConfig config;
    private final PanelComponent panelComponent = new PanelComponent();

    @Inject
    private MokhaLootOverlay(MokhaLootTrackerPlugin plugin, MokhaLootTrackerConfig config) {
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
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

        // Only show overlay if in arena or delve interface is visible
        if (plugin.isInMokhaArena() || plugin.isDelveInterfaceVisible()) {
            return panelComponent.render(graphics);
        }
        return null;
    }
}
