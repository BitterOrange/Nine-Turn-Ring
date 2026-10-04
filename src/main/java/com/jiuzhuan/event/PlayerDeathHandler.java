package com.jiuzhuan.event;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.config.ServerConfig;
import com.jiuzhuan.util.CombatEquipment;
import com.jiuzhuan.util.BalanceMath;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/**
 * 死亡相关处理。
 * <p>
 * 死亡保留采用 Curios 原生规则（对齐七咒之戒）：
 * <ul>
 *   <li>九转戒：{@link com.jiuzhuan.item.NineTurnRingItem#getDropRule} 返回
 *       {@code DropRule.ALWAYS_KEEP}，死亡时始终保留在戒指槽，不离槽、不掉落。</li>
 *   <li>轮转物品：使用默认 {@code DropRule.DEFAULT}，开启死亡掉落时随死亡正常掉落，
 *       开启死亡不掉落（keepInventory）时随原版规则保留。</li>
 * </ul>
 * 本类只负责：7转涅槃免死，以及死亡掉落时清除轮转的饰品快照/激活状态，
 * 防止饰品保护监控把正常掉落的轮转恢复回槽位。
 */
public class PlayerDeathHandler {

    // Vanilla has already attempted to consume a totem before this event is fired.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide || event.isCanceled()) return;
        if (CombatEquipment.bypassesRingDefenses(event.getSource())) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped() || !data.isActivated(7)
                    || !CombatEquipment.hasEquippedRotation(player, 7)) return;
            long now = data.getOnlineTicks();
            if (data.isInCooldown(now)) return;
            // Consume once, before restoring health. isAlive() cannot be required in a death callback.
            data.setUndyingCooldownEnd(BalanceMath.saturatingAdd(now, ServerConfig.getRot7CooldownTicks()));
            data.setInvincibleEnd(BalanceMath.saturatingAdd(now, ServerConfig.getRot7InvincibleTicks()));
            data.setEmergencyShield(0);
            data.setShieldEndTick(0);
            event.setCanceled(true);
            player.setHealth(player.getMaxHealth() * (float) ServerConfig.getRot7HealRatio());
            ModEventHandlers.enterCombat(player, data);
            data.syncToClient(player);
            player.sendSystemMessage(Component.translatable("nine_turn_ring.message.seven_triggered"));
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onActualDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide || event.isCanceled()) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // Temporary protection never survives an actual death; cooldowns do.
            data.setInvincibleEnd(0);
            data.setEmergencyShield(0);
            data.setShieldEndTick(0);
            if (!player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
                data.getAccessorySnapshot().keySet().removeIf(key -> key.startsWith("rotation:"));
            }
        });
    }

    /**
     * 复活后校准：死亡掉落开启时轮转已正常掉落，清除其激活状态与残留快照。
     * 九转戒 ALWAYS_KEEP 未离槽，其装备状态由 {@link com.jiuzhuan.capability.PlayerDataProvider}
     * 在复活时（LOWEST 优先级）按 Curios 槽位实际状态校准，此处不改动。
     */
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || event.isEndConquered()) return;
        // 死亡不掉落：物品与激活状态全部保留，不作处理
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            for (int i = 1; i <= 10; i++) {
                data.setActivated(i, false);
            }
            data.getAccessorySnapshot().keySet().removeIf(key -> key.startsWith("rotation:"));
            data.syncToClient(player);
        });
    }
}
