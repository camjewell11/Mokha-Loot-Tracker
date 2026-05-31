package com.camjewell;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class LootPanelDisplayUtils {
    private LootPanelDisplayUtils() {
    }

    static List<MokhaLootPanel.ItemData> sortItemDataForDisplay(
            java.util.Collection<MokhaLootPanel.ItemData> items,
            MokhaDisplaySortMode sortMode) {
        List<MokhaLootPanel.ItemData> sorted = new ArrayList<>(items);
        if (sortMode == MokhaDisplaySortMode.VALUE_DESC) {
            sorted.sort(Comparator
                    .comparingLong((MokhaLootPanel.ItemData item) -> item.totalValue).reversed()
                    .thenComparing(item -> item.name, String.CASE_INSENSITIVE_ORDER));
        } else {
            sorted.sort(Comparator.comparing(item -> item.name, String.CASE_INSENSITIVE_ORDER));
        }
        return sorted;
    }

    static List<MokhaLootTrackerPlugin.ItemAggregate> sortAggregatesForDisplay(
            java.util.Collection<MokhaLootTrackerPlugin.ItemAggregate> items,
            MokhaDisplaySortMode sortMode) {
        List<MokhaLootTrackerPlugin.ItemAggregate> sorted = new ArrayList<>(items);
        if (sortMode == MokhaDisplaySortMode.VALUE_DESC) {
            sorted.sort(Comparator
                    .comparingLong((MokhaLootTrackerPlugin.ItemAggregate item) -> item.totalValue).reversed()
                    .thenComparing(item -> item.name, String.CASE_INSENSITIVE_ORDER));
        } else {
            sorted.sort(Comparator.comparing(item -> item.name, String.CASE_INSENSITIVE_ORDER));
        }
        return sorted;
    }

    static String formatGp(long value) {
        if (value >= 1_000_000_000 || value <= -1_000_000_000) {
            return String.format("%.3fB gp", value / 1_000_000_000.0);
        } else if (value >= 1_000_000 || value <= -1_000_000) {
            return String.format("%.2fM gp", value / 1_000_000.0);
        } else if (value >= 1_000 || value <= -1_000) {
            return String.format("%.1fK gp", value / 1_000.0);
        } else {
            return value + " gp";
        }
    }

    static String formatTotalWithOptionalHa(long geTotal, long haTotal, boolean displayHaValueOnHover) {
        if (!displayHaValueOnHover) {
            return formatGp(geTotal);
        }

        return String.format(
                "<html><span style='color:%s'>GE: %s</span> <span style='color:%s'>| HA: %s</span></html>",
                colorToHex(Color.WHITE),
                formatGp(geTotal),
                colorToHex(new Color(0, 200, 0)),
                formatGp(haTotal));
    }

    static String formatGeHaTotalText(long geTotal, long haTotal) {
        return formatGeHaTotalText(formatGp(geTotal), formatGp(haTotal));
    }

    static String formatGeHaTotalText(String geText, String haText) {
        return String.format("GE: %s | HA: %s", geText, haText);
    }

    static String formatPricePerItemTooltip(long pricePerItem, long haPricePerItem, boolean includeHa) {
        if (pricePerItem < 0 && haPricePerItem < 0) {
            return "N/A";
        }

        if (!includeHa) {
            return formatGp(pricePerItem) + "/ea";
        }

        return String.format("%s/ea, HA: %s/ea", formatGp(pricePerItem), formatGp(haPricePerItem));
    }

    static boolean isUniqueLootItem(MokhaLootPanel.ItemData item, int ultraValuableThreshold) {
        if (item == null) {
            return false;
        }

        return item.pricePerItem > ultraValuableThreshold || "Dom".equalsIgnoreCase(item.name);
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
