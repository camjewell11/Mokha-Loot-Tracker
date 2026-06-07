package com.camjewell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    private static final int[] RUNE_POUCH_ITEM_IDS = new int[] {
        0,
        556, 555, 557, 554, 558, 562, 560, 565, 564,
        561, 563, 559, 566, 9075,
        4695, 4698, 4696, 4699, 4694, 4697,
        21880, 28929, 30843
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

    // When true, config-based weapon charges are excluded from per-tick snapshots.
    // WeaponChecklistOverlay owns charge tracking while active.
    private boolean weaponChecklistActive = false;

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
        // When checklist is active, use live-only ammo (BUFF_BAR varps) so arrows/bolts
        // are still tracked while stale config-based weapon data is excluded.
        Map<Integer, Integer> currentWeaponAmmo = weaponChecklistActive
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

    void setWeaponChecklistActive(boolean active) {
        weaponChecklistActive = active;
        if (active) {
            // initializeForArenaEntry() ran before this flag was set, so it populated
            // lastWeaponAmmoSnapshot with readWeaponAmmo() which includes stale config data.
            // Rebuild with live-only data so the first onGameTick has a clean baseline.
            lastWeaponAmmoSnapshot.clear();
            lastWeaponAmmoSnapshot.putAll(readLiveWeaponAmmo());
            lastCombinedSnapshot.clear();
            lastCombinedSnapshot.putAll(buildCombinedSnapshot());
        }
    }

    /** Reads current charges for all tracked weapons (used for initial/final snapshots). */
    Map<Integer, Integer> readAllTrackedCharges() {
        return readWeaponAmmo();
    }

    /**
     * Reads the current charge state for a single weapon from tictac7x config.
     * Used by the weapon checklist to capture a per-weapon snapshot at check time.
     */
    Map<Integer, Integer> readWeaponCharges(TrackedWeapon weapon) {
        Map<Integer, Integer> map = new HashMap<>();
        if (weapon.configFormat == TrackedWeapon.ConfigFormat.BLOWPIPE_JSON) {
            tryReadBlowpipeJson(map, weapon);
        } else {
            int canonicalId = weapon.itemIds[0];
            if (canonicalId <= 0) return map;
            String value = configManager.getConfiguration(WEAPON_CHARGES_CONFIG_GROUP, weapon.tictacConfigKey);
            if (value == null || value.isEmpty()) return map;
            try {
                int charges = Integer.parseInt(value.trim());
                if (charges > 0) {
                    map.put(canonicalId, charges);
                }
            } catch (NumberFormatException ignored) {}
        }
        return map;
    }

    /**
     * Scans player inventory and equipment for any TrackedWeapon item IDs.
     * Returns the ordered list of weapons found (preserving TrackedWeapon declaration order).
     */
    List<TrackedWeapon> detectWeaponsOnPlayer() {
        Set<Integer> playerItemIds = new HashSet<>();

        ItemContainer inventory = client.getItemContainer(INVENTORY_CONTAINER_ID);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item != null && item.getId() > 0) {
                    playerItemIds.add(item.getId());
                }
            }
        }

        ItemContainer equipment = client.getItemContainer(EQUIPMENT_CONTAINER_ID);
        if (equipment != null) {
            for (Item item : equipment.getItems()) {
                if (item != null && item.getId() > 0) {
                    playerItemIds.add(item.getId());
                }
            }
        }

        List<TrackedWeapon> found = new ArrayList<>();
        for (TrackedWeapon weapon : TrackedWeapon.values()) {
            for (int id : weapon.itemIds) {
                if (id > 0 && playerItemIds.contains(id)) {
                    found.add(weapon);
                    break;
                }
            }
        }
        return found;
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
        // When checklist is active, use live-only ammo so config-based weapon data is excluded.
        Map<Integer, Integer> weaponAmmo = weaponChecklistActive
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
                    combined.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }

        ItemContainer equipment = client.getItemContainer(EQUIPMENT_CONTAINER_ID);
        if (equipment != null) {
            for (Item item : equipment.getItems()) {
                if (item != null && item.getId() > 0) {
                    combined.merge(item.getId(), item.getQuantity(), Integer::sum);
                }
            }
        }

        for (Map.Entry<Integer, Integer> entry : readRunePouch().entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        for (Map.Entry<Integer, Integer> entry : weaponAmmo.entrySet()) {
            combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }

        return combined;
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
            map.merge(quiverAmmoId, quiverAmmoCount, Integer::sum);
        }

        return map;
    }

    // Returns live ammo first (BUFF_BAR varps, updated every tick).
    // If no live ammo, tries BLOWPIPE_JSON weapons (equipped one preferred).
    // INTEGER-format weapon charges are always appended.
    private Map<Integer, Integer> readWeaponAmmo() {
        Map<Integer, Integer> map = readLiveWeaponAmmo();
        if (!map.isEmpty()) {
            readIntegerFormatCharges(map);
            return map;
        }

        // Find which BLOWPIPE_JSON weapon is currently equipped (if any)
        int equippedWeaponId = client.getVarpValue(BUFF_BAR_WEAPON);
        TrackedWeapon preferredBlowpipe = null;
        for (TrackedWeapon w : TrackedWeapon.values()) {
            if (w.configFormat != TrackedWeapon.ConfigFormat.BLOWPIPE_JSON) continue;
            for (int id : w.itemIds) {
                if (id == equippedWeaponId) {
                    preferredBlowpipe = w;
                    break;
                }
            }
            if (preferredBlowpipe != null) break;
        }

        // Try equipped blowpipe first, then all others in declaration order
        boolean found = preferredBlowpipe != null && tryReadBlowpipeJson(map, preferredBlowpipe);
        if (!found) {
            for (TrackedWeapon w : TrackedWeapon.values()) {
                if (w.configFormat != TrackedWeapon.ConfigFormat.BLOWPIPE_JSON) continue;
                if (w == preferredBlowpipe) continue;
                if (tryReadBlowpipeJson(map, w)) break;
            }
        }

        readIntegerFormatCharges(map);
        return map;
    }

    private boolean tryReadBlowpipeJson(Map<Integer, Integer> map, TrackedWeapon weapon) {
        String serialized = configManager.getConfiguration(WEAPON_CHARGES_CONFIG_GROUP, weapon.tictacConfigKey);
        if (serialized == null || serialized.isEmpty()) return false;
        log.debug("[Mokha] {} storage raw config: {}", weapon.displayName, serialized);
        try {
            BlowpipeStorageEntry[] entries = gson.fromJson(serialized, BlowpipeStorageEntry[].class);
            boolean hadEntries = false;
            if (entries != null) {
                for (BlowpipeStorageEntry entry : entries) {
                    if (entry != null && entry.itemId > 0 && entry.quantity > 0) {
                        map.merge(entry.itemId, entry.quantity, Integer::sum);
                        hadEntries = true;
                    }
                }
            }
            return hadEntries;
        } catch (JsonParseException ex) {
            return false;
        }
    }

    private void readIntegerFormatCharges(Map<Integer, Integer> map) {
        for (TrackedWeapon weapon : TrackedWeapon.values()) {
            if (weapon.configFormat != TrackedWeapon.ConfigFormat.INTEGER) continue;
            int canonicalId = weapon.itemIds[0];
            if (canonicalId <= 0) continue;
            String value = configManager.getConfiguration(WEAPON_CHARGES_CONFIG_GROUP, weapon.tictacConfigKey);
            if (value == null || value.isEmpty()) continue;
            try {
                int charges = Integer.parseInt(value.trim());
                if (charges > 0) {
                    map.merge(canonicalId, charges, Integer::sum);
                }
            } catch (NumberFormatException ignored) {}
        }
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
            if (runeVar <= 0 || amt <= 0 || runeVar >= RUNE_POUCH_ITEM_IDS.length) continue;
            int itemId = RUNE_POUCH_ITEM_IDS[runeVar];
            if (itemId <= 0) continue;
            map.merge(itemId, amt, Integer::sum);
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
