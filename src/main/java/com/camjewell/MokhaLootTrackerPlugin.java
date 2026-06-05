package com.camjewell;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
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
import net.runelite.client.ui.overlay.OverlayManager;
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
    private static final int DOM_LOOT_VALUE_WIDGET_ID = InterfaceID.DomEndLevelUi.LOOT_VALUE;
    private static final int DOM_LOOT_CONTENTS_WIDGET_ID = InterfaceID.DomEndLevelUi.LOOT_CONTENTS;
    private static final Pattern DOSE_PATTERN = Pattern.compile("\\((\\d+)\\)$");

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

    @Inject
    private OverlayManager overlayManager;

    private NavigationButton navButton;
    private MokhaLootPanel panel;
    private BlowpipeCheckOverlay blowpipeCheckOverlay;
    private HistoricalDataManager historicalDataManager;
    private SupplyTrackingService supplyTrackingService;
    private LootTrackingService lootTrackingService;
    private ArenaStateService arenaStateService;
    private HistoricalRunService historicalRunService;
    private ValueCalculationService valueCalculationService;
    private PanelDataService panelDataService;
    private HighscoresSyncService highscoresSyncService;
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
    private NPC trackedBoss = null;
    private boolean lastDescendClickJustHappened = false; // Track if Descend was just clicked
    private long lastArenaExitTime = 0; // Track when player last exited arena to detect stale snapshot usage
    private int ticksOutsideArenaBounds = 0; // Failsafe: detect stale in-arena state

    // Blowpipe check overlay state machine
    private enum BlowpipeCheckState { INACTIVE, AWAITING_INITIAL, TRACKING, AWAITING_FINAL }
    private BlowpipeCheckState blowpipeCheckState = BlowpipeCheckState.INACTIVE;
    private final Map<Integer, Integer> blowpipeInitialDartSnapshot = new HashMap<>();
    // When the player checks a blowpipe or staff but the config value didn't change (so no
    // ConfigChanged fires), we detect the chat message and use this flag to trigger the read
    // on the next tick — after tictac7x has had a chance to update the config.
    private boolean pendingWeaponCheckRead = false;

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

    // Mokhaiotl Cloth handling
    private boolean hasWarnedAboutZeroClothValue = false; // Track if we've already warned player

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
        blowpipeCheckOverlay = new BlowpipeCheckOverlay();
        overlayManager.add(blowpipeCheckOverlay);
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
        highscoresSyncService = new HighscoresSyncService(client, itemManager,
                this::canonicalizeTrackedUniqueName,
                historicalCompletedRunsByWave, localCompletedRunsSinceLastSyncByWave,
                collectionLogClaimedUniqueCounts);

        // Load persisted historical data (default profile before we know account name)
        activeHistoricalPlayerKey = DEFAULT_PLAYER_PROFILE_KEY;
        loadHistoricalData();
        updatePanelData();
    }

    @Override
    protected void shutDown() throws Exception {
        clientToolbar.removeNavigation(navButton);
        overlayManager.remove(blowpipeCheckOverlay);
        resetBlowpipeCheckState();
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
                if (blowpipeCheckState == BlowpipeCheckState.AWAITING_FINAL) {
                    resetBlowpipeCheckState(); // Cancel leftover AWAITING_FINAL from previous run
                }
                int arenaEntryAmmo = supplyTrackingService.initializeForArenaEntry();
                log.debug("[Mokha] Arena entry complete. Initial snapshot captured: {} items, Blowpipe ammo: {}",
                        lastCombinedSnapshot.size(), arenaEntryAmmo);
                if (blowpipeCheckState == BlowpipeCheckState.TRACKING) {
                    // Player already checked weapon at entrance; re-sync live snapshot since
                    // initializeForArenaEntry rebuilt lastWeaponAmmoSnapshot from config data
                    supplyTrackingService.setBlowpipeOverlayActive(true);
                } else if (blowpipeCheckState == BlowpipeCheckState.INACTIVE) {
                    startBlowpipeCheckIfNeeded();
                }
                // AWAITING_INITIAL: overlay already shown at entrance, player checks inside arena
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

                // Transition blowpipe overlay to final-check state if dart tracking was active
                handleBlowpipeOnRunEnd();

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
        if (pendingWeaponCheckRead) {
            pendingWeaponCheckRead = false;
            handleBlowpipeConfigChanged();
        }

        ensureHistoricalDataLoadedForCurrentPlayer();
        if (highscoresSyncService.attemptHighscoresWidgetSync()) {
            updatePanelData();
            saveHistoricalData();
        }
        if (highscoresSyncService.attemptCollectionLogUniqueSync()) {
            updatePanelData();
            saveHistoricalData();
        }

        performanceTracker.onTick();

        boolean wasInArena = inMokhaArena;

        // Track boss presence and health (only near entrance or in arena)
        WorldPoint location = null;
        if (client.getLocalPlayer() != null) {
            location = client.getLocalPlayer().getWorldLocation();
        }

        // Start weapon check overlay when player is at the entrance staging area
        if (!inMokhaArena && blowpipeCheckState == BlowpipeCheckState.INACTIVE
                && location != null && isAtEntrance(location)) {
            startBlowpipeCheckIfNeeded();
        }

        boolean performanceTrackingActive = isPerformanceTrackingActive(location);
        if (inMokhaArena) {
            boolean inConsumptionBounds = isInsideConsumptionBounds(location);
            supplyTrackingService.onGameTick(isDead, inConsumptionBounds, lastArenaExitTime);
        }

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
        NPC boss = shouldCheckBoss ? trackedBoss : null;
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

                handleBlowpipeOnRunEnd();
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
            Map<Integer, Long> parsed = highscoresSyncService.parseWaveCompletionsFromDomScoreboard();
            if (!parsed.isEmpty() && highscoresSyncService.syncHistoricalRunsFromHighscoresData(parsed)) {
                log.debug("[Mokha] Synced {} highscores wave buckets from DomScoreboard", parsed.size());
                updatePanelData();
                saveHistoricalData();
            }
        }

        if (groupId == InterfaceID.Collection.BOSS_TEXT >>> 16 && highscoresSyncService.attemptCollectionLogUniqueSync()) {
            updatePanelData();
            saveHistoricalData();
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        if (event.getNpc().getId() == DOOM_BOSS_NPC_ID) {
            trackedBoss = event.getNpc();
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        if (event.getNpc() == trackedBoss) {
            trackedBoss = null;
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
    public void onChatMessage(ChatMessage event) {
        if (blowpipeCheckState != BlowpipeCheckState.AWAITING_INITIAL
                && blowpipeCheckState != BlowpipeCheckState.AWAITING_FINAL) {
            return;
        }
        // Detect when the player "Checks" a blowpipe or powered staff. tictac7x-charges may not
        // fire ConfigChanged if the stored values are identical to the last check, so we use the
        // in-game check message as a fallback trigger. The actual config read is deferred one tick
        // so tictac7x has time to update the config value first.
        String msg = event.getMessage();
        if (msg.contains("blowpipe") && (msg.contains("darts") || msg.contains("scales") || msg.contains("empty"))) {
            pendingWeaponCheckRead = true;
            log.debug("[Mokha] Blowpipe check message detected, scheduling config read next tick");
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (event.getGroup().equals("tictac7x-charges") && isTrackedWeaponConfigKey(event.getKey())) {
            handleBlowpipeConfigChanged();
            return;
        }

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
                                        currentPerformance.getPrayerRegained(),
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
                currentPerformance.getPrayerRegained(),
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

        handleBlowpipeOnRunEnd();
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
        if (!highscoresSyncService.isHighscoresBaselineSynced()) {
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
        return DOSE_PATTERN.matcher(itemName).replaceAll("").trim();
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
        Matcher matcher = DOSE_PATTERN.matcher(itemName);
        if (matcher.find()) {
            return fullPrice / Integer.parseInt(matcher.group(1));
        }
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
        highscoresSyncService.setHighscoresBaselineSynced(!historicalCompletedRunsByWave.isEmpty());

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
        // Skip the migration if the player already has their own saved data â€” in that
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

        handleBlowpipeOnRunEnd();
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

    // ---- Blowpipe check overlay ----

    private void resetBlowpipeCheckState() {
        blowpipeCheckState = BlowpipeCheckState.INACTIVE;
        blowpipeInitialDartSnapshot.clear();
        pendingWeaponCheckRead = false;
        if (supplyTrackingService != null) {
            supplyTrackingService.setBlowpipeOverlayActive(false);
        }
        if (blowpipeCheckOverlay != null) {
            blowpipeCheckOverlay.hide();
        }
    }

    private void startBlowpipeCheckIfNeeded() {
        if (blowpipeCheckState != BlowpipeCheckState.INACTIVE) {
            return;
        }
        if (!config.blowpipeCheckReminder()) {
            log.debug("[Mokha] Blowpipe check reminder disabled");
            return;
        }
        if (!supplyTrackingService.hasTrackedWeaponPresent()) {
            log.debug("[Mokha] No charge-tracked weapon detected");
            return;
        }
        log.debug("[Mokha] Charge-tracked weapon detected — starting check overlay");
        blowpipeCheckState = BlowpipeCheckState.AWAITING_INITIAL;
        blowpipeInitialDartSnapshot.clear();
        supplyTrackingService.setBlowpipeOverlayActive(true);
        blowpipeCheckOverlay.showInitialPrompt();
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                "<col=FFAA00>[Mokha] Weapon with trackable charges detected — check it to begin charge tracking.</col>", null);
    }

    private void handleBlowpipeOnRunEnd() {
        if (blowpipeCheckState == BlowpipeCheckState.TRACKING && !blowpipeInitialDartSnapshot.isEmpty()) {
            blowpipeCheckState = BlowpipeCheckState.AWAITING_FINAL;
            blowpipeCheckOverlay.showFinalPrompt();
        } else {
            resetBlowpipeCheckState();
        }
    }

    private void handleBlowpipeConfigChanged() {
        switch (blowpipeCheckState) {
            case AWAITING_INITIAL:
                blowpipeInitialDartSnapshot.clear();
                blowpipeInitialDartSnapshot.putAll(supplyTrackingService.readCurrentWeaponAmmo());
                log.debug("[Mokha] Weapon charge initial snapshot: {}", blowpipeInitialDartSnapshot);
                if (!blowpipeInitialDartSnapshot.isEmpty()) {
                    blowpipeCheckState = BlowpipeCheckState.TRACKING;
                    blowpipeCheckOverlay.hide();
                    int totalCharges = blowpipeInitialDartSnapshot.values().stream().mapToInt(Integer::intValue).sum();
                    log.debug("[Mokha] Weapon charge snapshot captured: {}", blowpipeInitialDartSnapshot);
                    client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                            "<col=00CC66>[Mokha] Weapon charge tracking active — " + totalCharges + " charges at start of run.</col>", null);
                }
                break;
            case AWAITING_FINAL:
                Map<Integer, Integer> finalDarts = supplyTrackingService.readCurrentWeaponAmmo();
                log.debug("[Mokha] Weapon charge final snapshot: {}", finalDarts);
                int used = 0;
                for (Map.Entry<Integer, Integer> e : blowpipeInitialDartSnapshot.entrySet()) {
                    int consumed = Math.max(0, e.getValue() - finalDarts.getOrDefault(e.getKey(), 0));
                    used += consumed;
                    if (consumed > 0) {
                        previousRunSuppliesConsumed.merge(e.getKey(), consumed, Integer::sum);
                    }
                }
                applyBlowpipeDartConsumptionToHistorical(blowpipeInitialDartSnapshot, finalDarts);
                log.debug("[Mokha] historicalSuppliesUsed after charge write: {} entries: {}", historicalSuppliesUsed.size(), historicalSuppliesUsed.keySet());
                resetBlowpipeCheckState();
                saveHistoricalData();
                SwingUtilities.invokeLater(this::updatePanelData);
                client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
                        "<col=00CC66>[Mokha] Weapon charges recorded: " + used + " used this run.</col>", null);
                break;
            default:
                break;
        }
    }

    private static boolean isTrackedWeaponConfigKey(String key) {
        switch (key) {
            // Blowpipe storage keys (JSON array format)
            case "toxic_blowpipe_storage":
            case "camphor_blowpipe_storage":
            case "ironwood_blowpipe_storage":
            case "rosewood_blowpipe_storage":
            case "blazing_blowpipe_storage":
            // Powered staff charge keys (plain integer format)
            case "trident_of_the_seas_charges":
            case "trident_of_the_swamp_charges":
            case "sanguinesti_staff_charges":
            case "tumekens_shadow_charges":
            case "eye_of_ayak_charges":
                return true;
            default:
                return false;
        }
    }

    private void applyBlowpipeDartConsumptionToHistorical(
            Map<Integer, Integer> initial, Map<Integer, Integer> finalCounts) {
        for (Map.Entry<Integer, Integer> entry : initial.entrySet()) {
            int itemId = entry.getKey();
            int initialQty = entry.getValue();
            int finalQty = finalCounts.getOrDefault(itemId, 0);
            if (finalQty >= initialQty) {
                continue;
            }
            int consumed = initialQty - finalQty;
            String baseName = getBasePotionName(itemManager.getItemComposition(itemId).getName());
            // Staff charge item IDs are the weapon itself — per-charge GE price is meaningless
            int priceEach = SupplyTrackingService.isStaffChargeItemId(itemId) ? 0 : getPricePerDose(itemId);
            ItemAggregate existing = historicalSuppliesUsed.get(baseName);
            if (existing != null) {
                existing.add(consumed, priceEach);
            } else {
                historicalSuppliesUsed.put(baseName, new ItemAggregate(baseName, consumed, priceEach));
            }
            historicalSupplyCost += (long) priceEach * consumed;
            log.debug("[Mokha] Weapon charges applied to historical: {} x{} @ {} gp", baseName, consumed, priceEach);
        }
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

        Map<Integer, Long> effectiveRuns = getEffectiveHistoricalCompletedRunsByWave();
        long totalWaveRolls = DrynessMath.calculateTotalHistoricalWaveRolls(effectiveRuns);
        ExpectedDropsByItem expectedDropsByItem = DrynessMath.calculateHistoricalExpectedDropsByItem(effectiveRuns);
        long deepRolls = effectiveRuns.getOrDefault(8, 0L) + effectiveRuns.getOrDefault(9, 0L);
        panel.updateHistoricalDryness(
                totalWaveRolls,
                deepRolls,
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
                previousRunPerformance.getPrayerRegained(),
                previousRunPerformance.getHpLost(),
                previousRunPerformance.getHpRegained(),
                previousRunPerformance.getSpecialAttackUses(),
                previousRunPerformance.getVenomApplications(),
                previousRunData.itemsByWave,
                previousRunData.totalsByWave,
                previousRunData.haTotalsByWave);
        panel.updateCurrentRunUniqueChance(
                currentWaveNumber,
                DrynessMath.calculateCumulativeUniqueChancePercent(currentWaveNumber),
                DrynessMath.calculateCumulativeUniqueChancePercent(2, currentWaveNumber, DrynessMath::getClothUniqueChanceForDelve),
                DrynessMath.calculateCumulativeUniqueChancePercent(3, currentWaveNumber, DrynessMath::getStandardUniqueChanceForDelve),
                DrynessMath.calculateCumulativeUniqueChancePercent(4, currentWaveNumber, DrynessMath::getStandardUniqueChanceForDelve),
                DrynessMath.calculateCumulativeUniqueChancePercent(6, currentWaveNumber, DrynessMath::getDomUniqueChanceForDelve));

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
                    currentPerformance.getPrayerRegained(),
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
        if (!highscoresSyncService.isHighscoresBaselineSynced() || localCompletedRunsSinceLastSyncByWave.isEmpty()) {
            return historicalCompletedRunsByWave;
        }

        Map<Integer, Long> combined = new HashMap<>(historicalCompletedRunsByWave);
        for (Map.Entry<Integer, Long> entry : localCompletedRunsSinceLastSyncByWave.entrySet()) {
            combined.put(entry.getKey(), combined.getOrDefault(entry.getKey(), 0L) + entry.getValue());
        }
        return combined;
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
