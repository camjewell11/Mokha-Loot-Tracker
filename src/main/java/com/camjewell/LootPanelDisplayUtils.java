package com.camjewell;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

final class LootPanelDisplayUtils {
    static final Set<String> UNIQUE_ITEM_NAMES = Set.of(
            "Dom", "Avernic treads", "Eye of ayak (uncharged)", "Mokhaiotl cloth");

    private LootPanelDisplayUtils() {
    }

    static List<ItemData> sortItemDataForDisplay(
            java.util.Collection<ItemData> items,
            MokhaDisplaySortMode sortMode) {
        List<ItemData> sorted = new ArrayList<>(items);
        if (sortMode == MokhaDisplaySortMode.VALUE_DESC) {
            sorted.sort(Comparator
                    .comparingLong((ItemData item) -> item.totalValue).reversed()
                    .thenComparing(item -> item.name, String.CASE_INSENSITIVE_ORDER));
        } else {
            sorted.sort(Comparator.comparing(item -> item.name, String.CASE_INSENSITIVE_ORDER));
        }
        return sorted;
    }

    static List<ItemAggregate> sortAggregatesForDisplay(
            java.util.Collection<ItemAggregate> items,
            MokhaDisplaySortMode sortMode) {
        List<ItemAggregate> sorted = new ArrayList<>(items);
        if (sortMode == MokhaDisplaySortMode.VALUE_DESC) {
            sorted.sort(Comparator
                    .comparingLong((ItemAggregate item) -> item.totalValue).reversed()
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

    static boolean isUniqueLootItem(ItemData item) {
        if (item == null) {
            return false;
        }

        return UNIQUE_ITEM_NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(item.name));
    }

    private static String colorToHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
}
