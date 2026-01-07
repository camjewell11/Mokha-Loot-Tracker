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
    private Map<String, ItemAggregate> historicalSuppliesUsed;
    private Map<Integer, Long> historicalClaimedByWave;
    private long historicalTotalClaimed;

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
            data.historicalSuppliesUsed = this.historicalSuppliesUsed;
            data.historicalClaimedByWave = this.historicalClaimedByWave;
            data.historicalTotalClaimed = this.historicalTotalClaimed;

            gson.toJson(data, writer);
            log.info("Saved historical data to file");
        } catch (IOException e) {
            log.error("Failed to save historical data", e);
        }
    }

    public void migrateFromConfigManager(String claimedItemsJson, String suppliesUsedJson,
            String claimedByWaveJson, long totalClaimed) {
        if (claimedItemsJson != null && !claimedItemsJson.isEmpty()) {
            Type type = new TypeToken<Map<Integer, Map<String, ItemAggregate>>>() {
            }.getType();
            Map<Integer, Map<String, ItemAggregate>> items = gson.fromJson(claimedItemsJson, type);
            if (items != null) {
                this.historicalClaimedItemsByWave = items;
            }
        }

        if (suppliesUsedJson != null && !suppliesUsedJson.isEmpty()) {
            Type type = new TypeToken<Map<String, ItemAggregate>>() {
            }.getType();
            Map<String, ItemAggregate> supplies = gson.fromJson(suppliesUsedJson, type);
            if (supplies != null) {
                this.historicalSuppliesUsed = supplies;
            }
        }

        if (claimedByWaveJson != null && !claimedByWaveJson.isEmpty()) {
            Type type = new TypeToken<Map<Integer, Long>>() {
            }.getType();
            Map<Integer, Long> byWave = gson.fromJson(claimedByWaveJson, type);
            if (byWave != null) {
                this.historicalClaimedByWave = byWave;
            }
        }

        this.historicalTotalClaimed = totalClaimed;

        saveData();
        log.info("Migrated historical data from ConfigManager to file");
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

    private static class HistoricalData {
        Map<Integer, Map<String, ItemAggregate>> historicalClaimedItemsByWave;
        Map<String, ItemAggregate> historicalSuppliesUsed;
        Map<Integer, Long> historicalClaimedByWave;
        long historicalTotalClaimed;
    }
}
