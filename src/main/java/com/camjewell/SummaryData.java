package com.camjewell;

public class SummaryData {
    private final long totalLost;
    private final long totalClaimed;
    private final int deathCount;
    private final long deathCosts;
    private final long supplyCosts;
    private final long profitLoss;

    public SummaryData(long totalLost,
            long totalClaimed,
            int deathCount,
            long deathCosts,
            long supplyCosts,
            long profitLoss) {
        this.totalLost = totalLost;
        this.totalClaimed = totalClaimed;
        this.deathCount = deathCount;
        this.deathCosts = deathCosts;
        this.supplyCosts = supplyCosts;
        this.profitLoss = profitLoss;
    }

    public long getTotalLost() {
        return totalLost;
    }

    public long getTotalClaimed() {
        return totalClaimed;
    }

    public int getDeathCount() {
        return deathCount;
    }

    public long getDeathCosts() {
        return deathCosts;
    }

    public long getSupplyCosts() {
        return supplyCosts;
    }

    public long getProfitLoss() {
        return profitLoss;
    }
}
