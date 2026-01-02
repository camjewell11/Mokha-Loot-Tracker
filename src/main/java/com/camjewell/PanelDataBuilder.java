package com.camjewell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PanelDataBuilder {
    private static final Logger log = LoggerFactory.getLogger(PanelDataBuilder.class);

    private PanelDataBuilder() {
    }

    static PanelData build(MokhaLootTrackerPlugin plugin) {
        int minValue = plugin.config.minItemValueThreshold();
        boolean showSuppliesUsed = plugin.config.showSuppliesUsedBeta();

        Map<Integer, Long> itemPriceCache = new HashMap<>();

        long totalLost = plugin.getTotalLostValue();
        int deaths = plugin.getTimesDied();
        long totalClaimed = plugin.getTotalClaimedValue();
        long deathCosts = plugin.getTotalDeathCosts();
        long totalSuppliesCost = plugin.getTotalSuppliesCost();
        long profitLoss = totalClaimed - totalSuppliesCost - deathCosts;

        List<WaveData> lostWaves = new ArrayList<>();
        List<WaveData> claimedWaves = new ArrayList<>();

        for (int wave = 1; wave <= 9; wave++) {
            List<LootItem> lostAll = plugin.getWaveLostItems(wave);
            List<LootItem> lostDisplay = (minValue > 0) ? plugin.filterItemsByValue(lostAll) : lostAll;
            cachePrices(itemPriceCache, plugin, lostDisplay);
            lostWaves.add(new WaveData(wave,
                    plugin.getWaveLostValue(wave),
                    plugin.getWaveLostFullValue(wave),
                    lostDisplay));

            List<LootItem> claimedAll = plugin.getWaveClaimedItems(wave);
            List<LootItem> claimedDisplay = (minValue > 0) ? plugin.filterItemsByValue(claimedAll) : claimedAll;
            cachePrices(itemPriceCache, plugin, claimedDisplay);
            claimedWaves.add(new WaveData(wave,
                    plugin.getWaveClaimedValue(wave),
                    plugin.getWaveClaimedFullValue(wave),
                    claimedDisplay));
        }

        List<LootItem> allCurrentRunItems = plugin.getCurrentLootItems();
        List<LootItem> currentRunItems = (minValue > 0) ? plugin.filterItemsByValue(allCurrentRunItems)
                : allCurrentRunItems;
        cachePrices(itemPriceCache, plugin, currentRunItems);
        long currentRunValue = plugin.getFilteredLootValue(allCurrentRunItems);
        long currentRunFullValue = plugin.getTotalLootValue(allCurrentRunItems);
        CurrentRunData currentRun = new CurrentRunData(currentRunValue, currentRunFullValue, currentRunItems);

        List<LootItem> suppliesUsedItems = plugin.getTotalSuppliesItems();
        List<LootItem> liveSuppliesUsedItems = plugin.getLiveSuppliesUsedItems();
        cachePrices(itemPriceCache, plugin, suppliesUsedItems);
        cachePrices(itemPriceCache, plugin, liveSuppliesUsedItems);

        if (plugin.config.debugItemValueLogging() && currentRunItems != null) {
            log.info("[MokhaLootTracker] Debug: Current Run Items (filtered):");
            for (LootItem item : currentRunItems) {
                long pricePerItem = itemPriceCache.getOrDefault(item.getId(), 0L);
                long value = pricePerItem * item.getQuantity();
                log.info("  " + (item.getName() != null ? item.getName() : ("Item " + item.getId())) +
                        " x" + item.getQuantity() + " @ " + pricePerItem + "ea = " + value + " gp");
            }
            if (minValue > 0 && allCurrentRunItems != null) {
                log.info("[MokhaLootTracker] Debug: Current Run Items (excluded by threshold < " + minValue + "):");
                for (LootItem item : allCurrentRunItems) {
                    long pricePerItem = itemPriceCache.getOrDefault(item.getId(), 0L);
                    if (pricePerItem < minValue) {
                        long value = pricePerItem * item.getQuantity();
                        log.info("  " + (item.getName() != null ? item.getName() : ("Item " + item.getId())) +
                                " x" + item.getQuantity() + " @ " + pricePerItem + "ea = " + value + " gp");
                    }
                }
            }
        }

        SummaryData summary = new SummaryData(totalLost, totalClaimed, deaths, deathCosts, totalSuppliesCost,
                profitLoss);
        SuppliesData supplies = new SuppliesData(plugin.getLiveSuppliesUsedValue(), totalSuppliesCost,
                liveSuppliesUsedItems, suppliesUsedItems);

        return new PanelData(summary, lostWaves, claimedWaves, currentRun, supplies, showSuppliesUsed, itemPriceCache);
    }

    private static void cachePrices(Map<Integer, Long> cache, MokhaLootTrackerPlugin plugin, List<LootItem> items) {
        if (items == null) {
            return;
        }
        for (LootItem item : items) {
            if (item.getId() > 0 && !cache.containsKey(item.getId())) {
                cache.put(item.getId(), plugin.getItemValue(item.getId(), 1));
            }
        }
    }
}
