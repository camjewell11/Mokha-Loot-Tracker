package com.camjewell;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.inject.Provides;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@PluginDescriptor(name = "Mokha Loot Tracker", description = "Tracks loot obtained from Mokhaiotl encounters", enabledByDefault = true, tags = {
        "mokha", "loot", "tracker", "mokhaiotl" })
public class MokhaLootTrackerPlugin extends Plugin {

    private static final Logger log = LoggerFactory.getLogger(MokhaLootTrackerPlugin.class);
    private static final String LOOT_VALUE_COLOR_HEX = "66CCFF";
    private static final String LOOT_VALUE_HA_COLOR_HEX = "00CC66";
    private static final String MOKHA_CLOTH_NAME = "Mokhaiotl cloth";
    private static final String EYE_OF_AYAK_NAME = "Eye of ayak";
    private static final String AVERNIC_TREADS_NAME = "Avernic treads";
    private static final String DOM_NAME = "Dom";
    private static final String SUN_KISSED_BONES_NAME = "Sun-kissed bones";
    private static final int SUN_KISSED_BONES_HA_VALUE = 96;
    private static final String DEFAULT_PLAYER_PROFILE_KEY = "default";
    private static final int ARENA_EXIT_GRACE_TICKS = 5;
    private static final double UNIQUE_CHANCE_DELVE_2 = 1.0 / 2500.0;
    private static final double UNIQUE_CHANCE_DELVE_3 = 1.0 / 1000.0;
    private static final double UNIQUE_CHANCE_DELVE_4 = 1.0 / 450.0;
    private static final double UNIQUE_CHANCE_DELVE_5 = 1.0 / 270.0;
    private static final double UNIQUE_CHANCE_DELVE_6 = 1.0 / 255.0;
    private static final double UNIQUE_CHANCE_DELVE_7 = 1.0 / 240.0;
    private static final double UNIQUE_CHANCE_DELVE_8 = 1.0 / 210.0;
    private static final double UNIQUE_CHANCE_DELVE_9_PLUS = 1.0 / 180.0;
    private static final double UNIQUE_CHANCE_STANDARD_DELVE_3 = 1.0 / 2000.0;
    private static final double UNIQUE_CHANCE_STANDARD_DELVE_4 = 1.0 / 1350.0;
    private static final double UNIQUE_CHANCE_STANDARD_DELVE_5 = 1.0 / 810.0;
    private static final double UNIQUE_CHANCE_STANDARD_DELVE_6 = 1.0 / 765.0;
    private static final double UNIQUE_CHANCE_STANDARD_DELVE_7 = 1.0 / 720.0;
    private static final double UNIQUE_CHANCE_STANDARD_DELVE_8 = 1.0 / 630.0;
    private static final double UNIQUE_CHANCE_STANDARD_DELVE_9_PLUS = 1.0 / 540.0;
    private static final double UNIQUE_CHANCE_DOM_DELVE_6 = 1.0 / 1000.0;
    private static final double UNIQUE_CHANCE_DOM_DELVE_7 = 1.0 / 750.0;
    private static final double UNIQUE_CHANCE_DOM_DELVE_8 = 1.0 / 500.0;
    private static final double UNIQUE_CHANCE_DOM_DELVE_9_PLUS = 1.0 / 250.0;
    private static final int COLLECTION_LOG_BOSS_SELECTED_WIDGET_ID = InterfaceID.Collection.BOSS_TEXT;
    private static final int COLLECTION_LOG_ITEMS_CONTAINER_WIDGET_ID = InterfaceID.Collection.ITEMS_CONTENTS;
    private static final int DOM_LOOT_VALUE_WIDGET_ID = InterfaceID.DomEndLevelUi.LOOT_VALUE;
    private static final int DOM_LOOT_CONTENTS_WIDGET_ID = InterfaceID.DomEndLevelUi.LOOT_CONTENTS;
    private static final int COLLECTION_LOG_EXPECTED_UNIQUE_SLOTS = 4;
    private static final Pattern WAVE_KEYWORD_LINE_PATTERN = Pattern
            .compile("(?i)\\bwave\\s*(\\d+\\+?)\\b\\s*[:\\-]?\\s*([\\d,]+)");
    private static final Pattern WAVE_COMPACT_LINE_PATTERN = Pattern
            .compile("^(\\d+\\+?)\\s*[:\\-]?\\s*([\\d,]+)$");
    private static final Pattern LEVEL_COMPLETION_LINE_PATTERN = Pattern
            .compile("(?i)\\blevel\\s*(\\d+\\+?)\\b\\s+([\\d,]+)(?:\\s+[a-z])?");
    private static final Pattern LEVEL_ONLY_LINE_PATTERN = Pattern
            .compile("(?i)^level\\s*(\\d+\\+?)$");
    private static final Pattern COUNT_ONLY_LINE_PATTERN = Pattern
            .compile("^([\\d,]+)(?:\\s+[a-z])?$");

    /**
     * Region IDs observed during Doom runs from location recorder logs.
     * Tracking should only occur in these regions (or at the entrance) to avoid
     * bank/non-Doom activity being counted as supplies.
     */
    private static final Set<Integer> DOOM_ARENA_REGION_IDS = new HashSet<>();

    static {
        DOOM_ARENA_REGION_IDS.add(5269); // Entrance / pre-jump area
        DOOM_ARENA_REGION_IDS.add(50436); // Arena wave region (waves 1-5, recorded)
        DOOM_ARENA_REGION_IDS.add(50439); // Arena wave region (waves 2-5, recorded)
        DOOM_ARENA_REGION_IDS.add(42954); // Arena wave region (waves 6+, recorded)
    }

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

    @Inject
    private Notifier notifier;

    private NavigationButton navButton;
    private MokhaLootPanel panel;
    private HistoricalDataManager historicalDataManager;
    private SupplyTrackingService supplyTrackingService;
    private LootTrackingService lootTrackingService;
    private ArenaStateService arenaStateService;
    private HistoricalRunService historicalRunService;
    private ValueCalculationService valueCalculationService;
    private PanelDataService panelDataService;
    private String activeHistoricalPlayerKey = DEFAULT_PLAYER_PROFILE_KEY;

    @Inject
    private Gson gson;

    // Arena state tracking
    private boolean inMokhaArena = false;
    private boolean isDead = false;
    private int currentWaveNumber = 0;
    private static final int DOOM_BOSS_NPC_ID = NpcID.DOM_BOSS;
    private boolean bossSeenThisRun = false;
    private boolean bossDefeatedThisWave = false;
    private boolean bossWasEverPresentThisWave = false; // Track if boss appeared this wave (for teleport detection)
    private boolean lastDescendClickJustHappened = false; // Track if Descend was just clicked
    private long lastArenaExitTime = 0; // Track when player last exited arena to detect stale snapshot usage
    private int ticksOutsideArenaBounds = 0; // Failsafe: detect stale in-arena state

    // Performance tracking (current + previous run snapshot)
    private final PerformanceTracker performanceTracker = new PerformanceTracker();
    private PerformanceSnapshot previousRunPerformance = PerformanceSnapshot.empty();

    private static final int ENTRANCE_CENTER_X = 1311;
    private static final int ENTRANCE_CENTER_Y = 9555;
    private static final int ENTRANCE_RADIUS = 25;

    // Combined item tracking (inventory + equipment)
    private final Map<Integer, Integer> lastCombinedSnapshot = new HashMap<>();

    // Loot tracking by wave
    private final Map<Integer, List<LootItem>> lootByWave = new HashMap<>();
    private final Map<Integer, List<LootItem>> previousRunLootByWave = new HashMap<>();
    private boolean hasPreviousRunSnapshot;
    private boolean previousRunClaimed;
    private final Map<Integer, Integer> previousLootSnapshot = new HashMap<>();
    private final Map<Integer, Integer> previousRunSuppliesConsumed = new HashMap<>();

    // Supply consumption tracking
    private final Map<Integer, Integer> initialSupplySnapshot = new HashMap<>();
    private final Map<Integer, Integer> totalSuppliesConsumed = new HashMap<>();
    private final Map<Integer, Integer> lastWeaponAmmoSnapshot = new HashMap<>();

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
    private final Map<Integer, Long> historicalCompletedRunsByWave = new HashMap<>(); // Wave -> completed count
    private final Map<Integer, Long> localCompletedRunsSinceLastSyncByWave = new HashMap<>();
    private final Map<String, Long> collectionLogClaimedUniqueCounts = new HashMap<>();
    private boolean highscoresBaselineSynced;

    // Mokhaiotl Cloth handling
    private boolean hasWarnedAboutZeroClothValue = false; // Track if we've already warned player

    /**
     * Represents a single loot item with name, quantity, and value
     */
    static class LootItem {
        String name;
        int quantity;
        int value;
        int haValue;

        LootItem(String name, int quantity, int value, int haValue) {
            this.name = name;
            this.quantity = quantity;
            this.value = value;
            this.haValue = haValue;
        }
    }

    /**
     * Aggregates items across multiple runs (for historical tracking)
     */
    public static class ItemAggregate {
        String name;
        int totalQuantity;
        int pricePerItem;
        int haPricePerItem;
        long totalValue;
        long totalHaValue;

        ItemAggregate(String name, int quantity, int pricePerItem) {
            this(name, quantity, pricePerItem, 0);
        }

        ItemAggregate(String name, int quantity, int pricePerItem, int haPricePerItem) {
            this.name = name;
            this.totalQuantity = quantity;
            this.pricePerItem = pricePerItem;
            this.haPricePerItem = haPricePerItem;
            this.totalValue = (long) pricePerItem * quantity;
            this.totalHaValue = (long) haPricePerItem * quantity;
        }

        void add(int quantity, int pricePerItem) {
            add(quantity, pricePerItem, this.haPricePerItem);
        }

        void add(int quantity, int pricePerItem, int haPricePerItem) {
            this.totalQuantity += quantity;
            this.totalValue += (long) pricePerItem * quantity;
            this.totalHaValue += (long) haPricePerItem * quantity;
            // Update price per item to latest (could also average, but latest is simpler)
            this.pricePerItem = pricePerItem;
            this.haPricePerItem = haPricePerItem;
        }
    }

    private static class ExpectedDropsByItem {
        double cloth;
        double eye;
        double treads;
        double dom;

        double total() {
            return cloth + eye + treads + dom;
        }
    }

    @Provides
    MokhaLootTrackerConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(MokhaLootTrackerConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        panel = new MokhaLootPanel(config, this::clearAllData, this::recalculateAllTotals,
                () -> inMokhaArena,
                this::clearClaimedHistoricalData,
                this::clearUnclaimedHistoricalData,
                this::clearSuppliesHistoricalData,
                (wave, itemName) -> this.removeHistoricalClaimedWaveItem(wave == null ? 0 : wave, itemName),
                (wave, itemName) -> this.removeHistoricalUnclaimedWaveItem(wave == null ? 0 : wave, itemName),
                this::removeHistoricalClaimedItemAllWaves,
                this::removeHistoricalUnclaimedItemAllWaves,
                this::removeHistoricalSupplyItem,
                this::exportHistoricalData,
                this::importHistoricalData);

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
        supplyTrackingService = new SupplyTrackingService(client, itemManager, configManager, gson, log,
                lastCombinedSnapshot, lastWeaponAmmoSnapshot, totalSuppliesConsumed, this::updateSuppliesPanelData);
        lootTrackingService = new LootTrackingService(client, itemManager, config, log, notifier,
                previousLootSnapshot);
        arenaStateService = new ArenaStateService();
        historicalRunService = new HistoricalRunService();
        valueCalculationService = new ValueCalculationService();
        panelDataService = new PanelDataService();

        // Load persisted historical data (default profile before we know account name)
        activeHistoricalPlayerKey = DEFAULT_PLAYER_PROFILE_KEY;
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

        // Consumable HP changes from Eat/Drink should not count toward performance HP
        // metrics.
        if (option != null && (option.regionMatches(true, 0, "Eat", 0, 3)
                || option.regionMatches(true, 0, "Drink", 0, 5))) {
            performanceTracker.markConsumableHpChangeExpected();
        }

        // Detect entering arena via "Jump over gap"
        if (option != null && option.contains("Jump-over")) {
            if (!inMokhaArena) {
                log.debug("[Mokha] ===== ARENA ENTRY VIA JUMP-OVER =====");
                log.debug("[Mokha] Initializing new run tracking");
                // Fallback cleanup: if supplies were left from a previous run (e.g., death not
                // logged or disconnect),
                // move them to historical before starting new run
                if (!totalSuppliesConsumed.isEmpty()) {
                    log.warn(
                            "[Mokha] Fallback cleanup: Found {} orphaned supplies from previous run, moving to historical",
                            totalSuppliesConsumed.size());
                    archiveCurrentRunSuppliesToHistorical();
                    calculateSuppliesCost(); // Update historical category costs
                }

                applyArenaState(arenaStateService.createArenaEntryState());
                lootByWave.clear();
                previousLootSnapshot.clear();
                totalSuppliesConsumed.clear();

                // Reset performance tracking for new run
                performanceTracker.resetForRunStart(
                        client.getBoostedSkillLevel(Skill.PRAYER),
                        client.getBoostedSkillLevel(Skill.HITPOINTS),
                        isCurrentlyVenomed(),
                        client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));

                // Initialize supply snapshots for consumption tracking
                int arenaEntryAmmo = supplyTrackingService.initializeForArenaEntry();
                log.debug("[Mokha] Arena entry complete. Initial snapshot captured: {} items, Blowpipe ammo: {}",
                        lastCombinedSnapshot.size(), arenaEntryAmmo);
            }
        }

        // Detect "Descend" button - continues to next wave without claiming loot
        if (option != null && option.equalsIgnoreCase("Descend")) {
            log.debug("[Mokha] Descend button clicked, moving to wave {}", currentWaveNumber + 1);
            // Print accumulated loot so far (not claimed yet, could still be lost)
            printSuppliesConsumed();
            printAccumulatedLoot();
            // Increment wave number since we're moving to the next wave
            currentWaveNumber++;
            // Reset boss tracking for next wave
            bossDefeatedThisWave = false;
            bossWasEverPresentThisWave = false; // Reset boss presence for next wave
            lastDescendClickJustHappened = true; // Mark that Descend was clicked (not a teleport)
        }

        // Detect "Leave" button - player exits arena (loot is claimed, whether taken to
        // inv/bank or not)
        if (option != null && option.equals("Leave")) {
            log.debug("[Mokha] ===== LEAVE BUTTON DETECTED - player leaving arena with claimed loot");
            if (inMokhaArena) {
                log.debug("[Mokha] Arena state cleared: transitioning from inMokhaArena=true to false");
                // Print supplies consumed and claimed loot before clearing state
                printSuppliesConsumed();
                printAccumulatedLoot();

                capturePreviousRunSnapshot(true);

                // Update historical data with claimed loot (even if not yet in inventory/bank)
                updateHistoricalDataOnClaim();

                // Save historical data immediately after claiming
                saveHistoricalData();

                // Clear all tracking state
                applyArenaState(arenaStateService.createArenaExitState());
                clearCurrentRunTrackingCollections();

                log.debug("[Mokha] All arena state cleared. Snapshot size: {}, Supplies consumed size: {}",
                        lastCombinedSnapshot.size(), totalSuppliesConsumed.size());

                // Update panel to show cleared current run data
                updatePanelData();
            } else {
                log.warn(
                        "[Mokha] Leave button pressed but inMokhaArena=false! This should not happen. Current state: inMokhaArena={}, isDead={}",
                        inMokhaArena, isDead);
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        ensureHistoricalDataLoadedForCurrentPlayer();
        attemptHighscoresWidgetSyncIfVisible();
        attemptCollectionLogUniqueSyncIfVisible();

        performanceTracker.onTick();

        boolean wasInArena = inMokhaArena;

        // Track boss presence and health (only near entrance or in arena)
        WorldPoint location = null;
        if (client.getLocalPlayer() != null) {
            location = client.getLocalPlayer().getWorldLocation();
        }

        boolean performanceTrackingActive = isPerformanceTrackingActive(location);
        boolean inConsumptionBounds = isInsideConsumptionBounds(location);
        supplyTrackingService.onGameTick(inMokhaArena, isDead, inConsumptionBounds, lastArenaExitTime);

        // Count venom transitions and special attack uses during active runs.
        if (performanceTrackingActive) {
            performanceTracker.onVenomAndSpecialTick(
                    isCurrentlyVenomed(),
                    client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));
        } else if (inMokhaArena && !isDead) {
            // Keep baselines in sync while paused at entrance so those deltas are not
            // counted when entering combat bounds.
            performanceTracker.syncCurrentState(
                    client.getBoostedSkillLevel(Skill.PRAYER),
                    client.getBoostedSkillLevel(Skill.HITPOINTS),
                    isCurrentlyVenomed(),
                    client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));
        }

        // Failsafe arena boundary check to avoid stale in-arena state causing supply
        // tracking outside Doom.
        if (inMokhaArena) {
            boolean inTrackedArenaBounds = isInsideRunBounds(location);
            if (inTrackedArenaBounds) {
                ticksOutsideArenaBounds = 0;
            } else {
                ticksOutsideArenaBounds++;
                if (ticksOutsideArenaBounds == 1) {
                    log.warn(
                            "[Mokha] Player outside tracked arena bounds while run is active; starting grace window ({} ticks)",
                            ARENA_EXIT_GRACE_TICKS);
                }

                if (ticksOutsideArenaBounds >= ARENA_EXIT_GRACE_TICKS) {
                    handleForcedArenaExit("outside tracked arena bounds", location);
                    return;
                }
            }
        }

        boolean shouldCheckBoss = inMokhaArena || (location != null && isAtEntrance(location));
        NPC boss = shouldCheckBoss ? getBoss() : null;
        boolean bossCurrentlyPresent = (boss != null);

        if (inMokhaArena && !bossSeenThisRun && bossCurrentlyPresent) {
            bossSeenThisRun = true;
            // Update panel to show confirmed supplies
            updatePanelData();
        }

        // Track boss health to detect when it's defeated
        if (inMokhaArena && bossSeenThisRun && bossCurrentlyPresent && !bossDefeatedThisWave) {
            if (boss != null) {
                int bossHealth = boss.getHealthRatio();
                if (bossHealth == 0) {
                    bossDefeatedThisWave = true;
                    log.debug("[Mokha] ===== BOSS DEFEATED - Wave {} =====", currentWaveNumber);
                }
            }
        }

        // Detect teleport only when leaving the instanced arena
        // Boss disappearance inside the arena (post-kill) should not be treated as
        // teleport
        boolean leftInstance = client.getLocalPlayer() == null || isOutsideInstancedRegion();
        if (inMokhaArena && bossWasEverPresentThisWave && !bossCurrentlyPresent && !lastDescendClickJustHappened
                && !isDead && leftInstance) {
            log.debug("[Mokha] ===== TELEPORT DETECTED (boss disappeared) =====");
            log.debug("[Mokha] Boss defeated this wave: {}", bossDefeatedThisWave);
            log.debug("[Mokha] Current wave: {}", currentWaveNumber);
            log.debug("[Mokha] Unclaimed loot waves: {}", lootByWave.size());
            handleTeleportExit();
        }

        // Track if boss ever appeared this wave
        if (bossCurrentlyPresent) {
            bossWasEverPresentThisWave = true;
        }

        // Reset Descend flag after this tick
        if (lastDescendClickJustHappened) {
            lastDescendClickJustHappened = false;
        }

        // Check for player death
        if (client.getLocalPlayer() != null) {
            boolean currentlyDead = client.getLocalPlayer().getHealthRatio() == 0;

            if (currentlyDead && !isDead && inMokhaArena) {
                isDead = true;
                historicalDeaths += 1;
                log.debug("[Mokha] ===== PLAYER DEATH - Wave {} =====", currentWaveNumber);
                log.debug("[Mokha] Total deaths this run: {}", historicalDeaths);

                // Print supplies consumed before death
                printSuppliesConsumed();

                // Print lost loot from previous waves (couldn't claim because of death)
                printLostLoot();

                capturePreviousRunSnapshot(false);

                // Update historical supply costs (loot was lost, so don't count it)
                // Add supplies to historical tracking on any arena exit
                archiveCurrentRunSuppliesToHistorical();

                calculateSuppliesCost(); // This updates historical category costs
                long suppliesCost = 0;
                for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
                    suppliesCost += (long) getPricePerDose(entry.getKey()) * entry.getValue();
                }
                historicalSupplyCost += suppliesCost;

                // Move all unclaimed loot from this run to historical (never claimed, so now
                // unclaimed forever)
                moveCurrentRunUnclaimedToHistorical();

                applyArenaState(arenaStateService.createArenaExitState());
                clearCurrentRunTrackingCollections();

                // Update panel to show cleared current run data and updated historical costs
                updatePanelData();

                // Save persisted data
                saveHistoricalData();
            } else if (!currentlyDead && isDead) {
                isDead = false;
            }
        }

        // Clear snapshot when leaving arena - also clear supplies if they're still
        // pending
        if (!inMokhaArena && wasInArena) {
            lastCombinedSnapshot.clear();
            // If supplies are still in memory when leaving arena, clear them
            if (!totalSuppliesConsumed.isEmpty()) {
                totalSuppliesConsumed.clear();
                updatePanelData();
            }
        }

        // Check for loot window and capture loot
        checkForLootWindow();

        // Flush performance panel update once per tick (metrics may be dirtied multiple
        // times per tick, e.g. rapid hitsplats from the boss)
        if (performanceTracker.consumeDirty()) {
            updatePerformancePanelData();
        }

        // Panel updates are now only triggered when data changes:
        // - When loot is captured (in checkForLootWindow via parseLootItems)
        // - When supplies are consumed (in checkForConsumption)
        // - When claiming/dying (already handled above)
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        int groupId = event.getGroupId();

        if (groupId == InterfaceID.DomScoreboard.UNIVERSE >>> 16) {
            Map<Integer, Long> parsed = parseWaveCompletionsFromDomScoreboard();
            if (!parsed.isEmpty() && syncHistoricalRunsFromHighscoresData(parsed)) {
                log.debug("[Mokha] Synced {} highscores wave buckets from DomScoreboard", parsed.size());
                updatePanelData();
                saveHistoricalData();
            }
        }

        if (groupId == InterfaceID.Collection.BOSS_TEXT >>> 16 && syncCollectionLogUniquesFromVisibleWidgets()) {
            updatePanelData();
            saveHistoricalData();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == net.runelite.api.GameState.LOGGED_IN) {
            ensureHistoricalDataLoadedForCurrentPlayer();
        }

        // Save data immediately when logging out or leaving the game
        net.runelite.api.GameState state = event.getGameState();
        if (state == net.runelite.api.GameState.LOGIN_SCREEN ||
                state == net.runelite.api.GameState.HOPPING) {
            if (inMokhaArena && !lootByWave.isEmpty()) {
                // If player is still in arena and has unclaimed loot, save it first
                capturePreviousRunSnapshot(false);
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
            switch (event.getKey()) {
                case "ignoreSpiritSeedsValue":
                case "excludeUltraValuableItems":
                    // Trigger complete recalculation with new config settings
                    recalculateAllTotals();
                    // Save the updated state
                    saveHistoricalData();
                    break;
                case "displaySortMode":
                case "enableHistoricalEdit":
                    // Display-only settings: re-render panel without changing tracked data.
                    clientThread.invoke(this::updatePanelData);
                    break;
                case "displayHaValueOnHover":
                    boolean isEnabled = "true".equalsIgnoreCase(event.getNewValue());
                    SwingUtilities.invokeLater(() -> {
                        if (panel != null) {
                            panel.setDisplayHaValueOnHover(isEnabled);
                            panel.repaint();
                        }
                    });
                    break;
                case "showPerformancePanel":
                    boolean show = "true".equalsIgnoreCase(event.getNewValue());
                    SwingUtilities.invokeLater(() -> {
                        if (panel != null) {
                            panel.setPerformanceSectionVisible(show);
                            if (show) {
                                PerformanceSnapshot currentPerformance = performanceTracker.snapshot();
                                panel.updatePerformance(
                                        currentPerformance.getPrayerUsed(),
                                        currentPerformance.getHpLost(),
                                        currentPerformance.getHpRegained(),
                                        currentPerformance.getSpecialAttackUses(),
                                        currentPerformance.getVenomApplications());
                            }
                        }
                    });
                    break;
                case "showDrynessPanel":
                    boolean showDryness = "true".equalsIgnoreCase(event.getNewValue());
                    SwingUtilities.invokeLater(() -> {
                        if (panel != null) {
                            panel.setDrynessSectionVisible(showDryness);
                        }
                    });
                    break;
                default:
                    break;
            }
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        WorldPoint location = client.getLocalPlayer() != null ? client.getLocalPlayer().getWorldLocation() : null;
        supplyTrackingService.onItemContainerChanged(
                event.getContainerId(),
                inMokhaArena,
                isDead,
                isInsideConsumptionBounds(location),
                lastArenaExitTime);
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        WorldPoint location = client.getLocalPlayer() != null ? client.getLocalPlayer().getWorldLocation() : null;
        if (!isPerformanceTrackingActive(location)) {
            if (inMokhaArena && !isDead) {
                performanceTracker.syncCurrentState(
                        client.getBoostedSkillLevel(Skill.PRAYER),
                        client.getBoostedSkillLevel(Skill.HITPOINTS),
                        isCurrentlyVenomed(),
                        client.getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT));
            }
            return;
        }
        performanceTracker.onStatChanged(event);
    }

    private void updatePerformancePanelData() {
        if (panel == null || !config.showPerformancePanel()) {
            return;
        }
        PerformanceSnapshot currentPerformance = performanceTracker.snapshot();
        panel.updatePerformance(
                currentPerformance.getPrayerUsed(),
                currentPerformance.getHpLost(),
                currentPerformance.getHpRegained(),
                currentPerformance.getSpecialAttackUses(),
                currentPerformance.getVenomApplications());
    }

    private boolean isCurrentlyVenomed() {
        return client.getVarpValue(VarPlayer.POISON) >= 1_000_000;
    }

    private void checkForLootWindow() {
        LootTrackingService.LootWindowUpdate update = lootTrackingService.pollLootWindow(inMokhaArena);

        if (update.getDetectedWave() > 0) {
            currentWaveNumber = update.getDetectedWave();
        } else if (update.isLootWindowVisible() && currentWaveNumber == 0) {
            currentWaveNumber = 1;
        }

        if (!update.getNewLootByItemId().isEmpty()) {
            List<LootItem> newLootThisWave = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : update.getNewLootByItemId().entrySet()) {
                int itemId = entry.getKey();
                int newQty = entry.getValue();
                String itemName = itemManager.getItemComposition(itemId).getName();
                int itemValue = calculateTrackedLootItemValue(itemId, itemName, newQty);
                int itemHaValue = calculateTrackedLootItemHaValue(itemId, itemName, newQty);
                newLootThisWave.add(new LootItem(itemName, newQty, itemValue, itemHaValue));
            }

            if (!newLootThisWave.isEmpty()) {
                lootByWave.put(currentWaveNumber, newLootThisWave);
                updatePanelData();
            }
        }

        if (update.isLootWindowVisible()) {
            updateLootWindowDisplayedValue();
        }
    }

    private void updateLootWindowDisplayedValue() {
        Widget valueWidget = client.getWidget(DOM_LOOT_VALUE_WIDGET_ID);
        Widget lootContainerWidget = client.getWidget(DOM_LOOT_CONTENTS_WIDGET_ID);
        if (valueWidget == null || lootContainerWidget == null) {
            return;
        }

        if (!config.showAdjustedLootValueDisplay()) {
            restoreUnadjustedLootValueText(valueWidget);
            return;
        }

        String valueText = valueWidget.getText();
        if (valueText == null || !valueText.contains("Value:")) {
            return;
        }

        // White = all tracked loot at GE price, no ignore filters applied.
        // Blue = tracked loot at GE price with ignore filters applied.
        // Green = tracked loot at HA price with ignore filters applied.
        long unadjustedValue = calculateUnadjustedCurrentRunLootValue();
        if (unadjustedValue <= 0) {
            return;
        }

        long adjustedValue = calculateCurrentRunLootValue();
        long adjustedHaValue = calculateCurrentRunLootHaValue();

        String adjustedText = formatLootValueText(unadjustedValue, adjustedValue, adjustedHaValue);
        if (!adjustedText.equals(valueText)) {
            valueWidget.setText(adjustedText);
        }
    }

    private boolean syncHistoricalRunsFromHighscoresData(Map<Integer, Long> parsed) {
        Map<Integer, Long> normalized = new HashMap<>();
        for (Map.Entry<Integer, Long> entry : parsed.entrySet()) {
            int wave = normalizeWaveKey(entry.getKey());
            long parsedCount = Math.max(0, entry.getValue());
            normalized.put(wave, parsedCount);
        }

        boolean changed = !historicalCompletedRunsByWave.equals(normalized)
                || !localCompletedRunsSinceLastSyncByWave.isEmpty()
                || !highscoresBaselineSynced;

        if (!changed) {
            return false;
        }

        historicalCompletedRunsByWave.clear();
        historicalCompletedRunsByWave.putAll(normalized);
        localCompletedRunsSinceLastSyncByWave.clear();
        highscoresBaselineSynced = true;
        return true;
    }

    private void attemptHighscoresWidgetSyncIfVisible() {
        Widget root = client.getWidget(InterfaceID.DomScoreboard.UNIVERSE);
        Widget levels = client.getWidget(InterfaceID.DomScoreboard.PERSONAL);
        boolean rootVisible = root != null && !root.isHidden();
        boolean levelsVisible = levels != null && !levels.isHidden();
        if (!rootVisible && !levelsVisible) {
            return;
        }

        Map<Integer, Long> parsed = parseWaveCompletionsFromDomScoreboard();
        if (parsed.isEmpty()) {
            return;
        }

        if (syncHistoricalRunsFromHighscoresData(parsed)) {
            updatePanelData();
            saveHistoricalData();
        }
    }

    private void attemptCollectionLogUniqueSyncIfVisible() {
        if (syncCollectionLogUniquesFromVisibleWidgets()) {
            updatePanelData();
            saveHistoricalData();
        }
    }

    private boolean syncCollectionLogUniquesFromVisibleWidgets() {
        Widget selectedBossWidget = client.getWidget(COLLECTION_LOG_BOSS_SELECTED_WIDGET_ID);
        Widget itemsContainerWidget = client.getWidget(COLLECTION_LOG_ITEMS_CONTAINER_WIDGET_ID);

        if (selectedBossWidget == null || itemsContainerWidget == null) {
            return false;
        }

        if (selectedBossWidget.isHidden() || itemsContainerWidget.isHidden()) {
            return false;
        }

        String selectedBossText = selectedBossWidget.getText();
        if (selectedBossText == null
                || !selectedBossText.toLowerCase(Locale.ROOT).contains("doom of mokhaiotl")) {
            return false;
        }

        Map<String, Long> parsedCounts = parseCollectionLogUniqueCounts(itemsContainerWidget);
        if (parsedCounts.isEmpty()) {
            return false;
        }

        if (parsedCounts.equals(collectionLogClaimedUniqueCounts)) {
            return false;
        }

        collectionLogClaimedUniqueCounts.clear();
        collectionLogClaimedUniqueCounts.putAll(parsedCounts);
        return true;
    }

    private Map<String, Long> parseCollectionLogUniqueCounts(Widget itemsContainerWidget) {
        Map<String, Long> parsed = new HashMap<>();
        Widget[] children = itemsContainerWidget.getChildren();
        if (children == null || children.length == 0) {
            return parsed;
        }

        int slotsToParse = Math.min(COLLECTION_LOG_EXPECTED_UNIQUE_SLOTS, children.length);
        for (int slot = 0; slot < slotsToParse; slot++) {
            Widget itemWidget = children[slot];
            if (itemWidget == null || itemWidget.isHidden()) {
                continue;
            }

            int itemId = itemWidget.getItemId();
            if (itemId <= 0) {
                continue;
            }

            String rawName = itemManager.getItemComposition(itemId).getName();
            String canonicalUniqueName = canonicalizeTrackedUniqueName(rawName);
            if (canonicalUniqueName == null) {
                continue;
            }

            long quantity = Math.max(0, itemWidget.getItemQuantity());
            parsed.put(canonicalUniqueName, quantity);
        }

        return parsed;
    }

    private Map<Integer, Long> parseWaveCompletionsFromDomScoreboard() {
        Map<Integer, Long> parsed = new HashMap<>();
        boolean sawExpectedFormat = false;

        Widget root = findHighscoresWaveRoot();
        if (root == null) {
            return parsed;
        }

        sawExpectedFormat = collectWaveCompletionsFromWidget(root, parsed);

        // Some scoreboard layouts split "Level N" and completion count into sibling
        // widgets, so line-based parsing alone misses valid rows.
        parseWaveCompletionsFromStructuredTokens(root, parsed);

        if (!sawExpectedFormat || parsed.isEmpty()) {
            return new HashMap<>();
        }

        return parsed;
    }

    private Widget findHighscoresWaveRoot() {
        Widget preferred = client.getWidget(InterfaceID.DomScoreboard.PERSONAL);
        if (preferred != null && !preferred.isHidden()) {
            return preferred;
        }

        Widget primary = client.getWidget(InterfaceID.DomScoreboard.UNIVERSE);
        if (primary != null && !primary.isHidden()) {
            return primary;
        }

        Widget[] roots = client.getWidgetRoots();
        if (roots == null) {
            return null;
        }

        for (Widget root : roots) {
            if (root == null || root.isHidden()) {
                continue;
            }
            if (root.getId() == InterfaceID.DomScoreboard.UNIVERSE) {
                return root;
            }
        }

        return null;
    }

    private boolean collectWaveCompletionsFromWidget(Widget widget, Map<Integer, Long> parsed) {
        boolean sawExpectedFormat = false;
        if (widget == null || widget.isHidden()) {
            return false;
        }

        String text = widget.getText();
        if (text != null && !text.isEmpty()) {
            String stripped = text.replaceAll("<[^>]*>", " ").trim();
            if (!stripped.isEmpty()) {
                String[] lines = stripped.split("\\r?\\n|<br>|<br/>");
                for (String rawLine : lines) {
                    String line = rawLine == null ? "" : rawLine.replace('\u00A0', ' ').trim();
                    if (line.isEmpty()) {
                        continue;
                    }

                    String lowered = line.toLowerCase(Locale.ROOT);
                    if (lowered.contains("personal completions") || lowered.contains("level")) {
                        sawExpectedFormat = true;
                    }

                    if (parseWaveCompletionLine(line, parsed)) {
                        sawExpectedFormat = true;
                    }
                }
            }
        }

        Widget[] children = widget.getChildren();
        if (children != null) {
            for (Widget child : children) {
                sawExpectedFormat |= collectWaveCompletionsFromWidget(child, parsed);
            }
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null) {
            for (Widget child : dynamicChildren) {
                sawExpectedFormat |= collectWaveCompletionsFromWidget(child, parsed);
            }
        }

        Widget[] staticChildren = widget.getStaticChildren();
        if (staticChildren != null) {
            for (Widget child : staticChildren) {
                sawExpectedFormat |= collectWaveCompletionsFromWidget(child, parsed);
            }
        }

        Widget[] nestedChildren = widget.getNestedChildren();
        if (nestedChildren != null) {
            for (Widget child : nestedChildren) {
                sawExpectedFormat |= collectWaveCompletionsFromWidget(child, parsed);
            }
        }

        return sawExpectedFormat;
    }

    private boolean parseWaveCompletionLine(String line, Map<Integer, Long> parsed) {
        Matcher keywordMatcher = WAVE_KEYWORD_LINE_PATTERN.matcher(line);
        if (keywordMatcher.find()) {
            Integer wave = parseWaveToken(keywordMatcher.group(1));
            Long count = parseCountToken(keywordMatcher.group(2));
            if (wave != null && count != null) {
                parsed.merge(normalizeWaveKey(wave), count, Math::max);
                return true;
            }
        }

        Matcher compactMatcher = WAVE_COMPACT_LINE_PATTERN.matcher(line);
        if (compactMatcher.find()) {
            Integer wave = parseWaveToken(compactMatcher.group(1));
            Long count = parseCountToken(compactMatcher.group(2));
            if (wave != null && count != null && wave >= 1 && wave <= 99) {
                parsed.merge(normalizeWaveKey(wave), count, Math::max);
                return true;
            }
        }

        Matcher levelMatcher = LEVEL_COMPLETION_LINE_PATTERN.matcher(line);
        if (levelMatcher.find()) {
            Integer wave = parseWaveToken(levelMatcher.group(1));
            Long count = parseCountToken(levelMatcher.group(2));
            if (wave != null && count != null) {
                parsed.merge(normalizeWaveKey(wave), count, Math::max);
                return true;
            }
        }

        return false;
    }

    private void parseWaveCompletionsFromStructuredTokens(Widget root, Map<Integer, Long> parsed) {
        List<String> tokens = new ArrayList<>();
        collectWidgetTextTokens(root, tokens);

        Deque<Integer> pendingWaves = new ArrayDeque<>();
        for (String token : tokens) {
            Matcher levelOnlyMatcher = LEVEL_ONLY_LINE_PATTERN.matcher(token);
            if (levelOnlyMatcher.find()) {
                Integer wave = parseWaveToken(levelOnlyMatcher.group(1));
                if (wave != null) {
                    pendingWaves.addLast(wave);
                }
                continue;
            }

            if (pendingWaves.isEmpty()) {
                continue;
            }

            Matcher countOnlyMatcher = COUNT_ONLY_LINE_PATTERN.matcher(token);
            if (countOnlyMatcher.find()) {
                Long count = parseCountToken(countOnlyMatcher.group(1));
                if (count != null) {
                    int wave = pendingWaves.removeFirst();
                    parsed.merge(normalizeWaveKey(wave), count, Math::max);
                }
            }
        }
    }

    private void collectWidgetTextTokens(Widget widget, List<String> tokens) {
        if (widget == null || widget.isHidden()) {
            return;
        }

        String text = widget.getText();
        if (text != null && !text.isEmpty()) {
            String stripped = text.replaceAll("<[^>]*>", " ").replace('\u00A0', ' ').trim();
            if (!stripped.isEmpty()) {
                String[] lines = stripped.split("\\r?\\n|<br>|<br/>");
                for (String rawLine : lines) {
                    String line = rawLine == null ? "" : rawLine.trim();
                    if (!line.isEmpty()) {
                        tokens.add(line);
                    }
                }
            }
        }

        Widget[] children = widget.getChildren();
        if (children != null) {
            for (Widget child : children) {
                collectWidgetTextTokens(child, tokens);
            }
        }

        Widget[] dynamicChildren = widget.getDynamicChildren();
        if (dynamicChildren != null) {
            for (Widget child : dynamicChildren) {
                collectWidgetTextTokens(child, tokens);
            }
        }

        Widget[] staticChildren = widget.getStaticChildren();
        if (staticChildren != null) {
            for (Widget child : staticChildren) {
                collectWidgetTextTokens(child, tokens);
            }
        }

        Widget[] nestedChildren = widget.getNestedChildren();
        if (nestedChildren != null) {
            for (Widget child : nestedChildren) {
                collectWidgetTextTokens(child, tokens);
            }
        }
    }

    private Integer parseWaveToken(String token) {
        if (token == null) {
            return null;
        }
        String cleaned = token.trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        if (cleaned.endsWith("+")) {
            // Any plus-level row should contribute to the 9+ bucket used by dryness
            // probabilities (e.g. 8+, 9+, 10+ all map to bucket 9).
            return 9;
        }

        try {
            return Integer.parseInt(cleaned);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long parseCountToken(String token) {
        if (token == null) {
            return null;
        }
        String cleaned = token.replace(",", "").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(cleaned);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int normalizeWaveKey(int wave) {
        if (wave < 1) {
            return 1;
        }
        return wave >= 9 ? 9 : wave;
    }

    private String formatLootValueText(long originalValue, long adjustedValue, long adjustedHaValue) {
        return String.format("Value: %,d gp (<col=%s>GE: %,d gp</col>, <col=%s>HA: %,d gp</col>)",
                originalValue,
                LOOT_VALUE_COLOR_HEX,
                adjustedValue,
                LOOT_VALUE_HA_COLOR_HEX,
                adjustedHaValue);
    }

    private void restoreUnadjustedLootValueText(Widget valueWidget) {
        String valueText = valueWidget.getText();
        if (valueText == null || !valueText.contains("<col=" + LOOT_VALUE_COLOR_HEX + ">")) {
            return;
        }

        long originalValue = extractOriginalValueFromAdjustedDisplay(valueText);
        if (originalValue <= 0) {
            return;
        }

        String restoredText = String.format("Value: %,d GP", originalValue);
        if (!restoredText.equals(valueText)) {
            valueWidget.setText(restoredText);
        }
    }

    private long extractOriginalValueFromAdjustedDisplay(String valueText) {
        try {
            String plainText = valueText.replaceAll("<[^>]*>", "").trim();
            int valueIndex = plainText.indexOf("Value:");
            if (valueIndex < 0) {
                return 0;
            }

            String valuePortion = plainText.substring(valueIndex + "Value:".length()).trim();
            int openParenIndex = valuePortion.indexOf('(');
            if (openParenIndex >= 0) {
                valuePortion = valuePortion.substring(0, openParenIndex);
            }

            int gpIndex = valuePortion.toUpperCase().indexOf("GP");
            if (gpIndex >= 0) {
                valuePortion = valuePortion.substring(0, gpIndex);
            }

            String numStr = valuePortion.replaceAll("[^0-9]", "");
            if (!numStr.isEmpty()) {
                return Long.parseLong(numStr);
            }
        } catch (NumberFormatException e) {
            log.error("[Mokha] Error parsing original value from adjusted display", e);
        }

        return 0;
    }

    private long calculateUnadjustedCurrentRunLootValue() {
        return valueCalculationService.calculateUnadjustedCurrentRunLootValue(lootByWave);
    }

    private long calculateCurrentRunLootValue() {
        return valueCalculationService.calculateCurrentRunLootValue(lootByWave, config);
    }

    private long calculateCurrentRunLootHaValue() {
        return valueCalculationService.calculateCurrentRunLootHaValue(lootByWave, config);
    }

    private int calculateTrackedLootItemValue(int itemId, String itemName, int quantity) {
        if (isSunKissedBones(itemName)) {
            return 0;
        }

        int itemValue = itemManager.getItemPrice(itemId) * quantity;

        if (isMokhaCloth(itemName) && itemValue == 0) {
            int clothValue = getMokhaClothValue();
            itemValue = clothValue * quantity;
        }

        // Untradable items with known fixed values
        if (itemValue == 0) {
            if (itemName.equals("Spirit seed")) {
                itemValue = 140_000 * quantity;
            }
        }

        return itemValue;
    }

    private int calculateTrackedLootItemHaValue(int itemId, String itemName, int quantity) {
        if (isSunKissedBones(itemName)) {
            return SUN_KISSED_BONES_HA_VALUE * quantity;
        }

        int itemValue = getItemHighAlchPrice(itemId) * quantity;

        if (itemValue == 0 && itemName.equals("Spirit seed")) {
            return 0;
        }

        return itemValue;
    }

    private int getItemHighAlchPrice(int itemId) {
        return Math.max(0, itemManager.getItemComposition(itemId).getHaPrice());
    }

    @SuppressWarnings("deprecation")
    private NPC getBoss() {
        List<NPC> npcs = client.getNpcs();
        if (npcs == null || npcs.isEmpty()) {
            return null;
        }
        for (NPC npc : npcs) {
            if (npc != null && npc.getId() == DOOM_BOSS_NPC_ID) {
                return npc;
            }
        }
        return null;
    }

    /**
     * Handle teleport exit from arena
     * Routes loot to claimed or unclaimed based on whether boss was defeated
     */
    private void handleTeleportExit() {
        log.debug("[Mokha] ===== HANDLING TELEPORT EXIT =====");
        log.debug("[Mokha] Boss defeated: {}", bossDefeatedThisWave);
        log.debug("[Mokha] Wave: {}", currentWaveNumber);
        log.debug("[Mokha] Loot by wave entries: {}", lootByWave.size());
        log.debug("[Mokha] Total supplies consumed: {}", totalSuppliesConsumed.size());

        // Print supplies consumed
        printSuppliesConsumed();

        // Add supplies to historical tracking on any arena exit
        archiveCurrentRunSuppliesToHistorical();

        long suppliesCost = calculateSuppliesCost();
        historicalSupplyCost += suppliesCost;

        // Route loot - teleporting out always results in unclaimed loot
        // (player abandoned arena without claiming)
        log.debug("[Mokha] ===== ADDING LOOT TO UNCLAIMED (teleport exit) =====");
        log.debug("[Mokha] Boss was defeated: {}", bossDefeatedThisWave);
        printAccumulatedLoot();
        capturePreviousRunSnapshot(false);
        moveCurrentRunUnclaimedToHistorical();

        // Clear arena state
        applyArenaState(arenaStateService.createArenaExitState());
        clearCurrentRunTrackingCollections();

        // Update panel and save
        updatePanelData();
        saveHistoricalData();
    }

    /**
     * Move all current run unclaimed loot to historical when run ends (death or
     * disconnect)
     */
    private void moveCurrentRunUnclaimedToHistorical() {
        incrementLocalWaveCompletionsFromCurrentRun();
        historicalRunService.moveCurrentRunUnclaimedToHistorical(
                lootByWave,
                historicalUnclaimedByWave,
                historicalUnclaimedItemsByWave);
    }

    private void incrementLocalWaveCompletionsFromCurrentRun() {
        if (!highscoresBaselineSynced) {
            return;
        }

        for (Map.Entry<Integer, List<LootItem>> entry : lootByWave.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }

            int waveKey = normalizeWaveKey(entry.getKey());
            if (waveKey < 1) {
                continue;
            }

            localCompletedRunsSinceLastSyncByWave.put(
                    waveKey,
                    localCompletedRunsSinceLastSyncByWave.getOrDefault(waveKey, 0L) + 1L);
        }
    }

    /**
     * Print supplies consumed during the run
     * Groups potions by base name (e.g., all Prayer potion doses together)
     */
    private void printSuppliesConsumed() {
        if (totalSuppliesConsumed.isEmpty()) {
            log.debug("[Mokha] ===== NO SUPPLIES CONSUMED =====");
            return;
        }
        long totalValue = 0;

        for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            totalValue += (long) getPricePerDose(itemId) * quantity;
        }

        log.debug("[Mokha] ===== TOTAL SUPPLIES VALUE: {} gp =====", totalValue);
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
     * Calculate the price per individual dose for a potion, or return full price
     * for non-potion items.
     * For items with dose suffixes like (1), (2), (3), (4): divides full price by
     * dose count.
     * For items without dose suffixes (food, etc.): returns the full item price.
     */
    private int getPricePerDose(int itemId) {
        String itemName = itemManager.getItemComposition(itemId).getName();
        int fullPrice = itemManager.getItemPrice(itemId);

        // Check if item has a dose suffix like (1), (2), (3), (4)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\((\\d+)\\)$");
        java.util.regex.Matcher matcher = pattern.matcher(itemName);
        if (matcher.find()) {
            // Item has doses - divide by dose count
            int doseCount = Integer.parseInt(matcher.group(1));
            return fullPrice / doseCount;
        }

        // Item doesn't have doses - return full price
        return fullPrice;
    }

    /**
     * Load historical data from config
     */
    private void loadHistoricalData() {
        try {
            String playerKey = getCurrentPlayerProfileKey();
            previousRunLootByWave.clear();
            previousRunSuppliesConsumed.clear();
            hasPreviousRunSnapshot = false;
            previousRunClaimed = false;
            previousRunPerformance = PerformanceSnapshot.empty();
            historicalDataManager.loadDataForPlayer(playerKey);
            activeHistoricalPlayerKey = historicalDataManager.getActivePlayerKey();

            copyHistoricalDataFromManager();

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

            // Supply cost should mirror historical supplies totals. Keep ConfigManager as
            // fallback for legacy data that predates per-item supplies persistence.
            recalculateHistoricalSupplyCost();
            if (historicalSuppliesUsed.isEmpty()) {
                String supplyCostStr = configManager.getConfiguration("mokhaloot", "historicalSupplyCost");
                historicalSupplyCost = supplyCostStr != null && !supplyCostStr.isEmpty() ? Long.parseLong(supplyCostStr)
                        : 0;
            }

            // Load current run loot by wave
            String currentRunJson = configManager.getConfiguration("mokhaloot", "currentRunLootByWaveJson");
            if (currentRunJson != null && !currentRunJson.isEmpty() && !currentRunJson.equals("{}")) {
                try {
                    historicalRunService.restoreCurrentRunLootJsonAsUnclaimed(
                            currentRunJson,
                            gson,
                            lootByWave,
                            historicalUnclaimedByWave,
                            historicalUnclaimedItemsByWave);
                } catch (Exception e) {
                    log.warn("[Mokha] Failed to load current run loot by wave data", e);
                }
            }
        } catch (RuntimeException e) {
            log.error("[Mokha] Error loading historical data", e);
        }
    }

    private void copyHistoricalDataFromManager() {
        historicalClaimedItemsByWave.clear();
        historicalClaimedItemsByWave.putAll(historicalDataManager.getHistoricalClaimedItemsByWave());

        historicalSuppliesUsed.clear();
        historicalSuppliesUsed.putAll(historicalDataManager.getHistoricalSuppliesUsed());

        historicalClaimedByWave.clear();
        historicalClaimedByWave.putAll(historicalDataManager.getHistoricalClaimedByWave());

        historicalCompletedRunsByWave.clear();
        historicalCompletedRunsByWave.putAll(historicalDataManager.getHistoricalCompletedRunsByWave());
        localCompletedRunsSinceLastSyncByWave.clear();
        collectionLogClaimedUniqueCounts.clear();
        collectionLogClaimedUniqueCounts.putAll(historicalDataManager.getCollectionLogClaimedUniqueCounts());
        highscoresBaselineSynced = !historicalCompletedRunsByWave.isEmpty();

        historicalTotalClaimed = historicalDataManager.getHistoricalTotalClaimed();
        historicalClaims = historicalDataManager.getHistoricalClaims();
        historicalDeaths = historicalDataManager.getHistoricalDeaths();

        historicalUnclaimedByWave.clear();
        historicalUnclaimedByWave.putAll(historicalDataManager.getHistoricalUnclaimedByWave());
        historicalUnclaimedItemsByWave.clear();
        historicalUnclaimedItemsByWave.putAll(historicalDataManager.getHistoricalUnclaimedItemsByWave());
    }

    /**
     * Save historical data to config
     */
    private void saveHistoricalData() {
        try {
            String playerKey = activeHistoricalPlayerKey != null
                    ? activeHistoricalPlayerKey
                    : getCurrentPlayerProfileKey();

            syncHistoricalDataManagerState();
            historicalDataManager.saveDataForPlayer(playerKey);
            activeHistoricalPlayerKey = historicalDataManager.getActivePlayerKey();
        } catch (Exception e) {
            log.error("[Mokha] Error saving historical data", e);
        }
    }

    private void exportHistoricalData() {
        if (historicalDataManager == null) {
            return;
        }

        try {
            syncHistoricalDataManagerState();
            String json = historicalDataManager.exportActivePlayerDataJson();
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(json), null);

            JOptionPane.showMessageDialog(panel,
                    "Historical stats copied to clipboard.",
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException e) {
            log.error("[Mokha] Failed to copy historical data to clipboard", e);
            JOptionPane.showMessageDialog(panel,
                    "Failed to copy historical stats to clipboard.\n" + e.getMessage(),
                    "Export Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void importHistoricalData() {
        if (historicalDataManager == null) {
            return;
        }

        try {
            String clipboardText = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            String currentPlayerKey = getCurrentPlayerProfileKey();
            historicalDataManager.importActivePlayerDataJson(clipboardText, currentPlayerKey);
            copyHistoricalDataFromManager();
            saveHistoricalData();
            updatePanelData();

            JOptionPane.showMessageDialog(panel,
                    "Historical stats imported from clipboard.",
                    "Import Complete",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (UnsupportedFlavorException e) {
            log.error("[Mokha] Clipboard does not contain text", e);
            JOptionPane.showMessageDialog(panel,
                    "Clipboard does not contain text data.",
                    "Import Failed",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            log.error("[Mokha] Failed to import historical data", e);
            JOptionPane.showMessageDialog(panel,
                    "Failed to import historical stats.\n" + e.getMessage(),
                    "Import Failed",
                    JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException e) {
            log.error("[Mokha] Unexpected error importing historical data", e);
            JOptionPane.showMessageDialog(panel,
                    "Failed to import historical stats.\n" + e.getMessage(),
                    "Import Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void syncHistoricalDataManagerState() {
        historicalDataManager.setHistoricalClaimedItemsByWave(historicalClaimedItemsByWave);
        historicalDataManager.setHistoricalSuppliesUsed(historicalSuppliesUsed);
        historicalDataManager.setHistoricalClaimedByWave(historicalClaimedByWave);
        historicalDataManager.setHistoricalCompletedRunsByWave(historicalCompletedRunsByWave);
        historicalDataManager.setCollectionLogClaimedUniqueCounts(collectionLogClaimedUniqueCounts);
        historicalDataManager.setHistoricalTotalClaimed(historicalTotalClaimed);
        historicalDataManager.setHistoricalClaims(historicalClaims);
        historicalDataManager.setHistoricalDeaths(historicalDeaths);
        historicalDataManager.setHistoricalUnclaimedByWave(historicalUnclaimedByWave);
        historicalDataManager.setHistoricalUnclaimedItemsByWave(historicalUnclaimedItemsByWave);
    }

    private void ensureHistoricalDataLoadedForCurrentPlayer() {
        if (historicalDataManager == null) {
            return;
        }

        String currentKey = getCurrentPlayerProfileKey();
        if (currentKey.equals(activeHistoricalPlayerKey)) {
            return;
        }

        // One-time legacy migration: if we loaded under the "default" fallback key and
        // now know the real player name, move that data into the player's own profile
        // rather than discarding it by switching to an empty one.
        // Skip the migration if the player already has their own saved data — in that
        // case the "default" data is stale and the player's file data should be loaded.
        boolean isLegacyMigration = DEFAULT_PLAYER_PROFILE_KEY.equals(activeHistoricalPlayerKey)
                && !DEFAULT_PLAYER_PROFILE_KEY.equals(currentKey)
                && hasHistoricalDataInMemory()
                && !historicalDataManager.hasDataForPlayer(currentKey);

        if (isLegacyMigration) {
            activeHistoricalPlayerKey = currentKey;
            saveHistoricalData();
            updatePanelData();
            return;
        }

        if (hasHistoricalDataInMemory()) {
            saveHistoricalData();
        }
        activeHistoricalPlayerKey = currentKey;
        loadHistoricalData();
        updatePanelData();
    }

    private String getCurrentPlayerProfileKey() {
        if (client != null) {
            try {
                if (client.getLocalPlayer() != null) {
                    String name = client.getLocalPlayer().getName();
                    if (name != null) {
                        String normalized = name.trim().toLowerCase(Locale.ROOT);
                        if (!normalized.isEmpty()) {
                            return normalized;
                        }
                    }
                }
            } catch (AssertionError ignored) {
                // Client access attempted off-thread; fall back to current/default key.
            }
        }

        return activeHistoricalPlayerKey != null ? activeHistoricalPlayerKey : DEFAULT_PLAYER_PROFILE_KEY;
    }

    private boolean hasHistoricalDataInMemory() {
        return historicalTotalClaimed > 0
                || historicalSupplyCost > 0
                || historicalClaims > 0
                || historicalDeaths > 0
                || !historicalClaimedByWave.isEmpty()
                || !historicalCompletedRunsByWave.isEmpty()
                || !historicalClaimedItemsByWave.isEmpty()
                || !historicalSuppliesUsed.isEmpty()
                || !historicalUnclaimedByWave.isEmpty()
                || !historicalUnclaimedItemsByWave.isEmpty();
    }

    /**
     * Clear all current and historical data
     */
    private void clearAllData() {
        // Clear current run data
        lootByWave.clear();
        previousRunLootByWave.clear();
        previousRunSuppliesConsumed.clear();
        hasPreviousRunSnapshot = false;
        previousRunClaimed = false;
        previousRunPerformance = PerformanceSnapshot.empty();
        previousLootSnapshot.clear();
        initialSupplySnapshot.clear();
        totalSuppliesConsumed.clear();

        // Clear historical data
        historicalTotalClaimed = 0;
        historicalSupplyCost = 0;
        historicalClaims = 0;
        historicalDeaths = 0;
        historicalClaimedByWave.clear();
        historicalCompletedRunsByWave.clear();
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

    }

    private void clearClaimedHistoricalData() {
        historicalTotalClaimed = 0;
        historicalClaims = 0;
        historicalClaimedByWave.clear();
        historicalCompletedRunsByWave.clear();
        historicalClaimedItemsByWave.clear();

        updatePanelData();
        saveHistoricalData();

    }

    private void clearUnclaimedHistoricalData() {
        historicalUnclaimedByWave.clear();
        historicalUnclaimedItemsByWave.clear();

        updatePanelData();
        saveHistoricalData();

    }

    private void clearSuppliesHistoricalData() {
        historicalSupplyCost = 0;
        historicalSuppliesUsed.clear();

        updatePanelData();
        saveHistoricalData();

    }

    private void removeHistoricalClaimedWaveItem(int wave, String itemName) {
        clientThread.invoke(() -> {
            if (!removeHistoricalItemFromWaveMap(historicalClaimedItemsByWave, wave, itemName)) {
                return;
            }

            recalculateWaveTotals();
            recalculateHistoricalTotalClaimed();
            saveHistoricalData();
            updatePanelData();
        });
    }

    private void removeHistoricalUnclaimedWaveItem(int wave, String itemName) {
        clientThread.invoke(() -> {
            if (!removeHistoricalItemFromWaveMap(historicalUnclaimedItemsByWave, wave, itemName)) {
                return;
            }

            recalculateWaveTotals();
            recalculateHistoricalTotalClaimed();
            saveHistoricalData();
            updatePanelData();
        });
    }

    private void removeHistoricalClaimedItemAllWaves(String itemName) {
        clientThread.invoke(() -> {
            if (!removeHistoricalItemFromAllWaves(historicalClaimedItemsByWave, itemName)) {
                return;
            }

            recalculateWaveTotals();
            recalculateHistoricalTotalClaimed();
            saveHistoricalData();
            updatePanelData();
        });
    }

    private void removeHistoricalUnclaimedItemAllWaves(String itemName) {
        clientThread.invoke(() -> {
            if (!removeHistoricalItemFromAllWaves(historicalUnclaimedItemsByWave, itemName)) {
                return;
            }

            recalculateWaveTotals();
            recalculateHistoricalTotalClaimed();
            saveHistoricalData();
            updatePanelData();
        });
    }

    private void removeHistoricalSupplyItem(String itemName) {
        clientThread.invoke(() -> {
            if (!removeHistoricalItemByName(historicalSuppliesUsed, itemName)) {
                return;
            }

            historicalSupplyCost = 0;
            for (ItemAggregate item : historicalSuppliesUsed.values()) {
                historicalSupplyCost += item.totalValue;
            }

            saveHistoricalData();
            updatePanelData();
        });
    }

    private boolean removeHistoricalItemFromWaveMap(Map<Integer, Map<String, ItemAggregate>> byWave,
            int wave,
            String itemName) {
        if (wave >= 9) {
            return removeHistoricalItemFromNinePlusWaves(byWave, itemName);
        }

        Map<String, ItemAggregate> waveItems = byWave.get(wave);
        if (waveItems == null) {
            return false;
        }

        boolean removed = removeHistoricalItemByName(waveItems, itemName);
        if (removed && waveItems.isEmpty()) {
            byWave.remove(wave);
        }
        return removed;
    }

    private boolean removeHistoricalItemFromAllWaves(Map<Integer, Map<String, ItemAggregate>> byWave, String itemName) {
        boolean removedAny = false;
        List<Integer> emptyWaves = new ArrayList<>();

        for (Map.Entry<Integer, Map<String, ItemAggregate>> waveEntry : byWave.entrySet()) {
            Map<String, ItemAggregate> waveItems = waveEntry.getValue();
            if (removeHistoricalItemByName(waveItems, itemName)) {
                removedAny = true;
            }
            if (waveItems.isEmpty()) {
                emptyWaves.add(waveEntry.getKey());
            }
        }

        for (Integer wave : emptyWaves) {
            byWave.remove(wave);
        }

        return removedAny;
    }

    private boolean removeHistoricalItemFromNinePlusWaves(Map<Integer, Map<String, ItemAggregate>> byWave,
            String itemName) {
        boolean removedAny = false;
        List<Integer> emptyWaves = new ArrayList<>();

        for (Map.Entry<Integer, Map<String, ItemAggregate>> waveEntry : byWave.entrySet()) {
            if (waveEntry.getKey() < 9) {
                continue;
            }

            Map<String, ItemAggregate> waveItems = waveEntry.getValue();
            if (removeHistoricalItemByName(waveItems, itemName)) {
                removedAny = true;
            }
            if (waveItems.isEmpty()) {
                emptyWaves.add(waveEntry.getKey());
            }
        }

        for (Integer wave : emptyWaves) {
            byWave.remove(wave);
        }

        return removedAny;
    }

    private boolean removeHistoricalItemByName(Map<String, ItemAggregate> items, String itemName) {
        if (items == null || itemName == null) {
            return false;
        }

        String keyToRemove = null;
        for (Map.Entry<String, ItemAggregate> itemEntry : items.entrySet()) {
            if (itemEntry.getValue() != null && itemEntry.getValue().name != null
                    && itemEntry.getValue().name.equalsIgnoreCase(itemName)) {
                keyToRemove = itemEntry.getKey();
                break;
            }

            if (itemEntry.getKey() != null && itemEntry.getKey().equalsIgnoreCase(itemName)) {
                keyToRemove = itemEntry.getKey();
                break;
            }
        }

        if (keyToRemove == null) {
            return false;
        }

        items.remove(keyToRemove);
        return true;
    }

    /**
     * Recalculate all totals based on historical data
     * This includes: total claimed, total unclaimed, supply cost
     * Also applies the ignore settings for sun-kissed bones and spirit seeds
     */
    private void recalculateAllTotals() {
        clientThread.invoke(() -> {
            // Recalculate GE value for all items in claimed and unclaimed loot
            recalculateAllItemGEValues(historicalClaimedItemsByWave);
            recalculateAllItemGEValues(historicalUnclaimedItemsByWave);

            // Apply Mokhaiotl Cloth override after GE recalculation so it is not
            // overwritten by GE price refresh.
            updateMokhaClothPrices();

            // Apply ignore settings to all historical items (this will update totalValue
            // based on current config)
            applyIgnoreSettingsToHistoricalItems(historicalClaimedItemsByWave);
            applyIgnoreSettingsToHistoricalItems(historicalUnclaimedItemsByWave);

            // Recalculate wave totals based on current settings
            recalculateWaveTotals();

            // Recalculate total claimed from historical claimed items
            recalculateHistoricalTotalClaimed();

            // Recalculate supply cost
            recalculateHistoricalSupplyCost();

            // Save the recalculated data
            saveHistoricalData();

            // Update panel with recalculated totals
            updatePanelData();

        });
    }

    /**
     * Recalculate GE value for all items in the provided map (by wave)
     */
    private void recalculateAllItemGEValues(Map<Integer, Map<String, ItemAggregate>> itemsByWave) {
        for (Map<String, ItemAggregate> waveItems : itemsByWave.values()) {
            for (ItemAggregate item : waveItems.values()) {
                if (isMokhaCloth(item.name)) {
                    continue;
                }

                if (isSunKissedBones(item.name)) {
                    item.pricePerItem = 0;
                    item.haPricePerItem = SUN_KISSED_BONES_HA_VALUE;
                    item.totalValue = 0;
                    item.totalHaValue = (long) SUN_KISSED_BONES_HA_VALUE * item.totalQuantity;
                    continue;
                }

                int itemId = getItemIdForName(item.name);
                if (itemId > 0) {
                    int gePrice = itemManager.getItemPrice(itemId);
                    int haPrice = getItemHighAlchPrice(itemId);
                    item.pricePerItem = gePrice;
                    item.haPricePerItem = haPrice;
                    item.totalValue = (long) gePrice * item.totalQuantity;
                    item.totalHaValue = (long) haPrice * item.totalQuantity;
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
            log.debug("[Mokha] ===== NO LOOT COLLECTED =====");
            return;
        }
        long totalValue = 0;

        // Iterate through all waves that have loot
        for (Map.Entry<Integer, List<LootItem>> entry : lootByWave.entrySet()) {
            List<LootItem> waveLoot = entry.getValue();
            if (waveLoot != null && !waveLoot.isEmpty()) {
                for (LootItem item : waveLoot) {
                    totalValue += item.value;
                }
            }
        }

        log.debug("[Mokha] ===== TOTAL LOOT VALUE: {} gp =====", totalValue);
    }

    /**
     * Print loot lost from death (loot that could have been claimed but wasn't)
     * This includes all loot from completed waves up to the wave they died on
     */
    private void printLostLoot() {
        // Intentionally left without informational logging.
    }

    /**
     * Check if player is at the arena entrance
     */
    private boolean isAtEntrance(WorldPoint location) {
        int dx = location.getX() - ENTRANCE_CENTER_X;
        int dy = location.getY() - ENTRANCE_CENTER_Y;
        int distanceSquared = dx * dx + dy * dy;
        int radiusSquared = ENTRANCE_RADIUS * ENTRANCE_RADIUS;
        return distanceSquared <= radiusSquared;
    }

    private boolean isInsideRunBounds(WorldPoint location) {
        if (location == null) {
            return false;
        }

        // Explicit known regions from coordinate recording logs.
        if (DOOM_ARENA_REGION_IDS.contains(location.getRegionID())) {
            return true;
        }

        // Fallback for future waves/regions: while a run is active, treat any instanced
        // region as arena so we do not miss wave 6+ segments that have not been mapped
        return !isOutsideInstancedRegion();
    }

    @SuppressWarnings("deprecation")
    private boolean isOutsideInstancedRegion() {
        return !client.isInInstancedRegion();
    }

    private boolean isInsideConsumptionBounds(WorldPoint location) {
        if (!isInsideRunBounds(location)) {
            return false;
        }

        // Do not track consumption in entrance area where reclaim/setup can happen.
        return !isAtEntrance(location);
    }

    private boolean isPerformanceTrackingActive(WorldPoint location) {
        if (!inMokhaArena || isDead) {
            return false;
        }

        if (!isInsideRunBounds(location)) {
            return false;
        }

        // Exclude entrance staging area to avoid counting setup/self-damage as run
        // performance.
        return !isAtEntrance(location);
    }

    private void handleForcedArenaExit(String reason, WorldPoint location) {
        log.warn("[Mokha] ===== FORCED ARENA EXIT =====");
        log.warn("[Mokha] Reason: {}", reason);
        if (location != null) {
            log.warn("[Mokha] Location at forced exit: ({}, {}, {}), region {}",
                    location.getX(), location.getY(), location.getPlane(), location.getRegionID());
        }

        // Treat as unclaimed exit to avoid losing tracked run data.
        printSuppliesConsumed();
        printAccumulatedLoot();

        archiveCurrentRunSuppliesToHistorical();

        long suppliesCost = calculateSuppliesCost();
        historicalSupplyCost += suppliesCost;

        capturePreviousRunSnapshot(false);
        moveCurrentRunUnclaimedToHistorical();

        applyArenaState(arenaStateService.createArenaExitState());
        clearCurrentRunTrackingCollections();

        updatePanelData();
        saveHistoricalData();
    }

    private void applyArenaState(ArenaStateService.ArenaStateSnapshot state) {
        inMokhaArena = state.isInMokhaArena();
        isDead = state.isDead();
        currentWaveNumber = state.getCurrentWaveNumber();
        bossSeenThisRun = state.isBossSeenThisRun();
        bossDefeatedThisWave = state.isBossDefeatedThisWave();
        bossWasEverPresentThisWave = state.isBossWasEverPresentThisWave();
        lastDescendClickJustHappened = state.isLastDescendClickJustHappened();
        lastArenaExitTime = state.getLastArenaExitTime();
        ticksOutsideArenaBounds = state.getTicksOutsideArenaBounds();
    }

    private void clearCurrentRunTrackingCollections() {
        arenaStateService.clearRunTrackingCollections(
                lastCombinedSnapshot,
                lootByWave,
                previousLootSnapshot,
                totalSuppliesConsumed,
                initialSupplySnapshot);

        // Performance metrics are per-run and should be reset when a run ends.
        performanceTracker.reset();
    }

    private void archiveCurrentRunSuppliesToHistorical() {
        arenaStateService.archiveConsumedSupplies(
                totalSuppliesConsumed,
                historicalSuppliesUsed,
                itemId -> getBasePotionName(itemManager.getItemComposition(itemId).getName()),
                this::getPricePerDose);
    }

    /**
     * Update historical data when loot is claimed
     */
    private void updateHistoricalDataOnClaim() {
        incrementLocalWaveCompletionsFromCurrentRun();

        long claimedValue = historicalRunService.applyClaimedLoot(
                lootByWave,
                historicalClaimedByWave,
                historicalClaimedItemsByWave);
        historicalTotalClaimed += claimedValue;
        historicalClaims += 1;

        // Add supplies cost to historical total and track items on any arena exit
        archiveCurrentRunSuppliesToHistorical();

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
     * Update only supplies sections to avoid full panel redraw on ammo consumption
     * ticks.
     */
    private void updateSuppliesPanelData() {
        if (panel == null) {
            return;
        }

        PanelDataService.SuppliesPanelData suppliesData = panelDataService.buildSuppliesPanelData(
                totalSuppliesConsumed,
                historicalSuppliesUsed,
                itemId -> getBasePotionName(itemManager.getItemComposition(itemId).getName()),
                this::getPricePerDose);

        panel.updateSuppliesCurrentRun(suppliesData.currentSuppliesTotalValue, suppliesData.currentSuppliesData);
        panel.updateSuppliesTotal(suppliesData.historicalSuppliesTotalValue, suppliesData.historicalSuppliesData);
    }

    /**
     * Update all panel data
     */
    private void updatePanelData() {
        if (panel == null) {
            return;
        }

        panel.setDisplayHaValueOnHover(config.displayHaValueOnHover());

        // Keep summary supply cost synchronized with historical supplies totals.
        recalculateHistoricalSupplyCost();

        // Apply ignore settings to historical items before displaying
        applyIgnoreSettingsToHistoricalItems(historicalClaimedItemsByWave);
        applyIgnoreSettingsToHistoricalItems(historicalUnclaimedItemsByWave);

        // Recalculate historical total with ignore settings applied
        recalculateHistoricalTotalClaimed();

        PanelDataService.PanelData panelData = panelDataService.buildPanelData(
                lootByWave,
                historicalClaimedItemsByWave,
                historicalClaimedByWave,
                historicalUnclaimedItemsByWave,
                historicalUnclaimedByWave,
                totalSuppliesConsumed,
                historicalSuppliesUsed,
                config,
                valueCalculationService,
                itemId -> getBasePotionName(itemManager.getItemComposition(itemId).getName()),
                this::getPricePerDose);
        PanelDataService.RunPanelData previousRunData = panelDataService.buildRunPanelData(
                previousRunLootByWave,
                config,
                valueCalculationService);
        PanelDataService.SuppliesPanelData previousRunSuppliesData = panelDataService.buildSuppliesPanelData(
                previousRunSuppliesConsumed,
                new HashMap<>(),
                itemId -> getBasePotionName(itemManager.getItemComposition(itemId).getName()),
                this::getPricePerDose);

        long uniqueClaimsCount = valueCalculationService
                .calculateHistoricalUniqueClaimCount(historicalClaimedItemsByWave);

        long totalWaveRolls = calculateTotalHistoricalWaveRolls();
        ExpectedDropsByItem expectedDropsByItem = calculateHistoricalExpectedDropsByItem();
        panel.updateHistoricalDryness(
                totalWaveRolls,
                expectedDropsByItem.total(),
                uniqueClaimsCount,
                expectedDropsByItem.dom,
                expectedDropsByItem.treads,
                expectedDropsByItem.eye,
                expectedDropsByItem.cloth,
                calculateHistoricalReceivedCountForUnique(DOM_NAME),
                calculateHistoricalReceivedCountForUnique(AVERNIC_TREADS_NAME),
                calculateHistoricalReceivedCountForUnique(EYE_OF_AYAK_NAME),
                calculateHistoricalReceivedCountForUnique(MOKHA_CLOTH_NAME));

        // Update Profit/Loss section
        panel.updateProfitLoss(historicalTotalClaimed, historicalSupplyCost, panelData.totalUnclaimed,
                historicalClaims, historicalDeaths, uniqueClaimsCount);

        panel.updateCurrentRun(
                panelData.currentRunValue,
                panelData.currentRunItems,
                panelData.currentRunItemsByWave,
                panelData.currentRunTotalsByWave,
                panelData.currentRunHaTotalsByWave);
        panel.updatePreviousRun(hasPreviousRunSnapshot, previousRunClaimed,
                previousRunData.totalValue, previousRunData.totalHaValue,
                previousRunData.items,
                previousRunSuppliesData.currentSuppliesTotalValue,
                previousRunSuppliesData.currentSuppliesData,
                previousRunPerformance.getPrayerUsed(),
                previousRunPerformance.getHpLost(),
                previousRunPerformance.getHpRegained(),
                previousRunPerformance.getSpecialAttackUses(),
                previousRunPerformance.getVenomApplications(),
                previousRunData.itemsByWave,
                previousRunData.totalsByWave,
                previousRunData.haTotalsByWave);
        panel.updateCurrentRunUniqueChance(
                currentWaveNumber,
                calculateCumulativeUniqueChancePercent(currentWaveNumber),
                calculateCumulativeUniqueChancePercent(2, currentWaveNumber, this::getClothUniqueChanceForDelve),
                calculateCumulativeUniqueChancePercent(3, currentWaveNumber, this::getStandardUniqueChanceForDelve),
                calculateCumulativeUniqueChancePercent(4, currentWaveNumber, this::getStandardUniqueChanceForDelve),
                calculateCumulativeUniqueChancePercent(6, currentWaveNumber, this::getDomUniqueChanceForDelve));

        for (int wave = 1; wave <= 10; wave++) {
            panel.updateClaimedWave(
                    wave,
                    panelData.claimedItemsByWave.getOrDefault(wave, new HashMap<>()),
                    panelData.claimedTotalsByWave.getOrDefault(wave, 0L));
        }

        for (int wave = 1; wave <= 10; wave++) {
            panel.updateUnclaimedWave(
                    wave,
                    panelData.unclaimedItemsByWave.getOrDefault(wave, new HashMap<>()),
                    panelData.unclaimedTotalsByWave.getOrDefault(wave, 0L));
        }

        panel.updateSuppliesCurrentRun(panelData.currentSuppliesTotalValue, panelData.currentSuppliesData);
        panel.updateSuppliesTotal(panelData.historicalSuppliesTotalValue, panelData.historicalSuppliesData);

        // Update performance section
        if (config.showPerformancePanel()) {
            PerformanceSnapshot currentPerformance = performanceTracker.snapshot();
            panel.updatePerformance(
                    currentPerformance.getPrayerUsed(),
                    currentPerformance.getHpLost(),
                    currentPerformance.getHpRegained(),
                    currentPerformance.getSpecialAttackUses(),
                    currentPerformance.getVenomApplications());
        }

        // Force panel refresh after all updates
        panel.revalidate();
        panel.repaint();
    }

    private void recalculateHistoricalSupplyCost() {
        historicalSupplyCost = 0;
        for (ItemAggregate item : historicalSuppliesUsed.values()) {
            historicalSupplyCost += item.totalValue;
        }
    }

    private double calculateCumulativeUniqueChancePercent(int currentDepth) {
        return calculateCumulativeUniqueChancePercent(2, currentDepth, this::getOverallUniqueChanceForDelve);
    }

    private double calculateCumulativeUniqueChancePercent(int unlockDepth, int currentDepth,
            IntToDoubleFunction chanceByDelve) {
        if (currentDepth < unlockDepth) {
            return 0;
        }

        double chanceNoUnique = 1.0;
        for (int delve = unlockDepth; delve <= currentDepth; delve++) {
            chanceNoUnique *= (1.0 - chanceByDelve.applyAsDouble(delve));
        }

        return (1.0 - chanceNoUnique) * 100.0;
    }

    private double getOverallUniqueChanceForDelve(int delve) {
        switch (delve) {
            case 2:
                return UNIQUE_CHANCE_DELVE_2;
            case 3:
                return UNIQUE_CHANCE_DELVE_3;
            case 4:
                return UNIQUE_CHANCE_DELVE_4;
            case 5:
                return UNIQUE_CHANCE_DELVE_5;
            case 6:
                return UNIQUE_CHANCE_DELVE_6;
            case 7:
                return UNIQUE_CHANCE_DELVE_7;
            case 8:
                return UNIQUE_CHANCE_DELVE_8;
            default:
                return UNIQUE_CHANCE_DELVE_9_PLUS;
        }
    }

    private double getClothUniqueChanceForDelve(int delve) {
        if (delve <= 2) {
            return UNIQUE_CHANCE_DELVE_2;
        }
        return getStandardUniqueChanceForDelve(delve);
    }

    private double getStandardUniqueChanceForDelve(int delve) {
        switch (delve) {
            case 3:
                return UNIQUE_CHANCE_STANDARD_DELVE_3;
            case 4:
                return UNIQUE_CHANCE_STANDARD_DELVE_4;
            case 5:
                return UNIQUE_CHANCE_STANDARD_DELVE_5;
            case 6:
                return UNIQUE_CHANCE_STANDARD_DELVE_6;
            case 7:
                return UNIQUE_CHANCE_STANDARD_DELVE_7;
            case 8:
                return UNIQUE_CHANCE_STANDARD_DELVE_8;
            default:
                return UNIQUE_CHANCE_STANDARD_DELVE_9_PLUS;
        }
    }

    private double getDomUniqueChanceForDelve(int delve) {
        switch (delve) {
            case 6:
                return UNIQUE_CHANCE_DOM_DELVE_6;
            case 7:
                return UNIQUE_CHANCE_DOM_DELVE_7;
            case 8:
                return UNIQUE_CHANCE_DOM_DELVE_8;
            default:
                return UNIQUE_CHANCE_DOM_DELVE_9_PLUS;
        }
    }

    private long calculateTotalHistoricalWaveRolls() {
        long total = 0;
        for (long count : getEffectiveHistoricalCompletedRunsByWave().values()) {
            total += count;
        }
        return total;
    }

    private double calculateHistoricalExpectedUniqueDrops() {
        return calculateHistoricalExpectedDropsByItem().total();
    }

    private ExpectedDropsByItem calculateHistoricalExpectedDropsByItem() {
        ExpectedDropsByItem expected = new ExpectedDropsByItem();

        for (Map.Entry<Integer, Long> entry : getEffectiveHistoricalCompletedRunsByWave().entrySet()) {
            int wave = entry.getKey();
            long completedCount = entry.getValue();

            if (wave < 2 || completedCount <= 0) {
                continue;
            }

            expected.cloth += completedCount * getClothUniqueChanceForDelve(wave);

            if (wave >= 3) {
                expected.eye += completedCount * getStandardUniqueChanceForDelve(wave);
            }

            if (wave >= 4) {
                expected.treads += completedCount * getStandardUniqueChanceForDelve(wave);
            }

            if (wave >= 6) {
                expected.dom += completedCount * getDomUniqueChanceForDelve(wave);
            }
        }

        return expected;
    }

    private long calculateHistoricalReceivedCountForUnique(String uniqueName) {
        if (uniqueName == null || uniqueName.trim().isEmpty()) {
            return 0;
        }

        String canonicalTarget = canonicalizeTrackedUniqueName(uniqueName);
        if (canonicalTarget == null) {
            return 0;
        }

        long total = 0;

        for (Map<String, ItemAggregate> itemsByName : historicalClaimedItemsByWave.values()) {
            for (ItemAggregate item : itemsByName.values()) {
                if (item == null || item.name == null) {
                    continue;
                }

                String itemCanonicalName = canonicalizeTrackedUniqueName(item.name);
                if (canonicalTarget.equals(itemCanonicalName)) {
                    total += item.totalQuantity;
                }
            }
        }

        long collectionLogCount = collectionLogClaimedUniqueCounts.getOrDefault(canonicalTarget, 0L);
        return Math.max(total, collectionLogCount);
    }

    private long calculateEffectiveHistoricalUniqueClaimCount() {
        return calculateHistoricalReceivedCountForUnique(MOKHA_CLOTH_NAME)
                + calculateHistoricalReceivedCountForUnique(EYE_OF_AYAK_NAME)
                + calculateHistoricalReceivedCountForUnique(AVERNIC_TREADS_NAME)
                + calculateHistoricalReceivedCountForUnique(DOM_NAME);
    }

    private String canonicalizeTrackedUniqueName(String rawName) {
        if (rawName == null) {
            return null;
        }

        String normalized = rawName.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.startsWith("mokhaiotl cloth")) {
            return MOKHA_CLOTH_NAME;
        }
        if (normalized.startsWith("eye of ayak")) {
            return EYE_OF_AYAK_NAME;
        }
        if (normalized.startsWith("avernic treads")) {
            return AVERNIC_TREADS_NAME;
        }
        if (normalized.startsWith("dom")) {
            return DOM_NAME;
        }

        return null;
    }

    private Map<Integer, Long> getEffectiveHistoricalCompletedRunsByWave() {
        if (!highscoresBaselineSynced || localCompletedRunsSinceLastSyncByWave.isEmpty()) {
            return historicalCompletedRunsByWave;
        }

        Map<Integer, Long> combined = new HashMap<>(historicalCompletedRunsByWave);
        for (Map.Entry<Integer, Long> entry : localCompletedRunsSinceLastSyncByWave.entrySet()) {
            combined.put(entry.getKey(), combined.getOrDefault(entry.getKey(), 0L) + entry.getValue());
        }
        return combined;
    }

    private double getExpectedUniqueDropsPerCompletionForWave(int wave) {
        if (wave < 2) {
            return 0.0;
        }

        double expected = 0.0;

        // Cloth starts at level 2.
        expected += getClothUniqueChanceForDelve(wave);

        // At level 3 only one standard unique roll is available; levels 4+
        // have both standard unique rolls.
        if (wave == 3) {
            expected += getStandardUniqueChanceForDelve(wave);
        } else if (wave >= 4) {
            expected += getStandardUniqueChanceForDelve(wave) * 2.0;
        }

        // Dom starts at level 6.
        if (wave >= 6) {
            expected += getDomUniqueChanceForDelve(wave);
        }

        return expected;
    }

    /**
     * Apply ignore settings to historical item data
     * Zeros out the totalValue for Spirit Seeds and Sun-kissed Bones if the
     * respective ignore settings are enabled. Restores correct value if setting is
     * disabled.
     */
    private void applyIgnoreSettingsToHistoricalItems(Map<Integer, Map<String, ItemAggregate>> historicalItems) {
        valueCalculationService.applyIgnoreSettingsToHistoricalItems(historicalItems, config);
    }

    /**
     * Recalculate historical claimed total based on actual item values after ignore
     * settings applied
     */
    private void recalculateHistoricalTotalClaimed() {
        historicalTotalClaimed = valueCalculationService.recalculateHistoricalTotalClaimed(
                historicalClaimedItemsByWave,
                config);
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

        // Update cloth in claimed items
        for (Map<String, ItemAggregate> waveItems : historicalClaimedItemsByWave.values()) {
            ItemAggregate clothItem = findMokhaClothItem(waveItems);
            if (clothItem != null) {
                clothItem.pricePerItem = clothPrice;
                clothItem.totalValue = (long) clothItem.totalQuantity * clothPrice;
            }
        }

        // Update cloth in unclaimed items
        for (Map<String, ItemAggregate> waveItems : historicalUnclaimedItemsByWave.values()) {
            ItemAggregate clothItem = findMokhaClothItem(waveItems);
            if (clothItem != null) {
                clothItem.pricePerItem = clothPrice;
                clothItem.totalValue = (long) clothItem.totalQuantity * clothPrice;
            }
        }
    }

    private boolean isMokhaCloth(String itemName) {
        return itemName != null && itemName.equalsIgnoreCase(MOKHA_CLOTH_NAME);
    }

    private boolean isSunKissedBones(String itemName) {
        return itemName != null && itemName.equalsIgnoreCase(SUN_KISSED_BONES_NAME);
    }

    private ItemAggregate findMokhaClothItem(Map<String, ItemAggregate> waveItems) {
        for (ItemAggregate item : waveItems.values()) {
            if (isMokhaCloth(item.name)) {
                return item;
            }
        }

        return null;
    }

    /**
     * Recalculate wave totals based on excludeUltraValuableItems setting
     */
    private void recalculateWaveTotals() {
        valueCalculationService.recalculateWaveTotals(
                historicalClaimedItemsByWave,
                historicalUnclaimedItemsByWave,
                historicalClaimedByWave,
                historicalUnclaimedByWave,
                config);
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
        String manualValueText = config.mokhaClothValue();
        if (manualValueText != null && !manualValueText.trim().isEmpty()) {
            try {
                String numeric = manualValueText.replaceAll("[^0-9-]", "");
                if (!numeric.isEmpty()) {
                    int manualValue = Integer.parseInt(numeric);
                    if (manualValue > 0) {
                        return manualValue;
                    }
                }
                log.warn("[Mokha] Manual cloth value '{}' is invalid; falling back to automatic calculation",
                        manualValueText);
            } catch (NumberFormatException e) {
                log.warn("[Mokha] Manual cloth value '{}' is invalid; falling back to automatic calculation",
                        manualValueText);
            }
        }

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

    private void capturePreviousRunSnapshot(boolean claimed) {
        previousRunLootByWave.clear();
        previousRunSuppliesConsumed.clear();
        hasPreviousRunSnapshot = true;
        previousRunClaimed = claimed;

        previousRunPerformance = performanceTracker.snapshot();

        for (Map.Entry<Integer, List<LootItem>> entry : lootByWave.entrySet()) {
            List<LootItem> copiedItems = new ArrayList<>();
            for (LootItem item : entry.getValue()) {
                copiedItems.add(new LootItem(item.name, item.quantity, item.value, item.haValue));
            }
            previousRunLootByWave.put(entry.getKey(), copiedItems);
        }

        previousRunSuppliesConsumed.putAll(totalSuppliesConsumed);
    }
}
