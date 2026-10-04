package com.jiuzhuan.util;

/** Pure balance arithmetic shared by gameplay, migration and tests. */
public final class BalanceMath {
    private BalanceMath() {}

    /** cap * (1 - 10^(-progress / progressTo90)); never reaches or exceeds cap. */
    public static double curve(double cap, double progress, double progressTo90) {
        if (!Double.isFinite(cap) || cap <= 0 || Double.isNaN(progress) || progress <= 0
                || !Double.isFinite(progressTo90) || progressTo90 <= 0) return 0;
        double result = cap * -Math.expm1(-Math.log(10.0) * progress / progressTo90);
        return Math.max(0, Math.min(Math.nextDown(cap), result));
    }

    public static int saturatingAdd(int current, int increment) {
        return (int) Math.min(Integer.MAX_VALUE, (long) Math.max(0, current) + Math.max(0, increment));
    }

    public static long saturatingAdd(long current, long increment) {
        long base = Math.max(0, current);
        long add = Math.max(0, increment);
        return add > Long.MAX_VALUE - base ? Long.MAX_VALUE : base + add;
    }

    public static boolean isTrainingHit(double finalDamage, double maxHealth, double healthBefore,
                                        double minDamage, double minHealthRatio) {
        return Double.isFinite(finalDamage) && Double.isFinite(maxHealth) && Double.isFinite(healthBefore)
                && maxHealth > 0 && healthBefore > 0 && finalDamage > 0 && finalDamage < healthBefore
                && finalDamage >= Math.max(minDamage, maxHealth * minHealthRatio);
    }

    public static long migrateCooldown(long oldEndMillis, long nowMillis, long oldDurationMillis,
                                        long newDurationTicks) {
        if (oldEndMillis <= nowMillis || oldDurationMillis <= 0 || newDurationTicks <= 0) return 0;
        double remaining = Math.min(1.0, ((double) oldEndMillis - nowMillis) / oldDurationMillis);
        return (long) Math.ceil(remaining * newDurationTicks);
    }

    public static int migrateEffectExposure(int oldLevel, int partialTicks) {
        return Math.min(5, Math.max(0, oldLevel)) * 100 + Math.min(99, Math.max(0, partialTicks));
    }

    /** Infinite/nonpositive durations are unchanged; each finite application is shortened once. */
    public static int shortenDuration(int durationTicks, double reduction) {
        if (durationTicks <= 0) return durationTicks;
        double bounded = Double.isFinite(reduction) ? Math.max(0, Math.min(0.30, reduction)) : 0;
        return Math.max(1, (int) Math.ceil(durationTicks * (1.0 - bounded)));
    }
}
