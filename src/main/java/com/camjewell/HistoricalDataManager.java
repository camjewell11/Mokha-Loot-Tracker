package com.camjewell;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.camjewell.MokhaLootTrackerPlugin.ItemAggregate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class HistoricalDataManager {
    private static final Logger log = LoggerFactory.getLogger(HistoricalDataManager.class);

    private static final String MOKHALOOT_DIR = "mokhaloot";
    private static final String DATA_FILE = "historical-data.json";
    private static final String DEFAULT_PLAYER_KEY = "default";

    private final File dataFile;
    private final Gson gson;

    private Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave;
    private Map<Integer, Long> historicalClaimedByWave;
    private Map<Integer, Map<String, ItemAggregate>> historicalUnclaimedItemsByWave;
    private Map<Integer, Long> historicalUnclaimedByWave;
    private Map<String, ItemAggregate> historicalSuppliesUsed;
    private long historicalTotalClaimed;
    private long historicalClaims;
    private long historicalDeaths;
    private String activePlayerKey;

    public HistoricalDataManager(File runeLiteDirectory, Gson gson) {
        File mokhalootDir = new File(runeLiteDirectory, MOKHALOOT_DIR);
        if (!mokhalootDir.exists()) {
            mokhalootDir.mkdirs();
        }

        this.dataFile = new File(mokhalootDir, DATA_FILE);
        this.gson = gson.newBuilder().setPrettyPrinting().create();

        // Initialize with empty data
        this.historicalClaimedItemsByWave = new HashMap<>();
        this.historicalSuppliesUsed = new HashMap<>();
        this.historicalClaimedByWave = new HashMap<>();
        this.historicalTotalClaimed = 0;
        this.historicalClaims = 0;
        this.historicalDeaths = 0;
        this.historicalUnclaimedByWave = new HashMap<>();
        this.historicalUnclaimedItemsByWave = new HashMap<>();
        this.activePlayerKey = DEFAULT_PLAYER_KEY;
    }

    public void loadData() {
        loadDataForPlayer(DEFAULT_PLAYER_KEY);
    }

    public void loadDataForPlayer(String playerKey) {
        String normalizedPlayerKey = normalizePlayerKey(playerKey);

        if (!dataFile.exists()) {
            this.activePlayerKey = normalizedPlayerKey;
            applyDataToFields(new HistoricalData());
            log.info("Historical data file does not exist, starting fresh for player '{}'", normalizedPlayerKey);
            return;
        }

        try {
            Map<String, HistoricalData> allData = readAllPlayerData();
            HistoricalData playerData = allData.getOrDefault(normalizedPlayerKey, new HistoricalData());
            this.activePlayerKey = normalizedPlayerKey;
            applyDataToFields(playerData);
            log.info("Loaded historical data from file for player '{}'", normalizedPlayerKey);
        } catch (IOException e) {
            log.error("Failed to load historical data for player '{}'", normalizedPlayerKey, e);
            applyDataToFields(new HistoricalData());
        }
    }

    public void saveData() {
        saveDataForPlayer(activePlayerKey);
    }

    public void saveDataForPlayer(String playerKey) {
        String normalizedPlayerKey = normalizePlayerKey(playerKey);

        try {
            Map<String, HistoricalData> allData = readAllPlayerData();
            allData.put(normalizedPlayerKey, snapshotCurrentData());

            HistoricalDataFile fileData = new HistoricalDataFile();
            fileData.players = allData;

            try (FileWriter writer = new FileWriter(dataFile)) {
                gson.toJson(fileData, writer);
            }

            this.activePlayerKey = normalizedPlayerKey;
            log.info("Saved historical data to file for player '{}'", normalizedPlayerKey);
        } catch (IOException e) {
            log.error("Failed to save historical data for player '{}'", normalizedPlayerKey, e);
        }
    }

    public String getActivePlayerKey() {
        return activePlayerKey;
    }

    // Getters
    public Map<Integer, Map<String, ItemAggregate>> getHistoricalClaimedItemsByWave() {
        return historicalClaimedItemsByWave;
    }

    public Map<String, ItemAggregate> getHistoricalSuppliesUsed() {
        return historicalSuppliesUsed;
    }

    public Map<Integer, Long> getHistoricalClaimedByWave() {
        return historicalClaimedByWave;
    }

    public long getHistoricalTotalClaimed() {
        return historicalTotalClaimed;
    }

    public long getHistoricalClaims() {
        return historicalClaims;
    }

    public long getHistoricalDeaths() {
        return historicalDeaths;
    }

    public Map<Integer, Long> getHistoricalUnclaimedByWave() {
        return historicalUnclaimedByWave;
    }

    public Map<Integer, Map<String, ItemAggregate>> getHistoricalUnclaimedItemsByWave() {
        return historicalUnclaimedItemsByWave;
    }

    // Setters
    public void setHistoricalClaimedItemsByWave(Map<Integer, Map<String, ItemAggregate>> data) {
        this.historicalClaimedItemsByWave = data;
    }

    public void setHistoricalSuppliesUsed(Map<String, ItemAggregate> data) {
        this.historicalSuppliesUsed = data;
    }

    public void setHistoricalClaimedByWave(Map<Integer, Long> data) {
        this.historicalClaimedByWave = data;
    }

    public void setHistoricalTotalClaimed(long total) {
        this.historicalTotalClaimed = total;
    }

    public void setHistoricalClaims(long claims) {
        this.historicalClaims = claims;
    }

    public void setHistoricalDeaths(long deaths) {
        this.historicalDeaths = deaths;
    }

    public void setHistoricalUnclaimedByWave(Map<Integer, Long> data) {
        this.historicalUnclaimedByWave = data;
    }

    public void setHistoricalUnclaimedItemsByWave(Map<Integer, Map<String, ItemAggregate>> data) {
        this.historicalUnclaimedItemsByWave = data;
    }

    private String normalizePlayerKey(String playerKey) {
        if (playerKey == null) {
            return DEFAULT_PLAYER_KEY;
        }

        String normalized = playerKey.trim().toLowerCase();
        return normalized.isEmpty() ? DEFAULT_PLAYER_KEY : normalized;
    }

    private HistoricalData snapshotCurrentData() {
        HistoricalData data = new HistoricalData();
        data.historicalClaimedItemsByWave = historicalClaimedItemsByWave;
        data.historicalUnclaimedItemsByWave = historicalUnclaimedItemsByWave;
        data.historicalClaimedByWave = historicalClaimedByWave;
        data.historicalUnclaimedByWave = historicalUnclaimedByWave;
        data.historicalSuppliesUsed = historicalSuppliesUsed;
        data.historicalTotalClaimed = historicalTotalClaimed;
        data.historicalClaims = historicalClaims;
        data.historicalDeaths = historicalDeaths;
        return data;
    }

    private void applyDataToFields(HistoricalData data) {
        HistoricalData safeData = Objects.requireNonNullElseGet(data, HistoricalData::new);

        this.historicalClaimedItemsByWave = safeData.historicalClaimedItemsByWave != null
                ? safeData.historicalClaimedItemsByWave
                : new HashMap<>();
        this.historicalSuppliesUsed = safeData.historicalSuppliesUsed != null
                ? safeData.historicalSuppliesUsed
                : new HashMap<>();
        this.historicalClaimedByWave = safeData.historicalClaimedByWave != null
                ? safeData.historicalClaimedByWave
                : new HashMap<>();
        this.historicalTotalClaimed = safeData.historicalTotalClaimed;
        this.historicalClaims = safeData.historicalClaims;
        this.historicalDeaths = safeData.historicalDeaths;
        this.historicalUnclaimedByWave = safeData.historicalUnclaimedByWave != null
                ? safeData.historicalUnclaimedByWave
                : new HashMap<>();
        this.historicalUnclaimedItemsByWave = safeData.historicalUnclaimedItemsByWave != null
                ? safeData.historicalUnclaimedItemsByWave
                : new HashMap<>();
    }

    public boolean hasDataForPlayer(String playerKey) {
        try {
            Map<String, HistoricalData> allData = readAllPlayerData();
            HistoricalData data = allData.get(normalizePlayerKey(playerKey));
            if (data == null) {
                return false;
            }
            return data.historicalClaims > 0
                    || data.historicalTotalClaimed > 0
                    || (data.historicalClaimedItemsByWave != null && !data.historicalClaimedItemsByWave.isEmpty());
        } catch (IOException e) {
            return false;
        }
    }

    private Map<String, HistoricalData> readAllPlayerData() throws IOException {
        Map<String, HistoricalData> allData = new HashMap<>();

        if (!dataFile.exists()) {
            return allData;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<HistoricalDataFile>() {
            }.getType();
            HistoricalDataFile parsed = gson.fromJson(reader, type);

            if (parsed == null) {
                return allData;
            }

            if (parsed.players != null && !parsed.players.isEmpty()) {
                for (Map.Entry<String, HistoricalData> entry : parsed.players.entrySet()) {
                    allData.put(normalizePlayerKey(entry.getKey()), entry.getValue());
                }
                return allData;
            }

            // Backward compatibility: migrate legacy single-profile structure into default
            // player slot.
            if (parsed.hasLegacyData()) {
                allData.put(DEFAULT_PLAYER_KEY, parsed.toLegacyHistoricalData());
            }

            return allData;
        }
    }

    private static class HistoricalDataFile {
        Map<String, HistoricalData> players;

        // Legacy single-profile fields (pre-player separation).
        Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave;
        Map<Integer, Long> historicalClaimedByWave;
        Map<Integer, Map<String, ItemAggregate>> historicalUnclaimedItemsByWave;
        Map<Integer, Long> historicalUnclaimedByWave;
        Map<String, ItemAggregate> historicalSuppliesUsed;
        long historicalTotalClaimed;
        long historicalClaims;
        long historicalDeaths;

        private boolean hasLegacyData() {
            return historicalClaimedItemsByWave != null ||
                    historicalClaimedByWave != null ||
                    historicalUnclaimedItemsByWave != null ||
                    historicalUnclaimedByWave != null ||
                    historicalSuppliesUsed != null ||
                    historicalTotalClaimed != 0 ||
                    historicalClaims != 0 ||
                    historicalDeaths != 0;
        }

        private HistoricalData toLegacyHistoricalData() {
            HistoricalData data = new HistoricalData();
            data.historicalClaimedItemsByWave = historicalClaimedItemsByWave;
            data.historicalClaimedByWave = historicalClaimedByWave;
            data.historicalUnclaimedItemsByWave = historicalUnclaimedItemsByWave;
            data.historicalUnclaimedByWave = historicalUnclaimedByWave;
            data.historicalSuppliesUsed = historicalSuppliesUsed;
            data.historicalTotalClaimed = historicalTotalClaimed;
            data.historicalClaims = historicalClaims;
            data.historicalDeaths = historicalDeaths;
            return data;
        }
    }

    private static class HistoricalData {
        Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave;
        Map<Integer, Long> historicalClaimedByWave;
        Map<Integer, Map<String, ItemAggregate>> historicalUnclaimedItemsByWave;
        Map<Integer, Long> historicalUnclaimedByWave;
        Map<String, ItemAggregate> historicalSuppliesUsed;
        long historicalTotalClaimed;
        long historicalClaims;
        long historicalDeaths;
    }
}
