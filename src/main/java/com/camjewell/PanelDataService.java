package com.camjewell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

class PanelDataService {
    static final class RunPanelData {
        long totalValue;
        long totalHaValue;
        Map<String, ItemData> items = new HashMap<>();
        Map<Integer, Map<String, ItemData>> itemsByWave = new TreeMap<>();
        Map<Integer, Long> totalsByWave = new TreeMap<>();
        Map<Integer, Long> haTotalsByWave = new TreeMap<>();
    }

    static final class PanelData {
        long currentRunValue;
        long totalUnclaimed;
        long currentSuppliesTotalValue;
        long historicalSuppliesTotalValue;

        Map<String, ItemData> currentRunItems = new HashMap<>();
        Map<Integer, Map<String, ItemData>> currentRunItemsByWave = new HashMap<>();
        Map<Integer, Long> currentRunTotalsByWave = new HashMap<>();
        Map<Integer, Long> currentRunHaTotalsByWave = new HashMap<>();
        Map<String, ItemData> currentSuppliesData = new HashMap<>();
        Map<String, ItemData> historicalSuppliesData = new HashMap<>();

        Map<Integer, Map<String, ItemData>> claimedItemsByWave = new HashMap<>();
        Map<Integer, Long> claimedTotalsByWave = new HashMap<>();

        Map<Integer, Map<String, ItemData>> unclaimedItemsByWave = new HashMap<>();
        Map<Integer, Long> unclaimedTotalsByWave = new HashMap<>();
    }

    static final class SuppliesPanelData {
        long currentSuppliesTotalValue;
        long historicalSuppliesTotalValue;
        Map<String, ItemData> currentSuppliesData = new HashMap<>();
        Map<String, ItemData> historicalSuppliesData = new HashMap<>();
    }

    PanelData buildPanelData(
            Map<Integer, List<LootItem>> lootByWave,
            Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave,
            Map<Integer, Long> historicalClaimedByWave,
            Map<Integer, Map<String, ItemAggregate>> historicalUnclaimedItemsByWave,
            Map<Integer, Long> historicalUnclaimedByWave,
            Map<Integer, Integer> totalSuppliesConsumed,
            Map<String, ItemAggregate> historicalSuppliesUsed,
            MokhaLootTrackerConfig config,
            ValueCalculationService valueCalculationService,
            IntFunction<String> getBasePotionNameByItemId,
            IntUnaryOperator getPricePerDoseByItemId,
            IntUnaryOperator getMaxDoseByItemId) {
        PanelData data = new PanelData();

        data.currentRunValue = valueCalculationService.calculateCurrentRunLootValue(lootByWave, config);
        RunPanelData currentRunData = buildRunPanelData(lootByWave, config, valueCalculationService);
        data.currentRunValue = currentRunData.totalValue;
        data.currentRunItems = currentRunData.items;
        data.currentRunItemsByWave = currentRunData.itemsByWave;
        data.currentRunTotalsByWave = currentRunData.totalsByWave;
        data.currentRunHaTotalsByWave = currentRunData.haTotalsByWave;
        data.totalUnclaimed = valueCalculationService.calculateTotalUnclaimed(
                historicalUnclaimedItemsByWave,
                historicalUnclaimedByWave,
                config);

        for (int wave = 1; wave <= 10; wave++) {
            Map<String, ItemData> claimedItems = buildWaveItemData(
                    wave,
                    historicalClaimedItemsByWave,
                    true);
            claimedItems = sortItemDataMapForDisplay(claimedItems, config);
            long claimedWaveTotal = calculateClaimedWaveTotal(
                    wave,
                    historicalClaimedByWave,
                    historicalClaimedItemsByWave);
            data.claimedItemsByWave.put(wave, claimedItems);
            data.claimedTotalsByWave.put(wave, claimedWaveTotal);

            Map<String, ItemData> unclaimedItems = buildWaveItemData(
                    wave,
                    historicalUnclaimedItemsByWave,
                    false);
            unclaimedItems = sortItemDataMapForDisplay(unclaimedItems, config);
            long unclaimedWaveTotal = historicalUnclaimedByWave.getOrDefault(wave, 0L);
            data.unclaimedItemsByWave.put(wave, unclaimedItems);
            data.unclaimedTotalsByWave.put(wave, unclaimedWaveTotal);
        }

        SuppliesPanelData suppliesData = buildSuppliesPanelData(
                totalSuppliesConsumed,
                historicalSuppliesUsed,
                getBasePotionNameByItemId,
                getPricePerDoseByItemId,
                getMaxDoseByItemId);
        data.currentSuppliesTotalValue = suppliesData.currentSuppliesTotalValue;
        data.historicalSuppliesTotalValue = suppliesData.historicalSuppliesTotalValue;
        data.currentSuppliesData = suppliesData.currentSuppliesData;
        data.historicalSuppliesData = suppliesData.historicalSuppliesData;

        return data;
    }

    RunPanelData buildRunPanelData(
            Map<Integer, List<LootItem>> lootByWave,
            MokhaLootTrackerConfig config,
            ValueCalculationService valueCalculationService) {
        RunPanelData data = new RunPanelData();

        for (List<LootItem> waveItems : lootByWave.values()) {
            for (LootItem item : waveItems) {
                long itemValue = valueCalculationService.getAdjustedLootItemValue(item.name, item.value, config);
                long itemHaValue = valueCalculationService.getAdjustedLootItemValue(item.name, item.haValue, config);

                ItemData itemData = data.items.getOrDefault(item.name,
                        new ItemData(item.name, 0, 0, 0, 0, 0));
                itemData.quantity += item.quantity;
                itemData.totalValue += itemValue;
                itemData.totalHaValue += itemHaValue;
                if (item.quantity > 0) {
                    itemData.pricePerItem = (int) (itemValue / item.quantity);
                    itemData.haPricePerItem = (int) (itemHaValue / item.quantity);
                }
                data.items.put(item.name, itemData);
                data.totalValue += itemValue;
                data.totalHaValue += itemHaValue;
            }
        }

        data.items = sortItemDataMapForDisplay(data.items, config);

        for (Map.Entry<Integer, List<LootItem>> waveEntry : lootByWave.entrySet()) {
            int wave = waveEntry.getKey();
            Map<String, ItemData> waveItems = new HashMap<>();
            long waveTotal = 0;
            long waveHaTotal = 0;

            for (LootItem item : waveEntry.getValue()) {
                long itemValue = valueCalculationService.getAdjustedLootItemValue(item.name, item.value, config);
                long itemHaValue = valueCalculationService.getAdjustedLootItemValue(item.name, item.haValue, config);

                ItemData itemData = waveItems.getOrDefault(item.name,
                        new ItemData(item.name, 0, 0, 0, 0, 0));
                itemData.quantity += item.quantity;
                itemData.totalValue += itemValue;
                itemData.totalHaValue += itemHaValue;
                if (item.quantity > 0) {
                    itemData.pricePerItem = (int) (itemValue / item.quantity);
                    itemData.haPricePerItem = (int) (itemHaValue / item.quantity);
                }
                waveItems.put(item.name, itemData);

                waveTotal += itemValue;
                waveHaTotal += itemHaValue;
            }

            data.itemsByWave.put(wave, sortItemDataMapForDisplay(waveItems, config));
            data.totalsByWave.put(wave, waveTotal);
            data.haTotalsByWave.put(wave, waveHaTotal);
        }

        return data;
    }

    SuppliesPanelData buildSuppliesPanelData(
            Map<Integer, Integer> totalSuppliesConsumed,
            Map<String, ItemAggregate> historicalSuppliesUsed,
            IntFunction<String> getBasePotionNameByItemId,
            IntUnaryOperator getPricePerDoseByItemId,
            IntUnaryOperator getMaxDoseByItemId) {
        SuppliesPanelData data = new SuppliesPanelData();

        for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            String baseName = getBasePotionNameByItemId.apply(itemId);
            int pricePerItem = getPricePerDoseByItemId.applyAsInt(itemId);
            int maxDose = getMaxDoseByItemId.applyAsInt(itemId);
            String displayName = maxDose > 0 ? baseName + " (" + maxDose + ")" : baseName;
            long totalValue = (long) pricePerItem * quantity;
            data.currentSuppliesTotalValue += totalValue;

            if (data.currentSuppliesData.containsKey(baseName)) {
                ItemData existing = data.currentSuppliesData.get(baseName);
                ItemData merged = new ItemData(displayName, existing.quantity + quantity, pricePerItem,
                        existing.totalValue + totalValue);
                merged.maxDosesForDisplay = maxDose;
                data.currentSuppliesData.put(baseName, merged);
            } else {
                ItemData itemData = new ItemData(displayName, quantity, pricePerItem, totalValue);
                itemData.maxDosesForDisplay = maxDose;
                data.currentSuppliesData.put(baseName, itemData);
            }
        }

        for (ItemAggregate agg : historicalSuppliesUsed.values()) {
            String displayName = agg.maxDosesForDisplay > 0
                    ? agg.name + " (" + agg.maxDosesForDisplay + ")"
                    : agg.name;
            ItemData itemData = new ItemData(displayName, agg.totalQuantity, agg.pricePerItem, agg.totalValue);
            itemData.tooltipText = agg.tooltipText;
            itemData.maxDosesForDisplay = agg.maxDosesForDisplay;
            data.historicalSuppliesData.put(agg.name, itemData);
            data.historicalSuppliesTotalValue += agg.totalValue;
        }

        return data;
    }

    private Map<String, ItemData> buildWaveItemData(
            int wave,
            Map<Integer, Map<String, ItemAggregate>> source,
            boolean combineNinePlus) {
        Map<String, ItemData> itemData = new HashMap<>();

        if (wave <= 9 || !combineNinePlus) {
            Map<String, ItemAggregate> waveItems = source.getOrDefault(wave, new HashMap<>());
            for (ItemAggregate agg : waveItems.values()) {
                mergeItemData(itemData, agg);
            }
            return itemData;
        }

        for (Map.Entry<Integer, Map<String, ItemAggregate>> waveEntry : source.entrySet()) {
            if (waveEntry.getKey() < 9) {
                continue;
            }
            for (ItemAggregate agg : waveEntry.getValue().values()) {
                mergeItemData(itemData, agg);
            }
        }

        return itemData;
    }

    private long calculateClaimedWaveTotal(
            int wave,
            Map<Integer, Long> historicalClaimedByWave,
            Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave) {
        if (wave <= 9) {
            return historicalClaimedByWave.getOrDefault(wave, 0L);
        }

        long total = 0;
        for (Map.Entry<Integer, Long> waveTotalEntry : historicalClaimedByWave.entrySet()) {
            if (waveTotalEntry.getKey() >= 9) {
                total += waveTotalEntry.getValue();
            }
        }

        if (total > 0) {
            return total;
        }

        for (Map.Entry<Integer, Map<String, ItemAggregate>> waveEntry : historicalClaimedItemsByWave
                .entrySet()) {
            if (waveEntry.getKey() < 9) {
                continue;
            }
            for (ItemAggregate agg : waveEntry.getValue().values()) {
                total += agg.totalValue;
            }
        }

        return total;
    }

    private void mergeItemData(Map<String, ItemData> itemData,
            ItemAggregate agg) {
        if (itemData.containsKey(agg.name)) {
            ItemData existing = itemData.get(agg.name);
            itemData.put(agg.name, new ItemData(
                    agg.name,
                    existing.quantity + agg.totalQuantity,
                    agg.pricePerItem,
                    existing.totalValue + agg.totalValue,
                    agg.haPricePerItem,
                    existing.totalHaValue + agg.totalHaValue));
        } else {
            itemData.put(agg.name,
                    new ItemData(agg.name, agg.totalQuantity, agg.pricePerItem, agg.totalValue,
                            agg.haPricePerItem, agg.totalHaValue));
        }
    }

    private Map<String, ItemData> sortItemDataMapForDisplay(
            Map<String, ItemData> itemData,
            MokhaLootTrackerConfig config) {
        List<ItemData> sortedItems = new ArrayList<>(itemData.values());

        if (config.displaySortMode() == MokhaDisplaySortMode.VALUE_DESC) {
            sortedItems.sort(Comparator
                    .comparingLong((ItemData item) -> item.totalValue).reversed()
                    .thenComparing(item -> item.name, String.CASE_INSENSITIVE_ORDER));
        } else {
            sortedItems.sort(Comparator.comparing(item -> item.name, String.CASE_INSENSITIVE_ORDER));
        }

        Map<String, ItemData> sortedMap = new LinkedHashMap<>();
        for (ItemData item : sortedItems) {
            sortedMap.put(item.name, item);
        }
        return sortedMap;
    }
}
