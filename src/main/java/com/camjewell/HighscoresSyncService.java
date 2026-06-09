package com.camjewell;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

class HighscoresSyncService {
    private static final Logger log = LoggerFactory.getLogger(HighscoresSyncService.class);
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

        if (!changed) {
            return false;
        }

        historicalCompletedRunsByWave.clear();
        historicalCompletedRunsByWave.putAll(normalized);
        localCompletedRunsSinceLastSyncByWave.clear();
        highscoresBaselineSynced = true;
        return true;
    }

    Map<Integer, Long> parseWaveCompletionsFromDomScoreboard() {
        Map<Integer, Long> parsed = new HashMap<>();

        Widget root = findHighscoresWaveRoot();
        if (root == null) {
            return parsed;
        }

        boolean sawExpectedFormat = collectWaveCompletionsFromWidget(root, parsed);
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
                    String line = rawLine == null ? "" : rawLine.replace(' ', ' ').trim();
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
                parsed.merge(normalizeWaveKey(wave), count, (a, b) -> Math.max(a, b));
                return true;
            }
        }

        Matcher compactMatcher = WAVE_COMPACT_LINE_PATTERN.matcher(line);
        if (compactMatcher.find()) {
            Integer wave = parseWaveToken(compactMatcher.group(1));
            Long count = parseCountToken(compactMatcher.group(2));
            if (wave != null && count != null && wave >= 1 && wave <= 99) {
                parsed.merge(normalizeWaveKey(wave), count, (a, b) -> Math.max(a, b));
                return true;
            }
        }

        Matcher levelMatcher = LEVEL_COMPLETION_LINE_PATTERN.matcher(line);
        if (levelMatcher.find()) {
            Integer wave = parseWaveToken(levelMatcher.group(1));
            Long count = parseCountToken(levelMatcher.group(2));
            if (wave != null && count != null) {
                parsed.merge(normalizeWaveKey(wave), count, (a, b) -> Math.max(a, b));
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
                    parsed.merge(normalizeWaveKey(wave), count, (a, b) -> Math.max(a, b));
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
            String stripped = text.replaceAll("<[^>]*>", " ").replace(' ', ' ').trim();
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

    private Integer parseWaveToken(String token) {
        if (token == null) {
            return null;
        }
        String cleaned = token.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.endsWith("+")) {
            return 9;
        }
        try {
            return Integer.valueOf(cleaned);
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
