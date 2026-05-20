package com.camjewell;

import java.util.List;
import java.util.Map;

class ValueCalculationService {
    private static final int ULTRA_VALUABLE_THRESHOLD = 20_000_000;

    long calculateUnadjustedCurrentRunLootValue(Map<Integer, List<MokhaLootTrackerPlugin.LootItem>> lootByWave) {
        long total = 0;
        for (List<MokhaLootTrackerPlugin.LootItem> items : lootByWave.values()) {
            for (MokhaLootTrackerPlugin.LootItem item : items) {
                total += item.value;
            }
        }
        return total;
    }

    long calculateCurrentRunLootValue(
            Map<Integer, List<MokhaLootTrackerPlugin.LootItem>> lootByWave,
            MokhaLootTrackerConfig config) {
        long currentRunValue = 0;
        for (List<MokhaLootTrackerPlugin.LootItem> items : lootByWave.values()) {
            for (MokhaLootTrackerPlugin.LootItem item : items) {
                currentRunValue += getAdjustedLootItemValue(item.name, item.value, config);
            }
        }
        return currentRunValue;
    }

    long getAdjustedLootItemValue(String itemName, long trackedValue, MokhaLootTrackerConfig config) {
        return shouldIgnoreLootItem(itemName, config) ? 0 : trackedValue;
    }

    boolean shouldIgnoreLootItem(String itemName, MokhaLootTrackerConfig config) {
        return (config.ignoreSpiritSeedsValue() && itemName.equals("Spirit seed")) ||
                (config.ignoreSunKissedBonesValue() && itemName.equals("Sun-kissed bones"));
    }

    void applyIgnoreSettingsToHistoricalItems(
            Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalItems,
            MokhaLootTrackerConfig config) {
        for (Map<String, MokhaLootTrackerPlugin.ItemAggregate> waveItems : historicalItems.values()) {
            for (MokhaLootTrackerPlugin.ItemAggregate item : waveItems.values()) {
                if (item.name.equals("Spirit seed")) {
                    item.totalValue = config.ignoreSpiritSeedsValue() ? 0 : 140000L * item.totalQuantity;
                } else if (item.name.equals("Sun-kissed bones")) {
                    item.totalValue = config.ignoreSunKissedBonesValue() ? 0 : 8000L * item.totalQuantity;
                }
            }
        }
    }

    long recalculateHistoricalTotalClaimed(
            Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalClaimedItemsByWave,
            MokhaLootTrackerConfig config) {
        long total = 0;
        boolean excludeUltra = config.excludeUltraValuableItems();

        for (Map<String, MokhaLootTrackerPlugin.ItemAggregate> waveItems : historicalClaimedItemsByWave.values()) {
            for (MokhaLootTrackerPlugin.ItemAggregate item : waveItems.values()) {
                if (excludeUltra && item.pricePerItem > ULTRA_VALUABLE_THRESHOLD) {
                    continue;
                }
                total += item.totalValue;
            }
        }

        return total;
    }

    long calculateTotalUnclaimed(
            Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalUnclaimedItemsByWave,
            Map<Integer, Long> historicalUnclaimedByWave,
            MokhaLootTrackerConfig config) {
        long totalUnclaimed = 0;

        if (config.excludeUltraValuableItems()) {
            for (Map<String, MokhaLootTrackerPlugin.ItemAggregate> waveItems : historicalUnclaimedItemsByWave
                    .values()) {
                for (MokhaLootTrackerPlugin.ItemAggregate item : waveItems.values()) {
                    if (item.pricePerItem <= ULTRA_VALUABLE_THRESHOLD) {
                        totalUnclaimed += item.totalValue;
                    }
                }
            }
        } else {
            for (Long waveValue : historicalUnclaimedByWave.values()) {
                totalUnclaimed += waveValue;
            }
        }

        return totalUnclaimed;
    }

    void recalculateWaveTotals(
            Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalClaimedItemsByWave,
            Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalUnclaimedItemsByWave,
            Map<Integer, Long> historicalClaimedByWave,
            Map<Integer, Long> historicalUnclaimedByWave,
            MokhaLootTrackerConfig config) {
        boolean excludeUltra = config.excludeUltraValuableItems();

        historicalClaimedByWave.clear();
        for (Map.Entry<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> waveEntry : historicalClaimedItemsByWave
                .entrySet()) {
            long waveTotal = 0;
            for (MokhaLootTrackerPlugin.ItemAggregate item : waveEntry.getValue().values()) {
                if (excludeUltra && item.pricePerItem > ULTRA_VALUABLE_THRESHOLD) {
                    continue;
                }
                waveTotal += item.totalValue;
            }
            historicalClaimedByWave.put(waveEntry.getKey(), waveTotal);
        }

        historicalUnclaimedByWave.clear();
        for (Map.Entry<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> waveEntry : historicalUnclaimedItemsByWave
                .entrySet()) {
            long waveTotal = 0;
            for (MokhaLootTrackerPlugin.ItemAggregate item : waveEntry.getValue().values()) {
                if (excludeUltra && item.pricePerItem > ULTRA_VALUABLE_THRESHOLD) {
                    continue;
                }
                waveTotal += item.totalValue;
            }
            historicalUnclaimedByWave.put(waveEntry.getKey(), waveTotal);
        }
    }
}
