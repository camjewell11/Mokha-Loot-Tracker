package com.camjewell;

import java.util.HashMap;
import java.util.Map;

final class HistoricalAggregateCombiner {
    private HistoricalAggregateCombiner() {
    }

    static CombinedAggregateResult combine(Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> byWave) {
        Map<String, MokhaLootTrackerPlugin.ItemAggregate> combined = new HashMap<>();
        long totalValue = 0;
        long totalHaValue = 0;

        if (byWave != null) {
            Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> waveSnapshot = new HashMap<>(byWave);
            for (Map.Entry<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> waveEntry : waveSnapshot
                    .entrySet()) {
                Map<String, MokhaLootTrackerPlugin.ItemAggregate> waveMap = waveEntry.getValue();
                if (waveMap == null) {
                    continue;
                }

                for (Map.Entry<String, MokhaLootTrackerPlugin.ItemAggregate> itemEntry : new HashMap<>(waveMap)
                        .entrySet()) {
                    MokhaLootTrackerPlugin.ItemAggregate aggregate = itemEntry.getValue();
                    if (aggregate == null) {
                        continue;
                    }

                    MokhaLootTrackerPlugin.ItemAggregate existing = combined.get(aggregate.name);
                    if (existing == null) {
                        MokhaLootTrackerPlugin.ItemAggregate copy = new MokhaLootTrackerPlugin.ItemAggregate(
                                aggregate.name,
                                aggregate.totalQuantity,
                                aggregate.pricePerItem,
                                aggregate.haPricePerItem);
                        copy.totalValue = aggregate.totalValue;
                        copy.totalHaValue = aggregate.totalHaValue;
                        combined.put(aggregate.name, copy);
                    } else {
                        existing.totalQuantity += aggregate.totalQuantity;
                        existing.totalValue += aggregate.totalValue;
                        existing.totalHaValue += aggregate.totalHaValue;
                        existing.haPricePerItem = aggregate.haPricePerItem;
                    }

                    totalValue += aggregate.totalValue;
                    totalHaValue += aggregate.totalHaValue;
                }
            }
        }

        return new CombinedAggregateResult(combined, totalValue, totalHaValue);
    }

    static final class CombinedAggregateResult {
        private final Map<String, MokhaLootTrackerPlugin.ItemAggregate> combined;
        private final long totalValue;
        private final long totalHaValue;

        CombinedAggregateResult(Map<String, MokhaLootTrackerPlugin.ItemAggregate> combined, long totalValue,
                long totalHaValue) {
            this.combined = combined;
            this.totalValue = totalValue;
            this.totalHaValue = totalHaValue;
        }

        Map<String, MokhaLootTrackerPlugin.ItemAggregate> getCombined() {
            return combined;
        }

        long getTotalValue() {
            return totalValue;
        }

        long getTotalHaValue() {
            return totalHaValue;
        }
    }
}
