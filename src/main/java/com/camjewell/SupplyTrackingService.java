package com.camjewell;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Varbits;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;

class SupplyTrackingService {
    private static final int INVENTORY_CONTAINER_ID = 93;
    private static final int EQUIPMENT_CONTAINER_ID = 94;

    private static final int BUFF_BAR_WEAPON = 3160;
    private static final int BUFF_BAR_AMMO_TYPE = 3158;
    private static final int BUFF_BAR_AMMO_AMOUNT = 3159;
    private static final int DIZANAS_QUIVER_TEMP_AMMO = 4142;
    private static final int DIZANAS_QUIVER_TEMP_AMMO_AMOUNT = 4141;

    private static final String WEAPON_CHARGES_CONFIG_GROUP = "tictac7x-charges";
    private static final String BLOWPIPE_STORAGE_CONFIG_KEY = "toxic_blowpipe_storage";
    private static final String CAMPHOR_BLOWPIPE_STORAGE_CONFIG_KEY = "camphor_blowpipe_storage";
    private static final String IRONWOOD_BLOWPIPE_STORAGE_CONFIG_KEY = "ironwood_blowpipe_storage";
    private static final String ROSEWOOD_BLOWPIPE_STORAGE_CONFIG_KEY = "rosewood_blowpipe_storage";
    private static final String BLAZING_BLOWPIPE_STORAGE_CONFIG_KEY = "blazing_blowpipe_storage";

    private static final int[] RUNE_POUCH_ITEM_IDS = new int[] {
            0,
            556,
            555,
            557,
            554,
            558,
            562,
            560,
            565,
            564,
            561,
            563,
            559,
            566,
            9075,
            4695,
            4698,
            4696,
            4699,
            4694,
            4697,
            21880,
            28929,
            30843
    };

    private static final class BlowpipeStorageEntry {
        int itemId;
        int quantity;
    }

    private final Client client;
    private final ItemManager itemManager;
    private final ConfigManager configManager;
    private final Gson gson;
    private final Logger log;
    private final Map<Integer, Integer> lastCombinedSnapshot;
    private final Map<Integer, Integer> lastWeaponAmmoSnapshot;
    private final Map<Integer, Integer> totalSuppliesConsumed;
    private final Runnable onSuppliesChanged;

    SupplyTrackingService(
            Client client,
            ItemManager itemManager,
            ConfigManager configManager,
            Gson gson,
            Logger log,
            Map<Integer, Integer> lastCombinedSnapshot,
            Map<Integer, Integer> lastWeaponAmmoSnapshot,
            Map<Integer, Integer> totalSuppliesConsumed,
            Runnable onSuppliesChanged) {
        this.client = client;
        this.itemManager = itemManager;
        this.configManager = configManager;
        this.gson = gson;
        this.log = log;
        this.lastCombinedSnapshot = lastCombinedSnapshot;
        this.lastWeaponAmmoSnapshot = lastWeaponAmmoSnapshot;
        this.totalSuppliesConsumed = totalSuppliesConsumed;
        this.onSuppliesChanged = onSuppliesChanged;
    }

    int initializeForArenaEntry() {
        lastWeaponAmmoSnapshot.clear();
        lastWeaponAmmoSnapshot.putAll(readWeaponAmmo());

        lastCombinedSnapshot.clear();
        lastCombinedSnapshot.putAll(buildCombinedSnapshot());

        int ammoSum = 0;
        for (int qty : lastWeaponAmmoSnapshot.values()) {
            ammoSum += qty;
        }
        return ammoSum;
    }

    void onGameTick(boolean isDead, boolean inConsumptionBounds, long lastArenaExitTime) {
        Map<Integer, Integer> currentWeaponAmmo = readWeaponAmmo();
        boolean hasWeaponAmmoConsumption = false;

        for (Map.Entry<Integer, Integer> entry : lastWeaponAmmoSnapshot.entrySet()) {
            int itemId = entry.getKey();
            int previousQty = entry.getValue();
            int currentQty = currentWeaponAmmo.getOrDefault(itemId, 0);
            if (currentQty < previousQty) {
                hasWeaponAmmoConsumption = true;
                break;
            }
        }

        if (hasWeaponAmmoConsumption && !isDead && inConsumptionBounds) {
            Map<Integer, Integer> currentCombined = buildCombinedSnapshot(currentWeaponAmmo);
            checkForConsumption(currentCombined, true, lastArenaExitTime);
        }

        lastWeaponAmmoSnapshot.clear();
        lastWeaponAmmoSnapshot.putAll(currentWeaponAmmo);
    }

    void onItemContainerChanged(
            int containerId,
            boolean inMokhaArena,
            boolean isDead,
            boolean inConsumptionBounds,
            long lastArenaExitTime) {
        if (!inMokhaArena || isDead) {
            if (!inMokhaArena && !lastCombinedSnapshot.isEmpty()) {
                long timeSinceExit = System.currentTimeMillis() - lastArenaExitTime;
                log.warn(
                        "[Mokha] WARNING: Item container changed outside arena with stale snapshot. Time since exit: {}ms, Snapshot size: {}, Supplies consumed: {}",
                        timeSinceExit, lastCombinedSnapshot.size(), totalSuppliesConsumed.size());
            }
            return;
        }

        if (containerId != INVENTORY_CONTAINER_ID && containerId != EQUIPMENT_CONTAINER_ID) {
            return;
        }

        if (!inConsumptionBounds) {
            Map<Integer, Integer> currentCombined = buildCombinedSnapshot();
            lastCombinedSnapshot.clear();
            lastCombinedSnapshot.putAll(currentCombined);
            return;
        }

        Map<Integer, Integer> currentCombined = buildCombinedSnapshot();
        checkForConsumption(currentCombined, true, lastArenaExitTime);
    }

    private Map<Integer, Integer> buildCombinedSnapshot() {
        return buildCombinedSnapshot(readWeaponAmmo());
    }

    private Map<Integer, Integer> buildCombinedSnapshot(Map<Integer, Integer> weaponAmmo) {
        Map<Integer, Integer> combined = new HashMap<>();

        ItemContainer inventory = client.getItemContainer(INVENTORY_CONTAINER_ID);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item != null && item.getId() > 0) {
                    combined.put(item.getId(), combined.getOrDefault(item.getId(), 0) + item.getQuantity());
                }
            }
        }

        ItemContainer equipment = client.getItemContainer(EQUIPMENT_CONTAINER_ID);
        if (equipment != null) {
            for (Item item : equipment.getItems()) {
                if (item != null && item.getId() > 0) {
                    combined.put(item.getId(), combined.getOrDefault(item.getId(), 0) + item.getQuantity());
                }
            }
        }

        Map<Integer, Integer> runePouchRunes = readRunePouch();
        for (Map.Entry<Integer, Integer> entry : runePouchRunes.entrySet()) {
            combined.put(entry.getKey(), combined.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }

        for (Map.Entry<Integer, Integer> entry : weaponAmmo.entrySet()) {
            combined.put(entry.getKey(), combined.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }

        return combined;
    }

    @SuppressWarnings("deprecation")
    private Map<Integer, Integer> readRunePouch() {
        Map<Integer, Integer> map = new HashMap<>();
        int[] runeVarbits = new int[] { Varbits.RUNE_POUCH_RUNE1, Varbits.RUNE_POUCH_RUNE2, Varbits.RUNE_POUCH_RUNE3,
                Varbits.RUNE_POUCH_RUNE4, Varbits.RUNE_POUCH_RUNE5, Varbits.RUNE_POUCH_RUNE6 };
        int[] amtVarbits = new int[] { Varbits.RUNE_POUCH_AMOUNT1, Varbits.RUNE_POUCH_AMOUNT2,
                Varbits.RUNE_POUCH_AMOUNT3, Varbits.RUNE_POUCH_AMOUNT4, Varbits.RUNE_POUCH_AMOUNT5,
                Varbits.RUNE_POUCH_AMOUNT6 };

        for (int i = 0; i < runeVarbits.length; i++) {
            int runeVar = client.getVarbitValue(runeVarbits[i]);
            int amt = client.getVarbitValue(amtVarbits[i]);
            if (runeVar <= 0 || amt <= 0) {
                continue;
            }
            if (runeVar >= RUNE_POUCH_ITEM_IDS.length) {
                continue;
            }
            int itemId = RUNE_POUCH_ITEM_IDS[runeVar];
            if (itemId <= 0) {
                continue;
            }
            map.put(itemId, map.getOrDefault(itemId, 0) + amt);
        }
        return map;
    }

    private Map<Integer, Integer> readWeaponAmmo() {
        Map<Integer, Integer> map = new HashMap<>();
        boolean hasBlowpipeAmmoFromVarps = false;

        int equippedWeapon = client.getVarpValue(BUFF_BAR_WEAPON);
        int ammoType = client.getVarpValue(BUFF_BAR_AMMO_TYPE);
        int ammoCount = client.getVarpValue(BUFF_BAR_AMMO_AMOUNT);
        if (ammoType > 0 && ammoCount > 0) {
            map.put(ammoType, ammoCount);
            hasBlowpipeAmmoFromVarps = true;
        }

        int quiverAmmoId = client.getVarpValue(DIZANAS_QUIVER_TEMP_AMMO);
        int quiverAmmoCount = client.getVarpValue(DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
        if (quiverAmmoId > 0 && quiverAmmoCount > 0) {
            map.put(quiverAmmoId, map.getOrDefault(quiverAmmoId, 0) + quiverAmmoCount);
        }

        if (!hasBlowpipeAmmoFromVarps) {
            String preferredKey = null;
            switch (equippedWeapon) {
                case 12926:
                    preferredKey = BLOWPIPE_STORAGE_CONFIG_KEY;
                    break;
                case 31575:
                    preferredKey = CAMPHOR_BLOWPIPE_STORAGE_CONFIG_KEY;
                    break;
                case 31579:
                    preferredKey = IRONWOOD_BLOWPIPE_STORAGE_CONFIG_KEY;
                    break;
                case 31583:
                    preferredKey = ROSEWOOD_BLOWPIPE_STORAGE_CONFIG_KEY;
                    break;
                case 28687:
                    preferredKey = BLAZING_BLOWPIPE_STORAGE_CONFIG_KEY;
                    break;
                default:
                    break;
            }

            String[] fallbackKeys = new String[] {
                    preferredKey,
                    BLOWPIPE_STORAGE_CONFIG_KEY,
                    CAMPHOR_BLOWPIPE_STORAGE_CONFIG_KEY,
                    IRONWOOD_BLOWPIPE_STORAGE_CONFIG_KEY,
                    ROSEWOOD_BLOWPIPE_STORAGE_CONFIG_KEY,
                    BLAZING_BLOWPIPE_STORAGE_CONFIG_KEY
            };

            for (String key : fallbackKeys) {
                if (key == null) {
                    continue;
                }

                String serialized = configManager.getConfiguration(WEAPON_CHARGES_CONFIG_GROUP, key);
                if (serialized == null || serialized.isEmpty()) {
                    continue;
                }

                try {
                    BlowpipeStorageEntry[] entries = gson.fromJson(serialized, BlowpipeStorageEntry[].class);
                    boolean hadEntries = false;
                    if (entries != null) {
                        for (BlowpipeStorageEntry entry : entries) {
                            if (entry != null && entry.itemId > 0 && entry.quantity > 0) {
                                map.put(entry.itemId, map.getOrDefault(entry.itemId, 0) + entry.quantity);
                                hadEntries = true;
                            }
                        }
                    }
                    if (hadEntries) {
                        break;
                    }
                } catch (JsonParseException ex) {
                    // Ignore malformed payloads and continue trying keys.
                }
            }
        }

        return map;
    }

    private void checkForConsumption(Map<Integer, Integer> currentCombined, boolean inMokhaArena,
            long lastArenaExitTime) {
        if (!inMokhaArena) {
            log.error(
                    "[Mokha] CRITICAL: checkForConsumption called outside arena! inMokhaArena={}, Snapshot size={}, Time since exit: {}ms",
                    inMokhaArena, lastCombinedSnapshot.size(), System.currentTimeMillis() - lastArenaExitTime);
            lastCombinedSnapshot.clear();
            return;
        }

        if (!lastCombinedSnapshot.isEmpty()) {
            boolean hasConsumption = false;

            for (Map.Entry<Integer, Integer> entry : lastCombinedSnapshot.entrySet()) {
                int itemId = entry.getKey();
                int previousQty = entry.getValue();
                int currentQty = currentCombined.getOrDefault(itemId, 0);

                if (currentQty < previousQty) {
                    int consumedQty = previousQty - currentQty;
                    // Preserve old behavior of name lookup side effect-free path.
                    itemManager.getItemComposition(itemId).getName();
                    totalSuppliesConsumed.put(itemId, totalSuppliesConsumed.getOrDefault(itemId, 0) + consumedQty);
                    hasConsumption = true;
                }
            }

            if (hasConsumption) {
                onSuppliesChanged.run();
            }
        }

        lastCombinedSnapshot.clear();
        lastCombinedSnapshot.putAll(currentCombined);
    }
}
