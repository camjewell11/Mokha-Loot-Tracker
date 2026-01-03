package com.camjewell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class LootTrackingState {
    private static final int MAX_TRACKED_WAVES = 9;

    private int currentDelveNumber = 0;
    private int previousDelveNumber = 0;
    private List<LootItem> currentUnclaimedLoot = new ArrayList<>();
    private List<LootItem> previousWaveItems = new ArrayList<>();
    private long currentLootValue = 0;
    private long previousWaveLootValue = 0;
    private long[] waveLootValues = new long[MAX_TRACKED_WAVES + 1];
    @SuppressWarnings("unchecked")
    private List<LootItem>[] waveItemStacks = new List[MAX_TRACKED_WAVES + 1];

    private long lastVisibleLootValue = 0;
    private List<LootItem> lastVisibleLootItems = new ArrayList<>();
    private int lastVisibleDelveNumber = 0;
    private long lastPanelUpdateValue = -1;

    LootTrackingState() {
        for (int i = 0; i <= MAX_TRACKED_WAVES; i++) {
            waveItemStacks[i] = new ArrayList<>();
        }
    }

    int getCurrentDelveNumber() {
        return currentDelveNumber;
    }

    void setCurrentDelveNumber(int delveNumber) {
        this.previousDelveNumber = this.currentDelveNumber;
        this.currentDelveNumber = delveNumber;
    }

    int getPreviousDelveNumber() {
        return previousDelveNumber;
    }

    List<LootItem> getCurrentUnclaimedLoot() {
        return currentUnclaimedLoot;
    }

    void setCurrentUnclaimedLoot(List<LootItem> items) {
        this.currentUnclaimedLoot.clear();
        if (items != null) {
            this.currentUnclaimedLoot.addAll(items);
        }
    }

    long getCurrentLootValue() {
        return currentLootValue;
    }

    void setCurrentLootValue(long value) {
        this.currentLootValue = value;
    }

    List<LootItem> getPreviousWaveItems() {
        return previousWaveItems;
    }

    long getPreviousWaveLootValue() {
        return previousWaveLootValue;
    }

    void updateWaveTracking(int waveIndex, long incrementalValue, List<LootItem> incrementalItems) {
        if (currentDelveNumber > MAX_TRACKED_WAVES) {
            // Wave 9+: accumulate
            waveLootValues[waveIndex] += incrementalValue;
            if (waveItemStacks[waveIndex] == null) {
                waveItemStacks[waveIndex] = new ArrayList<>();
            }
            // Merge items
            Map<Integer, Integer> itemMap = new HashMap<>();
            for (LootItem item : waveItemStacks[waveIndex]) {
                itemMap.put(item.getId(), itemMap.getOrDefault(item.getId(), 0) + item.getQuantity());
            }
            for (LootItem item : incrementalItems) {
                itemMap.put(item.getId(), itemMap.getOrDefault(item.getId(), 0) + item.getQuantity());
            }
            waveItemStacks[waveIndex] = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : itemMap.entrySet()) {
                waveItemStacks[waveIndex].add(new LootItem(entry.getKey(), entry.getValue(), null));
            }
        } else {
            // Regular wave: overwrite
            waveLootValues[waveIndex] = incrementalValue;
            waveItemStacks[waveIndex] = new ArrayList<>(incrementalItems);
        }

        previousWaveLootValue = currentLootValue;
        previousWaveItems = new ArrayList<>(currentUnclaimedLoot);
    }

    long getWaveValue(int wave) {
        if (wave >= 1 && wave <= MAX_TRACKED_WAVES) {
            return waveLootValues[wave];
        }
        return 0;
    }

    List<LootItem> getWaveItems(int wave) {
        if (wave >= 1 && wave <= MAX_TRACKED_WAVES && waveItemStacks[wave] != null) {
            return new ArrayList<>(waveItemStacks[wave]);
        }
        return new ArrayList<>();
    }

    long getLastVisibleLootValue() {
        return lastVisibleLootValue;
    }

    void setLastVisibleLootValue(long value) {
        this.lastVisibleLootValue = value;
    }

    List<LootItem> getLastVisibleLootItems() {
        return lastVisibleLootItems;
    }

    void setLastVisibleLootItems(List<LootItem> items) {
        this.lastVisibleLootItems.clear();
        if (items != null) {
            this.lastVisibleLootItems.addAll(items);
        }
    }

    int getLastVisibleDelveNumber() {
        return lastVisibleDelveNumber;
    }

    void setLastVisibleDelveNumber(int delveNumber) {
        this.lastVisibleDelveNumber = delveNumber;
    }

    long getLastPanelUpdateValue() {
        return lastPanelUpdateValue;
    }

    void setLastPanelUpdateValue(long value) {
        this.lastPanelUpdateValue = value;
    }

    List<LootItem> calculateIncrementalItems(List<LootItem> currentItems, List<LootItem> previousItems) {
        Map<Integer, Integer> previousItemMap = new HashMap<>();
        for (LootItem item : previousItems) {
            previousItemMap.put(item.getId(), item.getQuantity());
        }

        List<LootItem> incrementalItems = new ArrayList<>();
        for (LootItem currentItem : currentItems) {
            int itemId = currentItem.getId();
            int currentQuantity = currentItem.getQuantity();
            int previousQuantity = previousItemMap.getOrDefault(itemId, 0);

            int incrementalQuantity = currentQuantity - previousQuantity;
            if (incrementalQuantity > 0) {
                incrementalItems.add(new LootItem(itemId, incrementalQuantity, null));
            }
        }

        return incrementalItems;
    }

    void reset() {
        currentUnclaimedLoot.clear();
        previousWaveItems.clear();
        currentLootValue = 0;
        previousWaveLootValue = 0;
        waveLootValues = new long[MAX_TRACKED_WAVES + 1];
        waveItemStacks = new List[MAX_TRACKED_WAVES + 1];
        for (int i = 0; i <= MAX_TRACKED_WAVES; i++) {
            waveItemStacks[i] = new ArrayList<>();
        }
        currentDelveNumber = 0;
        previousDelveNumber = 0;
        lastPanelUpdateValue = -1;
    }
}
