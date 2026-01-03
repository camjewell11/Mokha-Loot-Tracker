package com.camjewell;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Provides;

import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
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

    // Arena state tracking
    private boolean inMokhaArena = false;
    private boolean isDead = false;

    // Combined item tracking (inventory + equipment)
    private final Map<Integer, Integer> lastCombinedSnapshot = new HashMap<>();

    @Provides
    MokhaLootTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MokhaLootTrackerConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        panel = new MokhaLootPanel(config);

        final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/48icon.png");

        navButton = NavigationButton.builder()
                .tooltip("Mokha Loot Tracker")
                .icon(icon)
                .priority(5)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        lastCombinedSnapshot.clear();
    }

    @Override
    protected void shutDown() throws Exception {
        clientToolbar.removeNavigation(navButton);
        lastCombinedSnapshot.clear();
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Check if we're in the Mokha arena using widget detection
        // Widget [303:9] contains "Doom of Mokhaiotl" when in arena
        boolean wasInArena = inMokhaArena;
        inMokhaArena = false;

        if (client.getLocalPlayer() != null) {
            Widget mokhaWidget = client.getWidget(303, 9);
            if (mokhaWidget != null && !mokhaWidget.isHidden()
                    && mokhaWidget.getText() != null
                    && mokhaWidget.getText().contains("Mokha")) {
                inMokhaArena = true;
            }
        }

        // Log when entering/exiting arena (debug only)
        if (config.debugLogging() && wasInArena != inMokhaArena) {
            if (inMokhaArena) {
                log.info("[Mokha Debug] Entered Mokha arena");
                // Take initial snapshot when entering arena
                lastCombinedSnapshot.clear();
                lastCombinedSnapshot.putAll(buildCombinedSnapshot());
            } else {
                log.info("[Mokha Debug] Exited Mokha arena");
            }
        }

        // Check for player death
        if (client.getLocalPlayer() != null) {
            boolean currentlyDead = client.getLocalPlayer().getHealthRatio() == 0;

            if (currentlyDead && !isDead && inMokhaArena) {
                isDead = true;
                // Clear snapshot immediately when death is detected
                lastCombinedSnapshot.clear();
                if (config.debugLogging()) {
                    log.info("[Mokha Debug] Player died in arena");
                }
            } else if (!currentlyDead && isDead && inMokhaArena) {
                isDead = false;
                // Clear snapshot on respawn to start fresh only if still in arena
                lastCombinedSnapshot.clear();
                if (config.debugLogging()) {
                    log.info("[Mokha Debug] Player respawned");
                }
            }
        }

        // Clear snapshot when leaving arena
        if (!inMokhaArena && wasInArena) {
            lastCombinedSnapshot.clear();
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

        return combined;
    }

    private void checkForConsumption(Map<Integer, Integer> currentCombined) {
        // Only log if we have a previous snapshot
        if (!lastCombinedSnapshot.isEmpty()) {
            StringBuilder consumed = new StringBuilder();

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
        }

        // Update combined snapshot
        lastCombinedSnapshot.clear();
        lastCombinedSnapshot.putAll(currentCombined);
    }
}
