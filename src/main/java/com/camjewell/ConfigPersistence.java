package com.camjewell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

class ConfigPersistence {
    private static final Logger log = LoggerFactory.getLogger(ConfigPersistence.class);

    private static final String CONFIG_KEY_TOTAL_LOST = "totalLostValue";
    private static final String CONFIG_KEY_TIMES_DIED = "timesDied";
    private static final String CONFIG_KEY_DEATH_COSTS = "totalDeathCosts";
    private static final String CONFIG_KEY_TOTAL_SUPPLIES = "totalSuppliesCost";
    private static final String CONFIG_KEY_TOTAL_SUPPLIES_ITEMS = "totalSuppliesItems";
    private static final String CONFIG_KEY_WAVE_PREFIX = "wave";
    private static final String CONFIG_KEY_WAVE_ITEMS_PREFIX = "waveItems";
    private static final String CONFIG_KEY_WAVE_CLAIMED_PREFIX = "waveClaimed";
    private static final String CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX = "waveClaimedItems";
    private static final int MAX_TRACKED_WAVES = 9;

    private final Client client;
    private final ConfigManager configManager;
    private final ItemManager itemManager;

    ConfigPersistence(Client client, ConfigManager configManager, ItemManager itemManager) {
        this.client = client;
        this.configManager = configManager;
        this.itemManager = itemManager;
    }

    String getConfigGroup() {
        long accountHash = client.getAccountHash();
        if (accountHash != -1) {
            return "mokhaloot." + accountHash;
        }
        return "mokhaloot.default";
    }

    long getLongConfig(String key) {
        String value = configManager.getConfiguration(getConfigGroup(), key);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.error("Error parsing long config", e);
            }
        }
        return 0L;
    }

    int getIntConfig(String key) {
        String value = configManager.getConfiguration(getConfigGroup(), key);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.error("Error parsing int config", e);
            }
        }
        return 0;
    }

    void setConfig(String key, long value) {
        configManager.setConfiguration(getConfigGroup(), key, value);
    }

    void setConfig(String key, int value) {
        configManager.setConfiguration(getConfigGroup(), key, value);
    }

    void setConfig(String key, String value) {
        configManager.setConfiguration(getConfigGroup(), key, value);
    }

    String getStringConfig(String key) {
        return configManager.getConfiguration(getConfigGroup(), key);
    }

    void unsetConfig(String key) {
        configManager.unsetConfiguration(getConfigGroup(), key);
    }

    long getWaveLostValue(int wave) {
        return getLongConfig(CONFIG_KEY_WAVE_PREFIX + wave);
    }

    List<LootItem> getWaveLostItems(int wave) {
        String serialized = getStringConfig(CONFIG_KEY_WAVE_ITEMS_PREFIX + wave);
        return deserializeItems(serialized);
    }

    long getWaveClaimedValue(int wave) {
        return getLongConfig(CONFIG_KEY_WAVE_CLAIMED_PREFIX + wave);
    }

    List<LootItem> getWaveClaimedItems(int wave) {
        String serialized = getStringConfig(CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX + wave);
        return deserializeItems(serialized);
    }

    void saveWaveLost(int wave, long value, List<LootItem> items) {
        String waveKey = CONFIG_KEY_WAVE_PREFIX + wave;
        long existing = getLongConfig(waveKey);
        setConfig(waveKey, existing + value);

        if (items != null && !items.isEmpty()) {
            String itemsKey = CONFIG_KEY_WAVE_ITEMS_PREFIX + wave;
            String existingItems = getStringConfig(itemsKey);
            String merged = mergeItemsSerialized(existingItems, items);
            setConfig(itemsKey, merged);
        }
    }

    void saveWaveClaimed(int wave, long value, List<LootItem> items) {
        String waveKey = CONFIG_KEY_WAVE_CLAIMED_PREFIX + wave;
        long existing = getLongConfig(waveKey);
        setConfig(waveKey, existing + value);

        if (items != null && !items.isEmpty()) {
            String itemsKey = CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX + wave;
            String existingItems = getStringConfig(itemsKey);
            String merged = mergeItemsSerialized(existingItems, items);
            setConfig(itemsKey, merged);
        }
    }

    void incrementTotalLost(long value) {
        long total = getLongConfig(CONFIG_KEY_TOTAL_LOST);
        setConfig(CONFIG_KEY_TOTAL_LOST, total + value);
    }

    void incrementTimesDied() {
        int times = getIntConfig(CONFIG_KEY_TIMES_DIED);
        setConfig(CONFIG_KEY_TIMES_DIED, times + 1);
    }

    void addDeathCost(long cost) {
        long total = getLongConfig(CONFIG_KEY_DEATH_COSTS);
        setConfig(CONFIG_KEY_DEATH_COSTS, total + cost);
    }

    void addSuppliesCost(long cost, String suppliesItemsSerialized) {
        long total = getLongConfig(CONFIG_KEY_TOTAL_SUPPLIES);
        setConfig(CONFIG_KEY_TOTAL_SUPPLIES, total + cost);

        if (suppliesItemsSerialized != null && !suppliesItemsSerialized.isEmpty()) {
            setConfig(CONFIG_KEY_TOTAL_SUPPLIES_ITEMS, suppliesItemsSerialized);
        }
    }

    long getTotalDeathCosts() {
        return getLongConfig(CONFIG_KEY_DEATH_COSTS);
    }

    int getTimesDied() {
        return getIntConfig(CONFIG_KEY_TIMES_DIED);
    }

    long getTotalSuppliesCost() {
        return getLongConfig(CONFIG_KEY_TOTAL_SUPPLIES);
    }

    List<LootItem> getTotalSuppliesItems() {
        String serialized = getStringConfig(CONFIG_KEY_TOTAL_SUPPLIES_ITEMS);
        return deserializeItems(serialized);
    }

    void resetAllStats() {
        unsetConfig(CONFIG_KEY_TOTAL_LOST);
        unsetConfig(CONFIG_KEY_TIMES_DIED);
        unsetConfig(CONFIG_KEY_DEATH_COSTS);
        unsetConfig(CONFIG_KEY_TOTAL_SUPPLIES);
        unsetConfig(CONFIG_KEY_TOTAL_SUPPLIES_ITEMS);

        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            unsetConfig(CONFIG_KEY_WAVE_PREFIX + wave);
            unsetConfig(CONFIG_KEY_WAVE_ITEMS_PREFIX + wave);
            unsetConfig(CONFIG_KEY_WAVE_CLAIMED_PREFIX + wave);
            unsetConfig(CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX + wave);
        }
    }

    void clearDefaultConfig() {
        String defaultGroup = "mokhaloot.default";
        configManager.unsetConfiguration(defaultGroup, CONFIG_KEY_TOTAL_LOST);
        configManager.unsetConfiguration(defaultGroup, CONFIG_KEY_TIMES_DIED);
        configManager.unsetConfiguration(defaultGroup, CONFIG_KEY_DEATH_COSTS);

        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            configManager.unsetConfiguration(defaultGroup, CONFIG_KEY_WAVE_PREFIX + wave);
            configManager.unsetConfiguration(defaultGroup, CONFIG_KEY_WAVE_ITEMS_PREFIX + wave);
            configManager.unsetConfiguration(defaultGroup, CONFIG_KEY_WAVE_CLAIMED_PREFIX + wave);
            configManager.unsetConfiguration(defaultGroup, CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX + wave);
        }
    }

    private String mergeItemsSerialized(String existingSerialized, List<LootItem> newItems) {
        Map<Integer, Integer> itemMap = new HashMap<>();

        if (existingSerialized != null && !existingSerialized.isEmpty()) {
            String[] pairs = existingSerialized.split(",");
            for (String pair : pairs) {
                try {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        int itemId = Integer.parseInt(parts[0]);
                        int quantity = Integer.parseInt(parts[1]);
                        itemMap.put(itemId, quantity);
                    }
                } catch (NumberFormatException e) {
                    log.error("Error parsing item pair: {}", pair, e);
                }
            }
        }

        for (LootItem item : newItems) {
            itemMap.put(item.getId(), itemMap.getOrDefault(item.getId(), 0) + item.getQuantity());
        }

        List<LootItem> merged = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : itemMap.entrySet()) {
            merged.add(new LootItem(entry.getKey(), entry.getValue(), null));
        }

        return serializeItems(merged);
    }

    public String serializeItems(List<LootItem> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (LootItem item : items) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(item.getId()).append(":").append(item.getQuantity());
        }
        return sb.toString();
    }

    private List<LootItem> deserializeItems(String serialized) {
        Map<Integer, Integer> itemMap = new HashMap<>();

        if (serialized != null && !serialized.isEmpty()) {
            String[] pairs = serialized.split(",");
            for (String pair : pairs) {
                try {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        int itemId = Integer.parseInt(parts[0]);
                        int quantity = Integer.parseInt(parts[1]);
                        itemMap.put(itemId, itemMap.getOrDefault(itemId, 0) + quantity);
                    }
                } catch (NumberFormatException e) {
                    log.error("Error parsing item pair: {}", pair, e);
                }
            }
        }

        List<LootItem> result = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : itemMap.entrySet()) {
            String itemName = null;
            try {
                if (client.isClientThread()) {
                    ItemComposition itemComp = itemManager.getItemComposition(entry.getKey());
                    if (itemComp != null) {
                        itemName = itemComp.getName();
                    }
                }
            } catch (Exception e) {
            }
            result.add(new LootItem(entry.getKey(), entry.getValue(), itemName));
        }
        return result;
    }
}
