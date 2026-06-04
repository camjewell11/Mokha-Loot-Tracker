package com.camjewell;

import java.util.Map;
import java.util.function.IntToDoubleFunction;

final class DrynessMath {
    static final double UNIQUE_CHANCE_DELVE_2 = 1.0 / 2500.0;
    static final double UNIQUE_CHANCE_DELVE_3 = 1.0 / 1000.0;
    static final double UNIQUE_CHANCE_DELVE_4 = 1.0 / 450.0;
    static final double UNIQUE_CHANCE_DELVE_5 = 1.0 / 270.0;
    static final double UNIQUE_CHANCE_DELVE_6 = 1.0 / 255.0;
    static final double UNIQUE_CHANCE_DELVE_7 = 1.0 / 240.0;
    static final double UNIQUE_CHANCE_DELVE_8 = 1.0 / 210.0;
    static final double UNIQUE_CHANCE_DELVE_9_PLUS = 1.0 / 180.0;
    static final double UNIQUE_CHANCE_STANDARD_DELVE_3 = 1.0 / 2000.0;
    static final double UNIQUE_CHANCE_STANDARD_DELVE_4 = 1.0 / 1350.0;
    static final double UNIQUE_CHANCE_STANDARD_DELVE_5 = 1.0 / 810.0;
    static final double UNIQUE_CHANCE_STANDARD_DELVE_6 = 1.0 / 765.0;
    static final double UNIQUE_CHANCE_STANDARD_DELVE_7 = 1.0 / 720.0;
    static final double UNIQUE_CHANCE_STANDARD_DELVE_8 = 1.0 / 630.0;
    static final double UNIQUE_CHANCE_STANDARD_DELVE_9_PLUS = 1.0 / 540.0;
    static final double UNIQUE_CHANCE_DOM_DELVE_6 = 1.0 / 1000.0;
    static final double UNIQUE_CHANCE_DOM_DELVE_7 = 1.0 / 750.0;
    static final double UNIQUE_CHANCE_DOM_DELVE_8 = 1.0 / 500.0;
    static final double UNIQUE_CHANCE_DOM_DELVE_9_PLUS = 1.0 / 250.0;

    private DrynessMath() {
    }

    static double getOverallUniqueChanceForDelve(int delve) {
        switch (delve) {
            case 2: return UNIQUE_CHANCE_DELVE_2;
            case 3: return UNIQUE_CHANCE_DELVE_3;
            case 4: return UNIQUE_CHANCE_DELVE_4;
            case 5: return UNIQUE_CHANCE_DELVE_5;
            case 6: return UNIQUE_CHANCE_DELVE_6;
            case 7: return UNIQUE_CHANCE_DELVE_7;
            case 8: return UNIQUE_CHANCE_DELVE_8;
            default: return UNIQUE_CHANCE_DELVE_9_PLUS;
        }
    }

    static double getClothUniqueChanceForDelve(int delve) {
        if (delve <= 2) {
            return UNIQUE_CHANCE_DELVE_2;
        }
        return getStandardUniqueChanceForDelve(delve);
    }

    static double getStandardUniqueChanceForDelve(int delve) {
        switch (delve) {
            case 3: return UNIQUE_CHANCE_STANDARD_DELVE_3;
            case 4: return UNIQUE_CHANCE_STANDARD_DELVE_4;
            case 5: return UNIQUE_CHANCE_STANDARD_DELVE_5;
            case 6: return UNIQUE_CHANCE_STANDARD_DELVE_6;
            case 7: return UNIQUE_CHANCE_STANDARD_DELVE_7;
            case 8: return UNIQUE_CHANCE_STANDARD_DELVE_8;
            default: return UNIQUE_CHANCE_STANDARD_DELVE_9_PLUS;
        }
    }

    static double getDomUniqueChanceForDelve(int delve) {
        switch (delve) {
            case 6: return UNIQUE_CHANCE_DOM_DELVE_6;
            case 7: return UNIQUE_CHANCE_DOM_DELVE_7;
            case 8: return UNIQUE_CHANCE_DOM_DELVE_8;
            default: return UNIQUE_CHANCE_DOM_DELVE_9_PLUS;
        }
    }

    static double calculateCumulativeUniqueChancePercent(int currentDepth) {
        return calculateCumulativeUniqueChancePercent(2, currentDepth, DrynessMath::getOverallUniqueChanceForDelve);
    }

    static double calculateCumulativeUniqueChancePercent(int unlockDepth, int currentDepth,
            IntToDoubleFunction chanceByDelve) {
        if (currentDepth < unlockDepth) {
            return 0;
        }
        double chanceNoUnique = 1.0;
        for (int delve = unlockDepth; delve <= currentDepth; delve++) {
            chanceNoUnique *= (1.0 - chanceByDelve.applyAsDouble(delve));
        }
        return (1.0 - chanceNoUnique) * 100.0;
    }

    static long calculateTotalHistoricalWaveRolls(Map<Integer, Long> effectiveRunsByWave) {
        long total = 0;
        for (long count : effectiveRunsByWave.values()) {
            total += count;
        }
        return total;
    }

    static ExpectedDropsByItem calculateHistoricalExpectedDropsByItem(Map<Integer, Long> effectiveRunsByWave) {
        ExpectedDropsByItem expected = new ExpectedDropsByItem();
        for (Map.Entry<Integer, Long> entry : effectiveRunsByWave.entrySet()) {
            int wave = entry.getKey();
            long completedCount = entry.getValue();
            if (wave < 2 || completedCount <= 0) {
                continue;
            }
            expected.cloth += completedCount * getClothUniqueChanceForDelve(wave);
            if (wave >= 3) {
                expected.eye += completedCount * getStandardUniqueChanceForDelve(wave);
            }
            if (wave >= 4) {
                expected.treads += completedCount * getStandardUniqueChanceForDelve(wave);
            }
            if (wave >= 6) {
                expected.dom += completedCount * getDomUniqueChanceForDelve(wave);
            }
        }
        return expected;
    }

    static double getExpectedUniqueDropsPerCompletionForWave(int wave) {
        if (wave < 2) {
            return 0.0;
        }
        double expected = getClothUniqueChanceForDelve(wave);
        if (wave == 3) {
            expected += getStandardUniqueChanceForDelve(wave);
        } else if (wave >= 4) {
            expected += getStandardUniqueChanceForDelve(wave) * 2.0;
        }
        if (wave >= 6) {
            expected += getDomUniqueChanceForDelve(wave);
        }
        return expected;
    }
}
