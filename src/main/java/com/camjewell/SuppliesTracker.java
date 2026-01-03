package com.camjewell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camjewell.util.PotionUtil;
import com.camjewell.util.RunePouchUtil;

import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.client.game.ItemManager;

class SuppliesTracker {
    private static final Logger log = LoggerFactory.getLogger(SuppliesTracker.class);

    private final Client client;
    private final ItemManager itemManager;
    private final MokhaLootTrackerConfig config;
    private final LootValueService lootValueService;

    private final Map<Integer, Integer> initialInventory = new HashMap<>();
    private final Map<Integer, Integer> currentInventory = new HashMap<>();
    private final Map<Integer, Integer> maxConsumedThisRun = new HashMap<>();
    private final Set<Integer> initialEquipmentIds = new HashSet<>();
    private final Set<Integer> equipmentSeenIds = new HashSet<>();
    private final Map<String, Integer> initialPotionSips = new HashMap<>();
    private final Map<String, Integer> maxConsumedPotionSips = new HashMap<>();
    private final Map<String, Map<Integer, Integer>> livePotionDoseIds = new HashMap<>();
    private final Map<Integer, Integer> initialRunePouch = new HashMap<>();
    private final Map<Integer, Integer> currentRunePouch = new HashMap<>();
    private final Map<Integer, Integer> maxConsumedRunes = new HashMap<>();

    private boolean hasInitialInventory = false;

    SuppliesTracker(Client client, ItemManager itemManager, MokhaLootTrackerConfig config,
            LootValueService lootValueService) {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
        this.lootValueService = lootValueService;
    }

    boolean hasInitialInventory() {
        return hasInitialInventory;
    }

    void reset() {
        initialInventory.clear();
        currentInventory.clear();
        maxConsumedThisRun.clear();
        initialEquipmentIds.clear();
        equipmentSeenIds.clear();
        initialPotionSips.clear();
        maxConsumedPotionSips.clear();
        livePotionDoseIds.clear();
        initialRunePouch.clear();
        currentRunePouch.clear();
        maxConsumedRunes.clear();
        hasInitialInventory = false;
    }

    void captureInitialSuppliesSnapshot(ItemContainer inventoryContainer, ItemContainer equipmentContainer) {
        if (inventoryContainer == null || hasInitialInventory) {
            return;
        }

        initialInventory.clear();
        initialPotionSips.clear();
        maxConsumedThisRun.clear();
        initialEquipmentIds.clear();
        equipmentSeenIds.clear();
        maxConsumedPotionSips.clear();
        livePotionDoseIds.clear();
        initialRunePouch.clear();
        currentRunePouch.clear();
        maxConsumedRunes.clear();

        initialRunePouch.putAll(RunePouchUtil.readRunePouch(client));
        currentRunePouch.putAll(initialRunePouch);

        for (Item item : inventoryContainer.getItems()) {
            if (item == null || item.getId() <= 0) {
                continue;
            }
            initialInventory.put(item.getId(), initialInventory.getOrDefault(item.getId(), 0) + item.getQuantity());
            String itemName = itemManager.getItemComposition(item.getId()).getName();
            if (PotionUtil.isPotion(itemName)) {
                String base = PotionUtil.getPotionBaseName(itemName);
                int dose = PotionUtil.getPotionDose(itemName);
                int sips = dose * item.getQuantity();
                initialPotionSips.put(base, initialPotionSips.getOrDefault(base, 0) + sips);
                livePotionDoseIds.computeIfAbsent(base, k -> new HashMap<>()).putIfAbsent(dose, item.getId());
            }
        }

        if (equipmentContainer != null) {
            for (Item item : equipmentContainer.getItems()) {
                if (item == null || item.getId() <= 0) {
                    continue;
                }
                initialEquipmentIds.add(item.getId());
                equipmentSeenIds.add(item.getId());
                initialInventory.put(item.getId(), initialInventory.getOrDefault(item.getId(), 0) + item.getQuantity());
                String itemName = itemManager.getItemComposition(item.getId()).getName();
                if (PotionUtil.isPotion(itemName)) {
                    String base = PotionUtil.getPotionBaseName(itemName);
                    int dose = PotionUtil.getPotionDose(itemName);
                    int sips = dose * item.getQuantity();
                    initialPotionSips.put(base, initialPotionSips.getOrDefault(base, 0) + sips);
                    livePotionDoseIds.computeIfAbsent(base, k -> new HashMap<>()).putIfAbsent(dose, item.getId());
                }
            }
        }

        hasInitialInventory = true;

        if (config.debugItemValueLogging()) {
            int invCount = inventoryContainer.getItems().length;
            int equipCount = equipmentContainer != null ? equipmentContainer.getItems().length : 0;
            log.info(
                    "[Supplies Debug] Captured initial snapshot (inventory {} slots, equipment {} slots, merged {} entries)",
                    invCount, equipCount, initialInventory.size());
            logInventory("Inventory", inventoryContainer);
            logInventory("Equipment", equipmentContainer);
        }
    }

    void handleItemContainerChange(ItemContainer inventoryContainer, ItemContainer equipmentContainer) {
        if (inventoryContainer == null) {
            return;
        }

        if (!hasInitialInventory) {
            captureInitialSuppliesSnapshot(inventoryContainer, equipmentContainer);
        }

        currentInventory.clear();
        for (Item item : inventoryContainer.getItems()) {
            if (item != null && item.getId() > 0) {
                currentInventory.put(item.getId(), currentInventory.getOrDefault(item.getId(), 0) + item.getQuantity());
                String itemName = itemManager.getItemComposition(item.getId()).getName();
                if (PotionUtil.isPotion(itemName)) {
                    String base = PotionUtil.getPotionBaseName(itemName);
                    int dose = PotionUtil.getPotionDose(itemName);
                    livePotionDoseIds.computeIfAbsent(base, k -> new HashMap<>()).putIfAbsent(dose, item.getId());
                }
            }
        }

        if (equipmentContainer != null) {
            for (Item item : equipmentContainer.getItems()) {
                if (item != null && item.getId() > 0) {
                    equipmentSeenIds.add(item.getId());
                    currentInventory.put(item.getId(),
                            currentInventory.getOrDefault(item.getId(), 0) + item.getQuantity());
                    String itemName = itemManager.getItemComposition(item.getId()).getName();
                    if (PotionUtil.isPotion(itemName)) {
                        String base = PotionUtil.getPotionBaseName(itemName);
                        int dose = PotionUtil.getPotionDose(itemName);
                        livePotionDoseIds.computeIfAbsent(base, k -> new HashMap<>()).putIfAbsent(dose, item.getId());
                    }
                }
            }
        }

        // If everything vanished (e.g., gravestone wipe), skip consumption updates for
        // this tick
        if (currentInventory.isEmpty()) {
            if (config.debugItemValueLogging()) {
                log.info("[Supplies Debug] Inventory/equipment empty snapshot detected; skipping consumption update");
            }
            return;
        }

        // If the initial capture missed equipment (null container at that time),
        // backfill
        if (hasInitialInventory && initialEquipmentIds.isEmpty() && equipmentContainer != null) {
            for (Item item : equipmentContainer.getItems()) {
                if (item != null && item.getId() > 0) {
                    initialEquipmentIds.add(item.getId());
                    equipmentSeenIds.add(item.getId());
                }
            }
        }

        currentRunePouch.clear();
        currentRunePouch.putAll(RunePouchUtil.readRunePouch(client));
        for (Map.Entry<Integer, Integer> entry : currentRunePouch.entrySet()) {
            initialRunePouch.putIfAbsent(entry.getKey(), 0);
        }
        for (Map.Entry<Integer, Integer> entry : initialRunePouch.entrySet()) {
            int itemId = entry.getKey();
            int initialQty = entry.getValue();
            int currentQty = currentRunePouch.getOrDefault(itemId, 0);
            int usedNow = initialQty - currentQty;
            if (usedNow > 0) {
                int prevMax = maxConsumedRunes.getOrDefault(itemId, 0);
                if (usedNow > prevMax) {
                    maxConsumedRunes.put(itemId, usedNow);
                }
            }
        }

        for (Map.Entry<Integer, Integer> entry : initialInventory.entrySet()) {
            int itemId = entry.getKey();
            int initialQty = entry.getValue();
            int currentQty = currentInventory.getOrDefault(itemId, 0);
            int usedNow = initialQty - currentQty;

            // Don't flag gear that has been equipped during the run as consumed when it
            // disappears
            // (e.g., death wipe or reclaim differences)
            if (initialEquipmentIds.contains(itemId) || equipmentSeenIds.contains(itemId)) {
                continue;
            }

            if (usedNow > 0) {
                int prevMax = maxConsumedThisRun.getOrDefault(itemId, 0);
                if (usedNow > prevMax) {
                    maxConsumedThisRun.put(itemId, usedNow);
                }
            }
        }

        Map<String, Integer> currentPotionSips = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : currentInventory.entrySet()) {
            int itemId = entry.getKey();
            int qty = entry.getValue();
            String itemName = itemManager.getItemComposition(itemId).getName();
            if (PotionUtil.isPotion(itemName)) {
                String base = PotionUtil.getPotionBaseName(itemName);
                int dose = PotionUtil.getPotionDose(itemName);
                int sips = dose * qty;
                currentPotionSips.put(base, currentPotionSips.getOrDefault(base, 0) + sips);
                livePotionDoseIds.computeIfAbsent(base, k -> new HashMap<>()).putIfAbsent(dose, itemId);
            }
        }
        for (Map.Entry<String, Integer> entry : initialPotionSips.entrySet()) {
            String base = entry.getKey();
            int initialSips = entry.getValue();
            int currentSips = currentPotionSips.getOrDefault(base, 0);
            int consumed = initialSips - currentSips;
            if (consumed > 0) {
                int prevMax = maxConsumedPotionSips.getOrDefault(base, 0);
                if (consumed > prevMax) {
                    maxConsumedPotionSips.put(base, consumed);
                }
            }
        }

        if (config.debugItemValueLogging()) {
            int invCount = inventoryContainer.getItems().length;
            int equipCount = equipmentContainer != null ? equipmentContainer.getItems().length : 0;
            log.info("[Supplies Debug] Current snapshot (inventory {} slots, equipment {} slots, merged {} entries)",
                    invCount, equipCount, currentInventory.size());
            logInventory("Inventory", inventoryContainer);
            logInventory("Equipment", equipmentContainer);
        }
    }

    long getSuppliesUsedValue() {
        if (!hasInitialInventory) {
            return 0;
        }

        long total = 0;
        for (LootItem li : getSuppliesUsedItems()) {
            if (li.getId() > 0) {
                long priceEach = lootValueService.getPotionDoseAdjustedPriceEach(li.getId());
                total += priceEach * li.getQuantity();
            }
        }
        return total;
    }

    List<LootItem> getSuppliesUsedItems() {
        if (!hasInitialInventory) {
            return new ArrayList<>();
        }

        Map<Integer, Integer> usageMap = new HashMap<>();
        Map<String, Integer> potionSipsUsed = new HashMap<>();
        Map<String, Map<Integer, Integer>> potionDoseIds = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : initialInventory.entrySet()) {
            int itemId = entry.getKey();
            int usedDelta = entry.getValue() - currentInventory.getOrDefault(itemId, 0);
            int trackedMax = maxConsumedThisRun.getOrDefault(itemId, 0);
            String name = itemManager.getItemComposition(itemId).getName();

            if (PotionUtil.isPotion(name)) {
                String base = PotionUtil.getPotionBaseName(name);
                int dose = PotionUtil.getPotionDose(name);
                potionDoseIds.computeIfAbsent(base, k -> new HashMap<>()).putIfAbsent(dose, itemId);
                // Potions are handled via sip diffs below; we don't rely on trackedMax for them
                if (usedDelta <= 0 && trackedMax <= 0) {
                    continue;
                }
            } else {
                int used = trackedMax;
                // If we never observed consumption, don't count gravestone depletion
                if (used <= 0) {
                    continue;
                }
                // Skip gear that was equipped at run start (death reclaim should not count it)
                if (initialEquipmentIds.contains(itemId)) {
                    continue;
                }
                usageMap.put(itemId, used);
            }
        }

        for (Map.Entry<Integer, Integer> entry : initialRunePouch.entrySet()) {
            int itemId = entry.getKey();
            int initialQty = entry.getValue();
            int currentQty = currentRunePouch.getOrDefault(itemId, 0);
            int netUsed = initialQty - currentQty;
            int trackedMax = maxConsumedRunes.getOrDefault(itemId, 0);
            int used = Math.max(netUsed, trackedMax);
            if (used > 0) {
                usageMap.put(itemId, usageMap.getOrDefault(itemId, 0) + used);
            }
        }

        if (initialPotionSips.isEmpty()) {
            initialPotionSips.putAll(computePotionSips(initialInventory));
        }
        Map<String, Integer> currentPotionSips = computePotionSips(currentInventory);
        for (Map.Entry<String, Integer> entry : initialPotionSips.entrySet()) {
            String base = entry.getKey();
            int initialSips = entry.getValue();
            int currentSips = currentPotionSips.getOrDefault(base, 0);
            int consumed = initialSips - currentSips;
            if (consumed > 0) {
                potionSipsUsed.put(base, consumed);
            }
        }

        List<LootItem> supplies = buildSuppliesFromUsage(usageMap, true);

        for (Map.Entry<String, Integer> entry : potionSipsUsed.entrySet()) {
            String base = entry.getKey();
            int totalDoses = entry.getValue();
            if (totalDoses <= 0) {
                continue;
            }

            String doseName = base + " (1)";
            int potionId = resolvePotionId(base, 1, potionDoseIds, doseName);
            if (potionId == 0) {
                potionId = resolvePotionId(base, 1, livePotionDoseIds, doseName);
            }
            if (potionId == 0) {
                Integer observedAny = potionDoseIds.getOrDefault(base, java.util.Collections.emptyMap())
                        .values().stream().findFirst().orElse(0);
                if (observedAny == 0) {
                    observedAny = livePotionDoseIds.getOrDefault(base, java.util.Collections.emptyMap())
                            .values().stream().findFirst().orElse(0);
                }
                potionId = observedAny;
            }
            supplies.add(new LootItem(potionId, totalDoses, doseName));
            if (config.debugItemValueLogging()) {
                log.info("[Supplies Debug]   Added {} x{} (id={})", doseName, totalDoses, potionId);
            }
        }

        if (config.debugItemValueLogging()) {
            log.info("[Supplies Debug] Final supplies list size: {}", supplies.size());
        }

        return supplies;
    }

    long getLiveSuppliesUsedValue() {
        if (!hasInitialInventory) {
            return 0;
        }

        long total = 0;
        for (LootItem li : getLiveSuppliesUsedItems()) {
            if (li.getId() > 0) {
                long priceEach = lootValueService.getPotionDoseAdjustedPriceEach(li.getId());
                total += priceEach * li.getQuantity();
            }
        }
        return total;
    }

    List<LootItem> getLiveSuppliesUsedItems() {
        if (!hasInitialInventory) {
            return new ArrayList<>();
        }

        Map<Integer, Integer> usageMap = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : maxConsumedThisRun.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            int itemId = entry.getKey();
            String name = itemManager.getItemComposition(itemId).getName();
            if (initialEquipmentIds.contains(itemId) || equipmentSeenIds.contains(itemId)) {
                continue;
            }
            if (PotionUtil.isPotion(name)) {
                continue;
            }
            usageMap.put(itemId, entry.getValue());
        }

        for (Map.Entry<Integer, Integer> entry : maxConsumedRunes.entrySet()) {
            int used = entry.getValue();
            if (used > 0) {
                int itemId = entry.getKey();
                usageMap.put(itemId, usageMap.getOrDefault(itemId, 0) + used);
            }
        }

        List<LootItem> nonPotion = buildSuppliesFromUsage(usageMap, false);

        List<LootItem> potions = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : maxConsumedPotionSips.entrySet()) {
            String base = entry.getKey();
            int totalDoses = entry.getValue();
            if (totalDoses <= 0) {
                continue;
            }

            String name = base + " (1)";
            int id = resolvePotionId(base, 1, livePotionDoseIds, name);
            potions.add(new LootItem(id, totalDoses, name));
        }

        List<LootItem> combined = new ArrayList<>(nonPotion.size() + potions.size());
        combined.addAll(nonPotion);
        combined.addAll(potions);
        return combined;
    }

    String mergeSuppliesTotals(String existingSerialized, List<LootItem> newItems) {
        Map<String, Integer> potionSips = new HashMap<>();
        Map<String, Map<Integer, Integer>> observedDoseIds = new HashMap<>();
        Map<Integer, Integer> nonPotions = new HashMap<>();

        java.util.function.BiConsumer<LootItem, Boolean> addItem = (item, fromNew) -> {
            if (item == null) {
                return;
            }
            String name = item.getName();
            if (name == null) {
                try {
                    name = itemManager.getItemComposition(item.getId()).getName();
                } catch (Exception e) {
                }
            }

            if (name != null && PotionUtil.isPotion(name)) {
                int dose = PotionUtil.getPotionDose(name);
                if (dose > 0) {
                    String base = PotionUtil.getPotionBaseName(name);
                    int sips = dose * item.getQuantity();
                    potionSips.put(base, potionSips.getOrDefault(base, 0) + sips);
                    if (fromNew) {
                        observedDoseIds.computeIfAbsent(base, k -> new HashMap<>()).putIfAbsent(dose, item.getId());
                    }
                    return;
                }
            }

            nonPotions.put(item.getId(), nonPotions.getOrDefault(item.getId(), 0) + item.getQuantity());
        };

        if (existingSerialized != null && !existingSerialized.isEmpty()) {
            String[] pairs = existingSerialized.split(",");
            for (String pair : pairs) {
                try {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        int itemId = Integer.parseInt(parts[0]);
                        int qty = Integer.parseInt(parts[1]);
                        String itemName = null;
                        try {
                            if (client.isClientThread()) {
                                itemName = itemManager.getItemComposition(itemId).getName();
                            }
                        } catch (Exception e) {
                        }
                        LootItem li = new LootItem(itemId, qty, itemName);
                        if (itemName != null && PotionUtil.isPotion(itemName)) {
                            String base = PotionUtil.getPotionBaseName(itemName);
                            int dose = PotionUtil.getPotionDose(itemName);
                            if (dose > 0) {
                                observedDoseIds.computeIfAbsent(base, k -> new HashMap<>()).putIfAbsent(dose, itemId);
                            }
                        }
                        addItem.accept(li, false);
                    }
                } catch (NumberFormatException e) {
                    log.error("Error parsing supplies item pair: {}", pair, e);
                }
            }
        }

        for (LootItem li : newItems) {
            addItem.accept(li, true);
        }

        List<LootItem> normalized = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : potionSips.entrySet()) {
            String base = entry.getKey();
            int totalDoses = entry.getValue();
            if (totalDoses <= 0) {
                continue;
            }
            String name = base + " (1)";
            int id = resolvePotionId(base, 1, observedDoseIds, name);
            if (id == 0) {
                Integer observedAny = observedDoseIds.getOrDefault(base, java.util.Collections.emptyMap())
                        .values().stream().findFirst().orElse(0);
                id = observedAny;
            }
            normalized.add(new LootItem(id, totalDoses, name));
        }

        for (Map.Entry<Integer, Integer> entry : nonPotions.entrySet()) {
            normalized.add(new LootItem(entry.getKey(), entry.getValue(), null));
        }

        return serializeItems(normalized);
    }

    private Map<String, Integer> computePotionSips(Map<Integer, Integer> inventory) {
        Map<String, Integer> sips = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : inventory.entrySet()) {
            int itemId = entry.getKey();
            int qty = entry.getValue();
            String itemName = itemManager.getItemComposition(itemId).getName();
            if (PotionUtil.isPotion(itemName)) {
                String base = PotionUtil.getPotionBaseName(itemName);
                int dose = PotionUtil.getPotionDose(itemName);
                int total = dose * qty;
                sips.put(base, sips.getOrDefault(base, 0) + total);
            }
        }
        return sips;
    }

    private List<LootItem> buildSuppliesFromUsage(Map<Integer, Integer> usageMap, boolean logItems) {
        List<LootItem> supplies = new ArrayList<>();
        Map<String, Integer> potionTotalSips = new HashMap<>();
        Map<String, Map<Integer, Integer>> potionDoseIds = new HashMap<>();

        if (logItems && config.debugItemValueLogging()) {
            log.info("[Supplies Debug] Processing supplies used items...");
        }

        for (Map.Entry<Integer, Integer> entry : usageMap.entrySet()) {
            int itemId = entry.getKey();
            int used = entry.getValue();
            if (used <= 0) {
                continue;
            }

            String itemName = itemManager.getItemComposition(itemId).getName();
            if (logItems && config.debugItemValueLogging()) {
                log.info("[Supplies Debug] Processing item {} ({}): used={}", itemId, itemName, used);
            }

            if (PotionUtil.isPotion(itemName)) {
                String baseName = PotionUtil.getPotionBaseName(itemName);
                int dose = PotionUtil.getPotionDose(itemName);
                int totalSips = used * dose;
                potionTotalSips.put(baseName, potionTotalSips.getOrDefault(baseName, 0) + totalSips);
                potionDoseIds.computeIfAbsent(baseName, k -> new HashMap<>()).putIfAbsent(dose, itemId);
                if (logItems && config.debugItemValueLogging()) {
                    log.info("[Supplies Debug]   Potion: base={}, dose={}, sips added={}, total sips={}",
                            baseName, dose, totalSips, potionTotalSips.get(baseName));
                }
            } else {
                supplies.add(new LootItem(itemId, used, itemName));
                if (logItems && config.debugItemValueLogging()) {
                    log.info("[Supplies Debug]   Non-potion supply added: {} x{}", itemName, used);
                }
            }
        }

        for (Map.Entry<String, Integer> entry : potionTotalSips.entrySet()) {
            String baseName = entry.getKey();
            int totalDoses = entry.getValue();

            if (logItems && config.debugItemValueLogging()) {
                log.info("[Supplies Debug] Potion breakdown for {}: total doses={}", baseName, totalDoses);
            }

            if (totalDoses > 0) {
                String doseName = baseName + " (1)";
                int potionId = resolvePotionId(baseName, 1, potionDoseIds, doseName);
                supplies.add(new LootItem(potionId, totalDoses, doseName));
                if (logItems && config.debugItemValueLogging()) {
                    log.info("[Supplies Debug]   Added {} x{} (id={})", doseName, totalDoses, potionId);
                }
            }
        }

        if (logItems && config.debugItemValueLogging()) {
            log.info("[Supplies Debug] Final supplies list size: {}", supplies.size());
        }

        return supplies;
    }

    private int resolvePotionId(String baseName, int dose, Map<String, Map<Integer, Integer>> doseMap,
            String displayName) {
        try {
            Integer observed = doseMap.getOrDefault(baseName, java.util.Collections.emptyMap()).get(dose);
            if (observed != null) {
                return observed;
            }
            if (client.isClientThread()) {
                String searchName = baseName + " (" + dose + ")";
                List<net.runelite.http.api.item.ItemPrice> results = itemManager.search(searchName);
                if (results != null) {
                    for (net.runelite.http.api.item.ItemPrice ip : results) {
                        if (ip.getName().equalsIgnoreCase(searchName)) {
                            return ip.getId();
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Supplies Debug] Failed to resolve potion id for {}: {}", displayName, e.getMessage());
        }
        try {
            if (doseMap != null && doseMap.containsKey(baseName)) {
                Map<Integer, Integer> observed = doseMap.get(baseName);
                if (observed != null && !observed.isEmpty()) {
                    return observed.values().iterator().next();
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String serializeItems(List<LootItem> items) {
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

    private void logInventory(String label, ItemContainer container) {
        if (!config.debugItemValueLogging()) {
            return;
        }
        log.info("[Supplies Debug]   {} items:", label);
        if (container == null) {
            log.info("    (none)");
            return;
        }
        for (Item item : container.getItems()) {
            if (item != null && item.getId() > 0) {
                String itemName = itemManager.getItemComposition(item.getId()).getName();
                log.info("    {} x{}", itemName, item.getQuantity());
            }
        }
    }

}
