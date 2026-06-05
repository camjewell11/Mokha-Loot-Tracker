package com.camjewell;

import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;

final class PerformanceTracker {
    private int prayerUsed;
    private int prayerRegained;
    private int hpLost;
    private int hpRegained;
    private int specialAttackUses;
    private int venomApplications;

    private boolean wasVenomedLastTick;
    private int lastPrayerPoints = -1;
    private int lastHitpoints = -1;
    private int lastSpecialAttackEnergy = -1;
    private int suppressConsumableHealTicksRemaining = 0;
    private int suppressConsumableHpLossTicksRemaining = 0;
    private int suppressConsumablePrayerRegainTicksRemaining = 0;
    private boolean dirty;

    void resetForRunStart(int prayerPoints, int hitpoints, boolean currentlyVenomed, int specialAttackEnergy) {
        prayerUsed = 0;
        prayerRegained = 0;
        hpLost = 0;
        hpRegained = 0;
        specialAttackUses = 0;
        venomApplications = 0;

        lastPrayerPoints = prayerPoints;
        lastHitpoints = hitpoints;
        lastSpecialAttackEnergy = specialAttackEnergy;
        suppressConsumableHealTicksRemaining = 0;
        suppressConsumableHpLossTicksRemaining = 0;
        suppressConsumablePrayerRegainTicksRemaining = 0;
        wasVenomedLastTick = currentlyVenomed;
        dirty = false;
    }

    void reset() {
        prayerUsed = 0;
        prayerRegained = 0;
        hpLost = 0;
        hpRegained = 0;
        specialAttackUses = 0;
        venomApplications = 0;

        wasVenomedLastTick = false;
        lastPrayerPoints = -1;
        lastHitpoints = -1;
        lastSpecialAttackEnergy = -1;
        suppressConsumableHealTicksRemaining = 0;
        suppressConsumableHpLossTicksRemaining = 0;
        suppressConsumablePrayerRegainTicksRemaining = 0;
        dirty = false;
    }

    void onTick() {
        if (suppressConsumableHealTicksRemaining > 0) {
            suppressConsumableHealTicksRemaining--;
        }
        if (suppressConsumableHpLossTicksRemaining > 0) {
            suppressConsumableHpLossTicksRemaining--;
        }
        if (suppressConsumablePrayerRegainTicksRemaining > 0) {
            suppressConsumablePrayerRegainTicksRemaining--;
        }
    }

    void markConsumableHpChangeExpected() {
        // Keep this short to cover immediate HP/prayer changes from eating/drinking.
        suppressConsumableHealTicksRemaining = 3;
        suppressConsumableHpLossTicksRemaining = 2;
        suppressConsumablePrayerRegainTicksRemaining = 3;
    }

    void onVenomAndSpecialTick(boolean currentlyVenomed, int currentSpecialAttackEnergy) {
        if (currentlyVenomed && !wasVenomedLastTick) {
            venomApplications++;
            dirty = true;
        }
        wasVenomedLastTick = currentlyVenomed;

        if (lastSpecialAttackEnergy >= 0 && currentSpecialAttackEnergy < lastSpecialAttackEnergy) {
            specialAttackUses++;
            dirty = true;
        }
        lastSpecialAttackEnergy = currentSpecialAttackEnergy;
    }

    void syncCurrentState(int prayerPoints, int hitpoints, boolean currentlyVenomed, int specialAttackEnergy) {
        lastPrayerPoints = prayerPoints;
        lastHitpoints = hitpoints;
        wasVenomedLastTick = currentlyVenomed;
        lastSpecialAttackEnergy = specialAttackEnergy;
        suppressConsumableHealTicksRemaining = 0;
        suppressConsumableHpLossTicksRemaining = 0;
        suppressConsumablePrayerRegainTicksRemaining = 0;
    }

    void onStatChanged(StatChanged event) {
        if (event.getSkill() == Skill.PRAYER) {
            int current = event.getBoostedLevel();
            if (lastPrayerPoints >= 0) {
                if (current < lastPrayerPoints) {
                    prayerUsed += lastPrayerPoints - current;
                    dirty = true;
                } else if (current > lastPrayerPoints) {
                    if (suppressConsumablePrayerRegainTicksRemaining > 0) {
                        suppressConsumablePrayerRegainTicksRemaining = 0;
                        lastPrayerPoints = current;
                        return;
                    }
                    prayerRegained += current - lastPrayerPoints;
                    dirty = true;
                }
            }
            lastPrayerPoints = current;
            return;
        }

        if (event.getSkill() == Skill.HITPOINTS) {
            int current = event.getBoostedLevel();
            if (lastHitpoints >= 0) {
                if (current < lastHitpoints) {
                    if (suppressConsumableHpLossTicksRemaining > 0) {
                        suppressConsumableHpLossTicksRemaining = 0;
                        lastHitpoints = current;
                        return;
                    }
                    hpLost += lastHitpoints - current;
                    dirty = true;
                } else if (current > lastHitpoints) {
                    int hpDelta = current - lastHitpoints;
                    if (suppressConsumableHealTicksRemaining > 0) {
                        suppressConsumableHealTicksRemaining = 0;
                        lastHitpoints = current;
                        return;
                    }
                    // Ignore passive +1 regen ticks to keep this metric action-focused.
                    if (hpDelta != 1) {
                        hpRegained += hpDelta;
                        dirty = true;
                    }
                }
            }
            lastHitpoints = current;
        }
    }

    boolean consumeDirty() {
        boolean wasDirty = dirty;
        dirty = false;
        return wasDirty;
    }

    PerformanceSnapshot snapshot() {
        return new PerformanceSnapshot(prayerUsed, prayerRegained, hpLost, hpRegained, specialAttackUses, venomApplications);
    }
}
