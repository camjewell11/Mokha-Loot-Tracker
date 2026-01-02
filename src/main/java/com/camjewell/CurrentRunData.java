package com.camjewell;

import java.util.List;

public class CurrentRunData {
    private final long filteredValue;
    private final long fullValue;
    private final List<LootItem> items;

    public CurrentRunData(long filteredValue, long fullValue, List<LootItem> items) {
        this.filteredValue = filteredValue;
        this.fullValue = fullValue;
        this.items = items;
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
