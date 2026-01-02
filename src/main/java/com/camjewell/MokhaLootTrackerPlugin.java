package com.camjewell;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.camjewell.util.PotionUtil;
import com.camjewell.util.RunePouchUtil;
import com.google.inject.Provides;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

@PluginDescriptor(name = "Mokha Loot Tracker", description = "Tracks loot lost and claimed at the Doom of Mokhaiotl boss", tags = {
        "mokha", "loot", "boss", "death", "tracking" })
public class MokhaLootTrackerPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(MokhaLootTrackerPlugin.class);
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

    private static final int SPIRIT_SEEDS_ID = 5317;
    private static final int SPIRIT_SEED_VALUE = 140000;

    private static final String CONFIG_KEY_TOTAL_LOST = "totalLostValue";
    private static final String CONFIG_KEY_TIMES_DIED = "timesDied";
    private static final String CONFIG_KEY_DEATH_COSTS = "totalDeathCosts";
    private static final String CONFIG_KEY_TOTAL_SUPPLIES = "totalSuppliesCost";
    private static final String CONFIG_KEY_TOTAL_SUPPLIES_ITEMS = "totalSuppliesItems";
    private static final String CONFIG_KEY_WAVE_PREFIX = "wave";
    private static final String CONFIG_KEY_WAVE_ITEMS_PREFIX = "waveItems";
    private static final String CONFIG_KEY_WAVE_CLAIMED_PREFIX = "waveClaimed";
    private static final String CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX = "waveClaimedItems";
    private static final int MAX_TRACKED_WAVES = 9; // 1-8 individually, 9+ combined

    @Inject
    private Client client;

    @Inject
    MokhaLootTrackerConfig config;

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

    // Track last panel update value to avoid excessive updates
    private long lastPanelUpdateValue = -1;

    // Supply tracking for current run
    private java.util.Map<Integer, Integer> initialInventory = new java.util.HashMap<>();
    private java.util.Map<Integer, Integer> currentInventory = new java.util.HashMap<>();
    // Monotonic max-consumed tracker for live supplies display
    private java.util.Map<Integer, Integer> maxConsumedThisRun = new java.util.HashMap<>();
    // Track potion sips for live tracking
    private java.util.Map<String, Integer> initialPotionSips = new java.util.HashMap<>();
    private java.util.Map<String, Integer> maxConsumedPotionSips = new java.util.HashMap<>();
    private java.util.Map<String, java.util.Map<Integer, Integer>> livePotionDoseIds = new java.util.HashMap<>();
    // Rune pouch tracking (itemId -> quantity)
    private java.util.Map<Integer, Integer> initialRunePouch = new java.util.HashMap<>();
    private java.util.Map<Integer, Integer> currentRunePouch = new java.util.HashMap<>();
    private java.util.Map<Integer, Integer> maxConsumedRunes = new java.util.HashMap<>();
    private boolean hasInitialInventory = false;

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

        // Capture initial supplies snapshot immediately on entering the arena so the
        // first
        // sip/consumption is recorded (avoids missing the first change-driven event)
        if (inMokhaArena && !wasInMokhaArena && !hasInitialInventory) {
            captureInitialSuppliesSnapshot(client.getItemContainer(InventoryID.INVENTORY));
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
                    boolean hasLoot = currentLootValue > 0 || lastVisibleLootValue > 0
                            || (currentUnclaimedLoot != null && !currentUnclaimedLoot.isEmpty());
                    int effectiveDelve = currentDelveNumber > 0 ? currentDelveNumber : lastVisibleDelveNumber;

                    if (hasLoot && effectiveDelve > 0) {
                        // If we lost visibility before death (e.g., interface closed), fall back to the
                        // last captured state so we still record the loss.
                        if (currentLootValue == 0) {
                            if (lastVisibleLootValue > 0) {
                                currentLootValue = lastVisibleLootValue;
                                if (currentUnclaimedLoot.isEmpty() && lastVisibleLootItems != null) {
                                    currentUnclaimedLoot = new ArrayList<>(lastVisibleLootItems);
                                }
                                if (currentDelveNumber == 0) {
                                    currentDelveNumber = lastVisibleDelveNumber;
                                }
                            } else if (!currentUnclaimedLoot.isEmpty()) {
                                currentLootValue = getTotalLootValue(currentUnclaimedLoot);
                            }
                        }

                        recordLostLoot();
                    } else {
                        // Count death even without loot
                        incrementDeathCounter();
                    }

                    // Always clear run state on death so the panel doesn't show stale loot
                    resetCurrentLoot();
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
    public void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getGroup().equals("mokhaloot")) {
            if (configChanged.getKey().equals("excludeSunKissedBonesValue") ||
                    configChanged.getKey().equals("minItemValueThreshold") ||
                    configChanged.getKey().equals("excludeSpiritSeedsValue") ||
                    configChanged.getKey().equals("showSuppliesUsedBeta")) {
                // Refresh panel when display-related settings change
                clientThread.invokeLater(() -> panel.updateStats());
            }
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        String menuOption = event.getMenuOption();
        // String menuTarget = event.getMenuTarget();

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
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Only track inventory changes in Mokha arena. If we haven't marked entry yet,
        // do a lightweight widget check to avoid missing the first sip before the
        // tick-based arena detection flips the flag.
        if (!inMokhaArena) {
            if (!hasInitialInventory) {
                Widget mokhaWidget = client.getWidget(303, 9);
                Widget delveWidget = client.getWidget(919, 2);
                boolean hasArenaWidget = mokhaWidget != null && !mokhaWidget.isHidden()
                        && mokhaWidget.getText() != null
                        && mokhaWidget.getText().contains("Mokha");
                boolean hasDelveWidget = delveWidget != null && !delveWidget.isHidden();
                if (hasArenaWidget || hasDelveWidget) {
                    inMokhaArena = true;
                }
            }
            if (!inMokhaArena) {
                return;
            }
        }

        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            ItemContainer container = event.getItemContainer();
            if (container != null) {
                // Capture initial snapshot if not yet captured (typically happens on arena
                // entry)
                if (!hasInitialInventory) {
                    captureInitialSuppliesSnapshot(container);
                }

                // Update current inventory
                currentInventory.clear();
                for (Item item : container.getItems()) {
                    if (item != null && item.getId() > 0) {
                        currentInventory.put(item.getId(),
                                currentInventory.getOrDefault(item.getId(), 0) + item.getQuantity());
                        String itemName = itemManager.getItemComposition(item.getId()).getName();
                        if (isPotion(itemName)) {
                            String base = getPotionBaseName(itemName);
                            int dose = getPotionDose(itemName);
                            int sips = dose * item.getQuantity();
                            livePotionDoseIds.computeIfAbsent(base, k -> new java.util.HashMap<>())
                                    .putIfAbsent(dose, item.getId());
                        }
                    }
                }
                // Also include currently equipped items
                ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);
                if (equipmentContainer != null) {
                    for (Item item : equipmentContainer.getItems()) {
                        if (item != null && item.getId() > 0) {
                            currentInventory.put(item.getId(),
                                    currentInventory.getOrDefault(item.getId(), 0) + item.getQuantity());
                            String itemName = itemManager.getItemComposition(item.getId()).getName();
                            if (isPotion(itemName)) {
                                String base = getPotionBaseName(itemName);
                                int dose = getPotionDose(itemName);
                                int sips = dose * item.getQuantity();
                                livePotionDoseIds.computeIfAbsent(base, k -> new java.util.HashMap<>())
                                        .putIfAbsent(dose, item.getId());
                            }
                        }
                    }
                }

                // Update rune pouch tracking
                currentRunePouch = RunePouchUtil.readRunePouch(client);
                for (java.util.Map.Entry<Integer, Integer> entry : currentRunePouch.entrySet()) {
                    initialRunePouch.putIfAbsent(entry.getKey(), 0);
                }
                for (java.util.Map.Entry<Integer, Integer> entry : initialRunePouch.entrySet()) {
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

                // Update monotonic consumed tracker based on new snapshot
                for (java.util.Map.Entry<Integer, Integer> entry : initialInventory.entrySet()) {
                    int itemId = entry.getKey();
                    int initialQty = entry.getValue();
                    int currentQty = currentInventory.getOrDefault(itemId, 0);
                    int usedNow = initialQty - currentQty;

                    if (usedNow > 0) {
                        int prevMax = maxConsumedThisRun.getOrDefault(itemId, 0);
                        if (usedNow > prevMax) {
                            maxConsumedThisRun.put(itemId, usedNow);
                        }
                    }
                }

                // Potion sip tracking (dose changes do not reduce quantity, so track sips)
                java.util.Map<String, Integer> currentPotionSips = new java.util.HashMap<>();
                for (java.util.Map.Entry<Integer, Integer> entry : currentInventory.entrySet()) {
                    int itemId = entry.getKey();
                    int qty = entry.getValue();
                    String itemName = itemManager.getItemComposition(itemId).getName();
                    if (isPotion(itemName)) {
                        String base = getPotionBaseName(itemName);
                        int dose = getPotionDose(itemName);
                        int sips = dose * qty;
                        currentPotionSips.put(base, currentPotionSips.getOrDefault(base, 0) + sips);
                        livePotionDoseIds.computeIfAbsent(base, k -> new java.util.HashMap<>())
                                .putIfAbsent(dose, itemId);
                    }
                }
                for (java.util.Map.Entry<String, Integer> entry : initialPotionSips.entrySet()) {
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
                    int invCount = container.getItems().length;
                    int equipCount = equipmentContainer != null ? equipmentContainer.getItems().length : 0;
                    log.info(
                            "[Supplies Debug] Current snapshot (inventory {} slots, equipment {} slots, merged {} entries)",
                            invCount, equipCount, currentInventory.size());
                    // Inventory items
                    log.info("[Supplies Debug]   Inventory items:");
                    for (Item item : container.getItems()) {
                        if (item != null && item.getId() > 0) {
                            String itemName = itemManager.getItemComposition(item.getId()).getName();
                            log.info("    {} x{}", itemName, item.getQuantity());
                        }
                    }
                    // Equipment items
                    log.info("[Supplies Debug]   Equipment items:");
                    if (equipmentContainer != null) {
                        for (Item item : equipmentContainer.getItems()) {
                            if (item != null && item.getId() > 0) {
                                String itemName = itemManager.getItemComposition(item.getId()).getName();
                                log.info("    {} x{}", itemName, item.getQuantity());
                            }
                        }
                    } else {
                        log.info("    (none)");
                    }
                }
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

                // Recalculate loot value with custom pricing for Mokhaiotl Cloth
                currentLootValue = 0;
                for (LootItem item : currentUnclaimedLoot) {
                    currentLootValue += getItemValue(item.getId(), item.getQuantity());
                }
            }

            // Save the current state while interface is visible (before it gets cleared)
            if (currentLootValue > 0) {
                // Track if we need to update the panel
                boolean shouldUpdatePanel = false;

                lastVisibleLootValue = currentLootValue;
                lastVisibleLootItems = new ArrayList<>(currentUnclaimedLoot);
                lastVisibleDelveNumber = currentDelveNumber;

                // Calculate and save incremental loot for THIS wave
                // The interface shows cumulative rewards, so we subtract previous total
                if (currentDelveNumber > 0) {
                    int waveIndex = currentDelveNumber > MAX_TRACKED_WAVES ? MAX_TRACKED_WAVES : currentDelveNumber;
                    long incrementalValue = currentLootValue - previousWaveLootValue;
                    if (incrementalValue > 0) {
                        // For wave 9+, accumulate instead of overwrite
                        if (currentDelveNumber > MAX_TRACKED_WAVES) {
                            waveLootValues[waveIndex] += incrementalValue;
                            // Merge incremental items into waveItemStacks[9]
                            List<LootItem> incrementalItems = calculateIncrementalItems(currentUnclaimedLoot,
                                    previousWaveItems);
                            if (waveItemStacks[waveIndex] == null) {
                                waveItemStacks[waveIndex] = new ArrayList<>();
                            }
                            // Merge by itemId
                            java.util.Map<Integer, Integer> itemMap = new java.util.HashMap<>();
                            for (LootItem item : waveItemStacks[waveIndex]) {
                                itemMap.put(item.getId(), itemMap.getOrDefault(item.getId(), 0) + item.getQuantity());
                            }
                            for (LootItem item : incrementalItems) {
                                itemMap.put(item.getId(), itemMap.getOrDefault(item.getId(), 0) + item.getQuantity());
                            }
                            waveItemStacks[waveIndex] = new ArrayList<>();
                            for (java.util.Map.Entry<Integer, Integer> entry : itemMap.entrySet()) {
                                waveItemStacks[waveIndex].add(new LootItem(entry.getKey(), entry.getValue(), null));
                            }
                        } else {
                            waveLootValues[waveIndex] = incrementalValue;
                            // Calculate incremental items (items added this wave only)
                            List<LootItem> incrementalItems = calculateIncrementalItems(currentUnclaimedLoot,
                                    previousWaveItems);
                            waveItemStacks[waveIndex] = incrementalItems;
                        }

                        // Update previous wave tracking
                        previousWaveLootValue = currentLootValue;
                        previousWaveItems = new ArrayList<>(currentUnclaimedLoot);

                        shouldUpdatePanel = true;
                    }
                }

                // Update panel if wave data changed, or if current value changed since last
                // update
                // This ensures the panel stays in sync with the overlay during the run
                if (shouldUpdatePanel || currentLootValue != lastPanelUpdateValue) {
                    panel.updateStats();
                    lastPanelUpdateValue = currentLootValue;
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

    // Capture the initial supplies snapshot (inventory + equipment + rune pouch +
    // potion doses)
    private void captureInitialSuppliesSnapshot(ItemContainer inventoryContainer) {
        if (inventoryContainer == null || hasInitialInventory) {
            return;
        }

        initialInventory.clear();
        initialPotionSips.clear();
        maxConsumedThisRun.clear();
        maxConsumedPotionSips.clear();
        livePotionDoseIds.clear();
        initialRunePouch = RunePouchUtil.readRunePouch(client);
        currentRunePouch = new java.util.HashMap<>(initialRunePouch);
        maxConsumedRunes.clear();

        for (Item item : inventoryContainer.getItems()) {
            if (item == null || item.getId() <= 0) {
                continue;
            }
            initialInventory.put(item.getId(), initialInventory.getOrDefault(item.getId(), 0) + item.getQuantity());
            String itemName = itemManager.getItemComposition(item.getId()).getName();
            if (isPotion(itemName)) {
                String base = getPotionBaseName(itemName);
                int dose = getPotionDose(itemName);
                int sips = dose * item.getQuantity();
                initialPotionSips.put(base, initialPotionSips.getOrDefault(base, 0) + sips);
                livePotionDoseIds.computeIfAbsent(base, k -> new java.util.HashMap<>()).putIfAbsent(dose,
                        item.getId());
            }
        }

        ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);
        if (equipmentContainer != null) {
            for (Item item : equipmentContainer.getItems()) {
                if (item == null || item.getId() <= 0) {
                    continue;
                }
                initialInventory.put(item.getId(), initialInventory.getOrDefault(item.getId(), 0) + item.getQuantity());
                String itemName = itemManager.getItemComposition(item.getId()).getName();
                if (isPotion(itemName)) {
                    String base = getPotionBaseName(itemName);
                    int dose = getPotionDose(itemName);
                    int sips = dose * item.getQuantity();
                    initialPotionSips.put(base, initialPotionSips.getOrDefault(base, 0) + sips);
                    livePotionDoseIds.computeIfAbsent(base, k -> new java.util.HashMap<>()).putIfAbsent(dose,
                            item.getId());
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
            log.info("[Supplies Debug]   Inventory items:");
            for (Item item : inventoryContainer.getItems()) {
                if (item != null && item.getId() > 0) {
                    String itemName = itemManager.getItemComposition(item.getId()).getName();
                    log.info("    {} x{}", itemName, item.getQuantity());
                }
            }
            log.info("[Supplies Debug]   Equipment items:");
            if (equipmentContainer != null) {
                for (Item item : equipmentContainer.getItems()) {
                    if (item != null && item.getId() > 0) {
                        String itemName = itemManager.getItemComposition(item.getId()).getName();
                        log.info("    {} x{}", itemName, item.getQuantity());
                    }
                }
            } else {
                log.info("    (none)");
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
            return;
        }

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

        // Save supplies used for this run
        long suppliesCost = getSuppliesUsedValue();
        if (suppliesCost > 0) {
            long totalSupplies = getLongConfig(configGroup, CONFIG_KEY_TOTAL_SUPPLIES);
            totalSupplies += suppliesCost;
            configManager.setConfiguration(configGroup, CONFIG_KEY_TOTAL_SUPPLIES, totalSupplies);

            // Save supplies items (normalize potions by total sips so doses combine)
            List<LootItem> suppliesUsedItems = getSuppliesUsedItems();
            if (!suppliesUsedItems.isEmpty()) {
                String existingSuppliesItems = configManager.getConfiguration(configGroup,
                        CONFIG_KEY_TOTAL_SUPPLIES_ITEMS);
                String serializedSupplies = mergeSuppliesTotals(existingSuppliesItems, suppliesUsedItems);
                configManager.setConfiguration(configGroup, CONFIG_KEY_TOTAL_SUPPLIES_ITEMS, serializedSupplies);
            }
            if (config.debugItemValueLogging()) {
                log.info("[Supplies Debug] Saved supplies: value={} gp, items={}", suppliesCost,
                        serializeItems(getSuppliesUsedItems()));
            }
        }

        // Send chat notification
        if (config.showChatNotifications()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=ff0000>Mokha loot lost!</col> " + QuantityFormatter.quantityToStackSize(adjustedTotalValue)
                            + " gp (Wave " + currentDelveNumber + ")",
                    null);
        }

        // Update panel stats on client thread (ItemManager requires it)
        clientThread.invokeLater(() -> panel.updateStats());
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
            } else {
            }
        }

        // Save supplies used for this run
        long suppliesCost = getSuppliesUsedValue();
        if (suppliesCost > 0) {
            long totalSupplies = getLongConfig(configGroup, CONFIG_KEY_TOTAL_SUPPLIES);
            totalSupplies += suppliesCost;
            configManager.setConfiguration(configGroup, CONFIG_KEY_TOTAL_SUPPLIES, totalSupplies);

            // Save supplies items (normalize potions by total sips so doses combine)
            List<LootItem> suppliesUsedItems = getSuppliesUsedItems();
            if (!suppliesUsedItems.isEmpty()) {
                String existingSuppliesItems = configManager.getConfiguration(configGroup,
                        CONFIG_KEY_TOTAL_SUPPLIES_ITEMS);
                String serializedSupplies = mergeSuppliesTotals(existingSuppliesItems, suppliesUsedItems);
                configManager.setConfiguration(configGroup, CONFIG_KEY_TOTAL_SUPPLIES_ITEMS, serializedSupplies);
            }
            if (config.debugItemValueLogging()) {
                log.info("[Supplies Debug] Saved supplies: value={} gp, items={}", suppliesCost,
                        serializeItems(getSuppliesUsedItems()));
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

        // Update panel stats on client thread (ItemManager requires it)
        clientThread.invokeLater(() -> panel.updateStats());

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

        // Update panel stats on client thread (ItemManager requires it)
        clientThread.invokeLater(() -> panel.updateStats());
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
        lastPanelUpdateValue = -1; // Reset panel update tracking

        // Reset supply tracking
        initialInventory.clear();
        currentInventory.clear();
        maxConsumedThisRun.clear();
        initialPotionSips.clear();
        maxConsumedPotionSips.clear();
        livePotionDoseIds.clear();
        initialRunePouch.clear();
        currentRunePouch.clear();
        maxConsumedRunes.clear();
        hasInitialInventory = false;
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

    // Calculate loot value with optional Sun-kissed Bones or Spirit Seeds exclusion
    private long getAdjustedLootValue(long baseValue, List<LootItem> items) {
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
            if (item.getId() == SPIRIT_SEEDS_ID && config.excludeSpiritSeedValue()) {
                adjustment += (long) item.getQuantity() * SPIRIT_SEED_VALUE;
            }
        }

        return Math.max(0, baseValue - adjustment);
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

    // Merge existing serialized supplies totals with new supplies, combining potion
    // doses (stored as total doses using the (1)-dose item for pricing).
    private String mergeSuppliesTotals(String existingSerialized, List<LootItem> newItems) {
        java.util.Map<String, Integer> potionSips = new java.util.HashMap<>(); // base -> total sips
        java.util.Map<String, java.util.Map<Integer, Integer>> observedDoseIds = new java.util.HashMap<>(); // base ->
                                                                                                            // (dose ->
                                                                                                            // id)
        java.util.Map<Integer, Integer> nonPotions = new java.util.HashMap<>(); // itemId -> qty

        // Helper to process a single item
        java.util.function.BiConsumer<LootItem, Boolean> addItem = (item, fromNew) -> {
            if (item == null) {
                return;
            }
            String name = item.getName();
            // If name missing, try to fetch (we should be on client thread when saving
            // supplies)
            if (name == null) {
                try {
                    ItemComposition comp = itemManager.getItemComposition(item.getId());
                    if (comp != null) {
                        name = comp.getName();
                    }
                } catch (Exception e) {
                    // Ignore; leave name null if unavailable
                }
            }

            if (name != null && isPotion(name)) {
                int dose = getPotionDose(name);
                if (dose > 0) {
                    String base = getPotionBaseName(name);
                    int sips = dose * item.getQuantity();
                    potionSips.put(base, potionSips.getOrDefault(base, 0) + sips);
                    if (fromNew) {
                        observedDoseIds.computeIfAbsent(base, k -> new java.util.HashMap<>())
                                .putIfAbsent(dose, item.getId());
                    }
                    return;
                }
            }

            // Non-potion or unknown dose: aggregate by id
            nonPotions.put(item.getId(), nonPotions.getOrDefault(item.getId(), 0) + item.getQuantity());
        };

        // Parse existing serialized supplies
        if (existingSerialized != null && !existingSerialized.isEmpty()) {
            String[] pairs = existingSerialized.split(",");
            for (String pair : pairs) {
                try {
                    String[] parts = pair.split(":");
                    if (parts.length == 2) {
                        int itemId = Integer.parseInt(parts[0]);
                        int qty = Integer.parseInt(parts[1]);
                        LootItem li = new LootItem(itemId, qty, null);
                        addItem.accept(li, false);
                    }
                } catch (NumberFormatException e) {
                    log.error("Error parsing supplies item pair: {}", pair, e);
                }
            }
        }

        // Add new items
        for (LootItem li : newItems) {
            addItem.accept(li, true);
        }

        // Rebuild normalized list using total doses (1-dose item as unit)
        List<LootItem> normalized = new ArrayList<>();

        for (java.util.Map.Entry<String, Integer> entry : potionSips.entrySet()) {
            String base = entry.getKey();
            int totalDoses = entry.getValue();
            if (totalDoses <= 0) {
                continue;
            }
            String name = base + " (1)";
            int id = resolvePotionId(base, 1, observedDoseIds, name);
            normalized.add(new LootItem(id, totalDoses, name));
        }

        // Non-potions
        for (java.util.Map.Entry<Integer, Integer> entry : nonPotions.entrySet()) {
            normalized.add(new LootItem(entry.getKey(), entry.getValue(), null));
        }

        return serializeItems(normalized);
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
        // Calculate from wave values which already have adjustments applied
        long total = 0;
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            total += getWaveLostValue(wave);
        }
        return total;
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

    public long getTotalSuppliesCost() {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        return getLongConfig(configGroup, CONFIG_KEY_TOTAL_SUPPLIES);
    }

    public List<LootItem> getTotalSuppliesItems() {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        String serialized = configManager.getConfiguration(configGroup, CONFIG_KEY_TOTAL_SUPPLIES_ITEMS);
        return deserializeAndMergeItems(serialized);
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

    public List<LootItem> getCurrentLootItems() {
        List<LootItem> items = new ArrayList<>(currentUnclaimedLoot);

        // If test mode is enabled, add test items
        // Removed testMode and test item injection

        return items;
    }

    // Helper methods for potion handling
    private boolean isPotion(String itemName) {
        return PotionUtil.isPotion(itemName);
    }

    private String getPotionBaseName(String itemName) {
        return PotionUtil.getPotionBaseName(itemName);
    }

    // Resolve a potion item id for a base name and dose. Prefer observed ids;
    // fallback to search on client thread; else 0.
    private int resolvePotionId(String baseName, int dose,
            java.util.Map<String, java.util.Map<Integer, Integer>> doseMap,
            String displayName) {
        try {
            Integer observed = doseMap.getOrDefault(baseName, java.util.Collections.emptyMap()).get(dose);
            if (observed != null) {
                return observed;
            }
            if (client.isClientThread()) {
                // Attempt to find matching item by exact name "Base (dose)"
                String searchName = baseName + " (" + dose + ")";
                java.util.List<net.runelite.http.api.item.ItemPrice> results = itemManager.search(searchName);
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
        return 0; // Fallback; will display name but price 0
    }

    private int getPotionDose(String itemName) {
        return PotionUtil.getPotionDose(itemName);
    }

    private long computeSuppliesValue(java.util.Map<Integer, Integer> usageMap, boolean logItems) {
        long totalValue = 0;
        for (java.util.Map.Entry<Integer, Integer> entry : usageMap.entrySet()) {
            int itemId = entry.getKey();
            int used = entry.getValue();

            if (used > 0) {
                if (logItems && config.debugItemValueLogging()) {
                    int currentQty = currentInventory.getOrDefault(itemId, 0);
                    int initialQty = initialInventory.getOrDefault(itemId, 0);
                    log.info("[Supplies Debug] Item ID {}: initial={}, current={}, used={}",
                            itemId, initialQty, currentQty, used);
                }
                long itemValue = getItemValue(itemId, used);
                totalValue += itemValue;
                if (logItems && config.debugItemValueLogging()) {
                    log.info("[Supplies Debug]   Item {} value: {} gp", itemId, itemValue);
                }
            }
        }
        if (logItems && config.debugItemValueLogging()) {
            log.info("[Supplies Debug] Total supplies value: {} gp", totalValue);
        }
        return totalValue;
    }

    private java.util.Map<String, Integer> computePotionSips(java.util.Map<Integer, Integer> inv) {
        java.util.Map<String, Integer> sips = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, Integer> entry : inv.entrySet()) {
            int itemId = entry.getKey();
            int qty = entry.getValue();
            String itemName = itemManager.getItemComposition(itemId).getName();
            if (isPotion(itemName)) {
                String base = getPotionBaseName(itemName);
                int dose = getPotionDose(itemName);
                int total = dose * qty;
                sips.put(base, sips.getOrDefault(base, 0) + total);
            }
        }
        return sips;
    }

    private List<LootItem> buildSuppliesFromUsage(java.util.Map<Integer, Integer> usageMap, boolean logItems) {
        List<LootItem> supplies = new ArrayList<>();
        java.util.Map<String, Integer> potionTotalSips = new java.util.HashMap<>();
        java.util.Map<String, java.util.Map<Integer, Integer>> potionDoseIds = new java.util.HashMap<>();

        if (logItems && config.debugItemValueLogging()) {
            log.info("[Supplies Debug] Processing supplies used items...");
        }

        for (java.util.Map.Entry<Integer, Integer> entry : usageMap.entrySet()) {
            int itemId = entry.getKey();
            int used = entry.getValue();
            if (used <= 0) {
                continue;
            }

            String itemName = itemManager.getItemComposition(itemId).getName();
            if (logItems && config.debugItemValueLogging()) {
                log.info("[Supplies Debug] Processing item {} ({}): used={}", itemId, itemName, used);
            }

            if (isPotion(itemName)) {
                String baseName = getPotionBaseName(itemName);
                int dose = getPotionDose(itemName);
                int totalSips = used * dose;
                potionTotalSips.put(baseName, potionTotalSips.getOrDefault(baseName, 0) + totalSips);
                potionDoseIds.computeIfAbsent(baseName, k -> new java.util.HashMap<>()).putIfAbsent(dose, itemId);
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

        for (java.util.Map.Entry<String, Integer> entry : potionTotalSips.entrySet()) {
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

    public long getSuppliesUsedValue() {
        if (!hasInitialInventory) {
            return 0;
        }

        long total = 0;
        for (LootItem li : getSuppliesUsedItems()) {
            if (li.getId() > 0) {
                total += getItemValue(li.getId(), li.getQuantity());
            }
        }
        return total;
    }

    public List<LootItem> getSuppliesUsedItems() {
        if (!hasInitialInventory) {
            return new ArrayList<>();
        }

        java.util.Map<Integer, Integer> usageMap = new java.util.HashMap<>();
        java.util.Map<String, Integer> potionSipsUsed = new java.util.HashMap<>();
        java.util.Map<String, java.util.Map<Integer, Integer>> potionDoseIds = new java.util.HashMap<>();

        // Non-potion delta by quantity; collect potion IDs for lookup only
        for (java.util.Map.Entry<Integer, Integer> entry : initialInventory.entrySet()) {
            int itemId = entry.getKey();
            int used = entry.getValue() - currentInventory.getOrDefault(itemId, 0);
            if (used <= 0) {
                continue;
            }
            String name = itemManager.getItemComposition(itemId).getName();
            if (isPotion(name)) {
                String base = getPotionBaseName(name);
                int dose = getPotionDose(name);
                potionDoseIds.computeIfAbsent(base, k -> new java.util.HashMap<>()).putIfAbsent(dose, itemId);
            } else {
                usageMap.put(itemId, used);
            }
        }

        // Rune pouch consumption (non-potion)
        for (java.util.Map.Entry<Integer, Integer> entry : initialRunePouch.entrySet()) {
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

        // Potion consumption by sips (handles dose changes)
        if (initialPotionSips.isEmpty()) {
            initialPotionSips.putAll(computePotionSips(initialInventory));
        }
        java.util.Map<String, Integer> currentPotionSips = computePotionSips(currentInventory);
        for (java.util.Map.Entry<String, Integer> entry : initialPotionSips.entrySet()) {
            String base = entry.getKey();
            int initialSips = entry.getValue();
            int currentSips = currentPotionSips.getOrDefault(base, 0);
            int consumed = initialSips - currentSips;
            if (consumed > 0) {
                potionSipsUsed.put(base, consumed);
            }
        }

        // Build non-potion supplies
        List<LootItem> supplies = buildSuppliesFromUsage(usageMap, true);

        // Build potion supplies from total dose counts
        for (java.util.Map.Entry<String, Integer> entry : potionSipsUsed.entrySet()) {
            String base = entry.getKey();
            int totalDoses = entry.getValue();
            if (totalDoses <= 0) {
                continue;
            }

            String doseName = base + " (1)";
            int potionId = resolvePotionId(base, 1, potionDoseIds, doseName);
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

    public long getLiveSuppliesUsedValue() {
        if (!hasInitialInventory) {
            return 0;
        }

        long total = 0;
        for (LootItem li : getLiveSuppliesUsedItems()) {
            if (li.getId() > 0) {
                total += getItemValue(li.getId(), li.getQuantity());
            }
        }
        return total;
    }

    public List<LootItem> getLiveSuppliesUsedItems() {
        if (!hasInitialInventory) {
            return new ArrayList<>();
        }

        java.util.Map<Integer, Integer> usageMap = new java.util.HashMap<>();
        for (java.util.Map.Entry<Integer, Integer> entry : maxConsumedThisRun.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            int itemId = entry.getKey();
            String name = itemManager.getItemComposition(itemId).getName();
            if (isPotion(name)) {
                // Track via dose-based map instead
                continue;
            }
            usageMap.put(itemId, entry.getValue());
        }

        // Include rune pouch consumption in live view
        for (java.util.Map.Entry<Integer, Integer> entry : maxConsumedRunes.entrySet()) {
            int used = entry.getValue();
            if (used > 0) {
                int itemId = entry.getKey();
                usageMap.put(itemId, usageMap.getOrDefault(itemId, 0) + used);
            }
        }

        List<LootItem> nonPotion = buildSuppliesFromUsage(usageMap, false);

        // Build potion items from total dose counts
        List<LootItem> potions = new ArrayList<>();
        for (java.util.Map.Entry<String, Integer> entry : maxConsumedPotionSips.entrySet()) {
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

    public boolean isHighValueItem(int itemId) {
        return itemId == AVERNIC_TREADS_ID || itemId == MOKHAIOTL_CLOTH_ID || itemId == EYE_OF_AYAK_ID;
    }

    // Calculate the value of Mokhaiotl Cloth based on current GE prices
    // Formula: Confliction Gauntlets - (Tormented Bracelet + (Demon Tear * 10000))
    private long getMokhaiotlClothValue() {
        // Fallback values (update as needed)
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
            return Math.max(0, clothValue); // Ensure non-negative
        } catch (Exception e) {
            log.error("Error calculating Mokhaiotl Cloth value", e);
            return 0;
        }
    }

    // Get the value for a specific item, with special handling for Mokhaiotl Cloth
    long getItemValue(int itemId, int quantity) {
        if (itemId == MOKHAIOTL_CLOTH_ID) {
            return getMokhaiotlClothValue() * quantity;
        }
        long itemPrice = itemManager.getItemPrice(itemId);
        return itemPrice * quantity;
    }

    // Test mode: Add rare items to current loot for testing display
    private void addTestItems() {
        // Check if test items are already added to avoid duplicates
        boolean hasTestItems = currentUnclaimedLoot.stream()
                .anyMatch(item -> item.getId() == AVERNIC_TREADS_ID
                        || item.getId() == MOKHAIOTL_CLOTH_ID
                        || item.getId() == EYE_OF_AYAK_ID);

        if (!hasTestItems) {
            try {
                // Add one of each rare item
                String treadsName = itemManager.getItemComposition(AVERNIC_TREADS_ID).getName();
                String clothName = itemManager.getItemComposition(MOKHAIOTL_CLOTH_ID).getName();
                String eyeName = itemManager.getItemComposition(EYE_OF_AYAK_ID).getName();

                currentUnclaimedLoot.add(new LootItem(AVERNIC_TREADS_ID, 1, treadsName));
                currentUnclaimedLoot.add(new LootItem(MOKHAIOTL_CLOTH_ID, 1, clothName));
                currentUnclaimedLoot.add(new LootItem(EYE_OF_AYAK_ID, 1, eyeName));

                // Recalculate total value with test items
                currentLootValue = 0;
                for (LootItem item : currentUnclaimedLoot) {
                    currentLootValue += getItemValue(item.getId(), item.getQuantity());
                }
            } catch (Exception e) {
                log.error("Error adding test items", e);
            }
        }
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

    /**
     * Returns the filtered value (excluding items below threshold) for the wave.
     * Use getWaveLostFullValue for the unfiltered value.
     */
    public long getWaveLostValue(int wave) {
        List<LootItem> items = getWaveLostItems(wave);
        return getFilteredLootValue(items);
    }

    /**
     * Returns the full value (all items, no threshold) for the wave.
     */
    public long getWaveLostFullValue(int wave) {
        List<LootItem> items = getWaveLostItems(wave);
        return getTotalLootValue(items);
    }

    public List<LootItem> getWaveLostItems(int wave) {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        String itemsKey = CONFIG_KEY_WAVE_ITEMS_PREFIX + wave;
        String serialized = configManager.getConfiguration(configGroup, itemsKey);
        return deserializeAndMergeItems(serialized);
    }

    /**
     * Returns the filtered value (excluding items below threshold) for claimed loot
     * in the wave.
     * Use getWaveClaimedFullValue for the unfiltered value.
     */
    public long getWaveClaimedValue(int wave) {
        List<LootItem> items = getWaveClaimedItems(wave);
        return getFilteredLootValue(items);
    }

    /**
     * Returns the full value (all items, no threshold) for claimed loot in the
     * wave.
     */
    public long getWaveClaimedFullValue(int wave) {
        List<LootItem> items = getWaveClaimedItems(wave);
        return getTotalLootValue(items);
    }

    /**
     * Returns only items above the value threshold for display.
     */
    public List<LootItem> filterItemsByValue(List<LootItem> items) {
        int minValue = config.minItemValueThreshold();
        if (minValue <= 0 || items == null)
            return items;
        List<LootItem> filtered = new ArrayList<>();
        for (LootItem item : items) {
            if (getItemValue(item.getId(), 1) >= minValue) {
                filtered.add(item);
            }
        }
        return filtered;
    }

    /**
     * Returns the sum of values for items above the threshold.
     */
    public long getFilteredLootValue(List<LootItem> items) {
        int minValue = config.minItemValueThreshold();
        if (items == null)
            return 0;
        long total = 0;
        for (LootItem item : items) {
            if (minValue > 0 && getItemValue(item.getId(), 1) < minValue)
                continue;
            total += getItemValue(item.getId(), item.getQuantity());
        }
        return total;
    }

    /**
     * Returns the sum of values for all items (no threshold).
     */
    public long getTotalLootValue(List<LootItem> items) {
        if (items == null)
            return 0;
        long total = 0;
        for (LootItem item : items) {
            total += getItemValue(item.getId(), item.getQuantity());
        }
        return total;
    }

    public List<LootItem> getWaveClaimedItems(int wave) {
        String accountHash = getAccountHash();
        String configGroup = "mokhaloot." + accountHash;
        String itemsKey = CONFIG_KEY_WAVE_CLAIMED_ITEMS_PREFIX + wave;
        String serialized = configManager.getConfiguration(configGroup, itemsKey);
        List<LootItem> items = deserializeAndMergeItems(serialized);
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

        // Reset supplies costs
        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TOTAL_SUPPLIES);
        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TOTAL_SUPPLIES_ITEMS);

        // Update panel stats on client thread (to fetch item names and prices)
        clientThread.invokeLater(() -> {
            panel.updateStats();
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
}
