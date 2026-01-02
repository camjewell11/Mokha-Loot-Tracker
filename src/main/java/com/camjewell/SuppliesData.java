package com.camjewell;

import java.util.List;

public class SuppliesData {
    private final long liveValue;
    private final long historicalValue;
    private final List<LootItem> liveItems;
    private final List<LootItem> historicalItems;

    public SuppliesData(long liveValue,
            long historicalValue,
            List<LootItem> liveItems,
            List<LootItem> historicalItems) {
        this.liveValue = liveValue;
        this.historicalValue = historicalValue;
        this.liveItems = liveItems;
        this.historicalItems = historicalItems;
    }

    public long getLiveValue() {
        return liveValue;
    }

    public long getHistoricalValue() {
        return historicalValue;
    }

    public List<LootItem> getLiveItems() {
        return liveItems;
    }

    public List<LootItem> getHistoricalItems() {
        return historicalItems;
    }
}
