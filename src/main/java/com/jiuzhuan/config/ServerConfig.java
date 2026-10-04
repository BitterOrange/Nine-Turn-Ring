package com.jiuzhuan.config;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-authoritative balance settings. Clients receive a per-player display snapshot. */
public final class ServerConfig {
    private ServerConfig() {}
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.IntValue LEGACY_RESCUE_COOLDOWN_SECONDS;
    private static final Map<String, ForgeConfigSpec.ConfigValue<? extends Number>> VALUES = new LinkedHashMap<>();
    public static final String ROT1_CAP = "rot1Cap";
    public static final String ROT1_N90 = "rot1N90";
    public static final String ROT5_CAP = "rot5Cap";
    public static final String ROT5_N90 = "rot5N90";
    public static final String ROT7_HEAL = "rot7Heal";
    public static final String ROT7_INVINCIBLE_TICKS = "rot7InvincibleTicks";
    public static final String ROT7_COOLDOWN_TICKS = "rot7CooldownTicks";
    public static final String ROT9_TRIGGER_RATIO = "rot9TriggerRatio";
    public static final String ROT9_SHIELD_RATIO = "rot9ShieldRatio";
    public static final String ROT9_SHIELD_TICKS = "rot9ShieldTicks";
    public static final String ROT9_COOLDOWN_TICKS = "rot9CooldownTicks";
    public static final String ROT10_DAMAGE_CAP = "rot10DamageCap";
    public static final String ROT10_HITS90 = "rot10Hits90";
    public static final String ROT10_EFFECT_CAP = "rot10EffectCap";
    public static final String ROT10_EFFECT_SECONDS90 = "rot10EffectSeconds90";
    public static final String ROT10_STACK_TICKS = "rot10StackTicks";
    public static final String ROT10_MATURITY_COUNT = "rot10MaturityCount";
    public static final String ROT10_MIN_DAMAGE = "rot10MinDamage";
    public static final String ROT10_MIN_HEALTH_RATIO = "rot10MinHealthRatio";
    public static final String ROT6_REDUCTION = "rot6Reduction";
    public static final String ROT4_COMBAT_HEAL = "rot4CombatHeal";
    public static final String ROT4_REST_HEAL = "rot4RestHeal";
    public static final String ROT4_REST_TICKS = "rot4RestTicks";
    public static final String COMBAT_TICKS = "combatTicks";
    public static final String SPECIALIST_EFFECT_REDUCTION = "specialistEffectReduction";
    public static final String ROT2_FOOD_FLOOR = "rot2FoodFloor";
    public static final String FLIGHT_SLOW_FALL_TICKS = "flightSlowFallTicks";

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Bounded Nine Turn Ring balance. Old per-kill/per-stack and lock-health keys are obsolete.").push("general");
        LEGACY_RESCUE_COOLDOWN_SECONDS = builder.comment("Legacy cooldown length, used only to migrate old timestamp-based saves.")
                .defineInRange("rotation_7.cooldown_seconds", 40, 1, 3600);
        VALUES.put(ROT1_CAP, builder.comment("Damage bonus asymptote; 1.5 means final damage approaches 2.5x.").defineInRange("rotation_1.bonus_cap", 1.500, 0.000, 100.000));
        VALUES.put(ROT1_N90, builder.comment("Kills needed to reach 90% of the damage bonus cap.").defineInRange("rotation_1.kills_to_90_percent", 1000, 1, 1000000000));
        VALUES.put(ROT5_CAP, builder.comment("Maximum health bonus asymptote; 0.3 means health approaches 1.3x.").defineInRange("rotation_5.bonus_cap", 0.300, 0.000, 100.000));
        VALUES.put(ROT5_N90, builder.comment("Kills needed to reach 90% of the health bonus cap.").defineInRange("rotation_5.kills_to_90_percent", 1500, 1, 1000000000));
        VALUES.put(ROT7_HEAL, builder.comment("Health ratio restored on rescue.").defineInRange("rotation_7.revive_health_ratio", 0.250, 0.010, 1.000));
        VALUES.put(ROT7_INVINCIBLE_TICKS, builder.comment("Brief rescue protection, in online gameplay ticks.").defineInRange("rotation_7.protection_ticks", 30, 0, 1200));
        VALUES.put(ROT7_COOLDOWN_TICKS, builder.comment("Rescue cooldown, in online gameplay ticks; offline time does not reduce it.").defineInRange("rotation_7.cooldown_ticks", 3600, 1, 1728000));
        VALUES.put(ROT9_TRIGGER_RATIO, builder.comment("Emergency shield triggers when health crosses this ratio.").defineInRange("rotation_9.trigger_health_ratio", 0.300, 0.010, 1.000));
        VALUES.put(ROT9_SHIELD_RATIO, builder.comment("Emergency shield capacity as a ratio of maximum health.").defineInRange("rotation_9.shield_health_ratio", 0.200, 0.000, 1.000));
        VALUES.put(ROT9_SHIELD_TICKS, builder.comment("Emergency shield duration, in online gameplay ticks.").defineInRange("rotation_9.shield_ticks", 120, 1, 12000));
        VALUES.put(ROT9_COOLDOWN_TICKS, builder.comment("Emergency shield cooldown, in online gameplay ticks.").defineInRange("rotation_9.cooldown_ticks", 1800, 1, 1728000));
        VALUES.put(ROT10_DAMAGE_CAP, builder.comment("Damage adaptation asymptote, never above 30%.").defineInRange("rotation_10.damage_reduction_cap", 0.300, 0.000, 0.300));
        VALUES.put(ROT10_HITS90, builder.comment("Qualifying hits needed to reach 90% of the damage adaptation cap.").defineInRange("rotation_10.hits_to_90_percent", 100, 1, 1000000000));
        VALUES.put(ROT10_EFFECT_CAP, builder.comment("Harmful effect duration reduction asymptote, never above 30%.").defineInRange("rotation_10.effect_duration_cap", 0.300, 0.000, 0.300));
        VALUES.put(ROT10_EFFECT_SECONDS90, builder.comment("Actual exposure seconds needed to reach 90% of the duration reduction cap.").defineInRange("rotation_10.exposure_seconds_to_90_percent", 300, 1, 100000000));
        VALUES.put(ROT10_STACK_TICKS, builder.comment("Minimum interval between qualifying hits of the same damage type.").defineInRange("rotation_10.hit_interval_ticks", 100, 1, 12000));
        VALUES.put(ROT10_MATURITY_COUNT, builder.comment("Finite milestone for flight and ascension; never requires reaching the asymptote.").defineInRange("rotation_10.maturity_hit_count", 100, 1, 1000000000));
        VALUES.put(ROT10_MIN_DAMAGE, builder.comment("Minimum final health damage for a qualifying nonlethal hit.").defineInRange("rotation_10.minimum_training_damage", 1.000, 0.010, 1000000.000));
        VALUES.put(ROT10_MIN_HEALTH_RATIO, builder.comment("Qualifying hits must also deal this fraction of maximum health.").defineInRange("rotation_10.minimum_training_health_ratio", 0.010, 0.000, 1.000));
        VALUES.put(ROT6_REDUCTION, builder.comment("Uniform damage reduction; no magic, void or command-kill immunity.").defineInRange("rotation_6.damage_reduction", 0.200, 0.000, 0.300));
        VALUES.put(ROT4_COMBAT_HEAL, builder.comment("Healing per second during combat; respects healing events.").defineInRange("rotation_4.combat_heal_per_second", 0.500, 0.000, 100.000));
        VALUES.put(ROT4_REST_HEAL, builder.comment("Healing per second after the rest delay.").defineInRange("rotation_4.rest_heal_per_second", 2.000, 0.000, 100.000));
        VALUES.put(ROT4_REST_TICKS, builder.comment("Ticks without combat before faster regeneration.").defineInRange("rotation_4.rest_delay_ticks", 200, 0, 12000));
        VALUES.put(COMBAT_TICKS, builder.comment("Combat duration after dealing or receiving damage.").defineInRange("combat.duration_ticks", 400, 1, 12000));
        VALUES.put(SPECIALIST_EFFECT_REDUCTION, builder.comment("Rotations 2/3/4 duration reduction; use max with adaptation, never add.").defineInRange("effects.specialist_duration_reduction", 0.200, 0.000, 0.300));
        VALUES.put(ROT2_FOOD_FLOOR, builder.comment("Restore one food point each second until this food level, without saturation.").defineInRange("rotation_2.food_target", 18, 0, 20));
        VALUES.put(FLIGHT_SLOW_FALL_TICKS, builder.comment("Slow falling duration when combat suspends ring flight.").defineInRange("flight.combat_slow_fall_ticks", 60, 0, 1200));
        builder.pop();
        SPEC = builder.build();
    }

    public static double getBalanceValue(String key) {
        ForgeConfigSpec.ConfigValue<? extends Number> value = VALUES.get(key);
        if (value == null) throw new IllegalArgumentException("Unknown balance setting: " + key);
        return (SPEC.isLoaded() ? value.get() : value.getDefault()).doubleValue();
    }

    public static Map<String, Double> snapshot() {
        Map<String, Double> values = new LinkedHashMap<>();
        VALUES.keySet().forEach(key -> values.put(key, getBalanceValue(key)));
        return values;
    }
    public static double getRot1DamageCap() { return getBalanceValue(ROT1_CAP); }
    public static int getRot1KillsTo90() { return (int) getBalanceValue(ROT1_N90); }
    public static double getRot5HealthCap() { return getBalanceValue(ROT5_CAP); }
    public static int getRot5KillsTo90() { return (int) getBalanceValue(ROT5_N90); }
    public static double getRot7HealRatio() { return getBalanceValue(ROT7_HEAL); }
    public static long getLegacyRescueCooldownMillis() {
        return (SPEC.isLoaded() ? LEGACY_RESCUE_COOLDOWN_SECONDS.get() : LEGACY_RESCUE_COOLDOWN_SECONDS.getDefault()) * 1000L;
    }
    public static int getRot7InvincibleTicks() { return (int) getBalanceValue(ROT7_INVINCIBLE_TICKS); }
    public static int getRot7CooldownTicks() { return (int) getBalanceValue(ROT7_COOLDOWN_TICKS); }
    public static double getRot9TriggerRatio() { return getBalanceValue(ROT9_TRIGGER_RATIO); }
    public static double getRot9ShieldRatio() { return getBalanceValue(ROT9_SHIELD_RATIO); }
    public static int getRot9ShieldTicks() { return (int) getBalanceValue(ROT9_SHIELD_TICKS); }
    public static int getRot9CooldownTicks() { return (int) getBalanceValue(ROT9_COOLDOWN_TICKS); }
    public static double getRot10DamageCap() { return getBalanceValue(ROT10_DAMAGE_CAP); }
    public static int getRot10HitsTo90() { return (int) getBalanceValue(ROT10_HITS90); }
    public static double getRot10EffectCap() { return getBalanceValue(ROT10_EFFECT_CAP); }
    public static int getRot10EffectSecondsTo90() { return (int) getBalanceValue(ROT10_EFFECT_SECONDS90); }
    public static int getRot10StackCooldownTicks() { return (int) getBalanceValue(ROT10_STACK_TICKS); }
    public static int getRot10MaturityCount() { return (int) getBalanceValue(ROT10_MATURITY_COUNT); }
    public static double getRot10MinTrainingDamage() { return getBalanceValue(ROT10_MIN_DAMAGE); }
    public static double getRot10MinTrainingHealthRatio() { return getBalanceValue(ROT10_MIN_HEALTH_RATIO); }
    public static double getRot6DamageReduction() { return getBalanceValue(ROT6_REDUCTION); }
    public static double getRot4CombatHeal() { return getBalanceValue(ROT4_COMBAT_HEAL); }
    public static double getRot4RestHeal() { return getBalanceValue(ROT4_REST_HEAL); }
    public static int getRot4RestTicks() { return (int) getBalanceValue(ROT4_REST_TICKS); }
    public static int getCombatTicks() { return (int) getBalanceValue(COMBAT_TICKS); }
    public static double getSpecialistEffectReduction() { return getBalanceValue(SPECIALIST_EFFECT_REDUCTION); }
    public static int getRot2FoodFloor() { return (int) getBalanceValue(ROT2_FOOD_FLOOR); }
    public static int getFlightSlowFallTicks() { return (int) getBalanceValue(FLIGHT_SLOW_FALL_TICKS); }
}
