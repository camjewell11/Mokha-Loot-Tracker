package com.camjewell;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

class WidgetParser {
    private static final Logger log = LoggerFactory.getLogger(WidgetParser.class);

    private final ItemManager itemManager;

    WidgetParser(ItemManager itemManager) {
        this.itemManager = itemManager;
    }

    int parseDelveNumber(Widget mainWidget) {
        if (mainWidget == null) {
            return 0;
        }

        Widget[] children = mainWidget.getChildren();
        if (children != null && children.length > 1) {
            Widget levelWidget = children[1];
            if (levelWidget != null) {
                String text = levelWidget.getText();
                if (text != null && text.contains("Level")) {
                    try {
                        String numStr = text.replaceAll("[^0-9]", "");
                        if (!numStr.isEmpty()) {
                            return Integer.parseInt(numStr);
                        }
                    } catch (NumberFormatException e) {
                        log.error("Error parsing delve number from text: {}", text, e);
                    }
                }
            }
        }
        return 0;
    }

    long parseLootValue(Widget valueWidget) {
        if (valueWidget != null) {
            String valueText = valueWidget.getText();
            if (valueText != null && valueText.contains("Value:")) {
                try {
                    String numStr = valueText.replaceAll("[^0-9]", "");
                    if (!numStr.isEmpty()) {
                        return Long.parseLong(numStr);
                    }
                } catch (Exception e) {
                    log.error("Error parsing loot value", e);
                }
            }
        }
        return 0;
    }

    List<LootItem> parseLootItems(Widget lootContainer) {
        List<LootItem> items = new ArrayList<>();

        if (lootContainer == null) {
            return items;
        }

        Widget[] children = lootContainer.getChildren();
        if (children != null) {
            for (Widget child : children) {
                if (child != null && !child.isHidden()) {
                    int itemId = child.getItemId();
                    int quantity = child.getItemQuantity();

                    if (itemId > 0 && quantity > 0) {
                        String itemName = itemManager.getItemComposition(itemId).getName();
                        if (itemName != null && !itemName.equalsIgnoreCase("null")) {
                            items.add(new LootItem(itemId, quantity, itemName));
                        }
                    }
                }
            }
        }

        return items;
    }
}
