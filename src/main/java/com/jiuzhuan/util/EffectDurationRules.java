package com.jiuzhuan.util;

import com.google.common.collect.MapMaker;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;

/** Changes only the incoming duration; leaves amplifier, hidden effects and mod metadata intact. */
public final class EffectDurationRules {
    private static final Field DURATION = ObfuscationReflectionHelper.findField(MobEffectInstance.class, "f_19503_");
    // MobEffectInstance has mutable equals/hashCode, so weak identity keys are essential here.
    private static final ConcurrentMap<MobEffectInstance, Boolean> PROCESSED = new MapMaker().weakKeys().makeMap();

    private EffectDurationRules() {}

    public static void shortenIncoming(MobEffectInstance effect, double reduction) {
        if (effect.getDuration() <= 0 || reduction <= 0 || PROCESSED.putIfAbsent(effect, true) != null) return;
        try {
            DURATION.setInt(effect, BalanceMath.shortenDuration(effect.getDuration(), reduction));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot apply incoming effect duration resistance", exception);
        }
    }
}
