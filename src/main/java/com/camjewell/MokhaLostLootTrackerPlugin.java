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

// Simple class to hold item data for serialization
class LootItem {
    private final int id;
    private final int quantity;

    public LootItem(int id, int quantity) {
        this.id = id;
        this.quantity = quantity;
    }

    public int getId() {
        return id;
    }

    public int getQuantity() {
        return quantity;
    }
}

@Slf4j
@PluginDescriptor(name = "Mokha Lost Loot", description = "Tracks loot lost from dying at the Doom of Mokhaiotl boss", tags = {
        "mokha", "loot", "boss", "death", "tracking" })
public class MokhaLostLootTrackerPlugin extends Plugin {
    private static final String CONFIG_KEY_TOTAL_LOST = "totalLostValue";
    private static final String CONFIG_KEY_TIMES_DIED = "timesDied";
    private static final String CONFIG_KEY_DEATH_COSTS = "totalDeathCosts";
    private static final String CONFIG_KEY_WAVE_PREFIX = "wave";
    private static final String CONFIG_KEY_WAVE_ITEMS_PREFIX = "waveItems";
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
    private List<LootItem> currentUnclaimedLoot = new ArrayList<>();
    private long currentLootValue = 0;
    private long previousWaveLootValue = 0;
    private long[] waveLootValues = new long[MAX_TRACKED_WAVES + 1]; // Index 0 unused, 1-9 for waves
    @SuppressWarnings("unchecked")
    private List<LootItem>[] waveItemStacks = new List[MAX_TRACKED_WAVES + 1]; // Items per wave
    private boolean isDead = false;
    private boolean delveInterfaceVisible = false;
    private boolean inMokhaArena = false;

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(overlay);

        // Create a default icon first to ensure it's never null
        BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        try {
            BufferedImage loadedIcon = ImageUtil.loadImageResource(getClass(), "/mokha_icon.png");
            if (loadedIcon != null) {
                icon = loadedIcon;
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
    }

    @Override
    protected void shutDown() throws Exception {
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
        }

        // Check if player is dead
        if (client.getLocalPlayer() != null) {
            boolean currentlyDead = client.getLocalPlayer().getHealthRatio() == 0;

            if (currentlyDead && !isDead) {
                // Player just died
                isDead = true;

                // Only track deaths in Mokha arena
                if (inMokhaArena) {
                    if (currentLootValue > 0 && currentDelveNumber > 0) {
                        recordLostLoot();
                        resetCurrentLoot();
                    } else {
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
                    String configGroup = "mokhalostloot." + accountHash;
                    long totalCosts = getLongConfig(configGroup, CONFIG_KEY_DEATH_COSTS);
                    totalCosts += cost;
                    configManager.setConfiguration(configGroup, CONFIG_KEY_DEATH_COSTS, totalCosts);

                    // Update panel
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

                    // Only add valid items (positive ID and quantity)
                    if (itemId > 0 && quantity > 0) {
                        // Verify item is valid by checking if ItemManager can resolve it
                        String itemName = itemManager.getItemComposition(itemId).getName();
                        if (itemName != null && !itemName.equalsIgnoreCase("null")) {
                            LootItem item = new LootItem(itemId, quantity);
                            currentUnclaimedLoot.add(item);
                            log.debug("Found loot item: {} ({}) x{}", itemName, itemId, quantity);
                        } else {
                            log.debug("Skipping invalid item ID {} with quantity {}", itemId, quantity);
                        }
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
                // Copy current loot items for this wave
                waveItemStacks[waveIndex] = new ArrayList<>(currentUnclaimedLoot);
                previousWaveLootValue = currentLootValue;
                log.debug("Wave {} incremental loot: {} gp with {} items (total was {} gp)",
                        previousDelveNumber, incrementalLoot, currentUnclaimedLoot.size(), currentLootValue);
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
            // Copy current loot items for this wave
            waveItemStacks[waveIndex] = new ArrayList<>(currentUnclaimedLoot);
        }

        // Save each wave's incremental loot value and items
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            if (waveLootValues[wave] > 0) {
                String waveKey = CONFIG_KEY_WAVE_PREFIX + wave;
                long existingWaveLost = getLongConfig(configGroup, waveKey);
                existingWaveLost += waveLootValues[wave];
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
                        allItems.add(new LootItem(entry.getKey(), entry.getValue()));
                    }

                    String serializedItems = serializeItems(allItems);
                    configManager.setConfiguration(configGroup, itemsKey, serializedItems);
                }
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

    private void incrementDeathCounter() {
        String accountHash = getAccountHash();
        String configGroup = "mokhalostloot." + accountHash;

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
            result.add(new LootItem(entry.getKey(), entry.getValue()));
        }
        return result;
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

    public long getTotalDeathCosts() {
        String accountHash = getAccountHash();
        String configGroup = "mokhalostloot." + accountHash;
        return getLongConfig(configGroup, CONFIG_KEY_DEATH_COSTS);
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

    public List<LootItem> getWaveLostItems(int wave) {
        String accountHash = getAccountHash();
        String configGroup = "mokhalostloot." + accountHash;
        String itemsKey = CONFIG_KEY_WAVE_ITEMS_PREFIX + wave;
        String serialized = configManager.getConfiguration(configGroup, itemsKey);
        return deserializeAndMergeItems(serialized);
    }

    public void resetStats() {
        String accountHash = getAccountHash();
        String configGroup = "mokhalostloot." + accountHash;

        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TOTAL_LOST);
        configManager.unsetConfiguration(configGroup, CONFIG_KEY_TIMES_DIED);

        // Clear per-wave stats
        for (int wave = 1; wave <= MAX_TRACKED_WAVES; wave++) {
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_PREFIX + wave);
            configManager.unsetConfiguration(configGroup, CONFIG_KEY_WAVE_ITEMS_PREFIX + wave);
        }

        // Reset death costs
        configManager.unsetConfiguration(configGroup, CONFIG_KEY_DEATH_COSTS);

        // Update panel stats on UI thread with forced repaint
        javax.swing.SwingUtilities.invokeLater(() -> {
            panel.updateStats();
            panel.revalidate();
            panel.repaint();
        });
    }

    @Provides
    MokhaLostLootTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MokhaLostLootTrackerConfig.class);
    }

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
