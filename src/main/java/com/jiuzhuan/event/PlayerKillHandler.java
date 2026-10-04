package com.jiuzhuan.event;

import com.jiuzhuan.capability.PlayerDataProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;

public class PlayerKillHandler {
    public static final TagKey<EntityType<?>> GROWTH_HOSTILES = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation("nine_turn_ring", "growth_hostiles"));
    public static final TagKey<EntityType<?>> GROWTH_EXCLUDED = TagKey.create(Registries.ENTITY_TYPE,
            new ResourceLocation("nine_turn_ring", "growth_excluded"));

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || event.isCanceled() || !isGrowthTarget(entity)) return;

        // Retain vanilla attribution for delayed damage such as a player's fire or knockback.
        Player killer = event.getSource().getEntity() instanceof Player direct ? direct
                : entity.getKillCredit() instanceof Player credited ? credited : null;
        if (killer != null) {
            killer.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                if (!data.isRingEquipped()) return;

                // Only hostile kills grow permanent progress; owning the base ring remains sufficient.
                data.addPowerKill(1);
                data.addHealthKill(1);
                // 实时同步到客户端，刷新tooltip显示
                data.syncToClient(killer);
            });
        }
    }

    public static boolean isGrowthTarget(LivingEntity entity) {
        if (entity instanceof Player || entity.getType().is(GROWTH_EXCLUDED)) return false;
        if (entity instanceof TamableAnimal tameable && tameable.isTame()) return false;
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) return false;
        return entity instanceof Enemy || entity.getType().is(GROWTH_HOSTILES);
    }
}
