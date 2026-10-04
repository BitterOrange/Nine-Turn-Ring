package com.jiuzhuan.event;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.config.ServerConfig;
import com.jiuzhuan.util.BalanceMath;
import com.jiuzhuan.util.CombatEquipment;
import com.jiuzhuan.util.CombatRules;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class PlayerDamageHandler {
    private static String normalizeDamageType(String id) {
        return id.equals("explosion.player") ? "explosion" : id;
    }

    // Contact includes attacks absorbed by shields or revival protection.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
        if (event.getSource().getEntity() instanceof Player attacker) {
            attacker.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data ->
                    ModEventHandlers.enterCombat(attacker, data));
        }
        if (event.getEntity() instanceof Player player) {
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data ->
                    ModEventHandlers.enterCombat(player, data));
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
        if (event.getSource().getEntity() instanceof Player attacker) {
            attacker.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                if (data.isRingEquipped() && data.isActivated(1)) {
                    event.setAmount(event.getAmount() * (1.0f + (float) data.getPowerDamageBonus()));
                }
            });
        }

        if (!(event.getEntity() instanceof Player player)) return;
        if (CombatEquipment.bypassesRingDefenses(event.getSource())) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped()) return;
            long now = data.getOnlineTicks();
            if (data.isActivated(7) && data.isInvincible(now)
                    && CombatEquipment.hasEquippedRotation(player, 7)) {
                // Only damage is prevented. Projectiles, knockback and effects keep their normal behavior.
                event.setAmount(0);
                return;
            }
            float damage = event.getAmount();
            if (data.isActivated(6)) {
                damage *= 1.0f - (float) ServerConfig.getRot6DamageReduction();
            }
            String damageType = normalizeDamageType(event.getSource().getMsgId());
            if (data.isActivated(10) && !data.isDamageAdaptationDisabled(damageType)) {
                // Existing progress applies to this hit; training occurs after final damage and the ninth shield.
                damage *= 1.0f - (float) data.getAdaptationReduction(damageType);
            }
            event.setAmount(damage);
        });
    }

    // Final damage is after vanilla armor, magic mitigation and absorption hearts.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0) return;
        if (!(event.getEntity() instanceof Player player) || !player.isAlive()) return;
        if (CombatEquipment.bypassesRingDefenses(event.getSource())) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped()) return;
            long now = data.getOnlineTicks();
            float damage = event.getAmount();
            float maxHealth = player.getMaxHealth();
            boolean changed = false;

            if (data.isActivated(9) && CombatEquipment.hasEquippedRotation(player, 9)) {
                if (now >= data.getShieldEndTick() && data.getEmergencyShield() > 0) {
                    data.setEmergencyShield(0);
                    changed = true;
                }
                if (data.getEmergencyShield() <= 0 && now >= data.getShieldCooldownEnd()
                        && CombatRules.shouldTriggerShield(player.getHealth(), damage, maxHealth,
                        ServerConfig.getRot9TriggerRatio())) {
                    data.setEmergencyShield(maxHealth * (float) ServerConfig.getRot9ShieldRatio());
                    data.setShieldEndTick(BalanceMath.saturatingAdd(now, ServerConfig.getRot9ShieldTicks()));
                    data.setShieldCooldownEnd(BalanceMath.saturatingAdd(now, ServerConfig.getRot9CooldownTicks()));
                    changed = true;
                }
                if (now < data.getShieldEndTick() && data.getEmergencyShield() > 0) {
                    CombatRules.ShieldHit result = CombatRules.absorb(damage, data.getEmergencyShield());
                    damage = result.healthDamage();
                    data.setEmergencyShield(result.remainingShield());
                    event.setAmount(damage);
                    changed = true;
                }
            }

            String damageType = normalizeDamageType(event.getSource().getMsgId());
            if (data.isActivated(10) && !data.isDamageAdaptationDisabled(damageType)
                    && BalanceMath.isTrainingHit(damage, maxHealth, player.getHealth(),
                    ServerConfig.getRot10MinTrainingDamage(), ServerConfig.getRot10MinTrainingHealthRatio())) {
                int oldCount = data.getAdaptationLevel(damageType);
                data.addAdaptation(damageType, now);
                changed |= data.getAdaptationLevel(damageType) != oldCount;
            }
            if (changed) data.syncToClient(player);
        });
    }
}
