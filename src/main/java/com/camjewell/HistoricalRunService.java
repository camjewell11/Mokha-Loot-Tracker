package com.camjewell;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

class HistoricalRunService {
    long applyClaimedLoot(
            Map<Integer, List<MokhaLootTrackerPlugin.LootItem>> lootByWave,
            Map<Integer, Long> historicalClaimedByWave,
            Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalClaimedItemsByWave) {
        long claimedValue = 0L;

        for (Map.Entry<Integer, List<MokhaLootTrackerPlugin.LootItem>> entry : lootByWave.entrySet()) {
            int wave = entry.getKey();
            long waveValue = 0L;
            int waveIndex = wave > 9 ? 9 : wave;

            Map<String, MokhaLootTrackerPlugin.ItemAggregate> waveItems = historicalClaimedItemsByWave
                    .computeIfAbsent(waveIndex, k -> new HashMap<>());

            for (MokhaLootTrackerPlugin.LootItem item : entry.getValue()) {
                waveValue += item.value;
                int pricePerItem = item.quantity > 0 ? item.value / item.quantity : 0;
                int haPricePerItem = item.quantity > 0 ? item.haValue / item.quantity : 0;

                if (waveItems.containsKey(item.name)) {
                    waveItems.get(item.name).add(item.quantity, pricePerItem, haPricePerItem);
                } else {
                    waveItems.put(item.name,
                            new MokhaLootTrackerPlugin.ItemAggregate(item.name, item.quantity, pricePerItem,
                                    haPricePerItem));
                }
            }

            claimedValue += waveValue;
            historicalClaimedByWave.put(waveIndex,
                    historicalClaimedByWave.getOrDefault(waveIndex, 0L) + waveValue);
        }

        return claimedValue;
    }

    void moveCurrentRunUnclaimedToHistorical(
            Map<Integer, List<MokhaLootTrackerPlugin.LootItem>> lootByWave,
            Map<Integer, Long> historicalUnclaimedByWave,
            Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalUnclaimedItemsByWave) {
        for (int wave = 1; wave <= 20; wave++) {
            List<MokhaLootTrackerPlugin.LootItem> items = lootByWave.get(wave);
            if (items == null || items.isEmpty()) {
                continue;
            }

            long currentWaveTotal = historicalUnclaimedByWave.getOrDefault(wave, 0L);
            long newTotal = currentWaveTotal;
            Map<String, MokhaLootTrackerPlugin.ItemAggregate> waveItems = historicalUnclaimedItemsByWave
                    .getOrDefault(wave, new HashMap<>());

            for (MokhaLootTrackerPlugin.LootItem item : items) {
                newTotal += item.value;
                int pricePerItem = item.quantity > 0 ? item.value / item.quantity : 0;
                int haPricePerItem = item.quantity > 0 ? item.haValue / item.quantity : 0;

                if (waveItems.containsKey(item.name)) {
                    waveItems.get(item.name).add(item.quantity, pricePerItem, haPricePerItem);
                } else {
                    waveItems.put(item.name,
                            new MokhaLootTrackerPlugin.ItemAggregate(item.name, item.quantity, pricePerItem,
                                    haPricePerItem));
                }
            }

            historicalUnclaimedByWave.put(wave, newTotal);
            historicalUnclaimedItemsByWave.put(wave, waveItems);
        }
    }

    boolean restoreCurrentRunLootJsonAsUnclaimed(
            String currentRunJson,
            Gson gson,
            Map<Integer, List<MokhaLootTrackerPlugin.LootItem>> lootByWave,
            Map<Integer, Long> historicalUnclaimedByWave,
            Map<Integer, Map<String, MokhaLootTrackerPlugin.ItemAggregate>> historicalUnclaimedItemsByWave) {
        if (currentRunJson == null || currentRunJson.isEmpty() || currentRunJson.equals("{}")) {
            return false;
        }

        Type type = new TypeToken<Map<Integer, List<MokhaLootTrackerPlugin.LootItem>>>() {
        }.getType();
        Map<Integer, List<MokhaLootTrackerPlugin.LootItem>> loaded = gson.fromJson(currentRunJson, type);
        if (loaded == null || loaded.isEmpty()) {
            return false;
        }

        lootByWave.putAll(loaded);
        moveCurrentRunUnclaimedToHistorical(lootByWave, historicalUnclaimedByWave, historicalUnclaimedItemsByWave);
        lootByWave.clear();
        return true;
    }
}
