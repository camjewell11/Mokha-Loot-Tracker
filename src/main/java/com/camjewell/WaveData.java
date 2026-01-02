package com.camjewell;

import java.util.List;

public class WaveData {
    private final int waveNumber;
    private final long filteredValue;
    private final long fullValue;
    private final List<LootItem> items;

    public WaveData(int waveNumber, long filteredValue, long fullValue, List<LootItem> items) {
        this.waveNumber = waveNumber;
        this.filteredValue = filteredValue;
        this.fullValue = fullValue;
        this.items = items;
    }

    public int getWaveNumber() {
        return waveNumber;
    }

    public long getFilteredValue() {
        return filteredValue;
    }

    public long getFullValue() {
        return fullValue;
    }

    public List<LootItem> getItems() {
        return items;
    }
}
