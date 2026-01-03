package com.camjewell;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Provides;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
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

    private LootValueService lootValueService;
    private SuppliesTracker suppliesTracker;
    private ConfigPersistence configPersistence;
    private LootTrackingState trackingState;
    private WidgetParser widgetParser;

    private NavigationButton navButton;

    private boolean isDead = false;
    private boolean delveInterfaceVisible = false;
    private boolean wasDelveInterfaceVisible = false;
    private boolean inMokhaArena = false;
    private boolean wasInMokhaArena = false;

    // Flag to trigger panel refresh after login when account hash becomes available
    private boolean needsPanelRefresh = false;

    // Tracking state for current loot values and items
    private List<LootItem> currentUnclaimedLoot = new ArrayList<>();
    private List<LootItem> previousWaveItems = new ArrayList<>();
    private long currentLootValue = 0;
    private long previousWaveLootValue = 0;
    private long[] waveLootValues = new long[MAX_TRACKED_WAVES + 1];
    private List<LootItem>[] waveItemStacks = new List[MAX_TRACKED_WAVES + 1];
    private int currentDelveNumber = 0;
    private int previousDelveNumber = 0;
    private long lastVisibleLootValue = 0;
    private List<LootItem> lastVisibleLootItems = new ArrayList<>();
    private int lastVisibleDelveNumber = 0;
    private long lastPanelUpdateValue = -1;

    @Override
    protected void startUp() throws Exception {
        lootValueService = new LootValueService(itemManager, config);
        suppliesTracker = new SuppliesTracker(client, itemManager, config, lootValueService);
        configPersistence = new ConfigPersistence(client, configManager, itemManager);
        trackingState = new LootTrackingState();
        widgetParser = new WidgetParser(itemManager);
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
        // first sip/consumption is recorded (avoids missing the first change-driven
        // event)
        if (inMokhaArena && !wasInMokhaArena && suppliesTracker != null && !suppliesTracker.hasInitialInventory()) {
            ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);
            suppliesTracker.captureInitialSuppliesSnapshot(client.getItemContainer(InventoryID.INVENTORY),
                    equipmentContainer);
        }

        // Check if player is dead
        if (client.getLocalPlayer() != null) {
            boolean currentlyDead = client.getLocalPlayer().getHealthRatio() == 0;

            if (currentlyDead && !isDead) {
                // Player just died
                isDead = true;

                // Only track deaths in Mokha arena
                if (inMokhaArena) {
                    boolean hasLoot = trackingState.getCurrentLootValue() > 0
                            || trackingState.getLastVisibleLootValue() > 0
                            || (trackingState.getCurrentUnclaimedLoot() != null
                                    && !trackingState.getCurrentUnclaimedLoot().isEmpty());
                    int effectiveDelve = trackingState.getCurrentDelveNumber() > 0
                            ? trackingState.getCurrentDelveNumber()
                            : trackingState.getLastVisibleDelveNumber();

                    if (hasLoot && effectiveDelve > 0) {
                        // If we lost visibility before death (e.g., interface closed), fall back to the
                        // last captured state so we still record the loss.
                        if (trackingState.getCurrentLootValue() == 0) {
                            if (trackingState.getLastVisibleLootValue() > 0) {
                                trackingState.setCurrentLootValue(trackingState.getLastVisibleLootValue());
                                if (trackingState.getCurrentUnclaimedLoot().isEmpty()
                                        && trackingState.getLastVisibleLootItems() != null) {
                                    trackingState.setCurrentUnclaimedLoot(trackingState.getLastVisibleLootItems());
                                }
                                if (trackingState.getCurrentDelveNumber() == 0) {
                                    trackingState.setCurrentDelveNumber(trackingState.getLastVisibleDelveNumber());
                                }
                            } else if (!trackingState.getCurrentUnclaimedLoot().isEmpty()) {
                                trackingState.setCurrentLootValue(
                                        getTotalLootValue(trackingState.getCurrentUnclaimedLoot()));
                            }
                        }

                        recordLostLoot();
                    } else if (effectiveDelve > 0) {
                        // No loot but died in a wave: still save supplies used
                        recordSuppliesUsed();
                        // Count death even without loot
                        incrementDeathCounter();
                    } else {
                        // Count death even without loot or wave info
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
        checkDelveWidget();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged configChanged) {
        if (configChanged.getGroup().equals("mokhaloot")) {
            if (configChanged.getKey().equals("excludeSunKissedBonesValue") ||
                    configChanged.getKey().equals("excludeSpiritSeedValue") ||
                    configChanged.getKey().equals("minItemValueThreshold") ||
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
            if (trackingState.getLastVisibleLootValue() > 0 && trackingState.getLastVisibleDelveNumber() > 0) {
                // Ensure current wave data is populated before claiming
                int waveIndex = Math.min(trackingState.getLastVisibleDelveNumber(), MAX_TRACKED_WAVES);
                if (trackingState.getWaveItems(waveIndex).isEmpty()) {
                    long incrementalLoot = trackingState.getLastVisibleLootValue()
                            - trackingState.getPreviousWaveLootValue();
                    trackingState.updateWaveTracking(waveIndex, incrementalLoot,
                            trackingState.getLastVisibleLootItems());
                }

                recordClaimedLoot();

                // Clear the captured state
                trackingState.setLastVisibleLootValue(0);
                trackingState.setLastVisibleLootItems(new ArrayList<>());
                trackingState.setLastVisibleDelveNumber(0);
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Ignore supplies tracking while dead to avoid counting the gravestone emptying
        if (isDead) {
            return;
        }

        // Only track inventory changes in Mokha arena. If we haven't marked entry yet,
        // do a lightweight widget check to avoid missing the first sip before the
        // tick-based arena detection flips the flag.
        if (!inMokhaArena) {
            if (suppliesTracker != null && !suppliesTracker.hasInitialInventory()) {
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

        if (event.getContainerId() == InventoryID.INVENTORY.getId() && suppliesTracker != null) {
            ItemContainer container = event.getItemContainer();
            if (container != null) {
                ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);
                suppliesTracker.handleItemContainerChange(container, equipmentContainer);
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
                    configPersistence.addDeathCost(cost);

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
            int detectedDelveNumber = widgetParser.parseDelveNumber(mainWidget);

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
                List<LootItem> parsedItems = widgetParser.parseLootItems(lootContainerWidget);
                currentUnclaimedLoot.clear();
                currentUnclaimedLoot.addAll(parsedItems);

                // Recalculate loot value with custom pricing for Mokhaiotl Cloth
                currentLootValue = 0;
                for (LootItem item : currentUnclaimedLoot) {
                    currentLootValue += getItemValue(item.getId(), item.getQuantity());
                }

                // Apply exclusion adjustments at tracking time (so config changes mid-wave
                // don't affect already-tracked loot)
                currentLootValue = lootValueService.getAdjustedLootValue(currentLootValue, currentUnclaimedLoot);
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
                        List<LootItem> incrementalItems = trackingState.calculateIncrementalItems(
                                currentUnclaimedLoot, previousWaveItems);
                        trackingState.updateWaveTracking(waveIndex, incrementalValue, incrementalItems);

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
            }
            delveInterfaceVisible = false;
        }

        // Update previous state
        wasDelveInterfaceVisible = delveInterfaceVisible;
    }

    private void handleDelveNumberChange(int newDelveNumber) {
        if (newDelveNumber != currentDelveNumber) {
            previousDelveNumber = currentDelveNumber;
            currentDelveNumber = newDelveNumber;

            // If delve number reset to 1, clear tracking (loot was either claimed or
            // already recorded on death)
            if (currentDelveNumber == 1 && previousDelveNumber > 1) {
                // Clear current tracking
                trackingState.reset();
                if (suppliesTracker != null) {
                    suppliesTracker.reset();
                }
                resetCurrentLoot();
            }
        }
    }

    private void recordLostLoot() {
        if (currentLootValue == 0) {
            return;
        }

        // Note: Wave data should already be stored from checkDelveWidget()
        // Only recalculate if the current wave wasn't stored yet (edge case)
        if (currentDelveNumber > 0 && currentDelveNumber <= MAX_TRACKED_WAVES) {
            int waveIndex = currentDelveNumber;
            if (trackingState.getWaveValue(waveIndex) == 0 && currentLootValue > 0) {
                // Edge case: player died before checkDelveWidget could store the wave data
                long incrementalLoot = currentLootValue - previousWaveLootValue;
                // Calculate incremental items for this wave
                List<LootItem> incrementalItems = trackingState.calculateIncrementalItems(
                        currentUnclaimedLoot, previousWaveItems);
                trackingState.updateWaveTracking(waveIndex, incrementalLoot, incrementalItems);
            }
        }

        // Save each wave's incremental loot value and items
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            long waveValue = trackingState.getWaveValue(wave);
            List<LootItem> waveItems = trackingState.getWaveItems(wave);
            boolean hasItems = waveItems != null && !waveItems.isEmpty();
            if (waveValue > 0 || hasItems) {
                // Adjustment already applied at tracking time, use stored value directly
                configPersistence.saveWaveLost(wave, waveValue, waveItems);
            }
        }

        // Adjustment already applied at tracking time, use stored value directly
        long adjustedTotalValue = currentLootValue;
        configPersistence.incrementTotalLost(adjustedTotalValue);
        configPersistence.incrementTimesDied();

        // Save supplies used for this run
        long suppliesCost = getSuppliesUsedValue();
        if (suppliesCost > 0) {
            List<LootItem> suppliesUsedItems = getSuppliesUsedItems();
            if (!suppliesUsedItems.isEmpty()) {
                String existingSuppliesItems = configPersistence.getStringConfig(
                        "totalSuppliesItems");
                String serializedSupplies = suppliesTracker.mergeSuppliesTotals(existingSuppliesItems,
                        suppliesUsedItems);
                configPersistence.addSuppliesCost(suppliesCost, serializedSupplies);
            } else {
                configPersistence.addSuppliesCost(suppliesCost, "");
            }
            if (config.debugItemValueLogging()) {
                log.info("[Supplies Debug] Saved supplies: value={} gp, items={}", suppliesCost,
                        configPersistence.serializeItems(getSuppliesUsedItems()));
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

        // Save all waves that have loot data
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            long waveValue = trackingState.getWaveValue(wave);
            List<LootItem> waveItems = trackingState.getWaveItems(wave);
            boolean hasItems = waveItems != null && !waveItems.isEmpty();
            if (waveValue <= 0 && !hasItems) {
                continue; // Skip waves with no loot or items
            }

            // Adjustment already applied at tracking time, use stored value directly
            configPersistence.saveWaveClaimed(wave, waveValue, waveItems);
        }

        // Save supplies used for this run
        long suppliesCost = getSuppliesUsedValue();
        if (suppliesCost > 0) {
            List<LootItem> suppliesUsedItems = getSuppliesUsedItems();
            if (!suppliesUsedItems.isEmpty()) {
                String existingSuppliesItems = configPersistence.getStringConfig(
                        "totalSuppliesItems");
                String serializedSupplies = suppliesTracker.mergeSuppliesTotals(existingSuppliesItems,
                        suppliesUsedItems);
                configPersistence.addSuppliesCost(suppliesCost, serializedSupplies);
            } else {
                configPersistence.addSuppliesCost(suppliesCost, "");
            }
            if (config.debugItemValueLogging()) {
                log.info("[Supplies Debug] Saved supplies: value={} gp, items={}", suppliesCost,
                        configPersistence.serializeItems(getSuppliesUsedItems()));
            }
        }

        // Send chat notification
        if (config.showChatNotifications()) {
            // Adjustment already applied at tracking time, use stored value directly
            long adjustedTotalValue = lastVisibleLootValue;
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=00ff00>Mokha loot claimed!</col> " + QuantityFormatter.quantityToStackSize(adjustedTotalValue)
                            + " gp (Wave " + lastVisibleDelveNumber + ")",
                    null);
        }

        // Update panel stats on client thread (ItemManager requires it)
        clientThread.invokeLater(() -> panel.updateStats());

        // Reset tracking after claiming
        trackingState.reset();
        resetCurrentLoot();
    }

    private void incrementDeathCounter() {
        configPersistence.incrementTimesDied();

        // Update panel stats on client thread (ItemManager requires it)
        clientThread.invokeLater(() -> panel.updateStats());
    }

    private void recordSuppliesUsed() {
        // Save supplies used for this run without loot tracking
        long suppliesCost = getSuppliesUsedValue();
        if (suppliesCost > 0) {
            List<LootItem> suppliesUsedItems = getSuppliesUsedItems();
            if (!suppliesUsedItems.isEmpty()) {
                String existingSuppliesItems = configPersistence.getStringConfig(
                        "totalSuppliesItems");
                String serializedSupplies = suppliesTracker.mergeSuppliesTotals(existingSuppliesItems,
                        suppliesUsedItems);
                configPersistence.addSuppliesCost(suppliesCost, serializedSupplies);
            } else {
                configPersistence.addSuppliesCost(suppliesCost, "");
            }
            if (config.debugItemValueLogging()) {
                log.info("[Supplies Debug] Saved supplies on death: value={} gp, items={}", suppliesCost,
                        configPersistence.serializeItems(getSuppliesUsedItems()));
            }
        }
    }

    private void resetCurrentLoot() {
        currentUnclaimedLoot.clear();
        previousWaveItems.clear();
        currentLootValue = 0;
        previousWaveLootValue = 0;
        currentDelveNumber = 0;
        previousDelveNumber = 0;
        lastVisibleLootValue = 0;
        lastVisibleLootItems = new ArrayList<>();
        lastVisibleDelveNumber = 0;
        lastPanelUpdateValue = -1; // Reset panel update tracking
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
        return configPersistence.getTimesDied();
    }

    public long getTotalDeathCosts() {
        return configPersistence.getTotalDeathCosts();
    }

    public long getTotalSuppliesCost() {
        return configPersistence.getTotalSuppliesCost();
    }

    public List<LootItem> getTotalSuppliesItems() {
        return configPersistence.getTotalSuppliesItems();
    }

    public long getTotalClaimedValue() {
        long total = 0;
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            total += getWaveClaimedValue(wave);
        }
        return total;
    }

    public long getCurrentLootValue() {
        // Adjustment already applied at tracking time, return stored value directly
        return currentLootValue;
    }

    public List<LootItem> getCurrentLootItems() {
        List<LootItem> items = new ArrayList<>(currentUnclaimedLoot);

        // If test mode is enabled, add test items
        // Removed testMode and test item injection

        return items;
    }

    public long getSuppliesUsedValue() {
        return suppliesTracker != null ? suppliesTracker.getSuppliesUsedValue() : 0;
    }

    public List<LootItem> getSuppliesUsedItems() {
        if (suppliesTracker == null) {
            return new ArrayList<>();
        }
        return suppliesTracker.getSuppliesUsedItems();
    }

    public long getLiveSuppliesUsedValue() {
        return suppliesTracker != null ? suppliesTracker.getLiveSuppliesUsedValue() : 0;
    }

    public List<LootItem> getLiveSuppliesUsedItems() {
        if (suppliesTracker == null) {
            return new ArrayList<>();
        }
        return suppliesTracker.getLiveSuppliesUsedItems();
    }

    public boolean isHighValueItem(int itemId) {
        return lootValueService != null && lootValueService.isHighValueItem(itemId);
    }

    long getItemValue(int itemId, int quantity) {
        return lootValueService != null ? lootValueService.getItemValue(itemId, quantity) : 0;
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
        return configPersistence.getWaveLostItems(wave);
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
        return configPersistence.getWaveClaimedItems(wave);
    }

    public void resetStats() {
        configPersistence.resetAllStats();

        // Update panel stats on client thread (to fetch item names and prices)
        clientThread.invokeLater(() -> {
            panel.updateStats();
        });
    }

    private void clearDefaultConfig() {
        // Clear any leftover config from dummy data that was saved under "default"
        // account
        configPersistence.clearDefaultConfig();
    }

    @Provides
    MokhaLootTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MokhaLootTrackerConfig.class);
    }
}
