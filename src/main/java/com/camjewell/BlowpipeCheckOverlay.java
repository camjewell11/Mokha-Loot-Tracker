package com.camjewell;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class BlowpipeCheckOverlay extends Overlay {
    enum Prompt { INITIAL, FINAL }

    private final PanelComponent panelComponent = new PanelComponent();
    private Prompt prompt;

    BlowpipeCheckOverlay() {
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    void showInitialPrompt() {
        prompt = Prompt.INITIAL;
    }

    void showFinalPrompt() {
        prompt = Prompt.FINAL;
    }

    void hide() {
        prompt = null;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (prompt == null) {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Mokha Loot Tracker")
                .color(Color.YELLOW)
                .build());

        if (prompt == Prompt.INITIAL) {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Check weapon charges")
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("to start tracking")
                    .leftColor(Color.WHITE)
                    .build());
        } else {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Check weapon charges")
                    .leftColor(Color.WHITE)
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("to record usage")
                    .leftColor(Color.WHITE)
                    .build());
        }

        return panelComponent.render(graphics);
    }
}
