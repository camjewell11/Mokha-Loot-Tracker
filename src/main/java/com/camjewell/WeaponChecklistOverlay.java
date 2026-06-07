package com.camjewell;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class WeaponChecklistOverlay extends Overlay {
    enum Phase { INITIAL, FINAL }

    private final PanelComponent panelComponent = new PanelComponent();
    private Phase phase;
    private List<TrackedWeapon> weapons = Collections.emptyList();
    private Set<TrackedWeapon> checked = Collections.emptySet();

    WeaponChecklistOverlay() {
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    void showInitial(List<TrackedWeapon> weapons, Set<TrackedWeapon> checked) {
        this.phase = Phase.INITIAL;
        this.weapons = weapons;
        this.checked = checked;
    }

    void showFinal(List<TrackedWeapon> weapons, Set<TrackedWeapon> checked) {
        this.phase = Phase.FINAL;
        this.weapons = weapons;
        this.checked = checked;
    }

    void update(List<TrackedWeapon> weapons, Set<TrackedWeapon> checked) {
        this.weapons = weapons;
        this.checked = checked;
    }

    void hide() {
        phase = null;
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (phase == null || weapons.isEmpty()) {
            return null;
        }

        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Mokha Loot Tracker")
                .color(Color.YELLOW)
                .build());

        String subtitle = phase == Phase.INITIAL
                ? "Check charges to begin tracking:"
                : "Check charges to record usage:";
        panelComponent.getChildren().add(LineComponent.builder()
                .left(subtitle)
                .leftColor(Color.WHITE)
                .build());

        for (TrackedWeapon weapon : weapons) {
            boolean isDone = checked.contains(weapon);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left((isDone ? "[+] " : "[ ] ") + weapon.displayName)
                    .leftColor(isDone ? Color.GREEN : Color.LIGHT_GRAY)
                    .build());
        }

        return panelComponent.render(graphics);
    }
}
