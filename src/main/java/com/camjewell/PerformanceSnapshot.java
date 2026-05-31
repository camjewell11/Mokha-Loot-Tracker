package com.camjewell;

final class PerformanceSnapshot {
    private static final PerformanceSnapshot EMPTY = new PerformanceSnapshot(0, 0, 0, 0, 0);

    private final int prayerUsed;
    private final int hpLost;
    private final int hpRegained;
    private final int specialAttackUses;
    private final int venomApplications;

    PerformanceSnapshot(int prayerUsed, int hpLost, int hpRegained, int specialAttackUses, int venomApplications) {
        this.prayerUsed = prayerUsed;
        this.hpLost = hpLost;
        this.hpRegained = hpRegained;
        this.specialAttackUses = specialAttackUses;
        this.venomApplications = venomApplications;
    }

    static PerformanceSnapshot empty() {
        return EMPTY;
    }

    int getPrayerUsed() {
        return prayerUsed;
    }

    int getHpLost() {
        return hpLost;
    }

    int getHpRegained() {
        return hpRegained;
    }

    int getSpecialAttackUses() {
        return specialAttackUses;
    }

    int getVenomApplications() {
        return venomApplications;
    }
}
