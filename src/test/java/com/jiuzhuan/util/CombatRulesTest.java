package com.jiuzhuan.util;

/** Run with plain javac/java; these tests do not need a Minecraft runtime. */
public final class CombatRulesTest {
    public static void main(String[] args) {
        require(!CombatRules.shouldTriggerShield(20, 14, 20, 0.30), "exactly 30% health must not trigger");
        require(CombatRules.shouldTriggerShield(20, 15, 20, 0.30), "crossing 30% health must trigger");
        require(!CombatRules.shouldTriggerShield(2, 0, 20, 0.30), "zero final damage must not spend cooldown");
        require(!CombatRules.shouldTriggerShield(0, 5, 20, 0.30), "dead players cannot generate a shield");

        CombatRules.ShieldHit triggerHit = CombatRules.absorb(15, 4);
        close(triggerHit.healthDamage(), 11, "the triggering hit must consume the finite shield");
        close(triggerHit.remainingShield(), 0, "a broken shield has no remaining capacity");
        require(20 - triggerHit.healthDamage() > 0, "a shield can rescue a survivable burst");

        CombatRules.ShieldHit lethal = CombatRules.absorb(30, 4);
        require(20 - lethal.healthDamage() <= 0, "an oversized hit must still be lethal");

        CombatRules.ShieldHit first = CombatRules.absorb(3, 12);
        CombatRules.ShieldHit second = CombatRules.absorb(8, first.remainingShield());
        CombatRules.ShieldHit third = CombatRules.absorb(6, second.remainingShield());
        close(first.healthDamage(), 0, "small hit is fully absorbed");
        close(second.remainingShield(), 1, "shield budget persists across hits");
        close(third.healthDamage(), 5, "damage beyond the remaining budget reaches health");
        close(third.remainingShield(), 0, "shield budget cannot become negative");
        close(CombatRules.absorb(5, 0).healthDamage(), 5, "depleted shield grants no damage immunity");
        System.out.println("CombatRulesTest: 13 checks passed");
    }

    private static void close(float actual, float expected, String message) {
        require(Math.abs(actual - expected) < 0.00001f, message + ": " + actual + " != " + expected);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
