package com.jiuzhuan.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BalanceMathTest {
    @Test void curvesMatchTheApprovedMilestones() {
        assertEquals(0, BalanceMath.curve(1.5, 0, 1000));
        assertEquals(1.35, BalanceMath.curve(1.5, 1000, 1000), 1e-12);
        assertEquals(0.27, BalanceMath.curve(0.3, 1500, 1500), 1e-12);
        assertEquals(0.27, BalanceMath.curve(0.3, 100, 100), 1e-12);
        assertEquals(0.297, BalanceMath.curve(0.3, 200, 100), 1e-12);
        assertEquals(0.27, BalanceMath.curve(0.3, 300, 300), 1e-12);
        assertEquals(0.0617015295827155, BalanceMath.curve(0.3, 10, 100), 1e-12);
    }

    @Test void progressionIsMonotoneFiniteAndStrictlyBelowItsCap() {
        double previous = 0;
        for (int progress = 0; progress < 10000; progress++) {
            double value = BalanceMath.curve(0.3, progress, 100);
            assertTrue(value >= previous);
            assertTrue(value < 0.3);
            previous = value;
        }
        assertTrue(BalanceMath.curve(0.3, Integer.MAX_VALUE, 100) < 0.3);
        assertTrue(Double.isFinite(BalanceMath.curve(0.3, Double.POSITIVE_INFINITY, 100)));
        assertEquals(0, BalanceMath.curve(0.3, -1, 100));
        assertEquals(0, BalanceMath.curve(0.3, 100, 0));
        assertEquals(0, BalanceMath.curve(Double.NaN, 100, 100));
    }

    @Test void countersDoNotWrapOrLoseProgressOnNegativeInput() {
        assertEquals(Integer.MAX_VALUE, BalanceMath.saturatingAdd(Integer.MAX_VALUE - 1, 100));
        assertEquals(10, BalanceMath.saturatingAdd(10, -2));
        assertEquals(Long.MAX_VALUE, BalanceMath.saturatingAdd(Long.MAX_VALUE - 1, 100L));
        assertEquals(10L, BalanceMath.saturatingAdd(10L, -2L));
    }

    @Test void trainingRequiresMeaningfulNonlethalHealthDamage() {
        assertFalse(BalanceMath.isTrainingHit(0, 20, 20, 1, 0.01));
        assertFalse(BalanceMath.isTrainingHit(0.99, 20, 20, 1, 0.01));
        assertTrue(BalanceMath.isTrainingHit(1, 20, 20, 1, 0.01));
        assertFalse(BalanceMath.isTrainingHit(1, 1000, 1000, 1, 0.01));
        assertTrue(BalanceMath.isTrainingHit(10, 1000, 1000, 1, 0.01));
        assertFalse(BalanceMath.isTrainingHit(10, 1000, 10, 1, 0.01));
        assertFalse(BalanceMath.isTrainingHit(Double.NaN, 20, 20, 1, 0.01));
    }

    @Test void legacyMigrationPreservesOnlyEarnedProgressAndRemainingCooldownRatio() {
        assertEquals(1800, BalanceMath.migrateCooldown(120000, 100000, 40000, 3600));
        assertEquals(3600, BalanceMath.migrateCooldown(Long.MAX_VALUE, 100000, 40000, 3600));
        assertEquals(0, BalanceMath.migrateCooldown(99000, 100000, 40000, 3600));
        assertEquals(500, BalanceMath.migrateEffectExposure(5, 0));
        assertEquals(342, BalanceMath.migrateEffectExposure(3, 42));
        assertEquals(0, BalanceMath.migrateEffectExposure(-1, -1));
    }

    @Test void finiteEffectsRetainAtLeastSeventyPercentOfTheirDuration() {
        assertEquals(140, BalanceMath.shortenDuration(200, 0.3));
        assertEquals(160, BalanceMath.shortenDuration(200, 0.2));
        assertEquals(1, BalanceMath.shortenDuration(1, 0.3));
        assertEquals(8, BalanceMath.shortenDuration(11, 0.3));
        assertEquals(-1, BalanceMath.shortenDuration(-1, 0.3));
        assertEquals(0, BalanceMath.shortenDuration(0, 0.3));
        assertEquals(140, BalanceMath.shortenDuration(200, 1));
        assertEquals(200, BalanceMath.shortenDuration(200, Double.NaN));
    }
}
