package com.camjewell.util;

public final class PotionUtil {
    private PotionUtil() {
    }

    public static boolean isPotion(String itemName) {
        if (itemName == null) {
            return false;
        }
        int dose = getPotionDose(itemName);
        return dose >= 1 && dose <= 4;
    }

    public static String getPotionBaseName(String itemName) {
        if (itemName == null || !itemName.contains("(")) {
            return itemName;
        }
        return itemName.substring(0, itemName.lastIndexOf("(")).trim();
    }

    public static int getPotionDose(String itemName) {
        if (itemName == null || !itemName.contains("(")) {
            return 0;
        }
        try {
            String doseStr = itemName.substring(itemName.lastIndexOf("(") + 1, itemName.lastIndexOf(")"));
            return Integer.parseInt(doseStr);
        } catch (Exception e) {
            return 0;
        }
    }
}
