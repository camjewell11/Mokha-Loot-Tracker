package com.camjewell;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import com.camjewell.MokhaLootTrackerPlugin.ItemAggregate;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class HistoricalDataManager {
    private static final String MOKHALOOT_DIR = "mokhaloot";
    private static final String DATA_FILE = "historical-data.json";

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
    }

    public void loadData() {
        if (!dataFile.exists()) {
            log.info("Historical data file does not exist, starting fresh");
            return;
        }

        try (FileReader reader = new FileReader(dataFile)) {
            Type type = new TypeToken<HistoricalData>() {
            }.getType();
            HistoricalData data = gson.fromJson(reader, type);

            if (data != null) {
                this.historicalClaimedItemsByWave = data.historicalClaimedItemsByWave != null
                        ? data.historicalClaimedItemsByWave
                        : new HashMap<>();
                this.historicalSuppliesUsed = data.historicalSuppliesUsed != null ? data.historicalSuppliesUsed
                        : new HashMap<>();
                this.historicalClaimedByWave = data.historicalClaimedByWave != null ? data.historicalClaimedByWave
                        : new HashMap<>();
                this.historicalTotalClaimed = data.historicalTotalClaimed;
                this.historicalClaims = data.historicalClaims;
                this.historicalDeaths = data.historicalDeaths;

                this.historicalUnclaimedByWave = data.historicalUnclaimedByWave != null ? data.historicalUnclaimedByWave
                        : new HashMap<>();
                this.historicalUnclaimedItemsByWave = data.historicalUnclaimedItemsByWave != null
                        ? data.historicalUnclaimedItemsByWave
                        : new HashMap<>();

                log.info("Loaded historical data from file");
            }
        } catch (IOException e) {
            log.error("Failed to load historical data", e);
        }
    }

    public void saveData() {
        try (FileWriter writer = new FileWriter(dataFile)) {
            HistoricalData data = new HistoricalData();
            data.historicalClaimedItemsByWave = this.historicalClaimedItemsByWave;
            data.historicalUnclaimedItemsByWave = this.historicalUnclaimedItemsByWave;
            data.historicalClaimedByWave = this.historicalClaimedByWave;
            data.historicalUnclaimedByWave = this.historicalUnclaimedByWave;
            data.historicalSuppliesUsed = this.historicalSuppliesUsed;
            data.historicalTotalClaimed = this.historicalTotalClaimed;
            data.historicalClaims = this.historicalClaims;
            data.historicalDeaths = this.historicalDeaths;

            gson.toJson(data, writer);
            log.info("Saved historical data to file");
        } catch (IOException e) {
            log.error("Failed to save historical data", e);
        }
    }

    public void migrateFromConfigManager(String claimedItemsJson, String suppliesUsedJson,
            String claimedByWaveJson, long totalClaimed,
            String unclaimedByWaveJson, String unclaimedItemsByWaveJson) {
        // Merge claimed items
        if (claimedItemsJson != null && !claimedItemsJson.isEmpty() && !claimedItemsJson.equals("{}")) {
            Type type = new TypeToken<Map<Integer, Map<String, ItemAggregate>>>() {
            }.getType();
            Map<Integer, Map<String, ItemAggregate>> items = gson.fromJson(claimedItemsJson, type);
            if (items != null) {
                if (this.historicalClaimedItemsByWave == null)
                    this.historicalClaimedItemsByWave = new HashMap<>();
                this.historicalClaimedItemsByWave.putAll(items);
            }
        }
        // Merge supplies used
        if (suppliesUsedJson != null && !suppliesUsedJson.isEmpty() && !suppliesUsedJson.equals("{}")) {
            Type type = new TypeToken<Map<String, ItemAggregate>>() {
            }.getType();
            Map<String, ItemAggregate> supplies = gson.fromJson(suppliesUsedJson, type);
            if (supplies != null) {
                if (this.historicalSuppliesUsed == null)
                    this.historicalSuppliesUsed = new HashMap<>();
                this.historicalSuppliesUsed.putAll(supplies);
            }
        }
        // Merge claimed by wave
        if (claimedByWaveJson != null && !claimedByWaveJson.isEmpty() && !claimedByWaveJson.equals("{}")) {
            Type type = new TypeToken<Map<Integer, Long>>() {
            }.getType();
            Map<Integer, Long> byWave = gson.fromJson(claimedByWaveJson, type);
            if (byWave != null) {
                if (this.historicalClaimedByWave == null)
                    this.historicalClaimedByWave = new HashMap<>();
                this.historicalClaimedByWave.putAll(byWave);
            }
        }
        // Merge unclaimed by wave
        if (unclaimedByWaveJson != null && !unclaimedByWaveJson.isEmpty() && !unclaimedByWaveJson.equals("{}")) {
            Type type = new TypeToken<Map<Integer, Long>>() {
            }.getType();
            Map<Integer, Long> unclaimedByWave = gson.fromJson(unclaimedByWaveJson, type);
            if (unclaimedByWave != null) {
                if (this.historicalUnclaimedByWave == null)
                    this.historicalUnclaimedByWave = new HashMap<>();
                this.historicalUnclaimedByWave.putAll(unclaimedByWave);
            }
        }
        // Merge unclaimed items by wave
        if (unclaimedItemsByWaveJson != null && !unclaimedItemsByWaveJson.isEmpty()
                && !unclaimedItemsByWaveJson.equals("{}")) {
            Type type = new TypeToken<Map<Integer, Map<String, ItemAggregate>>>() {
            }.getType();
            Map<Integer, Map<String, ItemAggregate>> unclaimedItemsByWave = gson.fromJson(unclaimedItemsByWaveJson,
                    type);
            if (unclaimedItemsByWave != null) {
                if (this.historicalUnclaimedItemsByWave == null)
                    this.historicalUnclaimedItemsByWave = new HashMap<>();
                this.historicalUnclaimedItemsByWave.putAll(unclaimedItemsByWave);
            }
        }
        // Only overwrite total claimed if config value is nonzero
        if (totalClaimed > 0) {
            this.historicalTotalClaimed = totalClaimed;
        }
        saveData();
        log.info("Migrated historical data from ConfigManager to file (merge mode)");
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
