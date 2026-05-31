package com.camjewell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.game.ItemManager;

class LootTrackingService {
    private static final int MOKHA_INTERFACE_GROUP_ID = 919;
    private static final int MOKHA_INTERFACE_MAIN_CHILD_ID = 2;
    private static final int MOKHA_INTERFACE_LOOT_CONTAINER_CHILD_ID = 19;

    private final Client client;
    private final ItemManager itemManager;
    private final MokhaLootTrackerConfig config;
    private final Logger log;
    private final Notifier notifier;
    private final Map<Integer, Integer> previousLootSnapshot;

    private boolean lootWindowWasVisible = false;

    private static final class LootAlertRule {
        String name;
        String nameKey;
        int minQty;

        LootAlertRule(String name, int minQty) {
            this.name = name;
            this.nameKey = name.toLowerCase();
            this.minQty = minQty;
        }
    }

    static final class LootWindowUpdate {
        private final boolean lootWindowVisible;
        private final int detectedWave;
        private final Map<Integer, Integer> newLootByItemId;

        LootWindowUpdate(boolean lootWindowVisible, int detectedWave, Map<Integer, Integer> newLootByItemId) {
            this.lootWindowVisible = lootWindowVisible;
            this.detectedWave = detectedWave;
            this.newLootByItemId = newLootByItemId;
        }

        boolean isLootWindowVisible() {
            return lootWindowVisible;
        }

        int getDetectedWave() {
            return detectedWave;
        }

        Map<Integer, Integer> getNewLootByItemId() {
            return newLootByItemId;
        }
    }

    LootTrackingService(
            Client client,
            ItemManager itemManager,
            MokhaLootTrackerConfig config,
            Logger log,
            Notifier notifier,
            Map<Integer, Integer> previousLootSnapshot) {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
        this.log = log;
        this.notifier = notifier;
        this.previousLootSnapshot = previousLootSnapshot;
    }

    LootWindowUpdate pollLootWindow(boolean inMokhaArena) {
        if (!inMokhaArena) {
            lootWindowWasVisible = false;
            return new LootWindowUpdate(false, 0, Collections.emptyMap());
        }

        Widget mainWidget = client.getWidget(MOKHA_INTERFACE_GROUP_ID, MOKHA_INTERFACE_MAIN_CHILD_ID);
        boolean lootWindowVisible = mainWidget != null && !mainWidget.isHidden();

        int detectedWave = 0;
        Map<Integer, Integer> newLootByItemId = Collections.emptyMap();

        if (lootWindowVisible && !lootWindowWasVisible) {
            detectedWave = extractWaveNumber(mainWidget);
            Widget lootContainerWidget = client.getWidget(MOKHA_INTERFACE_GROUP_ID,
                    MOKHA_INTERFACE_LOOT_CONTAINER_CHILD_ID);
            if (lootContainerWidget != null) {
                newLootByItemId = parseNewLoot(lootContainerWidget);
            }
        }

        lootWindowWasVisible = lootWindowVisible;
        return new LootWindowUpdate(lootWindowVisible, detectedWave, newLootByItemId);
    }

    private Map<Integer, Integer> parseNewLoot(Widget containerWidget) {
        Widget[] children = containerWidget.getChildren();
        if (children == null) {
            return Collections.emptyMap();
        }

        Map<Integer, Integer> currentLoot = new HashMap<>();
        Map<String, Integer> currentLootByName = new HashMap<>();
        for (Widget child : children) {
            if (child == null || child.isHidden()) {
                continue;
            }

            int itemId = child.getItemId();
            int itemQuantity = child.getItemQuantity();
            if (itemId <= 0 || itemQuantity <= 0) {
                continue;
            }

            String itemName = itemManager.getItemComposition(itemId).getName();
            if (itemName == null || itemName.isEmpty() || itemName.equalsIgnoreCase("null")) {
                continue;
            }

            currentLoot.put(itemId, itemQuantity);
            String nameKey = itemName.toLowerCase();
            currentLootByName.put(nameKey, currentLootByName.getOrDefault(nameKey, 0) + itemQuantity);
        }

        notifyLootAlerts(currentLootByName);

        Map<Integer, Integer> newLootByItemId = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : currentLoot.entrySet()) {
            int itemId = entry.getKey();
            int currentQty = entry.getValue();
            int previousQty = previousLootSnapshot.getOrDefault(itemId, 0);
            if (currentQty > previousQty) {
                newLootByItemId.put(itemId, currentQty - previousQty);
            }
        }

        previousLootSnapshot.clear();
        previousLootSnapshot.putAll(currentLoot);
        return newLootByItemId;
    }

    private List<LootAlertRule> parseLootAlertRules() {
        List<LootAlertRule> rules = new ArrayList<>();
        String raw = config.lootAlertLines();
        if (raw == null || raw.trim().isEmpty()) {
            return rules;
        }

        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int commaIndex = trimmed.indexOf(',');
            if (commaIndex < 0) {
                log.warn("[Mokha] Loot alert line missing comma: {}", trimmed);
                continue;
            }

            String name = trimmed.substring(0, commaIndex).trim();
            String qtyText = trimmed.substring(commaIndex + 1).trim();
            if (name.isEmpty() || qtyText.isEmpty()) {
                log.warn("[Mokha] Loot alert line missing name or quantity: {}", trimmed);
                continue;
            }

            int minQty;
            try {
                minQty = Integer.parseInt(qtyText);
            } catch (NumberFormatException e) {
                log.warn("[Mokha] Loot alert quantity is not a number: {}", trimmed);
                continue;
            }

            if (minQty <= 0) {
                log.warn("[Mokha] Loot alert quantity must be positive: {}", trimmed);
                continue;
            }

            rules.add(new LootAlertRule(name, minQty));
        }

        return rules;
    }

    private void notifyLootAlerts(Map<String, Integer> currentLootByName) {
        if (currentLootByName.isEmpty()) {
            return;
        }

        List<LootAlertRule> rules = parseLootAlertRules();
        if (rules.isEmpty()) {
            return;
        }

        for (LootAlertRule rule : rules) {
            Integer qty = currentLootByName.get(rule.nameKey);
            if (qty != null && qty >= rule.minQty) {
                String message = String.format("[Mokha Tracker] Loot alert: %s x%d (>= %d)", rule.name, qty,
                        rule.minQty);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
                notifier.notify(message);
            }
        }
    }

    private int extractWaveNumber(Widget mainWidget) {
        if (mainWidget == null) {
            return 0;
        }

        String text = mainWidget.getText();
        if (text != null && text.toLowerCase().contains("wave")) {
            try {
                String[] parts = text.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].equalsIgnoreCase("wave")) {
                        return Integer.parseInt(parts[i + 1].replaceAll("[^0-9]", ""));
                    }
                }
            } catch (NumberFormatException e) {
                // Ignore parsing errors.
            }
        }

        Widget[] children = mainWidget.getChildren();
        if (children != null) {
            for (Widget child : children) {
                if (child == null) {
                    continue;
                }

                String childText = child.getText();
                if (childText == null || !childText.toLowerCase().contains("wave")) {
                    continue;
                }

                try {
                    String[] parts = childText.split("\\s+");
                    for (int i = 0; i < parts.length - 1; i++) {
                        if (parts[i].equalsIgnoreCase("wave")) {
                            return Integer.parseInt(parts[i + 1].replaceAll("[^0-9]", ""));
                        }
                    }
                } catch (NumberFormatException e) {
                    // Ignore parsing errors.
                }
            }
        }

        return 0;
    }
}
