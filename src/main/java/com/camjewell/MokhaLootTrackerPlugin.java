package com.camjewell;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.inject.Provides;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(name = "Mokha Loot Tracker", description = "Tracks loot obtained from Mokhaiotl encounters", tags = {
        "mokha", "loot", "tracker", "mokhaiotl" })
public class MokhaLootTrackerPlugin extends Plugin {

    private static final Logger log = LoggerFactory.getLogger(MokhaLootTrackerPlugin.class);

    @Inject
    private Client client;

    @Inject
    private MokhaLootTrackerConfig config;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ItemManager itemManager;

    private MokhaLootPanel panel;
    private NavigationButton navButton;

    private final Gson gson = new Gson();

    // Arena state tracking
    private boolean inMokhaArena = false;
    private boolean isDead = false;
    private boolean lootWindowWasVisible = false;
    private int currentWaveNumber = 0;

    // Entrance coordinates (for future use)
    private int entrance_centerX = 1311;
    private int entrance_centerY = 9555;
    private int entrance_radius = 25;

    // Combined item tracking (inventory + equipment)
    private final Map<Integer, Integer> lastCombinedSnapshot = new HashMap<>();

    // Loot tracking by wave
    private final Map<Integer, List<LootItem>> lootByWave = new HashMap<>();
    private final Map<Integer, Integer> previousLootSnapshot = new HashMap<>();

    // Supply consumption tracking
    private final Map<Integer, Integer> initialSupplySnapshot = new HashMap<>();
    private final Map<Integer, Integer> totalSuppliesConsumed = new HashMap<>();

    // Historical tracking (persisted across runs)
    private long historicalTotalClaimed = 0;
    private long historicalSupplyCost = 0;
    private long historicalPotionsCost = 0;
    private long historicalFoodCost = 0;
    private long historicalRunesCost = 0;
    private long historicalAmmoCost = 0;
    private final Map<Integer, Long> historicalClaimedByWave = new HashMap<>(); // Wave -> Total GP claimed
    private final Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave = new HashMap<>(); // Wave ->
                                                                                                           // Item
                                                                                                           // aggregates
    private final Map<String, ItemAggregate> historicalSuppliesUsed = new HashMap<>(); // Item name -> aggregate

    // Historical unclaimed loot (persisted, never cleared)
    private final Map<Integer, Long> historicalUnclaimedByWave = new HashMap<>(); // Wave -> Total GP unclaimed
    private final Map<Integer, Map<String, ItemAggregate>> historicalUnclaimedItemsByWave = new HashMap<>(); // Wave ->
                                                                                                             // Item
                                                                                                             // aggregates

    // Rune pouch mapping (varbit values to item IDs)
    private static final int[] RUNE_POUCH_ITEM_IDS = new int[] {
            0, // 0 unused / empty
            ItemID.AIR_RUNE, // 1
            ItemID.WATER_RUNE, // 2
            ItemID.EARTH_RUNE, // 3
            ItemID.FIRE_RUNE, // 4
            ItemID.MIND_RUNE, // 5
            ItemID.CHAOS_RUNE, // 6
            ItemID.DEATH_RUNE, // 7
            ItemID.BLOOD_RUNE, // 8
            ItemID.COSMIC_RUNE, // 9
            ItemID.NATURE_RUNE, // 10
            ItemID.LAW_RUNE, // 11
            ItemID.BODY_RUNE, // 12
            ItemID.SOUL_RUNE, // 13
            ItemID.ASTRAL_RUNE, // 14
            ItemID.MIST_RUNE, // 15
            ItemID.MUD_RUNE, // 16
            ItemID.DUST_RUNE, // 17
            ItemID.LAVA_RUNE, // 18
            ItemID.STEAM_RUNE, // 19
            ItemID.SMOKE_RUNE, // 20
            ItemID.WRATH_RUNE, // 21
            ItemID.SUNFIRE_RUNE, // 22
            ItemID.AETHER_RUNE // 23
    };

    /**
     * Represents a single loot item with name, quantity, and value
     */
    private static class LootItem {
        String name;
        int quantity;
        int value;

        LootItem(String name, int quantity, int value) {
            this.name = name;
            this.quantity = quantity;
            this.value = value;
        }
    }

    /**
     * Aggregates items across multiple runs (for historical tracking)
     */
    private static class ItemAggregate {
        String name;
        int totalQuantity;
        int pricePerItem;
        long totalValue;

        ItemAggregate(String name, int quantity, int pricePerItem) {
            this.name = name;
            this.totalQuantity = quantity;
            this.pricePerItem = pricePerItem;
            this.totalValue = (long) pricePerItem * quantity;
        }

        void add(int quantity, int pricePerItem) {
            this.totalQuantity += quantity;
            this.totalValue += (long) pricePerItem * quantity;
            // Update price per item to latest (could also average, but latest is simpler)
            this.pricePerItem = pricePerItem;
        }
    }

    @Provides
    MokhaLootTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MokhaLootTrackerConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        panel = new MokhaLootPanel(config, this::debugLocation);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/48icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Mokha Loot Tracker")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        lastCombinedSnapshot.clear();

        // Load persisted historical data
        loadHistoricalData();
        updatePanelData();
    }

    @Override
    protected void shutDown() throws Exception {
        clientToolbar.removeNavigation(navButton);
        lastCombinedSnapshot.clear();

        // Save historical data before shutdown
        saveHistoricalData();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String option = event.getMenuOption();
        String target = event.getMenuTarget();

        // Detect entering arena via "Jump over gap"
        if (option != null && option.contains("Jump-over")) {
            if (!inMokhaArena) {
                // Fallback cleanup: if supplies were left from a previous run (e.g., death not
                // logged or disconnect),
                // move them to historical before starting new run
                if (!totalSuppliesConsumed.isEmpty()) {
                    log.warn(
                            "[Mokha] Fallback cleanup: Found {} orphaned supplies from previous run, moving to historical",
                            totalSuppliesConsumed.size());
                    for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
                        int itemId = entry.getKey();
                        int quantity = entry.getValue();
                        String itemName = getBasePotionName(itemManager.getItemComposition(itemId).getName());
                        int pricePerItem = getPricePerDose(itemId);

                        if (historicalSuppliesUsed.containsKey(itemName)) {
                            historicalSuppliesUsed.get(itemName).add(quantity, pricePerItem);
                        } else {
                            historicalSuppliesUsed.put(itemName, new ItemAggregate(itemName, quantity, pricePerItem));
                        }
                    }
                    calculateSuppliesCost(); // Update historical category costs
                }

                inMokhaArena = true;
                currentWaveNumber = 1; // Start at wave 1
                lootByWave.clear();
                previousLootSnapshot.clear();
                totalSuppliesConsumed.clear();
                log.info("[Mokha Debug] Entered Mokha arena (jumped over gap)");
                // Take initial snapshot when entering arena
                lastCombinedSnapshot.clear();
                lastCombinedSnapshot.putAll(buildCombinedSnapshot());
                // Take initial supply snapshot
                initialSupplySnapshot.clear();
                initialSupplySnapshot.putAll(buildCombinedSnapshot());
                if (config.debugLogging()) {
                    log.info("[Mokha Debug] Initial snapshot taken with {} unique items", lastCombinedSnapshot.size());
                }
            }
        }

        // Detect "Descend" button - continues to next wave without claiming loot
        if (option != null && option.equalsIgnoreCase("Descend")) {
            log.info("[Mokha] Pressed DESCEND button - continuing to wave {} (loot unclaimed, will accumulate)",
                    currentWaveNumber + 1);
            // Print accumulated loot so far (not claimed yet, could still be lost)
            printSuppliesConsumed();
            printAccumulatedLoot();
            // Increment wave number since we're moving to the next wave
            currentWaveNumber++;
        }

        // Detect "Claim and leave" button - shows confirmation dialog
        if (option != null && option.contains("Claim and leave")) {
            log.info("[Mokha] Pressed CLAIM AND LEAVE button - confirming exit");
            // Don't print yet - wait for full sequence completion
        }

        // Detect "Confirm" button - appears after "Claim and leave"
        if (option != null && option.equalsIgnoreCase("Confirm")) {
            log.info("[Mokha] Pressed CONFIRM button - exiting to leave screen");
            // Don't print yet - wait for Leave button
        }

        // Detect "Leave" button - returns to entrance (only available after confirming
        // claim)
        if (option != null && option.equals("Leave") && !option.contains("Claim")) {
            log.info("[Mokha] Pressed LEAVE button - claiming loot and returning to entrance");
            if (inMokhaArena) {
                // Print supplies consumed and claimed loot before clearing state
                printSuppliesConsumed();
                printAccumulatedLoot();

                // Update historical data with claimed loot
                updateHistoricalDataOnClaim();

                // Save historical data immediately after claiming
                saveHistoricalData();

                // Clear all tracking state
                inMokhaArena = false;
                isDead = false;
                currentWaveNumber = 1;
                lastCombinedSnapshot.clear();
                lootByWave.clear();
                previousLootSnapshot.clear();
                totalSuppliesConsumed.clear();
                initialSupplySnapshot.clear();

                // Update panel to show cleared current run data
                updatePanelData();
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        boolean wasInArena = inMokhaArena;

        // Check for player death
        if (client.getLocalPlayer() != null) {
            boolean currentlyDead = client.getLocalPlayer().getHealthRatio() == 0;

            if (currentlyDead && !isDead && inMokhaArena) {
                isDead = true;
                log.info("[Mokha] ===== PLAYER DIED ON WAVE {} =====", currentWaveNumber);

                // Print supplies consumed before death
                printSuppliesConsumed();

                // Print lost loot from previous waves (couldn't claim because of death)
                printLostLoot();

                // Update historical supply costs (loot was lost, so don't count it)
                // But add supplies to historical tracking
                for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
                    int itemId = entry.getKey();
                    int quantity = entry.getValue();
                    String itemName = getBasePotionName(itemManager.getItemComposition(itemId).getName());
                    int pricePerItem = getPricePerDose(itemId);

                    // Update or add to historical supplies
                    if (historicalSuppliesUsed.containsKey(itemName)) {
                        historicalSuppliesUsed.get(itemName).add(quantity, pricePerItem);
                    } else {
                        historicalSuppliesUsed.put(itemName, new ItemAggregate(itemName, quantity, pricePerItem));
                    }
                }

                calculateSuppliesCost(); // This updates historical category costs
                long suppliesCost = 0;
                for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
                    suppliesCost += (long) getPricePerDose(entry.getKey()) * entry.getValue();
                }
                historicalSupplyCost += suppliesCost;

                // Move all unclaimed loot from this run to historical (never claimed, so now
                // unclaimed forever)
                moveCurrentRunUnclaimedToHistorical();

                inMokhaArena = false; // Exit arena on death
                // Clear all tracking data
                lastCombinedSnapshot.clear();
                lootByWave.clear();
                previousLootSnapshot.clear();
                totalSuppliesConsumed.clear();
                initialSupplySnapshot.clear();

                // Update panel to show cleared current run data and updated historical costs
                updatePanelData();

                // Save persisted data
                saveHistoricalData();
            } else if (!currentlyDead && isDead) {
                isDead = false;
                if (config.debugLogging()) {
                    log.info("[Mokha Debug] Player respawned");
                }
            }
        }

        // Clear snapshot when leaving arena
        if (!inMokhaArena && wasInArena) {
            lastCombinedSnapshot.clear();
        }

        // Check for loot window and capture loot
        checkForLootWindow();

        // Panel updates are now only triggered when data changes:
        // - When loot is captured (in checkForLootWindow via parseLootItems)
        // - When supplies are consumed (in checkForConsumption)
        // - When claiming/dying (already handled above)
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        // Save data immediately when logging out or leaving the game
        net.runelite.api.GameState state = event.getGameState();
        if (state == net.runelite.api.GameState.LOGIN_SCREEN ||
                state == net.runelite.api.GameState.HOPPING) {
            if (inMokhaArena && !lootByWave.isEmpty()) {
                // If player is still in arena and has unclaimed loot, save it first
                moveCurrentRunUnclaimedToHistorical();
            }
            // Always save data on logout/hopping
            saveHistoricalData();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Only track item changes while in arena and alive
        if (!inMokhaArena || isDead) {
            return;
        }

        // Only process inventory or equipment containers
        if (event.getContainerId() != InventoryID.INVENTORY.getId() &&
                event.getContainerId() != InventoryID.EQUIPMENT.getId()) {
            return;
        }

        // Build combined snapshot from both containers
        Map<Integer, Integer> currentCombined = buildCombinedSnapshot();
        checkForConsumption(currentCombined);
    }

    private Map<Integer, Integer> buildCombinedSnapshot() {
        Map<Integer, Integer> combined = new HashMap<>();

        // Add inventory items
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item != null && item.getId() > 0) {
                    combined.put(item.getId(), combined.getOrDefault(item.getId(), 0) + item.getQuantity());
                }
            }
        }

        // Add equipment items
        ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipment != null) {
            for (Item item : equipment.getItems()) {
                if (item != null && item.getId() > 0) {
                    combined.put(item.getId(), combined.getOrDefault(item.getId(), 0) + item.getQuantity());
                }
            }
        }

        // Add rune pouch contents
        Map<Integer, Integer> runePouchRunes = readRunePouch();
        for (Map.Entry<Integer, Integer> entry : runePouchRunes.entrySet()) {
            combined.put(entry.getKey(), combined.getOrDefault(entry.getKey(), 0) + entry.getValue());
        }

        return combined;
    }

    /**
     * Read rune pouch contents using varbits
     * Supports both regular and divine rune pouches (up to 6 rune slots)
     */
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
                continue; // 0 means empty slot
            }
            if (runeVar >= RUNE_POUCH_ITEM_IDS.length) {
                continue; // Unknown rune index
            }
            int itemId = RUNE_POUCH_ITEM_IDS[runeVar];
            if (itemId <= 0) {
                continue;
            }
            map.put(itemId, map.getOrDefault(itemId, 0) + amt);
        }
        return map;
    }

    private void checkForConsumption(Map<Integer, Integer> currentCombined) {
        // Only log if we have a previous snapshot
        if (!lastCombinedSnapshot.isEmpty()) {
            StringBuilder consumed = new StringBuilder();
            boolean hasConsumption = false;

            // Only check for items that DECREASED (completely left the player)
            for (Map.Entry<Integer, Integer> entry : lastCombinedSnapshot.entrySet()) {
                int itemId = entry.getKey();
                int previousQty = entry.getValue();
                int currentQty = currentCombined.getOrDefault(itemId, 0);

                if (currentQty < previousQty) {
                    int consumedQty = previousQty - currentQty;
                    String itemName = itemManager.getItemComposition(itemId).getName();
                    consumed.append(
                            String.format("%s: %d → %d (-%d), ", itemName, previousQty, currentQty, consumedQty));

                    // Accumulate total supplies consumed
                    totalSuppliesConsumed.put(itemId, totalSuppliesConsumed.getOrDefault(itemId, 0) + consumedQty);
                    hasConsumption = true;
                }
            }

            // Log consumption (don't log additions or equipment swaps)
            if (consumed.length() > 0) {
                String logMsg = consumed.toString();
                if (logMsg.endsWith(", ")) {
                    logMsg = logMsg.substring(0, logMsg.length() - 2);
                }
                log.info("[Mokha Debug] Items consumed: {}", logMsg);
            }

            // Update panel when supplies are consumed
            if (hasConsumption) {
                updatePanelData();
            }
        }

        // Update combined snapshot
        lastCombinedSnapshot.clear();
        lastCombinedSnapshot.putAll(currentCombined);
    }

    private void checkForLootWindow() {
        // Widget Group 919 is the Mokhaiotl delve interface
        Widget mainWidget = client.getWidget(919, 2);
        boolean lootWindowVisible = mainWidget != null && !mainWidget.isHidden();

        if (lootWindowVisible) {
            // Only log loot once when window first becomes visible
            if (!lootWindowWasVisible) {
                // Try to extract wave number from widget text
                int detectedWave = extractWaveNumber(mainWidget);
                if (detectedWave > 0) {
                    currentWaveNumber = detectedWave;
                } else if (currentWaveNumber == 0) {
                    // Fallback: if we couldn't detect wave and it's still 0, assume wave 1
                    currentWaveNumber = 1;
                }

                if (config.debugLogging()) {
                    log.info("[Mokha Debug] Loot window detected - Wave {}", currentWaveNumber);
                }

                // Parse loot value from widget [919:20]
                Widget valueWidget = client.getWidget(919, 20);
                if (valueWidget != null) {
                    String valueText = valueWidget.getText();
                    if (valueText != null && valueText.contains("Value:")) {
                        // Extract value from "Value: 3,636 GP"
                        try {
                            String numStr = valueText.replaceAll("[^0-9]", "");
                            if (!numStr.isEmpty()) {
                                long totalValue = Long.parseLong(numStr);
                                log.info("[Mokha] Wave {} completed - Total loot value: {} gp", currentWaveNumber,
                                        totalValue);
                            }
                        } catch (Exception e) {
                            log.error("[Mokha] Error parsing loot value", e);
                        }
                    }
                }

                // Parse loot items from widget [919:19] children
                Widget lootContainerWidget = client.getWidget(919, 19);
                if (lootContainerWidget != null) {
                    parseLootItems(lootContainerWidget);
                }
            }
        }

        // Update window visibility state
        lootWindowWasVisible = lootWindowVisible;
    }

    private void parseLootItems(Widget containerWidget) {
        if (containerWidget == null) {
            return;
        }

        Widget[] children = containerWidget.getChildren();
        if (children == null) {
            return;
        }

        // Build current loot snapshot
        Map<Integer, Integer> currentLoot = new HashMap<>();
        for (Widget child : children) {
            if (child == null || child.isHidden()) {
                continue;
            }

            int itemId = child.getItemId();
            int itemQuantity = child.getItemQuantity();

            if (itemId > 0 && itemQuantity > 0) {
                String itemName = itemManager.getItemComposition(itemId).getName();
                // Filter out null, empty, or "null" items
                if (itemName != null && !itemName.isEmpty() && !itemName.equalsIgnoreCase("null")) {
                    currentLoot.put(itemId, itemQuantity);
                }
            }
        }

        // Determine NEW loot items for this wave by comparing with previous snapshot
        List<LootItem> newLootThisWave = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : currentLoot.entrySet()) {
            int itemId = entry.getKey();
            int currentQty = entry.getValue();
            int previousQty = previousLootSnapshot.getOrDefault(itemId, 0);

            if (currentQty > previousQty) {
                int newQty = currentQty - previousQty;
                String itemName = itemManager.getItemComposition(itemId).getName();
                int itemValue = itemManager.getItemPrice(itemId) * newQty;

                // Override value to 0 if config toggle is enabled for specific items
                if (config.ignoreSunKissedBonesValue() && itemName.equals("Sun-kissed bones")) {
                    itemValue = 0;
                }
                if (config.ignoreSpiritSeedsValue() && itemName.equals("Spirit seed")) {
                    itemValue = 0;
                }

                newLootThisWave.add(new LootItem(itemName, newQty, itemValue));
                log.info("[Mokha] Wave {} NEW Loot: {} x{} (value: {} gp)", currentWaveNumber, itemName, newQty,
                        itemValue);
            }
        }

        // Store new loot for this wave
        if (!newLootThisWave.isEmpty()) {
            lootByWave.put(currentWaveNumber, newLootThisWave);

            // Update panel when new loot is captured
            updatePanelData();
        }

        // Update previous loot snapshot
        previousLootSnapshot.clear();
        previousLootSnapshot.putAll(currentLoot);
    }

    /**
     * Extract wave number from the loot window widget
     * Searches through widget text for patterns like "Wave 1", "Wave 2", etc.
     */
    private int extractWaveNumber(Widget mainWidget) {
        if (mainWidget == null) {
            return 0;
        }

        // Check main widget text
        String text = mainWidget.getText();
        if (text != null && text.toLowerCase().contains("wave")) {
            try {
                // Extract number after "Wave"
                String[] parts = text.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if (parts[i].equalsIgnoreCase("wave")) {
                        return Integer.parseInt(parts[i + 1].replaceAll("[^0-9]", ""));
                    }
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }

        // Check children widgets
        Widget[] children = mainWidget.getChildren();
        if (children != null) {
            for (Widget child : children) {
                if (child != null) {
                    String childText = child.getText();
                    if (childText != null && childText.toLowerCase().contains("wave")) {
                        try {
                            String[] parts = childText.split("\\s+");
                            for (int i = 0; i < parts.length - 1; i++) {
                                if (parts[i].equalsIgnoreCase("wave")) {
                                    return Integer.parseInt(parts[i + 1].replaceAll("[^0-9]", ""));
                                }
                            }
                        } catch (Exception e) {
                            // Ignore parsing errors
                        }
                    }
                }
            }
        }

        return 0;
    }

    private void debugLocation() {
        if (client.getLocalPlayer() == null) {
            log.info("[Mokha Debug] No player location available");
            return;
        }

        net.runelite.api.coords.WorldPoint location = client.getLocalPlayer().getWorldLocation();
        if (location == null) {
            log.info("[Mokha Debug] No player location available");
            return;
        }

        // Check if at entrance
        boolean atEntrance = isAtEntrance(location);

        log.info("[Mokha Debug] ==================== DEBUG LOCATION ====================");
        log.info("[Mokha Debug] Current coordinates: X={}, Y={}, Plane={}", location.getX(), location.getY(),
                location.getPlane());
        log.info("[Mokha Debug] At entrance: {}", atEntrance);
        log.info("[Mokha Debug] In arena: {}", inMokhaArena);
        log.info("[Mokha Debug] Is dead: {}", isDead);
        log.info("[Mokha Debug] ======================================================");
    }

    /**
     * Track unclaimed loot by wave to historical data (persisted across sessions)
     */
    private void trackUnclaimedLoot(int wave, List<LootItem> items) {
        if (items.isEmpty()) {
            return;
        }

        // Update total unclaimed by wave
        long currentWaveTotal = historicalUnclaimedByWave.getOrDefault(wave, 0L);
        long newTotal = currentWaveTotal;
        Map<String, ItemAggregate> waveItems = historicalUnclaimedItemsByWave.getOrDefault(wave, new HashMap<>());

        for (LootItem item : items) {
            newTotal += item.value;

            // Track item details
            int pricePerItem = item.quantity > 0 ? (int) (item.value / item.quantity) : 0;
            if (waveItems.containsKey(item.name)) {
                waveItems.get(item.name).add(item.quantity, pricePerItem);
            } else {
                waveItems.put(item.name, new ItemAggregate(item.name, item.quantity, pricePerItem));
            }
        }

        historicalUnclaimedByWave.put(wave, newTotal);
        historicalUnclaimedItemsByWave.put(wave, waveItems);

        log.info("[Mokha] Wave {} unclaimed loot recorded: {} gp (historical total)", wave, newTotal);
    }

    /**
     * Move all current run unclaimed loot to historical when run ends (death or
     * disconnect)
     */
    private void moveCurrentRunUnclaimedToHistorical() {
        for (int wave = 1; wave <= 20; wave++) {
            List<LootItem> items = lootByWave.get(wave);
            if (items != null && !items.isEmpty()) {
                trackUnclaimedLoot(wave, items);
            }
        }
        log.info("[Mokha] Current run unclaimed loot moved to historical");
    }

    /**
     * Print supplies consumed during the run
     * Groups potions by base name (e.g., all Prayer potion doses together)
     */
    private void printSuppliesConsumed() {
        if (totalSuppliesConsumed.isEmpty()) {
            log.info("[Mokha] ===== NO SUPPLIES CONSUMED =====");
            return;
        }

        log.info("[Mokha] ===== SUPPLIES CONSUMED =====");
        long totalValue = 0;

        // Group items by base name (for potions, remove dose numbers)
        Map<String, Integer> groupedSupplies = new HashMap<>();
        Map<String, Integer> groupedValues = new HashMap<>();

        for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            String itemName = itemManager.getItemComposition(itemId).getName();
            int itemValue = itemManager.getItemPrice(itemId) * quantity;

            // Extract base name (remove dose numbers like (1), (2), (3), (4))
            String baseName = getBasePotionName(itemName);

            // Accumulate quantities and values by base name
            groupedSupplies.put(baseName, groupedSupplies.getOrDefault(baseName, 0) + quantity);
            groupedValues.put(baseName, groupedValues.getOrDefault(baseName, 0) + itemValue);
        }

        // Print grouped supplies
        for (Map.Entry<String, Integer> entry : groupedSupplies.entrySet()) {
            String baseName = entry.getKey();
            int quantity = entry.getValue();
            int value = groupedValues.get(baseName);

            // For potions, show as doses
            if (baseName.endsWith(" potion") || baseName.contains("potion")) {
                log.info("[Mokha]   - {} x{} doses (value: {} gp)", baseName, quantity, value);
            } else {
                log.info("[Mokha]   - {} x{} (value: {} gp)", baseName, quantity, value);
            }
            totalValue += value;
        }

        log.info("[Mokha] ===== TOTAL SUPPLIES VALUE: {} gp =====", totalValue);
    }

    /**
     * Extract base potion name by removing dose numbers
     * E.g., "Prayer potion(4)" -> "Prayer potion"
     * E.g., "Super combat potion(2)" -> "Super combat potion"
     */
    private String getBasePotionName(String itemName) {
        // Remove dose indicators like (1), (2), (3), (4)
        if (itemName.matches(".*\\(\\d+\\)$")) {
            return itemName.replaceAll("\\(\\d+\\)$", "").trim();
        }
        return itemName;
    }

    /**
     * Extract the number of doses from a potion name.
     * If no dose count found, defaults to 4.
     * Examples: "Super Restore(4)" -> 4, "Stamina Mix(2)" -> 2
     */
    private int getPotionDoseCount(String itemName) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\((\\d+)\\)$");
        java.util.regex.Matcher matcher = pattern.matcher(itemName);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 4; // Default to 4
            }
        }
        return 4; // Default to 4 if no dose count found
    }

    /**
     * Calculate the price per individual dose for a potion.
     * Takes the full potion price and divides by dose count.
     */
    private int getPricePerDose(int itemId) {
        String itemName = itemManager.getItemComposition(itemId).getName();
        int doseCount = getPotionDoseCount(itemName);
        int fullPrice = itemManager.getItemPrice(itemId);
        return fullPrice / doseCount; // Price per individual dose
    }

    /**
     * Load historical data from config
     */
    private void loadHistoricalData() {
        try {
            historicalTotalClaimed = config.historicalTotalClaimed();
            historicalSupplyCost = config.historicalSupplyCost();

            // Load claimed by wave
            String claimedJson = config.historicalClaimedByWaveJson();
            if (claimedJson != null && !claimedJson.isEmpty() && !claimedJson.equals("{}")) {
                try {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<Integer, Long>>() {
                    }.getType();
                    Map<Integer, Long> loaded = gson.fromJson(claimedJson, type);
                    if (loaded != null) {
                        historicalClaimedByWave.putAll(loaded);
                        log.info("[Mokha] Loaded {} historical claimed wave entries", loaded.size());
                    }
                } catch (Exception e) {
                    log.warn("[Mokha] Failed to load historical claimed by wave data", e);
                }
            }

            // Load historical claimed items by wave
            String claimedItemsJson = config.historicalClaimedItemsByWaveJson();
            if (claimedItemsJson != null && !claimedItemsJson.isEmpty() && !claimedItemsJson.equals("{}")) {
                try {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<Integer, Map<String, ItemAggregate>>>() {
                    }.getType();
                    Map<Integer, Map<String, ItemAggregate>> loaded = gson.fromJson(claimedItemsJson, type);
                    if (loaded != null) {
                        historicalClaimedItemsByWave.putAll(loaded);
                        log.info("[Mokha] Loaded {} historical claimed wave item entries", loaded.size());
                    }
                } catch (Exception e) {
                    log.warn("[Mokha] Failed to load historical claimed items by wave data", e);
                }
            }

            // Load supplies used
            String suppliesJson = config.historicalSuppliesUsedJson();
            if (suppliesJson != null && !suppliesJson.isEmpty() && !suppliesJson.equals("{}")) {
                try {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<String, ItemAggregate>>() {
                    }.getType();
                    Map<String, ItemAggregate> loaded = gson.fromJson(suppliesJson, type);
                    if (loaded != null) {
                        historicalSuppliesUsed.putAll(loaded);
                        log.info("[Mokha] Loaded {} historical supplies entries", loaded.size());
                    }
                } catch (Exception e) {
                    log.warn("[Mokha] Failed to load historical supplies data", e);
                }
            }

            // Load historical unclaimed by wave
            String unclaimedByWaveJson = config.historicalUnclaimedByWaveJson();
            if (unclaimedByWaveJson != null && !unclaimedByWaveJson.isEmpty() && !unclaimedByWaveJson.equals("{}")) {
                try {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<Integer, Long>>() {
                    }.getType();
                    Map<Integer, Long> loaded = gson.fromJson(unclaimedByWaveJson, type);
                    if (loaded != null) {
                        historicalUnclaimedByWave.putAll(loaded);
                        log.info("[Mokha] Loaded {} historical unclaimed wave entries", loaded.size());
                    }
                } catch (Exception e) {
                    log.warn("[Mokha] Failed to load historical unclaimed by wave data", e);
                }
            }

            // Load historical unclaimed items by wave
            String unclaimedItemsJson = config.historicalUnclaimedItemsByWaveJson();
            if (unclaimedItemsJson != null && !unclaimedItemsJson.isEmpty() && !unclaimedItemsJson.equals("{}")) {
                try {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<Integer, Map<String, ItemAggregate>>>() {
                    }.getType();
                    Map<Integer, Map<String, ItemAggregate>> loaded = gson.fromJson(unclaimedItemsJson, type);
                    if (loaded != null) {
                        historicalUnclaimedItemsByWave.putAll(loaded);
                        log.info("[Mokha] Loaded {} historical unclaimed wave item entries", loaded.size());
                    }
                } catch (Exception e) {
                    log.warn("[Mokha] Failed to load historical unclaimed items by wave data", e);
                }
            }

            // Load current run loot by wave
            String currentRunJson = config.currentRunLootByWaveJson();
            if (currentRunJson != null && !currentRunJson.isEmpty() && !currentRunJson.equals("{}")) {
                try {
                    java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<Map<Integer, List<LootItem>>>() {
                    }.getType();
                    Map<Integer, List<LootItem>> loaded = gson.fromJson(currentRunJson, type);
                    if (loaded != null) {
                        lootByWave.putAll(loaded);
                        log.info(
                                "[Mokha] Loaded {} current run loot wave entries - moving to historical (disconnected before claim)",
                                loaded.size());

                        // Move all loaded current run loot to historical (player disconnected mid-run)
                        moveCurrentRunUnclaimedToHistorical();
                        lootByWave.clear(); // Clear so it doesn't show in UI
                    }
                } catch (Exception e) {
                    log.warn("[Mokha] Failed to load current run loot by wave data", e);
                }
            }

            log.info("[Mokha] Historical data loaded - Claimed: {}, Supply Cost: {}",
                    historicalTotalClaimed, historicalSupplyCost);
        } catch (Exception e) {
            log.error("[Mokha] Error loading historical data", e);
        }
    }

    /**
     * Save historical data to config
     */
    private void saveHistoricalData() {
        try {
            config.setHistoricalTotalClaimed(historicalTotalClaimed);
            config.setHistoricalSupplyCost(historicalSupplyCost);

            // Save claimed by wave
            String claimedJson = gson.toJson(historicalClaimedByWave);
            config.setHistoricalClaimedByWaveJson(claimedJson);

            // Save claimed items by wave
            String claimedItemsJson = gson.toJson(historicalClaimedItemsByWave);
            config.setHistoricalClaimedItemsByWaveJson(claimedItemsJson);

            // Save supplies used
            String suppliesJson = gson.toJson(historicalSuppliesUsed);
            config.setHistoricalSuppliesUsedJson(suppliesJson);

            // Save unclaimed by wave
            String unclaimedByWaveJson = gson.toJson(historicalUnclaimedByWave);
            config.setHistoricalUnclaimedByWaveJson(unclaimedByWaveJson);

            // Save unclaimed items by wave
            String unclaimedItemsJson = gson.toJson(historicalUnclaimedItemsByWave);
            config.setHistoricalUnclaimedItemsByWaveJson(unclaimedItemsJson);

            // Save current run loot by wave
            String currentRunJson = gson.toJson(lootByWave);
            config.setCurrentRunLootByWaveJson(currentRunJson);

            log.info("[Mokha] Historical data saved - Claimed: {}, Supply Cost: {}",
                    historicalTotalClaimed, historicalSupplyCost);
        } catch (Exception e) {
            log.error("[Mokha] Error saving historical data", e);
        }
    }

    /**
     * Print accumulated loot by wave
     */
    private void printAccumulatedLoot() {
        if (lootByWave.isEmpty()) {
            log.info("[Mokha] ===== NO LOOT COLLECTED =====");
            return;
        }

        log.info("[Mokha] ===== ACCUMULATED LOOT BY WAVE =====");
        long totalValue = 0;

        // Iterate through all waves that have loot
        for (Map.Entry<Integer, List<LootItem>> entry : lootByWave.entrySet()) {
            int wave = entry.getKey();
            List<LootItem> waveLoot = entry.getValue();
            if (waveLoot != null && !waveLoot.isEmpty()) {
                long waveValue = 0;
                log.info("[Mokha] Wave {}:", wave);
                for (LootItem item : waveLoot) {
                    log.info("[Mokha]   - {} x{} (value: {} gp)", item.name, item.quantity, item.value);
                    waveValue += item.value;
                    totalValue += item.value;
                }
                log.info("[Mokha]   Wave {} Total: {} gp", wave, waveValue);
            }
        }

        log.info("[Mokha] ===== TOTAL LOOT VALUE: {} gp =====", totalValue);
    }

    /**
     * Print loot lost from death (loot that could have been claimed but wasn't)
     * This includes all loot from completed waves up to the wave they died on
     */
    private void printLostLoot() {
        // Calculate which waves had claimable loot (all waves before the death wave)
        // If died on wave 1, there's no lost loot
        // If died on wave 2+, we lost loot from all previous waves

        if (currentWaveNumber <= 1 || lootByWave.isEmpty()) {
            log.info("[Mokha] ===== NO LOOT LOST (died on wave 1) =====");
            return;
        }

        log.info("[Mokha] ===== LOOT LOST FROM DEATH =====");
        long totalValue = 0;
        int lostWaves = 0;

        // Only count loot from waves BEFORE the current wave (the one they died on)
        for (int wave = 1; wave < currentWaveNumber; wave++) {
            List<LootItem> waveLoot = lootByWave.get(wave);
            if (waveLoot != null && !waveLoot.isEmpty()) {
                lostWaves++;
                long waveValue = 0;
                log.info("[Mokha] Wave {} (lost):", wave);
                for (LootItem item : waveLoot) {
                    log.info("[Mokha]   - {} x{} (value: {} gp)", item.name, item.quantity, item.value);
                    waveValue += item.value;
                    totalValue += item.value;
                }
                log.info("[Mokha]   Wave {} Total: {} gp", wave, waveValue);
            }
        }

        if (lostWaves == 0) {
            log.info("[Mokha] ===== NO LOOT LOST (no loot from previous waves) =====");
        } else {
            log.info("[Mokha] ===== TOTAL LOST LOOT VALUE: {} gp ({} waves) =====", totalValue, lostWaves);
        }
    }

    /**
     * Check if player is at the arena entrance
     */
    private boolean isAtEntrance(WorldPoint location) {
        int dx = location.getX() - entrance_centerX;
        int dy = location.getY() - entrance_centerY;
        int distanceSquared = dx * dx + dy * dy;
        int radiusSquared = entrance_radius * entrance_radius;
        return distanceSquared <= radiusSquared;
    }

    /**
     * Update historical data when loot is claimed
     */
    private void updateHistoricalDataOnClaim() {
        // Add claimed loot to historical total
        long claimedValue = 0;
        for (Map.Entry<Integer, List<LootItem>> entry : lootByWave.entrySet()) {
            int wave = entry.getKey();
            long waveValue = 0;

            // Track items by wave
            int waveIndex = wave > 9 ? 9 : wave;
            Map<String, ItemAggregate> waveItems = historicalClaimedItemsByWave.computeIfAbsent(waveIndex,
                    k -> new HashMap<>());

            for (LootItem item : entry.getValue()) {
                waveValue += item.value;
                int pricePerItem = item.quantity > 0 ? item.value / item.quantity : 0;

                // Update or add to historical items for this wave
                if (waveItems.containsKey(item.name)) {
                    waveItems.get(item.name).add(item.quantity, pricePerItem);
                } else {
                    waveItems.put(item.name, new ItemAggregate(item.name, item.quantity, pricePerItem));
                }
            }
            claimedValue += waveValue;

            // Update historical claimed by wave
            historicalClaimedByWave.put(waveIndex,
                    historicalClaimedByWave.getOrDefault(waveIndex, 0L) + waveValue);
        }
        historicalTotalClaimed += claimedValue;

        // Add supplies cost to historical total and track items
        for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            String itemName = getBasePotionName(itemManager.getItemComposition(itemId).getName());
            int pricePerItem = getPricePerDose(itemId);

            // Update or add to historical supplies
            if (historicalSuppliesUsed.containsKey(itemName)) {
                historicalSuppliesUsed.get(itemName).add(quantity, pricePerItem);
            } else {
                historicalSuppliesUsed.put(itemName, new ItemAggregate(itemName, quantity, pricePerItem));
            }
        }

        long suppliesCost = calculateSuppliesCost();
        historicalSupplyCost += suppliesCost;

        // Update panel
        updatePanelData();

        // Save persisted data
        saveHistoricalData();
    }

    /**
     * Calculate total supplies cost and categorize by type
     */
    private long calculateSuppliesCost() {
        long totalCost = 0;
        long potionsCost = 0;
        long foodCost = 0;
        long runesCost = 0;
        long ammoCost = 0;

        for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            String itemName = itemManager.getItemComposition(itemId).getName().toLowerCase();
            int itemValue = itemManager.getItemPrice(itemId) * quantity;

            totalCost += itemValue;

            // Categorize supplies
            if (itemName.contains("potion") || itemName.contains("brew") || itemName.contains("mix")) {
                potionsCost += itemValue;
            } else if (itemName.contains("rune") || itemName.contains("dust") || itemName.contains("chaos")) {
                runesCost += itemValue;
            } else if (itemName.contains("arrow") || itemName.contains("bolt") || itemName.contains("dart")
                    || itemName.contains("knife") || itemName.contains("javelin")) {
                ammoCost += itemValue;
            } else {
                // Assume everything else is food
                foodCost += itemValue;
            }
        }

        // Update historical category costs
        historicalPotionsCost += potionsCost;
        historicalFoodCost += foodCost;
        historicalRunesCost += runesCost;
        historicalAmmoCost += ammoCost;

        return totalCost;
    }

    /**
     * Update all panel data
     */
    private void updatePanelData() {
        if (panel == null) {
            return;
        }

        // Calculate current run unclaimed value
        long currentRunValue = 0;
        for (List<LootItem> items : lootByWave.values()) {
            for (LootItem item : items) {
                currentRunValue += item.value;
            }
        }

        // Update Profit/Loss section
        long totalUnclaimed = currentRunValue; // Current run is unclaimed until Leave pressed
        panel.updateProfitLoss(historicalTotalClaimed, historicalSupplyCost, totalUnclaimed);

        // Update Current Run section with items
        Map<String, MokhaLootPanel.ItemData> currentRunItems = new HashMap<>();
        for (List<LootItem> waveItems : lootByWave.values()) {
            for (LootItem item : waveItems) {
                MokhaLootPanel.ItemData itemData = currentRunItems.getOrDefault(item.name,
                        new MokhaLootPanel.ItemData(item.name, 0, 0, 0));
                itemData.quantity += item.quantity;
                itemData.totalValue += item.value;
                if (item.quantity > 0) {
                    itemData.pricePerItem = (int) (item.value / item.quantity);
                }
                currentRunItems.put(item.name, itemData);
            }
        }
        panel.updateCurrentRun(currentRunValue, currentRunItems);

        // Update Claimed Loot by Wave (historical) - with items
        for (int wave = 1; wave <= 10; wave++) {
            int index = wave > 9 ? 9 : wave;
            Map<String, ItemAggregate> waveItems = historicalClaimedItemsByWave.getOrDefault(index, new HashMap<>());

            // Convert to ItemData for panel
            Map<String, MokhaLootPanel.ItemData> itemData = new HashMap<>();
            for (ItemAggregate agg : waveItems.values()) {
                itemData.put(agg.name,
                        new MokhaLootPanel.ItemData(agg.name, agg.totalQuantity, agg.pricePerItem, agg.totalValue));
            }

            panel.updateClaimedWave(wave, itemData);
        }

        // Update Unclaimed Loot by Wave (historical - persisted and never cleared) -
        // with items
        for (int wave = 1; wave <= 10; wave++) {
            Map<String, MokhaLootPanel.ItemData> itemData = new HashMap<>();

            if (wave <= 9) {
                Map<String, ItemAggregate> waveItems = historicalUnclaimedItemsByWave.getOrDefault(wave,
                        new HashMap<>());
                for (ItemAggregate agg : waveItems.values()) {
                    itemData.put(agg.name,
                            new MokhaLootPanel.ItemData(agg.name, agg.totalQuantity, agg.pricePerItem, agg.totalValue));
                }
            } else {
                // For wave 9+, aggregate all waves >= 9
                for (int w = 9; w <= 20; w++) {
                    Map<String, ItemAggregate> waveItems = historicalUnclaimedItemsByWave.getOrDefault(w,
                            new HashMap<>());
                    for (ItemAggregate agg : waveItems.values()) {
                        if (itemData.containsKey(agg.name)) {
                            MokhaLootPanel.ItemData existing = itemData.get(agg.name);
                            itemData.put(agg.name, new MokhaLootPanel.ItemData(
                                    agg.name,
                                    existing.quantity + agg.totalQuantity,
                                    agg.pricePerItem,
                                    existing.totalValue + agg.totalValue));
                        } else {
                            itemData.put(agg.name, new MokhaLootPanel.ItemData(agg.name, agg.totalQuantity,
                                    agg.pricePerItem, agg.totalValue));
                        }
                    }
                }
            }

            panel.updateUnclaimedWave(wave, itemData);
        }

        // Update Supplies Current Run - with items
        Map<String, MokhaLootPanel.ItemData> currentSuppliesData = new HashMap<>();
        long currentSuppliesTotalValue = 0;
        for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            String baseName = getBasePotionName(itemManager.getItemComposition(itemId).getName());
            int pricePerItem = getPricePerDose(itemId);
            long totalValue = (long) pricePerItem * quantity;
            currentSuppliesTotalValue += totalValue;

            if (currentSuppliesData.containsKey(baseName)) {
                MokhaLootPanel.ItemData existing = currentSuppliesData.get(baseName);
                currentSuppliesData.put(baseName, new MokhaLootPanel.ItemData(
                        baseName,
                        existing.quantity + quantity,
                        pricePerItem,
                        existing.totalValue + totalValue));
            } else {
                currentSuppliesData.put(baseName,
                        new MokhaLootPanel.ItemData(baseName, quantity, pricePerItem, totalValue));
            }
        }
        panel.updateSuppliesCurrentRun(currentSuppliesTotalValue, currentSuppliesData);

        // Update Supplies Total (historical) - with items
        Map<String, MokhaLootPanel.ItemData> historicalSuppliesData = new HashMap<>();
        long historicalSuppliesTotalValue = 0;
        for (ItemAggregate agg : historicalSuppliesUsed.values()) {
            historicalSuppliesData.put(agg.name,
                    new MokhaLootPanel.ItemData(agg.name, agg.totalQuantity, agg.pricePerItem, agg.totalValue));
            historicalSuppliesTotalValue += agg.totalValue;
        }
        panel.updateSuppliesTotal(historicalSuppliesTotalValue, historicalSuppliesData);
    }
}
