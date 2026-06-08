package com.camjewell;

public class ItemAggregate {
    String name;
    int totalQuantity;
    int pricePerItem;
    int haPricePerItem;
    long totalValue;
    long totalHaValue;
    /** Optional hover tooltip override (e.g. charge ingredient breakdown). Null = use default. */
    String tooltipText;
    /** For dose-based consumables: max doses per full item (e.g. 4 for Prayer potion). 0 = not dose-based. */
    int maxDosesForDisplay;

    ItemAggregate(String name, int quantity, int pricePerItem) {
        this(name, quantity, pricePerItem, 0);
    }

    ItemAggregate(String name, int quantity, int pricePerItem, int haPricePerItem) {
        this.name = name;
        this.totalQuantity = quantity;
        this.pricePerItem = pricePerItem;
        this.haPricePerItem = haPricePerItem;
        this.totalValue = (long) pricePerItem * quantity;
        this.totalHaValue = (long) haPricePerItem * quantity;
    }

    ItemAggregate(String name, int quantity, long totalValue, String tooltipText) {
        this.name = name;
        this.totalQuantity = quantity;
        this.totalValue = totalValue;
        this.pricePerItem = quantity > 0 ? (int) (totalValue / quantity) : 0;
        this.tooltipText = tooltipText;
    }

    void add(int quantity, int pricePerItem) {
        add(quantity, pricePerItem, this.haPricePerItem);
    }

    void add(int quantity, int pricePerItem, int haPricePerItem) {
        this.totalQuantity += quantity;
        this.totalValue += (long) pricePerItem * quantity;
        this.totalHaValue += (long) haPricePerItem * quantity;
        this.pricePerItem = pricePerItem;
        this.haPricePerItem = haPricePerItem;
    }
}
