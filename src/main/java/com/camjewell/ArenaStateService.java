package com.camjewell;

import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

class ArenaStateService {
    static final class ArenaStateSnapshot {
        private final boolean inMokhaArena;
        private final boolean isDead;
        private final int currentWaveNumber;
        private final boolean bossSeenThisRun;
        private final boolean bossDefeatedThisWave;
        private final boolean bossWasEverPresentThisWave;
        private final boolean lastDescendClickJustHappened;
        private final long lastArenaExitTime;
        private final int ticksOutsideArenaBounds;

        ArenaStateSnapshot(
                boolean inMokhaArena,
                boolean isDead,
                int currentWaveNumber,
                boolean bossSeenThisRun,
                boolean bossDefeatedThisWave,
                boolean bossWasEverPresentThisWave,
                boolean lastDescendClickJustHappened,
                long lastArenaExitTime,
                int ticksOutsideArenaBounds) {
            this.inMokhaArena = inMokhaArena;
            this.isDead = isDead;
            this.currentWaveNumber = currentWaveNumber;
            this.bossSeenThisRun = bossSeenThisRun;
            this.bossDefeatedThisWave = bossDefeatedThisWave;
            this.bossWasEverPresentThisWave = bossWasEverPresentThisWave;
            this.lastDescendClickJustHappened = lastDescendClickJustHappened;
            this.lastArenaExitTime = lastArenaExitTime;
            this.ticksOutsideArenaBounds = ticksOutsideArenaBounds;
        }

        boolean isInMokhaArena() {
            return inMokhaArena;
        }

        boolean isDead() {
            return isDead;
        }

        int getCurrentWaveNumber() {
            return currentWaveNumber;
        }

        boolean isBossSeenThisRun() {
            return bossSeenThisRun;
        }

        boolean isBossDefeatedThisWave() {
            return bossDefeatedThisWave;
        }

        boolean isBossWasEverPresentThisWave() {
            return bossWasEverPresentThisWave;
        }

        boolean isLastDescendClickJustHappened() {
            return lastDescendClickJustHappened;
        }

        long getLastArenaExitTime() {
            return lastArenaExitTime;
        }

        int getTicksOutsideArenaBounds() {
            return ticksOutsideArenaBounds;
        }
    }

    ArenaStateSnapshot createArenaEntryState() {
        return new ArenaStateSnapshot(
                true,
                false,
                1,
                false,
                false,
                false,
                false,
                0L,
                0);
    }

    ArenaStateSnapshot createArenaExitState() {
        return new ArenaStateSnapshot(
                false,
                false,
                1,
                false,
                false,
                false,
                false,
                System.currentTimeMillis(),
                0);
    }

    void clearRunTrackingCollections(
            Map<Integer, Integer> lastCombinedSnapshot,
            Map<?, ?> lootByWave,
            Map<Integer, Integer> previousLootSnapshot,
            Map<Integer, Integer> totalSuppliesConsumed,
            Map<Integer, Integer> initialSupplySnapshot) {
        lastCombinedSnapshot.clear();
        lootByWave.clear();
        previousLootSnapshot.clear();
        totalSuppliesConsumed.clear();
        initialSupplySnapshot.clear();
    }

    void archiveConsumedSupplies(
            Map<Integer, Integer> totalSuppliesConsumed,
            Map<String, ItemAggregate> historicalSuppliesUsed,
            IntFunction<String> getBasePotionNameByItemId,
            IntUnaryOperator getPricePerDoseByItemId) {
        for (Map.Entry<Integer, Integer> entry : totalSuppliesConsumed.entrySet()) {
            int itemId = entry.getKey();
            int quantity = entry.getValue();
            String itemName = getBasePotionNameByItemId.apply(itemId);
            int pricePerItem = getPricePerDoseByItemId.applyAsInt(itemId);

            if (historicalSuppliesUsed.containsKey(itemName)) {
                historicalSuppliesUsed.get(itemName).add(quantity, pricePerItem);
            } else {
                historicalSuppliesUsed.put(itemName,
                        new ItemAggregate(itemName, quantity, pricePerItem));
            }
        }
    }
}
