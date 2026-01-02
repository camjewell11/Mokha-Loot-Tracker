package com.camjewell;

public class LootItem {
    private final int id;
    private final int quantity;
    private final String name;

    public LootItem(int id, int quantity, String name) {
        this.id = id;
        this.quantity = quantity;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getName() {
        return name;
    }
}
