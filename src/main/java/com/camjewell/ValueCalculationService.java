package com.camjewell;

import java.util.List;
import java.util.Map;

class ValueCalculationService {
    private static final int ULTRA_VALUABLE_THRESHOLD = 20_000_000;

    long calculateUnadjustedCurrentRunLootValue(Map<Integer, List<LootItem>> lootByWave) {
        long total = 0;
        for (List<LootItem> items : lootByWave.values()) {
            for (LootItem item : items) {
                total += item.value;
            }
        }
        return total;
    }

    long calculateCurrentRunLootValue(
            Map<Integer, List<LootItem>> lootByWave,
            MokhaLootTrackerConfig config) {
        long currentRunValue = 0;
        for (List<LootItem> items : lootByWave.values()) {
            for (LootItem item : items) {
                currentRunValue += getAdjustedLootItemValue(item.name, item.value, config);
            }
        }
        return currentRunValue;
    }

    long calculateCurrentRunLootHaValue(
            Map<Integer, List<LootItem>> lootByWave,
            MokhaLootTrackerConfig config) {
        long currentRunValue = 0;
        for (List<LootItem> items : lootByWave.values()) {
            for (LootItem item : items) {
                currentRunValue += getAdjustedLootItemValue(item.name, item.haValue, config);
            }
        }
        return currentRunValue;
    }

    long getAdjustedLootItemValue(String itemName, long trackedValue, MokhaLootTrackerConfig config) {
        return shouldIgnoreLootItem(itemName, config) ? 0 : trackedValue;
    }

    boolean shouldIgnoreLootItem(String itemName, MokhaLootTrackerConfig config) {
        return config.ignoreSpiritSeedsValue() && itemName.equals("Spirit seed");
    }

    void applyIgnoreSettingsToHistoricalItems(
            Map<Integer, Map<String, ItemAggregate>> historicalItems,
            MokhaLootTrackerConfig config) {
        for (Map<String, ItemAggregate> waveItems : historicalItems.values()) {
            for (ItemAggregate item : waveItems.values()) {
                if (item.name.equals("Spirit seed")) {
                    item.totalValue = config.ignoreSpiritSeedsValue() ? 0 : 140000L * item.totalQuantity;
                }
            }
        }
    }

    long recalculateHistoricalTotalClaimed(
            Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave,
            MokhaLootTrackerConfig config) {
        long total = 0;
        boolean excludeUltra = config.excludeUltraValuableItems();

        for (Map<String, ItemAggregate> waveItems : historicalClaimedItemsByWave.values()) {
            for (ItemAggregate item : waveItems.values()) {
                if (excludeUltra && item.pricePerItem > ULTRA_VALUABLE_THRESHOLD) {
                    continue;
                }
                total += item.totalValue;
            }
        }

        return total;
    }

    long calculateTotalUnclaimed(
            Map<Integer, Map<String, ItemAggregate>> historicalUnclaimedItemsByWave,
            Map<Integer, Long> historicalUnclaimedByWave,
            MokhaLootTrackerConfig config) {
        long totalUnclaimed = 0;

        if (config.excludeUltraValuableItems()) {
            for (Map<String, ItemAggregate> waveItems : historicalUnclaimedItemsByWave
                    .values()) {
                for (ItemAggregate item : waveItems.values()) {
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
            Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave,
            Map<Integer, Map<String, ItemAggregate>> historicalUnclaimedItemsByWave,
            Map<Integer, Long> historicalClaimedByWave,
            Map<Integer, Long> historicalUnclaimedByWave,
            MokhaLootTrackerConfig config) {
        boolean excludeUltra = config.excludeUltraValuableItems();

        historicalClaimedByWave.clear();
        for (Map.Entry<Integer, Map<String, ItemAggregate>> waveEntry : historicalClaimedItemsByWave
                .entrySet()) {
            long waveTotal = 0;
            for (ItemAggregate item : waveEntry.getValue().values()) {
                if (excludeUltra && item.pricePerItem > ULTRA_VALUABLE_THRESHOLD) {
                    continue;
                }
                waveTotal += item.totalValue;
            }
            historicalClaimedByWave.put(waveEntry.getKey(), waveTotal);
        }

        historicalUnclaimedByWave.clear();
        for (Map.Entry<Integer, Map<String, ItemAggregate>> waveEntry : historicalUnclaimedItemsByWave
                .entrySet()) {
            long waveTotal = 0;
            for (ItemAggregate item : waveEntry.getValue().values()) {
                if (excludeUltra && item.pricePerItem > ULTRA_VALUABLE_THRESHOLD) {
                    continue;
                }
                waveTotal += item.totalValue;
            }
            historicalUnclaimedByWave.put(waveEntry.getKey(), waveTotal);
        }
    }

    long calculateHistoricalUniqueClaimCount(
            Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave) {
        long uniqueCount = 0;

        for (Map<String, ItemAggregate> waveItems : historicalClaimedItemsByWave.values()) {
            for (ItemAggregate item : waveItems.values()) {
                if (LootPanelDisplayUtils.UNIQUE_ITEM_NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(item.name))) {
                    uniqueCount += item.totalQuantity;
                }
            }
        }

        return uniqueCount;
    }
}
