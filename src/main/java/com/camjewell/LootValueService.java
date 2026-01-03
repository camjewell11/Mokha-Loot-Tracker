package com.camjewell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.runelite.client.game.ItemManager;

class LootValueService {
    private static final Logger log = LoggerFactory.getLogger(LootValueService.class);

    // High-value items to highlight
    private static final int AVERNIC_TREADS_ID = 33209;
    private static final int MOKHAIOTL_CLOTH_ID = 33210;
    private static final int EYE_OF_AYAK_ID = 33211;

    // Items for calculating Mokhaiotl Cloth value
    private static final int DEMON_TEAR_ID = 33206;
    private static final int CONFLICTION_GAUNTLETS_ID = 33213;
    private static final int TORMENTED_BRACELET_ID = 19544;

    private static final int SUN_KISSED_BONES_ID = 29378;
    private static final int SUN_KISSED_BONES_VALUE = 8000;
    private static final int SPIRIT_SEED_ID = 5317;
    private static final int SPIRIT_SEED_VALUE = 140000;

    private final ItemManager itemManager;
    private final MokhaLootTrackerConfig config;

    LootValueService(ItemManager itemManager, MokhaLootTrackerConfig config) {
        this.itemManager = itemManager;
        this.config = config;
    }

    long getPotionDoseAdjustedPriceEach(int itemId) {
        long priceEach = itemManager.getItemPrice(itemId);
        try {
            String itemName = itemManager.getItemComposition(itemId).getName();
            int dose = com.camjewell.util.PotionUtil.getPotionDose(itemName);
            if (dose > 1) {
                priceEach = priceEach / dose;
            }
        } catch (Exception e) {
            log.debug("[Supplies Debug] Failed to adjust potion price for {}: {}", itemId, e.getMessage());
        }
        return priceEach;
    }

    long getItemValue(int itemId, int quantity) {
        if (itemId == MOKHAIOTL_CLOTH_ID) {
            return getMokhaiotlClothValue() * quantity;
        }
        long itemPrice = itemManager.getItemPrice(itemId);
        return itemPrice * quantity;
    }

    long getAdjustedLootValue(long baseValue, java.util.List<LootItem> items) {
        boolean excludeEnabled = config.excludeSunKissedBonesValue() || config.excludeSpiritSeedValue();

        if (!excludeEnabled) {
            return baseValue;
        }

        if (items == null || items.isEmpty()) {
            return baseValue;
        }

        long adjustment = 0;
        for (LootItem item : items) {
            if (item.getId() == SUN_KISSED_BONES_ID && config.excludeSunKissedBonesValue()) {
                adjustment += (long) item.getQuantity() * SUN_KISSED_BONES_VALUE;
            }
            if (item.getId() == SPIRIT_SEED_ID && config.excludeSpiritSeedValue()) {
                adjustment += (long) item.getQuantity() * SPIRIT_SEED_VALUE;
            }
        }

        return Math.max(0, baseValue - adjustment);
    }

    long getFilteredLootValue(java.util.List<LootItem> items) {
        int minValue = config.minItemValueThreshold();
        if (items == null) {
            return 0;
        }
        long total = 0;
        for (LootItem item : items) {
            if (minValue > 0 && getItemValue(item.getId(), 1) < minValue) {
                continue;
            }
            total += getItemValue(item.getId(), item.getQuantity());
        }
        return total;
    }

    long getTotalLootValue(java.util.List<LootItem> items) {
        if (items == null) {
            return 0;
        }
        long total = 0;
        for (LootItem item : items) {
            total += getItemValue(item.getId(), item.getQuantity());
        }
        return total;
    }

    boolean isHighValueItem(int itemId) {
        return itemId == AVERNIC_TREADS_ID || itemId == MOKHAIOTL_CLOTH_ID || itemId == EYE_OF_AYAK_ID;
    }

    long getMokhaiotlClothValue() {
        final int FALLBACK_GAUNTLETS = 71000000; // 71m
        final int FALLBACK_BRACELET = 22000000; // 22m
        final int FALLBACK_TEAR = 275; // 275 each
        try {
            log.info("Mokhaiotl Cloth: Gauntlets ID: {}, Bracelet ID: {}, Tear ID: {}", CONFLICTION_GAUNTLETS_ID,
                    TORMENTED_BRACELET_ID, DEMON_TEAR_ID);
            int gauntletsPrice = itemManager.getItemPrice(CONFLICTION_GAUNTLETS_ID);
            int braceletPrice = itemManager.getItemPrice(TORMENTED_BRACELET_ID);
            int tearPrice = itemManager.getItemPrice(DEMON_TEAR_ID);

            log.info("Raw prices - Gauntlets: {}, Bracelet: {}, Tear: {}", gauntletsPrice, braceletPrice, tearPrice);

            if (gauntletsPrice <= 0) {
                log.warn("Gauntlets price unavailable, using fallback: {}", FALLBACK_GAUNTLETS);
                gauntletsPrice = FALLBACK_GAUNTLETS;
            }
            if (braceletPrice <= 0) {
                log.warn("Bracelet price unavailable, using fallback: {}", FALLBACK_BRACELET);
                braceletPrice = FALLBACK_BRACELET;
            }
            if (tearPrice <= 0) {
                log.warn("Tear price unavailable, using fallback: {}", FALLBACK_TEAR);
                tearPrice = FALLBACK_TEAR;
            }

            log.info("Final prices used - Gauntlets: {} (ID: {}), Bracelet: {} (ID: {}), Tear: {} (ID: {})",
                    gauntletsPrice, CONFLICTION_GAUNTLETS_ID, braceletPrice, TORMENTED_BRACELET_ID, tearPrice,
                    DEMON_TEAR_ID);

            long clothValue = gauntletsPrice - (braceletPrice + ((long) tearPrice * 10000));
            log.info("Mokhaiotl Cloth calculated value: {} (Formula: {} - ({} + ({} * 10000)))", clothValue,
                    gauntletsPrice, braceletPrice, tearPrice);
            return Math.max(0, clothValue);
        } catch (Exception e) {
            log.error("Error calculating Mokhaiotl Cloth value", e);
            return 0;
        }
    }
}
