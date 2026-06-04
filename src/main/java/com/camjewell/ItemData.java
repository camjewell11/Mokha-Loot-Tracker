package com.camjewell;

public class ItemData {
    public String name;
    public int quantity;
    public int pricePerItem;
    public long totalValue;
    public int haPricePerItem;
    public long totalHaValue;

    public ItemData(String name, int quantity, int pricePerItem, long totalValue) {
        this(name, quantity, pricePerItem, totalValue, 0, 0);
    }

    public ItemData(String name, int quantity, int pricePerItem, long totalValue, int haPricePerItem,
            long totalHaValue) {
        this.name = name;
        this.quantity = quantity;
        this.pricePerItem = pricePerItem;
        this.totalValue = totalValue;
        this.haPricePerItem = haPricePerItem;
        this.totalHaValue = totalHaValue;
    }
}
