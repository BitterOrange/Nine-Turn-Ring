package com.jiuzhuan.util;

/** Numerical combat rules, independent of the game runtime. */
public final class CombatRules {
    private CombatRules() {}

    public static boolean shouldTriggerShield(float health, float damage, float maxHealth, double thresholdRatio) {
        return health > 0 && damage > 0 && maxHealth > 0
                && health - damage < maxHealth * thresholdRatio;
    }

    public static ShieldHit absorb(float damage, float shield) {
        float absorbed = Math.min(Math.max(0, damage), Math.max(0, shield));
        return new ShieldHit(Math.max(0, damage - absorbed), Math.max(0, shield - absorbed));
    }

    public record ShieldHit(float healthDamage, float remainingShield) {}
}
