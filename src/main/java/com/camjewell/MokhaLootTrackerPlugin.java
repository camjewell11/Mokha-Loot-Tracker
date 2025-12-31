package com.camjewell;

import com.google.inject.Provides;
import javax.inject.Inject;
import java.awt.image.BufferedImage;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.game.ItemManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;
import java.util.ArrayList;
import java.util.List;

// Simple class to hold item data for serialization
class LootItem {
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

@Slf4j
@PluginDescriptor(name = "Mokha Loot Tracker", description = "Tracks loot lost and claimed at the Doom of Mokhaiotl boss", tags = {
        "mokha", "loot", "boss", "death", "tracking" })
public class MokhaLootTrackerPlugin extends Plugin {
    private static final int SUN_KISSED_BONES_ID = 33212;
    private static final int SUN_KISSED_BONES_VALUE = 8000;
    private static final String CONFIG_KEY_TOTAL_LOST = "totalLostValue";
    private static final String CONFIG_KEY_TIMES_DIED = "timesDied";
    private static final String CONFIG_KEY_DEATH_COSTS = "totalDeathCosts";
    private static final String CONFIG_KEY_WAVE_PREFIX = "wave";
    private static final String CONFIG_KEY_WAVE_ITEMS_PREFIX = "waveItems";
    private static final String CONFIG_KEY_WAVE_CLAIMED_PREFIX = "waveClaimed";
    private static final String CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX = "waveClaimedItems";
    private static final int MAX_TRACKED_WAVES = 9; // 1-8 individually, 9+ combined

    @Inject
    private Client client;

    @Inject
    private MokhaLootTrackerConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private MokhaLootOverlay overlay;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private MokhaLootPanel panel;

    private NavigationButton navButton;

    private int currentDelveNumber = 0;
    private int previousDelveNumber = 0;
    private List<LootItem> currentUnclaimedLoot = new ArrayList<>();
    private List<LootItem> previousWaveItems = new ArrayList<>(); // Track items from previous wave
    private long currentLootValue = 0;
    private long previousWaveLootValue = 0;
    private long[] waveLootValues = new long[MAX_TRACKED_WAVES + 1]; // Index 0 unused, 1-9 for waves
    @SuppressWarnings("unchecked")
    private List<LootItem>[] waveItemStacks = new List[MAX_TRACKED_WAVES + 1]; // Items per wave
    private boolean isDead = false;
    private boolean delveInterfaceVisible = false;
    private boolean wasDelveInterfaceVisible = false;
    private boolean inMokhaArena = false;
    private boolean wasInMokhaArena = false;

    // Store loot data from last visible state to handle claim detection via button
    // clicks
    private long lastVisibleLootValue = 0;
    private List<LootItem> lastVisibleLootItems = new ArrayList<>();
    private int lastVisibleDelveNumber = 0;

    // Flag to trigger panel refresh after login when account hash becomes available
    private boolean needsPanelRefresh = false;

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);

        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "mokha_icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Mokha Loot")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);

        // Clear any "default" config from dummy data testing
        // This ensures we only show data for actual logged-in accounts
        clearDefaultConfig();

        // Set flag to refresh panel once account hash is available
        // Don't call updateStats() here - it would read from "default" config
        needsPanelRefresh = true;
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);
        resetCurrentLoot();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        // Refresh panel after login to load account-specific data
        if (event.getGameState() == GameState.LOGGED_IN) {
            needsPanelRefresh = true;
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        // Refresh panel once account hash is available
        // This ensures we load the correct account's data, not "default"
        if (needsPanelRefresh) {
            long accountHash = client.getAccountHash();
            if (accountHash != -1) {
                needsPanelRefresh = false;
                // Call updateStats directly - we're already on client thread
                // so ItemManager will work, then updateStats will handle UI updates
                panel.updateStats();
            }
        }

        // Store previous arena state
        wasInMokhaArena = inMokhaArena;

        // Check if player is in Mokha arena using widget detection
        // Widget [303:9] contains "Doom of Mokhaiotl" when in arena
        // Widget [919:2] is the delve interface itself
        if (client.getLocalPlayer() != null) {
            Widget mokhaWidget = client.getWidget(303, 9);
            Widget delveWidget = client.getWidget(919, 2);

            boolean hasArenaWidget = mokhaWidget != null && !mokhaWidget.isHidden()
                    && mokhaWidget.getText() != null
                    && mokhaWidget.getText().contains("Mokha");

            boolean hasDelveWidget = delveWidget != null && !delveWidget.isHidden();

            // We're in Mokha arena if either the arena widget or delve widget is present
            inMokhaArena = hasArenaWidget || hasDelveWidget;
        }

        // Note: Claim detection is now handled by onMenuOptionClicked tracking button
        // presses
        // The old pending claim check logic has been removed in favor of direct button
        // detection

        // Check if player is dead
        if (client.getLocalPlayer() != null) {
            boolean currentlyDead = client.getLocalPlayer().getHealthRatio() == 0;

            if (currentlyDead && !isDead) {
                // Player just died
                isDead = true;

                // Only track deaths in Mokha arena
                if (inMokhaArena) {
                    log.info("Player died in arena. currentLootValue={}, currentDelveNumber={}", currentLootValue,
                            currentDelveNumber);
                    if (currentLootValue > 0 && currentDelveNumber > 0) {
                        log.info("Recording lost loot for wave {}", currentDelveNumber);
                        recordLostLoot();
                        resetCurrentLoot();
                    } else {
                        log.info("No loot to record, just counting death");
                        // Count death even without loot
                        incrementDeathCounter();
                    }
                }
            } else if (!currentlyDead && isDead) {
                // Player respawned
                isDead = false;
            }
        }

        // Check for delve deeper widget and parse loot
        checkDelveWidget();
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
        // You may need to adjust this widget ID based on the actual game widget
        // This will likely need to be determined by examining the game
        checkDelveWidget();
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String menuOption = event.getMenuOption();
        String menuTarget = event.getMenuTarget();

        // Detect "Descend" button click (continue to next wave)
        if (menuOption != null && menuOption.contains("Descend")) {
            // Continue to next wave
        }

        // Detect "Leave" button click (final step in claim flow: Claim and Leave ->
        // Confirm -> Leave)
        if (menuOption != null && menuOption.contains("Leave")) {
            // Player is claiming - record the loot immediately
            if (lastVisibleLootValue > 0 && lastVisibleDelveNumber > 0) {
                // Ensure current wave data is populated before claiming
                int waveIndex = Math.min(lastVisibleDelveNumber, MAX_TRACKED_WAVES);
                if (waveItemStacks[waveIndex] == null || waveItemStacks[waveIndex].isEmpty()) {
                    long incrementalLoot = lastVisibleLootValue - previousWaveLootValue;
                    waveLootValues[waveIndex] = incrementalLoot;
                    waveItemStacks[waveIndex] = new ArrayList<>(lastVisibleLootItems);
                }

                recordClaimedLoot();

                // Clear the captured state
                lastVisibleLootValue = 0;
                lastVisibleLootItems.clear();
                lastVisibleDelveNumber = 0;
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
        String message = chatMessage.getMessage();

        if (chatMessage.getType() != ChatMessageType.GAMEMESSAGE) {
            return;
        }

        // Match "Death charges you X x Coins."
        // Message format: "[12:12] Death charges you 2,000 x Coins. You have 11,291,550
        // x Coins left in Death's Coffer."
        String lowerMessage = message.toLowerCase();
        if (lowerMessage.contains("death") && lowerMessage.contains("charges you") && lowerMessage.contains("coins")) {
            // Only track death costs if in Mokha arena
            if (!inMokhaArena) {
                return;
            }

            try {
                // Try to extract number from the message using regex
                String cleanMessage = message.replaceAll("<[^>]*>", ""); // Strip HTML tags

                // Find the pattern "Death charges you X x Coins"
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("Death charges you ([0-9,]+) x Coins",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher matcher = pattern.matcher(cleanMessage);

                if (matcher.find()) {
                    String costStr = matcher.group(1).replace(",", "");
                    long cost = Long.parseLong(costStr);

                    // Add to total death costs
                    String accountHash = getAccountHash();
                    String configGroup = "mokhaloot." + accountHash;
                    long totalCosts = getLongConfig(configGroup, CONFIG_KEY_DEATH_COSTS);
                    totalCosts += cost;
                    configManager.setConfiguration(configGroup, CONFIG_KEY_DEATH_COSTS, totalCosts);

                    // Update panel - we're already on client thread, so ItemManager will work
                    // updateStats() will handle UI updates on Swing thread internally
                    panel.updateStats();
                } else {
                    log.warn("Could not extract cost from message: {}", cleanMessage);
                }
            } catch (Exception e) {
                log.error("Failed to parse death cost from message: {}", message, e);
            }
        }
    }

    private void checkDelveWidget() {
        // Widget Group 919 is the Mokhaiotl delve interface
        Widget mainWidget = client.getWidget(919, 2);

        if (mainWidget != null && !mainWidget.isHidden()) {
            delveInterfaceVisible = true;
            // We're in the delve interface
            // Parse delve number from widget [919:2] child[1] which contains "Level X
            // Complete!"
            int detectedDelveNumber = parseDelveNumber(mainWidget);

            if (detectedDelveNumber > 0) {
                handleDelveNumberChange(detectedDelveNumber);
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
                            currentLootValue = Long.parseLong(numStr);
                        }
                    } catch (Exception e) {
                        log.error("Error parsing loot value", e);
                    }
                }
            }

            // Parse loot items from widget [919:19] children
            Widget lootContainerWidget = client.getWidget(919, 19);
            if (lootContainerWidget != null) {
                parseCurrentLoot(lootContainerWidget);
            }

            // Save the current state while interface is visible (before it gets cleared)
            if (currentLootValue > 0) {
                lastVisibleLootValue = currentLootValue;
                lastVisibleLootItems = new ArrayList<>(currentUnclaimedLoot);
                lastVisibleDelveNumber = currentDelveNumber;

                // Calculate and save incremental loot for THIS wave
                // The interface shows cumulative rewards, so we subtract previous total
                if (currentDelveNumber > 0 && currentDelveNumber <= MAX_TRACKED_WAVES) {
                    long incrementalValue = currentLootValue - previousWaveLootValue;
                    if (incrementalValue > 0) {
                        waveLootValues[currentDelveNumber] = incrementalValue;

                        // Calculate incremental items (items added this wave only)
                        List<LootItem> incrementalItems = calculateIncrementalItems(currentUnclaimedLoot,
                                previousWaveItems);
                        waveItemStacks[currentDelveNumber] = incrementalItems;

                        // Update previous wave tracking
                        previousWaveLootValue = currentLootValue;
                        previousWaveItems = new ArrayList<>(currentUnclaimedLoot);
                    }
                }
            }
        } else {
            // Delve interface just closed
            if (wasDelveInterfaceVisible) {
                // Note: Claim detection is now handled by onMenuOptionClicked tracking button
                // presses
            }
            delveInterfaceVisible = false;
        }

        // Update previous state
        wasDelveInterfaceVisible = delveInterfaceVisible;
    }

    private int parseDelveNumber(Widget widget) {
        // Parse the delve number from widget [919:2] child[1]
        // Child[1] contains text like "Level 1 Complete!"
        Widget[] children = widget.getChildren();
        if (children != null && children.length > 1) {
            Widget levelWidget = children[1];
            if (levelWidget != null) {
                String text = levelWidget.getText();
                if (text != null && text.contains("Level")) {
                    try {
                        // Extract number from "Level X Complete!"
                        String numStr = text.replaceAll("[^0-9]", "");
                        if (!numStr.isEmpty()) {
                            return Integer.parseInt(numStr);
                        }
                    } catch (NumberFormatException e) {
                        log.error("Error parsing delve number from text: {}", text, e);
                    }
                }
            }
        }
        return 0;
    }

    private void parseCurrentLoot(Widget lootContainer) {
        // Parse loot items from the container widget
        currentUnclaimedLoot.clear();

        Widget[] children = lootContainer.getChildren();
        if (children != null) {
            for (Widget child : children) {
                if (child != null && !child.isHidden()) {
                    int itemId = child.getItemId();
                    int quantity = child.getItemQuantity();

                    // Only add valid items (positive ID and quantity)
                    if (itemId > 0 && quantity > 0) {
                        // Verify item is valid by checking if ItemManager can resolve it
                        String itemName = itemManager.getItemComposition(itemId).getName();
                        if (itemName != null && !itemName.equalsIgnoreCase("null")) {
                            LootItem item = new LootItem(itemId, quantity, itemName);
                            currentUnclaimedLoot.add(item);
                        }
                    }
                }
            }
        }
    }

    private void handleDelveNumberChange(int newDelveNumber) {
        if (newDelveNumber != currentDelveNumber) {
            previousDelveNumber = currentDelveNumber;
            currentDelveNumber = newDelveNumber;

            // If delve number reset to 1, clear tracking (loot was either claimed or
            // already recorded on death)
            if (currentDelveNumber == 1 && previousDelveNumber > 1) {
                // Clear current tracking
                resetCurrentLoot();
            }
        }
    }

    private void recordLostLoot() {
        if (currentLootValue == 0) {
            log.info("recordLostLoot called but currentLootValue is 0, returning");
            return;
        }

        log.info("Recording lost loot: currentLootValue={}, currentDelveNumber={}", currentLootValue,
                currentDelveNumber);
        log.info("Wave loot values: {}", java.util.Arrays.toString(waveLootValues));

        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;

        // Note: Wave data should already be stored from checkDelveWidget()
        // Only recalculate if the current wave wasn't stored yet (edge case)
        if (currentDelveNumber > 0 && currentDelveNumber <= MAX_TRACKED_WAVES) {
            int waveIndex = currentDelveNumber;
            if (waveLootValues[waveIndex] == 0 && currentLootValue > 0) {
                // Edge case: player died before checkDelveWidget could store the wave data
                long incrementalLoot = currentLootValue - previousWaveLootValue;
                waveLootValues[waveIndex] = incrementalLoot;
                // Calculate incremental items for this wave
                List<LootItem> incrementalItems = calculateIncrementalItems(currentUnclaimedLoot, previousWaveItems);
                waveItemStacks[waveIndex] = incrementalItems;
            }
        }

        // Save each wave's incremental loot value and items
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            if (waveLootValues[wave] > 0) {
                // Apply adjustment for Sun-kissed Bones if enabled
                long adjustedWaveValue = getAdjustedLootValue(waveLootValues[wave], waveItemStacks[wave]);

                String waveKey = CONFIG_KEY_WAVE_PREFIX + wave;
                long existingWaveLost = getLongConfig(configGroup, waveKey);
                existingWaveLost += adjustedWaveValue;
                configManager.setConfiguration(configGroup, waveKey, existingWaveLost);

                // Save items for this wave
                if (waveItemStacks[wave] != null && !waveItemStacks[wave].isEmpty()) {
                    String itemsKey = CONFIG_KEY_WAVE_ITEMS_PREFIX + wave;
                    String existingItems = configManager.getConfiguration(configGroup, itemsKey);

                    // Merge new items with existing items using a map
                    java.util.Map<Integer, Integer> itemMap = new java.util.HashMap<>();

                    // Add existing items to map
                    if (existingItems != null && !existingItems.isEmpty()) {
                        String[] pairs = existingItems.split(",");
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

                    // Add new items to map
                    for (LootItem newItem : waveItemStacks[wave]) {
                        int itemId = newItem.getId();
                        int quantity = newItem.getQuantity();
                        itemMap.put(itemId, itemMap.getOrDefault(itemId, 0) + quantity);
                    }

                    // Convert map back to list for serialization
                    List<LootItem> allItems = new ArrayList<>();
                    for (java.util.Map.Entry<Integer, Integer> entry : itemMap.entrySet()) {
                        allItems.add(new LootItem(entry.getKey(), entry.getValue(), null));
                    }

                    String serializedItems = serializeItems(allItems);
                    configManager.setConfiguration(configGroup, itemsKey, serializedItems);
                }
            }
        }

        // Get current total lost value
        long totalLost = getLongConfig(configGroup, CONFIG_KEY_TOTAL_LOST);
        long adjustedTotalValue = getAdjustedLootValue(currentLootValue, currentUnclaimedLoot);
        totalLost += adjustedTotalValue;

        // Get current death count
        int timesDied = getIntConfig(configGroup, CONFIG_KEY_TIMES_DIED);
        timesDied++;

        // Save new values
        configManager.setConfiguration(configGroup, CONFIG_KEY_TOTAL_LOST, totalLost);
        configManager.setConfiguration(configGroup, CONFIG_KEY_TIMES_DIED, timesDied);

        // Send chat notification
        if (config.showChatNotifications()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=ff0000>Mokha loot lost!</col> " + QuantityFormatter.quantityToStackSize(adjustedTotalValue)
                            + " gp (Wave " + currentDelveNumber + ")",
                    null);
        }

        // Update panel stats on Swing thread (data fetched on this client thread)
        javax.swing.SwingUtilities.invokeLater(() -> {
            panel.updateStats();
        });
    }

    private void recordClaimedLoot() {
        if (lastVisibleLootValue == 0) {
            return;
        }

        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;

        // Save all waves that have loot data
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            if (waveLootValues[wave] <= 0) {
                continue; // Skip waves with no loot
            }

            int waveIndex = wave;
            long incrementalLoot = waveLootValues[wave];

            // Apply adjustment for Sun-kissed Bones if enabled
            long adjustedIncrementalLoot = getAdjustedLootValue(incrementalLoot, waveItemStacks[waveIndex]);

            // Save claimed loot for this wave
            String waveKey = CONFIG_KEY_WAVE_CLAIMED_PREFIX + waveIndex;
            long existingWaveClaimed = getLongConfig(configGroup, waveKey);
            existingWaveClaimed += adjustedIncrementalLoot;
            configManager.setConfiguration(configGroup, waveKey, existingWaveClaimed);

            // Save items for this wave
            if (waveItemStacks[waveIndex] != null && !waveItemStacks[waveIndex].isEmpty()) {
                String itemsKey = CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX + waveIndex;
                String existingItems = configManager.getConfiguration(configGroup, itemsKey);

                // Merge new items with existing items using a map
                java.util.Map<Integer, Integer> itemMap = new java.util.HashMap<>();

                // Add existing items to map
                if (existingItems != null && !existingItems.isEmpty()) {
                    String[] pairs = existingItems.split(",");
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

                // Add new items to map
                for (LootItem newItem : waveItemStacks[waveIndex]) {
                    int itemId = newItem.getId();
                    int quantity = newItem.getQuantity();
                    itemMap.put(itemId, itemMap.getOrDefault(itemId, 0) + quantity);
                }

                // Convert map back to list for serialization
                List<LootItem> allItems = new ArrayList<>();
                for (java.util.Map.Entry<Integer, Integer> entry : itemMap.entrySet()) {
                    allItems.add(new LootItem(entry.getKey(), entry.getValue(), null));
                }

                String serializedItems = serializeItems(allItems);
                configManager.setConfiguration(configGroup, itemsKey, serializedItems);
            }
        }

        // Send chat notification
        if (config.showChatNotifications()) {
            long adjustedTotalValue = getAdjustedLootValue(lastVisibleLootValue, lastVisibleLootItems);
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=00ff00>Mokha loot claimed!</col> " + QuantityFormatter.quantityToStackSize(adjustedTotalValue)
                            + " gp (Wave " + lastVisibleDelveNumber + ")",
                    null);
        }

        // Update panel stats
        panel.updateStats();

        // Reset tracking after claiming
        resetCurrentLoot();
    }

    private void incrementDeathCounter() {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;

        // Get current death count and increment
        int timesDied = getIntConfig(configGroup, CONFIG_KEY_TIMES_DIED);
        timesDied++;

        // Save new value
        configManager.setConfiguration(configGroup, CONFIG_KEY_TIMES_DIED, timesDied);

        // Update panel stats
        panel.updateStats();
    }

    private void resetCurrentLoot() {
        currentUnclaimedLoot.clear();
        previousWaveItems.clear();
        currentLootValue = 0;
        previousWaveLootValue = 0;
        waveLootValues = new long[MAX_TRACKED_WAVES + 1];
        waveItemStacks = new List[MAX_TRACKED_WAVES + 1];
        for (int i = 0; i <= MAX_TRACKED_WAVES; i++) {
            waveItemStacks[i] = new ArrayList<>();
        }
        currentDelveNumber = 0;
        previousDelveNumber = 0;
    }

    // Calculate incremental items by comparing current items to previous wave items
    private List<LootItem> calculateIncrementalItems(List<LootItem> currentItems, List<LootItem> previousItems) {
        // Create a map of previous items for easy lookup
        java.util.Map<Integer, Integer> previousItemMap = new java.util.HashMap<>();
        for (LootItem item : previousItems) {
            previousItemMap.put(item.getId(), item.getQuantity());
        }

        // Calculate incremental quantities
        List<LootItem> incrementalItems = new ArrayList<>();
        for (LootItem currentItem : currentItems) {
            int itemId = currentItem.getId();
            int currentQuantity = currentItem.getQuantity();
            int previousQuantity = previousItemMap.getOrDefault(itemId, 0);

            int incrementalQuantity = currentQuantity - previousQuantity;
            if (incrementalQuantity > 0) {
                incrementalItems.add(new LootItem(itemId, incrementalQuantity, null));
            }
        }

        return incrementalItems;
    }

    private String getAccountHash() {
        if (client.getAccountHash() != -1) {
            return String.valueOf(client.getAccountHash());
        }
        return "default";
    }

    private long getLongConfig(String group, String key) {
        String value = configManager.getConfiguration(group, key);
        if (value != null && !value.isEmpty()) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.error("Error parsing long config", e);
            }
        }
        return 0L;
    }

    private int getIntConfig(String group, String key) {
        String value = configManager.getConfiguration(group, key);
        if (value != null && !value.isEmpty()) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.error("Error parsing int config", e);
            }
        }
        return 0;
    }

    // Calculate loot value with optional Sun-kissed Bones exclusion
    private long getAdjustedLootValue(long baseValue, List<LootItem> items) {
        boolean excludeEnabled = config.excludeSunKissedBonesValue();

        if (!excludeEnabled) {
            return baseValue;
        }

        if (items == null || items.isEmpty()) {
            return baseValue;
        }

        long adjustment = 0;
        int bonesQuantity = 0;
        for (LootItem item : items) {
            if (item.getId() == SUN_KISSED_BONES_ID) {
                bonesQuantity = item.getQuantity();
                adjustment = (long) bonesQuantity * SUN_KISSED_BONES_VALUE;
                log.info("=== SUN-KISSED BONES DETECTED ===");
                log.info("Item ID: {} matches SUN_KISSED_BONES_ID ({})", item.getId(), SUN_KISSED_BONES_ID);
                log.info("Quantity: {}", bonesQuantity);
                log.info("Value per bone: {}", SUN_KISSED_BONES_VALUE);
                log.info("Total adjustment: {} ({}k)", adjustment, adjustment / 1000);
                log.info("Base value: {} ({}k)", baseValue, baseValue / 1000);
                break;
            }
        }

        long adjustedValue = Math.max(0, baseValue - adjustment);

        if (adjustment > 0) {
            log.info("Adjusted value: {} ({}k) - Subtracted {} sun-kissed bones worth {}gp",
                    adjustedValue, adjustedValue / 1000, bonesQuantity, adjustment);
            log.info("================================");
        }

        return adjustedValue;
    }

    // Serialize items to string format: "itemId:quantity,itemId:quantity,..."
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

    // Deserialize items from string and merge with existing items
    private List<LootItem> deserializeAndMergeItems(String serialized) {
        java.util.Map<Integer, Integer> itemMap = new java.util.HashMap<>();

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
        for (java.util.Map.Entry<Integer, Integer> entry : itemMap.entrySet()) {
            // Try to fetch the item name from ItemManager (only works on client thread)
            String itemName = null;
            try {
                // Check if we're on the client thread before calling ItemManager
                if (client.isClientThread()) {
                    ItemComposition itemComp = itemManager.getItemComposition(entry.getKey());
                    if (itemComp != null) {
                        itemName = itemComp.getName();
                    }
                }
                // If not on client thread, name will be null and display as "Item {id}"
            } catch (Exception e) {
                // If we can't fetch the name, it will remain null and display as "Item {id}"
            }
            result.add(new LootItem(entry.getKey(), entry.getValue(), itemName));
        }
        return result;
    }

    public long getTotalLostValue() {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        return getLongConfig(configGroup, CONFIG_KEY_TOTAL_LOST);
    }

    public int getTimesDied() {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        return getIntConfig(configGroup, CONFIG_KEY_TIMES_DIED);
    }

    public long getTotalDeathCosts() {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        return getLongConfig(configGroup, CONFIG_KEY_DEATH_COSTS);
    }

    public long getTotalClaimedValue() {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        long total = 0;
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            total += getWaveClaimedValue(wave);
        }
        return total;
    }

    public long getCurrentLootValue() {
        return getAdjustedLootValue(currentLootValue, currentUnclaimedLoot);
    }

    public int getCurrentDelveNumber() {
        return currentDelveNumber;
    }

    public boolean isDelveInterfaceVisible() {
        return delveInterfaceVisible;
    }

    public boolean isInMokhaArena() {
        return inMokhaArena;
    }

    public long getWaveLostValue(int wave) {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        String waveKey = CONFIG_KEY_WAVE_PREFIX + wave;
        return getLongConfig(configGroup, waveKey);
    }

    public List<LootItem> getWaveLostItems(int wave) {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        String itemsKey = CONFIG_KEY_WAVE_ITEMS_PREFIX + wave;
        String serialized = configManager.getConfiguration(configGroup, itemsKey);
        return deserializeAndMergeItems(serialized);
    }

    public long getWaveClaimedValue(int wave) {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        String waveKey = CONFIG_KEY_WAVE_CLAIMED_PREFIX + wave;
        long value = getLongConfig(configGroup, waveKey);
        if (wave <= 3) { // Only log first 3 waves to avoid spam
            log.info("getWaveClaimedValue({}) with accountHash={}, configGroup={}, value={}", wave, accountHash,
                    configGroup, value);
        }
        return value;
    }

    public List<LootItem> getWaveClaimedItems(int wave) {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        String itemsKey = CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX + wave;
        String serialized = configManager.getConfiguration(configGroup, itemsKey);
        List<LootItem> items = deserializeAndMergeItems(serialized);
        if (wave <= 3) { // Only log first 3 waves to avoid spam
            log.info("getWaveClaimedItems({}) with accountHash={}, serialized={}, items count={}", wave, accountHash,
                    serialized, items != null ? items.size() : 0);
        }
        return items;
    }

    public void resetStats() {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;

        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TOTAL_LOST);
        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TIMES_DIED);

        // Clear per-wave stats
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_PREFIX + wave);
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_ITEMS_PREFIX + wave);
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_CLAIMED_PREFIX + wave);
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX + wave);
        }

        // Reset death costs
        configManager.unsetConfiguration(configGroup, CONFIG_KEY_DEATH_COSTS);

        // Update panel stats on client thread (to fetch item names), then refresh UI
        clientThread.invokeLater(() -> {
            javax.swing.SwingUtilities.invokeLater(() -> {
                panel.updateStats();
                panel.revalidate();
                panel.repaint();
            });
        });
    }

    private void clearDefaultConfig() {
        // Clear any leftover config from dummy data that was saved under "default"
        // account
        String configGroup = "mokhaloot.default";

        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TOTAL_LOST);
        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TIMES_DIED);
        configManager.unsetConfiguration(configGroup, CONFIG_KEY_DEATH_COSTS);

        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_PREFIX + wave);
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_ITEMS_PREFIX + wave);
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_CLAIMED_PREFIX + wave);
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX + wave);
        }
    }

    @Provides
    MokhaLootTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MokhaLootTrackerConfig.class);
    }

    // TODO: Remove this method before production - for UI testing only

}
