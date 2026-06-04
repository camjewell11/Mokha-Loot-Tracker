package com.camjewell;

public class ItemAggregate {
    String name;
    int totalQuantity;
    int pricePerItem;
    int haPricePerItem;
    long totalValue;
    long totalHaValue;

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
