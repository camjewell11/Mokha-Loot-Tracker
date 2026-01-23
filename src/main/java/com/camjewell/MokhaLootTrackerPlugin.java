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
import net.runelite.api.Varbits;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.ChatMessageType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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

    @Inject
    private ClientThread clientThread;

    @Inject
    private ConfigManager configManager;
    private NavigationButton navButton;
    private MokhaLootPanel panel;
    private HistoricalDataManager historicalDataManager;

    @Inject
    private Gson gson;

    // Arena state tracking
    private boolean inMokhaArena = false;
    private boolean isDead = false;
    private boolean lootWindowWasVisible = false;
    private int currentWaveNumber = 0;

    // Entrance coordinates (for future use)
    private final int entrance_centerX = 1311;
    private final int entrance_centerY = 9555;
    private final int entrance_radius = 25;

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
    private long historicalClaims = 0;
    private long historicalDeaths = 0;
    private final Map<Integer, Long> historicalClaimedByWave = new HashMap<>(); // Wave -> Total GP claimed
    private final Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave = new HashMap<>();
    private final Map<String, ItemAggregate> historicalSuppliesUsed = new HashMap<>(); // Item name -> aggregate

    // Historical unclaimed loot (persisted, never cleared)
    private final Map<Integer, Long> historicalUnclaimedByWave = new HashMap<>(); // Wave -> Total GP unclaimed
    private final Map<Integer, Map<String, ItemAggregate>> historicalUnclaimedItemsByWave = new HashMap<>();

    // Mokhaiotl Cloth handling
    private boolean hasWarnedAboutZeroClothValue = false; // Track if we've already warned player

    // Rune pouch mapping (varbit values to item IDs)
    private static final int[] RUNE_POUCH_ITEM_IDS = new int[] {
            0, // 0 unused / empty
            556, // 1 - Air rune
            555, // 2 - Water rune
            557, // 3 - Earth rune
            554, // 4 - Fire rune
            558, // 5 - Mind rune
            562, // 6 - Chaos rune
            560, // 7 - Death rune
            565, // 8 - Blood rune
            564, // 9 - Cosmic rune
            561, // 10 - Nature rune
            563, // 11 - Law rune
            559, // 12 - Body rune
            566, // 13 - Soul rune
            9075, // 14 - Astral rune
            4695, // 15 - Mist rune
            4698, // 16 - Mud rune
            4696, // 17 - Dust rune
            4699, // 18 - Lava rune
            4694, // 19 - Steam rune
            4697, // 20 - Smoke rune
            21880, // 21 - Wrath rune
            28929, // 22 - Sunfire rune (corrected ID)
            30843 // 23 - Aether rune (corrected ID)
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
    public static class ItemAggregate {
        String name;
        int totalQuantity;
        int pricePerItem;
        long totalValue;
        long originalTotalValue; // Store original value to restore when ignoring is toggled off

        ItemAggregate(String name, int quantity, int pricePerItem) {
            this.name = name;
            this.totalQuantity = quantity;
            this.pricePerItem = pricePerItem;
            this.totalValue = (long) pricePerItem * quantity;
            this.originalTotalValue = this.totalValue; // Store original
        }

        void add(int quantity, int pricePerItem) {
            this.totalQuantity += quantity;
            this.totalValue += (long) pricePerItem * quantity;
            this.originalTotalValue = this.totalValue; // Update original when new items are added
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
        panel = new MokhaLootPanel(config, this::debugLocation, this::clearAllData, this::recalculateAllTotals,
                () -> inMokhaArena);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/48icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Mokha Loot Tracker")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        lastCombinedSnapshot.clear();

        // Initialize historical data manager
        historicalDataManager = new HistoricalDataManager(net.runelite.client.RuneLite.RUNELITE_DIR, gson);

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
                // Take initial snapshot when entering arena
                lastCombinedSnapshot.clear();
                lastCombinedSnapshot.putAll(buildCombinedSnapshot());
                // Take initial supply snapshot
                initialSupplySnapshot.clear();
                initialSupplySnapshot.putAll(buildCombinedSnapshot());
            }
        }

        // Detect "Descend" button - continues to next wave without claiming loot
        if (option != null && option.equalsIgnoreCase("Descend")) {
            // Print accumulated loot so far (not claimed yet, could still be lost)
            printSuppliesConsumed();
            printAccumulatedLoot();
            // Increment wave number since we're moving to the next wave
            currentWaveNumber++;
        }

        // Detect "Leave" button - returns to entrance (only available after confirming
        // claim)
        if (option != null && option.equals("Leave") && !option.contains("Claim")) {
            log.debug("[Mokha] Pressed LEAVE button - claiming loot and returning to entrance");
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
                historicalDeaths += 1;

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
    public void onConfigChanged(ConfigChanged event) {
        // When ignore settings are toggled, update panel immediately
        if (event.getGroup().equals("mokhaloot")) {
            if (event.getKey().equals("ignoreSunKissedBonesValue") ||
                    event.getKey().equals("ignoreSpiritSeedsValue") ||
                    event.getKey().equals("excludeUltraValuableItems") ||
                    event.getKey().equals("mokhaClothValue")) {
                log.info("[Mokha] Config changed: {} = {}", event.getKey(), event.getNewValue());
                // Trigger complete recalculation with new config settings
                recalculateAllTotals();
                // Save the updated state
                saveHistoricalData();
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Only track item changes while in arena and alive
        if (!inMokhaArena || isDead) {
            return;
        }

        // Only process inventory or equipment containers
        if (event.getContainerId() != 93 && event.getContainerId() != 94) {
            return;
        }

        // Build combined snapshot from both containers
        Map<Integer, Integer> currentCombined = buildCombinedSnapshot();
        checkForConsumption(currentCombined);
    }

    private Map<Integer, Integer> buildCombinedSnapshot() {
        Map<Integer, Integer> combined = new HashMap<>();

        // Add inventory items
        ItemContainer inventory = client.getItemContainer(93);
        if (inventory != null) {
            for (Item item : inventory.getItems()) {
                if (item != null && item.getId() > 0) {
                    combined.put(item.getId(), combined.getOrDefault(item.getId(), 0) + item.getQuantity());
                }
            }
        }

        // Add equipment items
        ItemContainer equipment = client.getItemContainer(94);
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

        // Add Dizanna quiver ammo (if present)
        // Uses varps as per RuneLite's Ammo plugin
        final int quiverAmmoId = client.getVarpValue(4142); // VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO
        final int quiverAmmoCount = client.getVarpValue(4141); // VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT
        if (quiverAmmoId > 0 && quiverAmmoCount > 0) {
            combined.put(quiverAmmoId, combined.getOrDefault(quiverAmmoId, 0) + quiverAmmoCount);
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

                // Special handling for Mokhaiotl Cloth (untradable, needs calculated value)
                if (itemName.equals("Mokhaiotl Cloth") && itemValue == 0) {
                    int clothValue = getMokhaClothValue();
                    itemValue = clothValue * newQty;
                }

                // Override value to 0 if config toggle is enabled for specific items
                if (config.ignoreSunKissedBonesValue() && itemName.equals("Sun-kissed bones")) {
                    itemValue = 0;
                }
                if (config.ignoreSpiritSeedsValue() && itemName.equals("Spirit seed")) {
                    itemValue = 0;
                }

                newLootThisWave.add(new LootItem(itemName, newQty, itemValue));
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
            return;
        }

        net.runelite.api.coords.WorldPoint location = client.getLocalPlayer().getWorldLocation();
        if (location == null) {
            return;
        }
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

            // Use per-dose pricing for potions; otherwise use full item price
            boolean hasDoseSuffix = itemName.matches(".*\\(\\d+\\)$");
            int pricePerUnit = hasDoseSuffix ? getPricePerDose(itemId) : itemManager.getItemPrice(itemId);
            int itemValue = pricePerUnit * quantity;

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
            log.info("[Mokha] Starting to load historical data from file...");
            // Only load from file
            historicalDataManager.loadData();
            // Copy data from manager to plugin fields
            historicalClaimedItemsByWave.clear();
            historicalClaimedItemsByWave.putAll(historicalDataManager.getHistoricalClaimedItemsByWave());

            historicalSuppliesUsed.clear();
            historicalSuppliesUsed.putAll(historicalDataManager.getHistoricalSuppliesUsed());

            historicalClaimedByWave.clear();
            historicalClaimedByWave.putAll(historicalDataManager.getHistoricalClaimedByWave());

            historicalTotalClaimed = historicalDataManager.getHistoricalTotalClaimed();
            historicalClaims = historicalDataManager.getHistoricalClaims();
            historicalDeaths = historicalDataManager.getHistoricalDeaths();

            historicalUnclaimedByWave.clear();
            historicalUnclaimedByWave.putAll(historicalDataManager.getHistoricalUnclaimedByWave());
            historicalUnclaimedItemsByWave.clear();
            historicalUnclaimedItemsByWave.putAll(historicalDataManager.getHistoricalUnclaimedItemsByWave());

            // Apply ignore settings and recalculate
            applyIgnoreSettingsToHistoricalItems(historicalClaimedItemsByWave);
            applyIgnoreSettingsToHistoricalItems(historicalUnclaimedItemsByWave);
            recalculateWaveTotals();
            recalculateHistoricalTotalClaimed();

            // Set historical data on the panel so combined view logic works
            if (panel != null) {
                panel.setHistoricalClaimedItemsByWave(historicalClaimedItemsByWave);
                panel.setHistoricalUnclaimedItemsByWave(historicalUnclaimedItemsByWave);
            }

            log.info("[Mokha] Loaded {} historical claimed wave entries", historicalClaimedItemsByWave.size());
            log.info("[Mokha] Loaded {} historical supplies entries", historicalSuppliesUsed.size());

            // Load supply cost (still in ConfigManager for now)
            String supplyCostStr = configManager.getConfiguration("mokhaloot", "historicalSupplyCost");
            historicalSupplyCost = supplyCostStr != null && !supplyCostStr.isEmpty() ? Long.parseLong(supplyCostStr)
                    : 0;

            // Load current run loot by wave
            String currentRunJson = configManager.getConfiguration("mokhaloot", "currentRunLootByWaveJson");
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

            log.info("[Mokha] Historical data loaded - Claimed: {}, Supply Cost: {}", historicalTotalClaimed,
                    historicalSupplyCost);
        } catch (Exception e) {
            log.error("[Mokha] Error loading historical data", e);
        }
    }

    /**
     * Save historical data to config
     */
    private void saveHistoricalData() {
        try {
            // Save historical data to file
            historicalDataManager.setHistoricalClaimedItemsByWave(historicalClaimedItemsByWave);
            historicalDataManager.setHistoricalSuppliesUsed(historicalSuppliesUsed);
            historicalDataManager.setHistoricalClaimedByWave(historicalClaimedByWave);
            historicalDataManager.setHistoricalTotalClaimed(historicalTotalClaimed);
            historicalDataManager.setHistoricalClaims(historicalClaims);
            historicalDataManager.setHistoricalDeaths(historicalDeaths);
            historicalDataManager.setHistoricalUnclaimedByWave(historicalUnclaimedByWave);
            historicalDataManager.setHistoricalUnclaimedItemsByWave(historicalUnclaimedItemsByWave);
            historicalDataManager.saveData();
            log.info("[Mokha] Historical data saved - Claimed: {}, Supply Cost: {}",
                    historicalTotalClaimed, historicalSupplyCost);
        } catch (Exception e) {
            log.error("[Mokha] Error saving historical data", e);
        }
    }

    /**
     * Clear all current and historical data
     */
    private void clearAllData() {
        // Clear current run data
        lootByWave.clear();
        previousLootSnapshot.clear();
        initialSupplySnapshot.clear();
        totalSuppliesConsumed.clear();

        // Clear historical data
        historicalTotalClaimed = 0;
        historicalSupplyCost = 0;
        historicalClaims = 0;
        historicalDeaths = 0;
        historicalClaimedByWave.clear();
        historicalClaimedItemsByWave.clear();
        historicalSuppliesUsed.clear();
        historicalUnclaimedByWave.clear();
        historicalUnclaimedItemsByWave.clear();

        // Clear panel data
        if (panel != null) {
            panel.clearAllPanelData();
        }

        // Save empty data to config
        saveHistoricalData();

        log.info("[Mokha] All data cleared successfully");
    }

    /**
     * Recalculate all totals based on historical data
     * This includes: total claimed, total unclaimed, supply cost
     * Also applies the ignore settings for sun-kissed bones and spirit seeds
     */
    private void recalculateAllTotals() {
        log.info("[Mokha] Starting recalculation of all totals...");

        clientThread.invoke(() -> {
            // Update Mokhaiotl Cloth prices first with current cloth value
            updateMokhaClothPrices();

            // Recalculate GE value for all items in claimed and unclaimed loot
            recalculateAllItemGEValues(historicalClaimedItemsByWave);
            recalculateAllItemGEValues(historicalUnclaimedItemsByWave);

            // Apply ignore settings to all historical items (this will update totalValue
            // based on current config)
            applyIgnoreSettingsToHistoricalItems(historicalClaimedItemsByWave);
            applyIgnoreSettingsToHistoricalItems(historicalUnclaimedItemsByWave);

            // Recalculate wave totals based on current settings
            recalculateWaveTotals();

            // Recalculate total claimed from historical claimed items
            recalculateHistoricalTotalClaimed();

            // Recalculate supply cost
            historicalSupplyCost = 0;
            for (ItemAggregate item : historicalSuppliesUsed.values()) {
                historicalSupplyCost += item.totalValue;
            }

            // Save the recalculated data
            saveHistoricalData();

            // Update panel with recalculated totals
            updatePanelData();

            log.info("[Mokha] Totals recalculated - Claimed: {}, Unclaimed: {}, Supply Cost: {}",
                    historicalTotalClaimed,
                    historicalUnclaimedByWave.values().stream().mapToLong(Long::longValue).sum(),
                    historicalSupplyCost);
        });
    }

    /**
     * Recalculate GE value for all items in the provided map (by wave)
     */
    private void recalculateAllItemGEValues(Map<Integer, Map<String, ItemAggregate>> itemsByWave) {
        for (Map<String, ItemAggregate> waveItems : itemsByWave.values()) {
            for (ItemAggregate item : waveItems.values()) {
                int itemId = getItemIdForName(item.name);
                if (itemId > 0) {
                    int gePrice = itemManager.getItemPrice(itemId);
                    item.pricePerItem = gePrice;
                    item.totalValue = (long) gePrice * item.totalQuantity;
                }
            }
        }
    }

    /**
     * Helper to get itemId from item name using ItemManager
     */
    private int getItemIdForName(String name) {
        if (name == null || name.isEmpty())
            return -1;
        try {
            return itemManager.search(name).stream()
                    .map(itemPrice -> itemPrice.getId())
                    .filter(id -> itemManager.getItemComposition(id).getName().equalsIgnoreCase(name))
                    .findFirst().orElse(-1);
        } catch (Exception e) {
            return -1;
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
        historicalClaims += 1;

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
     * Calculate total supplies cost
     */
    private long calculateSuppliesCost() {
        long totalCost = 0;

        for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            int itemValue = getPricePerDose(itemId) * quantity;
            totalCost += itemValue;
        }

        return totalCost;
    }

    /**
     * Update all panel data
     */
    private void updatePanelData() {
        if (panel == null) {
            return;
        }

        // Apply ignore settings to historical items before displaying
        applyIgnoreSettingsToHistoricalItems(historicalClaimedItemsByWave);
        applyIgnoreSettingsToHistoricalItems(historicalUnclaimedItemsByWave);

        // Recalculate historical total with ignore settings applied
        recalculateHistoricalTotalClaimed();

        // Calculate current run unclaimed value
        long currentRunValue = 0;
        for (List<LootItem> items : lootByWave.values()) {
            for (LootItem item : items) {
                long itemValue = item.value;
                // Apply ignore settings to current run loot as well
                if ((config.ignoreSpiritSeedsValue() && item.name.equals("Spirit seed")) ||
                        (config.ignoreSunKissedBonesValue() && item.name.equals("Sun-kissed bones"))) {
                    itemValue = 0;
                }
                currentRunValue += itemValue;
            }
        }

        // Calculate total unclaimed (only loot lost to deaths, not current run)
        long totalUnclaimed = 0;
        boolean excludeUltra = config.excludeUltraValuableItems();
        final int ULTRA_VALUABLE_THRESHOLD = 20_000_000;

        if (excludeUltra) {
            // Filter out items worth more than 20M
            for (Map<String, ItemAggregate> waveItems : historicalUnclaimedItemsByWave.values()) {
                for (ItemAggregate item : waveItems.values()) {
                    if (item.pricePerItem <= ULTRA_VALUABLE_THRESHOLD) {
                        totalUnclaimed += item.totalValue;
                    }
                }
            }
        } else {
            // Use original totals
            for (Long waveValue : historicalUnclaimedByWave.values()) {
                totalUnclaimed += waveValue;
            }
        }

        // Update Profit/Loss section
        panel.updateProfitLoss(historicalTotalClaimed, historicalSupplyCost, totalUnclaimed,
                historicalClaims, historicalDeaths);

        // Update Current Run section with items
        Map<String, MokhaLootPanel.ItemData> currentRunItems = new HashMap<>();
        for (List<LootItem> waveItems : lootByWave.values()) {
            for (LootItem item : waveItems) {
                long itemValue = item.value;
                // Apply ignore settings to current run loot as well
                if ((config.ignoreSpiritSeedsValue() && item.name.equals("Spirit seed")) ||
                        (config.ignoreSunKissedBonesValue() && item.name.equals("Sun-kissed bones"))) {
                    itemValue = 0;
                }

                MokhaLootPanel.ItemData itemData = currentRunItems.getOrDefault(item.name,
                        new MokhaLootPanel.ItemData(item.name, 0, 0, 0));
                itemData.quantity += item.quantity;
                itemData.totalValue += itemValue;
                if (item.quantity > 0) {
                    itemData.pricePerItem = (int) (itemValue / item.quantity);
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

            // Use pre-calculated wave total that respects the exclude setting
            long waveTotal = historicalClaimedByWave.getOrDefault(index, 0L);
            panel.updateClaimedWave(wave, itemData, waveTotal);
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

            // Use pre-calculated wave total that respects the exclude setting
            long waveTotal = historicalUnclaimedByWave.getOrDefault(wave, 0L);
            panel.updateUnclaimedWave(wave, itemData, waveTotal);
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

    /**
     * Apply ignore settings to historical item data
     * Zeros out the totalValue for Spirit Seeds and Sun-kissed Bones if the
     * respective ignore settings are enabled. Restores correct value if setting is
     * disabled.
     */
    private void applyIgnoreSettingsToHistoricalItems(Map<Integer, Map<String, ItemAggregate>> historicalItems) {
        for (Map<String, ItemAggregate> waveItems : historicalItems.values()) {
            for (ItemAggregate item : waveItems.values()) {
                if (item.name.equals("Spirit seed")) {
                    if (config.ignoreSpiritSeedsValue()) {
                        item.totalValue = 0; // Zero out if ignoring
                    } else {
                        // Restore correct value: 140000 per spirit seed
                        long restoredValue = 140000L * item.totalQuantity;
                        item.totalValue = restoredValue;
                    }
                } else if (item.name.equals("Sun-kissed bones")) {
                    if (config.ignoreSunKissedBonesValue()) {
                        item.totalValue = 0; // Zero out if ignoring
                    } else {
                        // Restore correct value: 8000 per sun-kissed bones
                        long restoredValue = 8000L * item.totalQuantity;
                        item.totalValue = restoredValue;
                    }
                }
            }
        }
    }

    /**
     * Recalculate historical claimed total based on actual item values after ignore
     * settings applied
     */
    private void recalculateHistoricalTotalClaimed() {
        historicalTotalClaimed = 0;
        boolean excludeUltra = config.excludeUltraValuableItems();
        final int ULTRA_VALUABLE_THRESHOLD = 20_000_000;

        for (Map<String, ItemAggregate> waveItems : historicalClaimedItemsByWave.values()) {
            for (ItemAggregate item : waveItems.values()) {
                // Skip items worth more than 20M if setting is enabled
                if (excludeUltra && item.pricePerItem > ULTRA_VALUABLE_THRESHOLD) {
                    continue;
                }
                historicalTotalClaimed += item.totalValue;
            }
        }
    }

    /**
     * Update Mokhaiotl Cloth prices in all historical items
     * Call this when the cloth value changes to update all existing cloth items
     */
    private void updateMokhaClothPrices() {
        int clothPrice = getMokhaClothValue();

        // Only update cloth prices if we have a valid (non-zero) price
        if (clothPrice <= 0) {
            log.warn("[Mokha] Cloth price is 0 or invalid - skipping cloth price update");
            return;
        }

        log.info("[Mokha] Updating cloth prices to {} gp", clothPrice);

        // Update cloth in claimed items
        for (Map<String, ItemAggregate> waveItems : historicalClaimedItemsByWave.values()) {
            ItemAggregate clothItem = waveItems.get("Mokhaiotl Cloth");
            if (clothItem != null) {
                clothItem.pricePerItem = clothPrice;
                clothItem.totalValue = (long) clothItem.totalQuantity * clothPrice;
            }
        }

        // Update cloth in unclaimed items
        for (Map<String, ItemAggregate> waveItems : historicalUnclaimedItemsByWave.values()) {
            ItemAggregate clothItem = waveItems.get("Mokhaiotl Cloth");
            if (clothItem != null) {
                clothItem.pricePerItem = clothPrice;
                clothItem.totalValue = (long) clothItem.totalQuantity * clothPrice;
            }
        }
    }

    /**
     * Recalculate wave totals based on excludeUltraValuableItems setting
     */
    private void recalculateWaveTotals() {
        boolean excludeUltra = config.excludeUltraValuableItems();
        final int ULTRA_VALUABLE_THRESHOLD = 20_000_000;

        // Recalculate claimed by wave
        historicalClaimedByWave.clear();
        for (Map.Entry<Integer, Map<String, ItemAggregate>> waveEntry : historicalClaimedItemsByWave.entrySet()) {
            long waveTotal = 0;
            for (ItemAggregate item : waveEntry.getValue().values()) {
                if (excludeUltra && item.pricePerItem > ULTRA_VALUABLE_THRESHOLD) {
                    continue;
                }
                waveTotal += item.totalValue;
            }
            historicalClaimedByWave.put(waveEntry.getKey(), waveTotal);
        }

        // Recalculate unclaimed by wave
        historicalUnclaimedByWave.clear();
        for (Map.Entry<Integer, Map<String, ItemAggregate>> waveEntry : historicalUnclaimedItemsByWave.entrySet()) {
            long waveTotal = 0;
            for (ItemAggregate item : waveEntry.getValue().values()) {
                if (excludeUltra && item.pricePerItem > ULTRA_VALUABLE_THRESHOLD) {
                    continue;
                }
                waveTotal += item.totalValue;
            }
            historicalUnclaimedByWave.put(waveEntry.getKey(), waveTotal);
        }
    }

    /**
     * Calculate Mokhaiotl Cloth value based on component prices
     * Formula: p(Confliction Gauntlets) - 10000*p(Demon Tear) - p(Tormented
     * Bracelet)
     * Returns the calculated value, or 0 if unable to determine component prices
     */
    private int calculateMokhaClothValue() {
        int conflictionGauntletsPrice = itemManager.getItemPrice(31106); // Confliction Gauntlets
        int demonTearPrice = itemManager.getItemPrice(31111); // Demon Tear
        int tormentedBraceletPrice = itemManager.getItemPrice(19544); // Tormented Bracelet

        if (conflictionGauntletsPrice <= 0 || demonTearPrice <= 0 || tormentedBraceletPrice <= 0) {
            return 0; // Return 0 if any component price is unavailable
        }

        return conflictionGauntletsPrice - (10000 * demonTearPrice) - tormentedBraceletPrice;
    }

    /**
     * Get the Mokhaiotl Cloth value (from chat if detected, otherwise calculated)
     */
    private int getMokhaClothValue() {
        int calculatedValue = calculateMokhaClothValue();

        // Warn player if cloth value is 0 and can't be calculated
        if (calculatedValue == 0 && !hasWarnedAboutZeroClothValue) {
            hasWarnedAboutZeroClothValue = true;
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=FF0000>[Mokha Tracker] Mokhaiotl Cloth value is 0 - cannot calculate from component prices.</col>",
                    null);
        }

        return calculatedValue;
    }
}
