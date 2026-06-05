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

    // Blowpipe config keys — JSON array format: [{itemId, quantity}, ...]
    // Each entry covers all stored items (darts AND Zulrah's scales for toxic blowpipe).
    private static final String BLOWPIPE_STORAGE_CONFIG_KEY = "toxic_blowpipe_storage";
    private static final String CAMPHOR_BLOWPIPE_STORAGE_CONFIG_KEY = "camphor_blowpipe_storage";
    private static final String IRONWOOD_BLOWPIPE_STORAGE_CONFIG_KEY = "ironwood_blowpipe_storage";
    private static final String ROSEWOOD_BLOWPIPE_STORAGE_CONFIG_KEY = "rosewood_blowpipe_storage";
    private static final String BLAZING_BLOWPIPE_STORAGE_CONFIG_KEY = "blazing_blowpipe_storage";

    // Powered staff config keys — integer format: charge count as a plain string.
    // Key names match the tictac7x-charges plugin convention; verify if tracking breaks.
    private static final String TRIDENT_SEAS_CONFIG_KEY = "trident_of_the_seas_charges";
    private static final String TRIDENT_SWAMP_CONFIG_KEY = "trident_of_the_swamp_charges";
    private static final String SANGUINESTI_CONFIG_KEY = "sanguinesti_staff_charges";
    private static final String TUMEKENS_SHADOW_CONFIG_KEY = "tumekens_shadow_charges";
    private static final String EYE_OF_AYAK_CONFIG_KEY = "eye_of_ayak_charges";

    // Canonical item IDs used as snapshot keys for powered staff charges.
    // When charges are consumed the delta is stored under this item ID in historicalSuppliesUsed.
    private static final int TRIDENT_SEAS_ITEM_ID = 11905;
    private static final int TRIDENT_SWAMP_ITEM_ID = 12899;
    private static final int SANGUINESTI_ITEM_ID = 22323;
    private static final int TUMEKENS_SHADOW_ITEM_ID = 27277;
    // Eye of Ayak item ID — update when confirmed
    private static final int EYE_OF_AYAK_ITEM_ID = 0;

    // Maps each powered staff config key to a canonical item ID for snapshot storage.
    private static final String[][] STAFF_CONFIG_KEY_ITEM_PAIRS = {
        { TRIDENT_SEAS_CONFIG_KEY, String.valueOf(TRIDENT_SEAS_ITEM_ID) },
        { TRIDENT_SWAMP_CONFIG_KEY, String.valueOf(TRIDENT_SWAMP_ITEM_ID) },
        { SANGUINESTI_CONFIG_KEY, String.valueOf(SANGUINESTI_ITEM_ID) },
        { TUMEKENS_SHADOW_CONFIG_KEY, String.valueOf(TUMEKENS_SHADOW_ITEM_ID) },
        { EYE_OF_AYAK_CONFIG_KEY, String.valueOf(EYE_OF_AYAK_ITEM_ID) },
    };

    // Powered staff item IDs for inventory/equipment detection (all variants).
    private static final int[] POWERED_STAFF_ITEM_IDS = {
        11905, 11907, // Trident of the seas, (e)
        12899, 12901, // Trident of the swamp, (e)
        22323,        // Sanguinesti staff
        27277,        // Tumeken's shadow
        // Eye of Ayak — add ID when confirmed
    };

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

    // When true, config-based weapon charges (blowpipe storage + staff charges) are excluded
    // from snapshots and per-tick ammo comparison. The BlowpipeCheckOverlay owns charge tracking.
    private boolean blowpipeOverlayActive = false;

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
        // When overlay is active, use live-only ammo (BUFF_BAR varps) so arrows/bolts from
        // a bow or crossbow are still tracked while stale config-based blowpipe data is excluded.
        Map<Integer, Integer> currentWeaponAmmo = blowpipeOverlayActive
                ? readLiveWeaponAmmo()
                : readWeaponAmmo();
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
            Map<Integer, Integer> currentCombined = buildCombinedSnapshot();
            checkForConsumption(currentCombined, true, lastArenaExitTime);
        }

        lastWeaponAmmoSnapshot.clear();
        lastWeaponAmmoSnapshot.putAll(currentWeaponAmmo);
    }

    void setBlowpipeOverlayActive(boolean active) {
        blowpipeOverlayActive = active;
        if (active) {
            // initializeForArenaEntry() ran before this flag was set, so it populated
            // lastWeaponAmmoSnapshot and lastCombinedSnapshot with readWeaponAmmo() which
            // includes stale config-based blowpipe ammo. Rebuild both with live-only data
            // so the first onGameTick doesn't see a spurious dart delta.
            lastWeaponAmmoSnapshot.clear();
            lastWeaponAmmoSnapshot.putAll(readLiveWeaponAmmo());
            lastCombinedSnapshot.clear();
            lastCombinedSnapshot.putAll(buildCombinedSnapshot());
        }
    }

    Map<Integer, Integer> readCurrentWeaponAmmo() {
        return readWeaponAmmo();
    }

    boolean hasTrackedWeaponPresent() {
        int equippedWeapon = client.getVarpValue(BUFF_BAR_WEAPON);
        if (isTrackedWeaponId(equippedWeapon)) {
            return true;
        }
        net.runelite.api.ItemContainer inventory = client.getItemContainer(INVENTORY_CONTAINER_ID);
        if (inventory != null) {
            for (net.runelite.api.Item item : inventory.getItems()) {
                if (item != null && isTrackedWeaponId(item.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isTrackedWeaponId(int itemId) {
        return isBlowpipeId(itemId) || isPoweredStaffId(itemId);
    }

    private boolean isBlowpipeId(int itemId) {
        switch (itemId) {
            case 12926: case 31575: case 31579: case 31583: case 28687:
                return true;
            default:
                return false;
        }
    }

    private boolean isPoweredStaffId(int itemId) {
        for (int staffId : POWERED_STAFF_ITEM_IDS) {
            if (staffId == itemId) return true;
        }
        return false;
    }

    // Returns true if itemId is a staff whose charges are tracked via STAFF_CONFIG_KEY_ITEM_PAIRS.
    // Used by the plugin to detect that per-charge pricing is meaningless (use 0 gp).
    static boolean isStaffChargeItemId(int itemId) {
        if (itemId <= 0) return false;
        for (String[] pair : STAFF_CONFIG_KEY_ITEM_PAIRS) {
            try {
                if (Integer.parseInt(pair[1]) == itemId) return true;
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
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
        // When overlay is active, use live-only ammo so arrows/bolts are included but stale
        // config-based blowpipe ammo cannot contaminate the snapshot comparison.
        Map<Integer, Integer> weaponAmmo = blowpipeOverlayActive
                ? readLiveWeaponAmmo()
                : readWeaponAmmo();
        return buildCombinedSnapshot(weaponAmmo);
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

    // Reads powered staff charges from tictac7x-charges config. Staff charges use a plain integer
    // format (e.g. "2500"), unlike blowpipes which use a JSON array. Populates map with
    // (staffItemId → chargeCount) entries; existing entries are merged (summed).
    private void readStaffCharges(Map<Integer, Integer> map) {
        for (String[] pair : STAFF_CONFIG_KEY_ITEM_PAIRS) {
            int itemId;
            try {
                itemId = Integer.parseInt(pair[1]);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (itemId <= 0) continue;

            String value = configManager.getConfiguration(WEAPON_CHARGES_CONFIG_GROUP, pair[0]);
            if (value == null || value.isEmpty()) continue;

            try {
                int charges = Integer.parseInt(value.trim());
                if (charges > 0) {
                    map.merge(itemId, charges, Integer::sum);
                }
            } catch (NumberFormatException ignored) {
                // Not integer format — some other weapon type, skip.
            }
        }
    }

    // Always accurate — updated by the server every tick without player interaction.
    private Map<Integer, Integer> readLiveWeaponAmmo() {
        Map<Integer, Integer> map = new HashMap<>();

        int ammoType = client.getVarpValue(BUFF_BAR_AMMO_TYPE);
        int ammoCount = client.getVarpValue(BUFF_BAR_AMMO_AMOUNT);
        if (ammoType > 0 && ammoCount > 0) {
            map.put(ammoType, ammoCount);
        }

        int quiverAmmoId = client.getVarpValue(DIZANAS_QUIVER_TEMP_AMMO);
        int quiverAmmoCount = client.getVarpValue(DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
        if (quiverAmmoId > 0 && quiverAmmoCount > 0) {
            map.put(quiverAmmoId, map.getOrDefault(quiverAmmoId, 0) + quiverAmmoCount);
        }

        return map;
    }

    // Returns live ammo first (BUFF_BAR varps, updated every tick for conventional ranged weapons).
    // Falls back to tictac7x-charges config for blowpipes, whose darts are stored inside the weapon
    // and not exposed via BUFF_BAR varps. The config is only updated when the player opens the
    // blowpipe interface, so blowpipe dart counts are only tracked at "check" events, not per-shot.
    // Staff charges (integer format) are always appended after the live/blowpipe path.
    private Map<Integer, Integer> readWeaponAmmo() {
        Map<Integer, Integer> map = readLiveWeaponAmmo();
        if (!map.isEmpty()) {
            readStaffCharges(map); // staff item IDs never conflict with arrow/bolt item IDs
            return map;
        }

        int equippedWeapon = client.getVarpValue(BUFF_BAR_WEAPON);
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

            log.debug("[Mokha] Blowpipe storage raw config [{}]: {}", key, serialized);
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

        readStaffCharges(map); // always add staff charges; item IDs never overlap with blowpipe darts
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
