package com.camjewell;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

class HighscoresSyncService {
    private static final Logger log = LoggerFactory.getLogger(HighscoresSyncService.class);
    private static final int COLLECTION_LOG_EXPECTED_UNIQUE_SLOTS = 4;

    // Personal level value widgets, in wave order. The scoreboard exposes separate
    // G_* (global leader) and P_* (personal) widgets in the same interface tree.
    // Reading P_* directly avoids the Math.max contamination that plagued the old
    // recursive text-scan, which was picking up the global leader's higher counts.
    // Level 8+ (the boss encounter) is normalised to wave key 9.
    private static final int[][] PERSONAL_LEVEL_VALUE_WIDGETS = {
        {1, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_1_VAL},
        {2, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_2_VAL},
        {3, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_3_VAL},
        {4, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_4_VAL},
        {5, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_5_VAL},
        {6, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_6_VAL},
        {7, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_7_VAL},
        {8, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_8_VAL},
        {9, InterfaceID.DomScoreboard.P_TOTAL_LEVEL_8__VAL},
    };

    private final Client client;
    private final ItemManager itemManager;
    private final Function<String, String> canonicalizeUniqueName;
    private final Map<Integer, Long> historicalCompletedRunsByWave;
    private final Map<Integer, Long> localCompletedRunsSinceLastSyncByWave;
    private final Map<String, Long> collectionLogClaimedUniqueCounts;

    private boolean highscoresBaselineSynced;

    HighscoresSyncService(
            Client client,
            ItemManager itemManager,
            Function<String, String> canonicalizeUniqueName,
            Map<Integer, Long> historicalCompletedRunsByWave,
            Map<Integer, Long> localCompletedRunsSinceLastSyncByWave,
            Map<String, Long> collectionLogClaimedUniqueCounts) {
        this.client = client;
        this.itemManager = itemManager;
        this.canonicalizeUniqueName = canonicalizeUniqueName;
        this.historicalCompletedRunsByWave = historicalCompletedRunsByWave;
        this.localCompletedRunsSinceLastSyncByWave = localCompletedRunsSinceLastSyncByWave;
        this.collectionLogClaimedUniqueCounts = collectionLogClaimedUniqueCounts;
    }

    boolean isHighscoresBaselineSynced() {
        return highscoresBaselineSynced;
    }

    void setHighscoresBaselineSynced(boolean synced) {
        this.highscoresBaselineSynced = synced;
    }

    boolean attemptHighscoresWidgetSync() {
        Widget root = client.getWidget(InterfaceID.DomScoreboard.UNIVERSE);
        Widget levels = client.getWidget(InterfaceID.DomScoreboard.PERSONAL);
        boolean rootVisible = root != null && !root.isHidden();
        boolean levelsVisible = levels != null && !levels.isHidden();
        if (!rootVisible && !levelsVisible) {
            return false;
        }

        Map<Integer, Long> parsed = parseWaveCompletionsFromDomScoreboard();
        if (parsed.isEmpty()) {
            return false;
        }

        return syncHistoricalRunsFromHighscoresData(parsed);
    }

    boolean attemptCollectionLogUniqueSync() {
        return syncCollectionLogUniquesFromVisibleWidgets();
    }

    boolean syncCollectionLogUniquesFromVisibleWidgets() {
        Widget headerPanel = client.getWidget(InterfaceID.Collection.HEADER_TEXT);
        Widget itemsContainerWidget = client.getWidget(InterfaceID.Collection.ITEMS_CONTENTS);

        if (headerPanel == null || itemsContainerWidget == null) {
            return false;
        }

        if (headerPanel.isHidden() || itemsContainerWidget.isHidden()) {
            return false;
        }

        // Boss name is in the first child of the HEADER_TEXT panel, not the panel itself
        String bossName = getChildText(headerPanel, 0);
        if (bossName == null || !bossName.toLowerCase(Locale.ROOT).contains("doom of mokhaiotl")) {
            log.debug("[Mokha] Collection log sync: boss name not found (child0=[{}])", bossName);
            return false;
        }

        Map<String, Long> parsedCounts = parseCollectionLogUniqueCounts(itemsContainerWidget);
        if (parsedCounts.isEmpty()) {
            log.debug("[Mokha] Collection log sync: parsed counts empty for [{}]", bossName);
            return false;
        }

        if (parsedCounts.equals(collectionLogClaimedUniqueCounts)) {
            return false;
        }

        log.debug("[Mokha] Collection log sync: updating unique counts: {}", parsedCounts);
        collectionLogClaimedUniqueCounts.clear();
        collectionLogClaimedUniqueCounts.putAll(parsedCounts);
        return true;
    }

    private String getChildText(Widget parent, int childIndex) {
        Widget[] children = parent.getChildren();
        if (children == null || children.length <= childIndex) {
            children = parent.getDynamicChildren();
        }
        if (children != null && children.length > childIndex && children[childIndex] != null) {
            return children[childIndex].getText();
        }
        return null;
    }

    boolean syncHistoricalRunsFromHighscoresData(Map<Integer, Long> parsed) {
        Map<Integer, Long> normalized = new HashMap<>();
        for (Map.Entry<Integer, Long> entry : parsed.entrySet()) {
            int wave = normalizeWaveKey(entry.getKey());
            long parsedCount = Math.max(0, entry.getValue());
            normalized.put(wave, parsedCount);
        }

        boolean changed = !historicalCompletedRunsByWave.equals(normalized)
                || !localCompletedRunsSinceLastSyncByWave.isEmpty()
                || !highscoresBaselineSynced;

        // Always apply the parsed widget data as the source of truth regardless of
        // the changed flag — this guarantees that opening the highscores page always
        // clears any stale local increments and snaps the display to the real counts.
        historicalCompletedRunsByWave.clear();
        historicalCompletedRunsByWave.putAll(normalized);
        localCompletedRunsSinceLastSyncByWave.clear();
        highscoresBaselineSynced = true;
        return changed;
    }

    Map<Integer, Long> parseWaveCompletionsFromDomScoreboard() {
        // Read personal completion counts directly from the named P_TOTAL_LEVEL_N_VAL
        // widgets. This avoids mixing personal data with global-leader data that the old
        // recursive text-scan produced (both G_* and P_* live in the same widget tree and
        // the old Math.max merge picked the higher of the two for each wave).
        Map<Integer, Long> parsed = new HashMap<>();
        for (int[] pair : PERSONAL_LEVEL_VALUE_WIDGETS) {
            int wave = pair[0];
            Widget widget = client.getWidget(pair[1]);
            if (widget == null) {
                continue;
            }
            String text = widget.getText();
            if (text == null || text.isEmpty()) {
                continue;
            }
            Long count = parseCountToken(text);
            if (count != null) {
                parsed.put(wave, count);
            }
        }
        return parsed;
    }

    private Map<String, Long> parseCollectionLogUniqueCounts(Widget itemsContainerWidget) {
        Map<String, Long> parsed = new HashMap<>();
        Widget[] children = itemsContainerWidget.getChildren();
        if (children == null || children.length == 0) {
            children = itemsContainerWidget.getDynamicChildren();
        }
        if (children == null || children.length == 0) {
            return parsed;
        }

        int slotsToParse = Math.min(COLLECTION_LOG_EXPECTED_UNIQUE_SLOTS, children.length);
        for (int slot = 0; slot < slotsToParse; slot++) {
            Widget itemWidget = children[slot];
            if (itemWidget == null || itemWidget.isHidden()) {
                continue;
            }

            // Opacity 0 = obtained (fully visible); non-zero = unobtained (greyed out)
            if (itemWidget.getOpacity() != 0) {
                continue;
            }

            int itemId = itemWidget.getItemId();
            if (itemId <= 0) {
                continue;
            }

            String rawName = itemManager.getItemComposition(itemId).getName();
            String canonicalUniqueName = canonicalizeUniqueName.apply(rawName);
            if (canonicalUniqueName == null) {
                continue;
            }

            long quantity = Math.max(1, itemWidget.getItemQuantity());
            parsed.put(canonicalUniqueName, quantity);
        }

        return parsed;
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
            long value = Long.parseLong(cleaned);
            return value >= 0 ? value : null;
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
}
