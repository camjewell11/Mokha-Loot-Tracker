package com.camjewell;

import java.util.List;
import java.util.Map;

/**
 * Immutable container for all data the panel needs to render.
 */
public class PanelData {
    private final SummaryData summary;
    private final List<WaveData> lostWaves;
    private final List<WaveData> claimedWaves;
    private final CurrentRunData currentRun;
    private final SuppliesData supplies;
    private final boolean showSuppliesUsed;
    private final Map<Integer, Long> itemPriceCache;

    public PanelData(SummaryData summary,
            List<WaveData> lostWaves,
            List<WaveData> claimedWaves,
            CurrentRunData currentRun,
            SuppliesData supplies,
            boolean showSuppliesUsed,
            Map<Integer, Long> itemPriceCache) {
        this.summary = summary;
        this.lostWaves = lostWaves;
        this.claimedWaves = claimedWaves;
        this.currentRun = currentRun;
        this.supplies = supplies;
        this.showSuppliesUsed = showSuppliesUsed;
        this.itemPriceCache = itemPriceCache;
    }

    public SummaryData getSummary() {
        return summary;
    }

    public List<WaveData> getLostWaves() {
        return lostWaves;
    }

    public List<WaveData> getClaimedWaves() {
        return claimedWaves;
    }

    public CurrentRunData getCurrentRun() {
        return currentRun;
    }

    public SuppliesData getSupplies() {
        return supplies;
    }

    public boolean isShowSuppliesUsed() {
        return showSuppliesUsed;
    }

    public Map<Integer, Long> getItemPriceCache() {
        return itemPriceCache;
    }
}
