package com.camjewell;

import com.google.inject.Provides;
import javax.inject.Inject;
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
import net.runelite.client.util.ImageUtil;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.QuantityFormatter;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@PluginDescriptor(name = "Mokha Lost Loot", description = "Tracks loot lost from dying at the Doom of Mokhaihitl boss", tags = {
        "mokha", "loot", "boss", "death", "tracking" })
public class MokhaLostLootTrackerPlugin extends Plugin {
    private static final String CONFIG_KEY_TOTAL_LOST = "totalLostValue";
    private static final String CONFIG_KEY_TIMES_DIED = "timesDied";
    private static final String CONFIG_KEY_WAVE_PREFIX = "wave";
    private static final int MAX_TRACKED_WAVES = 9; // 1-8 individually, 9+ combined

    @Inject
    private Client client;

    @Inject
    private MokhaLostLootTrackerConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private ItemManager itemManager;

    @Inject
    private MokhaLostLootOverlay overlay;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private MokhaLostLootPanel panel;

    private NavigationButton navButton;

    private int currentDelveNumber = 0;
    private int previousDelveNumber = 0;
    private List<ItemStack> currentUnclaimedLoot = new ArrayList<>();
    private long currentLootValue = 0;
    private long previousWaveLootValue = 0;
    private long[] waveLootValues = new long[MAX_TRACKED_WAVES + 1]; // Index 0 unused, 1-9 for waves
    private boolean isDead = false;
    private boolean delveInterfaceVisible = false;
    private boolean inMokhaArena = false;

    @Override
    protected void startUp() throws Exception {
        log.info("Mokha Lost Loot Tracker started!");
        overlayManager.add(overlay);

        // Create a default icon first to ensure it's never null
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        try {
            BufferedImage loadedIcon = ImageUtil.loadImageResource(getClass(), "/mokha_icon.png");
            if (loadedIcon != null) {
                icon = loadedIcon;
                log.info("Mokha icon loaded successfully");
            }
        } catch (Exception e) {
            log.warn("Could not load mokha_icon.png, using default blank icon", e);
        }

        navButton = NavigationButton.builder()
                .tooltip("Mokha Lost Loot")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        log.info("Navigation button added to toolbar");
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("Mokha Lost Loot Tracker stopped!");
        overlayManager.remove(overlay);
        clientToolbar.removeNavigation(navButton);
        resetCurrentLoot();
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        // Check if player is in Mokha arena using widget detection
        // Widget [303:9] contains "Doom of Mokhaiotl" when in arena
        if (client.getLocalPlayer() != null) {
            Widget mokhaWidget = client.getWidget(303, 9);
            inMokhaArena = mokhaWidget != null && !mokhaWidget.isHidden()
                    && mokhaWidget.getText() != null
                    && mokhaWidget.getText().contains("Mokha");

            if (config.enableDebugMode() && inMokhaArena) {
                int[] regions = client.getMapRegions();
                if (regions != null && regions.length > 0) {
                    log.info("In Mokha arena! Regions: {}", java.util.Arrays.toString(regions));
                }
            }
        }

        // Check if player is dead
        if (client.getLocalPlayer() != null) {
            boolean currentlyDead = client.getLocalPlayer().getHealthRatio() == 0;

            if (currentlyDead && !isDead) {
                // Player just died
                isDead = true;
                log.debug("Player death detected");

                // If player died with unclaimed loot, record it immediately
                if (currentLootValue > 0 && currentDelveNumber > 0) {
                    log.info("Recording lost loot immediately upon death: {} gp", currentLootValue);
                    recordLostLoot();
                    resetCurrentLoot();
                }
            } else if (!currentlyDead && isDead) {
                // Player respawned
                isDead = false;
            }
        }

        // Debug: Log all visible widgets to help find Mokhaihitl interface
        if (config.enableDebugMode()) {
            logVisibleWidgets();
        }

        // Check for delve deeper widget and parse loot
        checkDelveWidget();
    }

    private void logVisibleWidgets() {
        // Log all widgets with specific IDs that might be the delve interface
        for (int groupId = 0; groupId < 1000; groupId++) {
            for (int childId = 0; childId < 100; childId++) {
                Widget widget = client.getWidget(groupId, childId);
                if (widget != null && !widget.isHidden()) {
                    String text = widget.getText();
                    int itemId = widget.getItemId();
                    int itemQty = widget.getItemQuantity();
                    Widget[] children = widget.getChildren();
                    int childCount = children != null ? children.length : 0;

                    // Log if it has text, items, or children
                    if ((text != null && !text.isEmpty()) || itemId > 0 || childCount > 0) {
                        log.info("Widget [{}:{}] Text='{}' ItemId={} ItemQty={} Children={}",
                                groupId, childId, text != null ? text : "", itemId, itemQty, childCount);

                        // SPECIAL: Log all children of Widget 919:2 to find "Level X Complete!"
                        if (groupId == 919 && childId == 2 && children != null) {
                            log.info("=== EXAMINING ALL CHILDREN OF WIDGET 919:2 ===");
                            for (int i = 0; i < children.length; i++) {
                                Widget child = children[i];
                                if (child != null) {
                                    String childText = child.getText();
                                    String childName = child.getName();
                                    log.info("  Child[{}] Text='{}' Name='{}' ItemId={} ItemQty={} Hidden={}",
                                            i, childText != null ? childText : "",
                                            childName != null ? childName : "",
                                            child.getItemId(), child.getItemQuantity(), child.isHidden());
                                }
                            }
                            log.info("=== END OF WIDGET 919:2 CHILDREN ===");
                        }

                        // If it has "Level" or "Delve" or "Claim" in text, log all children
                        if (text != null && (text.contains("Level") || text.contains("Delve") ||
                                text.contains("Claim") || text.contains("value:") || text.contains("GP"))) {
                            if (children != null) {
                                for (int i = 0; i < children.length; i++) {
                                    Widget child = children[i];
                                    if (child != null && !child.isHidden()) {
                                        log.info("  Child[{}] Text='{}' ItemId={} ItemQty={}",
                                                i, child.getText() != null ? child.getText() : "",
                                                child.getItemId(), child.getItemQuantity());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded widgetLoaded) {
        // You may need to adjust this widget ID based on the actual game widget
        // This will likely need to be determined by examining the game
        checkDelveWidget();
    }

    private void checkDelveWidget() {
        // Widget Group 919 is the Mokhaihitl delve interface
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
                            log.debug("Current loot value: {} gp", currentLootValue);
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
        } else {
            delveInterfaceVisible = false;
        }
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

                    if (itemId > 0 && quantity > 0) {
                        ItemStack item = new ItemStack(itemId, quantity);
                        currentUnclaimedLoot.add(item);
                        log.debug("Found loot item: {} x{}", itemId, quantity);
                    }
                }
            }
        }

        log.debug("Total loot items: {}", currentUnclaimedLoot.size());
    }

    private void handleDelveNumberChange(int newDelveNumber) {
        if (newDelveNumber != currentDelveNumber) {
            previousDelveNumber = currentDelveNumber;
            currentDelveNumber = newDelveNumber;

            log.debug("Delve number changed from {} to {}", previousDelveNumber, currentDelveNumber);

            // Calculate incremental loot for the wave we just completed
            if (previousDelveNumber > 0 && previousDelveNumber < currentDelveNumber) {
                long incrementalLoot = currentLootValue - previousWaveLootValue;
                int waveIndex = Math.min(previousDelveNumber, MAX_TRACKED_WAVES);
                waveLootValues[waveIndex] = incrementalLoot;
                previousWaveLootValue = currentLootValue;
                log.debug("Wave {} incremental loot: {} gp (total was {} gp)",
                        previousDelveNumber, incrementalLoot, currentLootValue);
            }

            // If delve number reset to 1, clear tracking (loot was either claimed or
            // already recorded on death)
            if (currentDelveNumber == 1 && previousDelveNumber > 1) {
                log.info("Delve reset detected ({}->1). Loot value: {}", previousDelveNumber, currentLootValue);

                if (currentLootValue > 0) {
                    log.info("Loot was claimed or already recorded");
                }
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
        String configGroup = "mokhalostloot." + accountHash;

        // Calculate incremental loot for the current wave if not already done
        if (currentDelveNumber > 0) {
            long incrementalLoot = currentLootValue - previousWaveLootValue;
            int waveIndex = Math.min(currentDelveNumber, MAX_TRACKED_WAVES);
            waveLootValues[waveIndex] = incrementalLoot;
        }

        // Save each wave's incremental loot value
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            if (waveLootValues[wave] > 0) {
                String waveKey = CONFIG_KEY_WAVE_PREFIX + wave;
                long existingWaveLost = getLongConfig(configGroup, waveKey);
                existingWaveLost += waveLootValues[wave];
                configManager.setConfiguration(configGroup, waveKey, existingWaveLost);
                log.debug("Recorded wave {} lost: {} gp", wave, waveLootValues[wave]);
            }
        }

        // Get current total lost value
        long totalLost = getLongConfig(configGroup, CONFIG_KEY_TOTAL_LOST);
        totalLost += currentLootValue;

        // Get current death count
        int timesDied = getIntConfig(configGroup, CONFIG_KEY_TIMES_DIED);
        timesDied++;

        // Save new values
        configManager.setConfiguration(configGroup, CONFIG_KEY_TOTAL_LOST, totalLost);
        configManager.setConfiguration(configGroup, CONFIG_KEY_TIMES_DIED, timesDied);

        log.info("Lost loot recorded: {} gp on wave {} (Total lost: {} gp, Deaths: {})",
                currentLootValue, currentDelveNumber, totalLost, timesDied);

        // Send chat notification
        if (config.showChatNotifications()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "<col=ff0000>Mokha loot lost!</col> " + QuantityFormatter.quantityToStackSize(currentLootValue)
                            + " gp (Wave " + currentDelveNumber + ")",
                    null);
        }

        // Update panel stats
        panel.updateStats();
    }

    private void resetCurrentLoot() {
        currentUnclaimedLoot.clear();
        currentLootValue = 0;
        previousWaveLootValue = 0;
        waveLootValues = new long[MAX_TRACKED_WAVES + 1];
        currentDelveNumber = 0;
        previousDelveNumber = 0;
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

    public long getTotalLostValue() {
        String accountHash = getAccountHash();
        String configGroup = "mokhalostloot." + accountHash;
        return getLongConfig(configGroup, CONFIG_KEY_TOTAL_LOST);
    }

    public int getTimesDied() {
        String accountHash = getAccountHash();
        String configGroup = "mokhalostloot." + accountHash;
        return getIntConfig(configGroup, CONFIG_KEY_TIMES_DIED);
    }

    public long getCurrentLootValue() {
        return currentLootValue;
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
        String configGroup = "mokhalostloot." + accountHash;
        String waveKey = CONFIG_KEY_WAVE_PREFIX + wave;
        return getLongConfig(configGroup, waveKey);
    }

    public void resetStats() {
        String accountHash = getAccountHash();
        String configGroup = "mokhalostloot." + accountHash;

        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TOTAL_LOST);
        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TIMES_DIED);

        // Clear per-wave stats
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_PREFIX + wave);
        }

        log.info("Mokha lost loot stats reset");

        if (config.showChatNotifications()) {
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                    "Mokha lost loot stats have been reset.", null);
        }

        // Update panel stats
        panel.updateStats();
    }

    @Provides
    MokhaLostLootTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MokhaLostLootTrackerConfig.class);
    }

    // Helper class for storing item information
    private static class ItemStack {
        private final int itemId;
        private final int quantity;

        public ItemStack(int itemId, int quantity) {
            this.itemId = itemId;
            this.quantity = quantity;
        }

        public int getItemId() {
            return itemId;
        }

        public int getQuantity() {
            return quantity;
        }
    }
}
