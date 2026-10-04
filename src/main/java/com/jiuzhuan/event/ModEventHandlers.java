package com.jiuzhuan.event;

import com.jiuzhuan.capability.IPlayerData;
import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.item.ModItems;
import com.jiuzhuan.config.ServerConfig;
import com.jiuzhuan.util.EffectDurationRules;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.event.entity.player.PlayerFlyableFallEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.tags.FluidTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.UUID;

public class ModEventHandlers {

    // 5转血量修饰符UUID
    private static final UUID HEALTH_BONUS_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    // 8转幸运修饰符UUID
    private static final UUID LUCK_BONUS_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");

    // 玩家进入游戏自动给予九转戒（仅一次）
    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 检查玩家是否已经拥有过戒指（用一个标记）
            if (!data.isRingEquipped() && !hasRing(player)) {
                // 给予戒指
                ItemStack ring = new ItemStack(ModItems.NINE_TURN_RING.get());
                if (!player.getInventory().add(ring)) {
                    player.spawnAtLocation(ring);
                }
                player.sendSystemMessage(Component.translatable("nine_turn_ring.message.ring_obtained"));
            }
            // 同步轮转槽位状态：装备了戒指开10个，没装备关闭
            boolean equipped = isRingWorn(player);
            PlayerDataProvider.setRotationSlots(player, equipped ? 10 : 0);
            data.setRingEquipped(equipped);
            // Login rebuilds abilities. Reconcile a persisted grant once, rather than mistaking it
            // for an external flight provider or leaving a stale owner flag after vanilla resets it.
            revokeRingFlight(player, data, false);
            applyFlightAdaptation(player, data);
            data.syncToClient(player);
        });
    }

    // 玩家维度切换：重新设置轮转槽位（Curios在维度切换时会重建饰品栏）
    @SubscribeEvent
    public void onPlayerChangedDimension(net.minecraftforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 立即同步一次当前数据（适应层数、击杀数等）
            data.syncToClient(player);
            // 延迟10 tick再重建轮转槽和校验激活状态（等Curios饰品栏完全重建）
            data.setDimensionFixDelay(10);
            if (data.isFlightGrantedByMod() && !player.getAbilities().mayfly) {
                data.setFlightGrantedByMod(false);
            }
            // Re-evaluate the exploration permission without overriding combat restrictions.
            applyFlightAdaptation(player, data);
        });
    }

    // 检查戒指是否装备在Curios饰品栏中
    private boolean isRingWorn(Player player) {
        try {
            var curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                var inv = curiosOpt.get();
                for (var entry : inv.getCurios().entrySet()) {
                    var handler = entry.getValue();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        if (handler.getStacks().getStackInSlot(i).is(ModItems.NINE_TURN_RING.get())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    // 检查玩家是否已有戒指（背包或饰品栏）
    private boolean hasRing(Player player) {
        // 检查背包
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.NINE_TURN_RING.get())) {
                return true;
            }
        }
        // 检查Curios饰品栏
        try {
            var curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                var inv = curiosOpt.get();
                for (var entry : inv.getCurios().entrySet()) {
                    var stacksHandler = entry.getValue();
                    for (int i = 0; i < stacksHandler.getSlots(); i++) {
                        if (stacksHandler.getStacks().getStackInSlot(i).is(ModItems.NINE_TURN_RING.get())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // Online time advances even with the ring unequipped; offline time never advances cooldowns.
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        Player player = event.player;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            data.tickOnlineTime();
            if (data.getDimensionFixDelay() > 0) {
                data.setDimensionFixDelay(data.getDimensionFixDelay() - 1);
                if (data.getDimensionFixDelay() == 0) {
                    boolean equipped = isRingWorn(player);
                    PlayerDataProvider.setRotationSlots(player, equipped ? 10 : 0);
                    data.setRingEquipped(equipped);
                }
            }
            if (player.tickCount % 20 == 0 && data.getDimensionFixDelay() <= 0) {
                validateActivatedRotations(player, data);
            }
            boolean equipped = data.isRingEquipped();
            if (!equipped || !data.isActivated(7)) data.setInvincibleEnd(0);
            if (!equipped || !data.isActivated(9) || data.getOnlineTicks() >= data.getShieldEndTick()) {
                data.setEmergencyShield(0);
                data.setShieldEndTick(0);
            }
            applyLuckModifier(player, equipped && data.isActivated(8));
            applyHealthModifier(player, data);
            applyFlightAdaptation(player, data);
            // Synchronize online cooldowns and exposure, including while abilities are unequipped.
            if (player.tickCount % 20 == 0) data.syncToClient(player);
            if (!equipped || !player.isAlive()) return;

            if (data.isActivated(2) && player.tickCount % 20 == 0) {
                int food = player.getFoodData().getFoodLevel();
                int target = ServerConfig.getRot2FoodFloor();
                if (food < target) player.getFoodData().setFoodLevel(Math.min(target, food + 1));
            }
            if (data.isActivated(3) && data.isNightVisionEnabled()) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false, false));
            }
            if (data.isActivated(4) && player.tickCount % 20 == 0 && player.getHealth() < player.getMaxHealth()) {
                boolean rested = data.getOnlineTicks() - data.getLastCombatTick() >= ServerConfig.getRot4RestTicks();
                player.heal((float) (rested ? ServerConfig.getRot4RestHeal() : ServerConfig.getRot4CombatHeal()));
            }
            applyEffectAdaptation(player, data);
        });
    }

    // Added exposes the incoming, unmerged instance. Never rescale the active effect every tick.
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onMobEffectAdded(MobEffectEvent.Added event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        MobEffectInstance incoming = event.getEffectInstance();
        if (incoming == event.getOldEffectInstance()) return;
        if (incoming.getEffect().getCategory() != MobEffectCategory.HARMFUL
                || incoming.getEffect().isInstantenous() || incoming.isInfiniteDuration()) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped()) return;
            var effect = incoming.getEffect();
            double reduction = 0;
            if ((data.isActivated(2) && (effect == MobEffects.HUNGER || effect == MobEffects.WEAKNESS))
                    || (data.isActivated(3) && (effect == MobEffects.BLINDNESS || effect == MobEffects.DARKNESS))
                    || (data.isActivated(4) && (effect == MobEffects.POISON || effect == MobEffects.WITHER))) {
                reduction = ServerConfig.getSpecialistEffectReduction();
            }
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
            if (id != null && data.isActivated(10) && !data.isEffectAdaptationDisabled(id.toString())) {
                reduction = Math.max(reduction, data.getEffectAdaptationReduction(id.toString()));
            }
            EffectDurationRules.shortenIncoming(incoming, reduction);
        });
    }

    // 阻止药水效果被移除（牛奶等）- 针对戒指提供的夜视
    @SubscribeEvent
    public void onMobEffectRemoved(MobEffectEvent.Remove event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped()) return;
            // 3转夜视不允许被移除（仅当手动开关开启时）
            if (data.isActivated(3) && data.isNightVisionEnabled() && event.getEffect() == MobEffects.NIGHT_VISION) {
                event.setCanceled(true);
            }
        });
    }

    // 校验所有转的激活状态：如果某转标记为激活但物品不在轮转槽中，则取消激活
    private void validateActivatedRotations(Player player, IPlayerData data) {
        boolean[] equipped = new boolean[11];
        try {
            var curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                var inv = curiosOpt.get();
                for (var entry : inv.getCurios().entrySet()) {
                    if (!"rotation".equals(entry.getKey())) continue;
                    var handler = entry.getValue();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStacks().getStackInSlot(i);
                        int rot = getRotationLevelFromStack(stack);
                        if (rot >= 1 && rot <= 10) equipped[rot] = true;
                    }
                }
            }
        } catch (Exception ignored) {}
        boolean needSync = false;
        for (int i = 1; i <= 10; i++) {
            if (data.isActivated(i) && !equipped[i]) {
                data.setActivated(i, false);
                needSync = true;
            }
        }
        if (needSync) data.syncToClient(player);
    }

    private int getRotationLevelFromStack(ItemStack stack) {
        if (stack.is(ModItems.ROTATION_1_POWER.get())) return 1;
        if (stack.is(ModItems.ROTATION_2_SATIETY.get())) return 2;
        if (stack.is(ModItems.ROTATION_3_NIGHT_VISION.get())) return 3;
        if (stack.is(ModItems.ROTATION_4_REGEN.get())) return 4;
        if (stack.is(ModItems.ROTATION_5_HEALTH.get())) return 5;
        if (stack.is(ModItems.ROTATION_6_RESISTANCE.get())) return 6;
        if (stack.is(ModItems.ROTATION_7_UNDYING.get())) return 7;
        if (stack.is(ModItems.ROTATION_8_LUCK.get())) return 8;
        if (stack.is(ModItems.ROTATION_9_IMMORTAL.get())) return 9;
        if (stack.is(ModItems.ROTATION_10_ADAPTATION.get())) return 10;
        return 0;
    }

    // 应用幸运修饰符
    private void applyLuckModifier(Player player, boolean active) {
        AttributeInstance luckAttr = player.getAttribute(Attributes.LUCK);
        if (luckAttr == null) return;

        AttributeModifier existing = luckAttr.getModifier(LUCK_BONUS_UUID);
        if (active) {
            if (existing == null) {
                luckAttr.addPermanentModifier(new AttributeModifier(
                        LUCK_BONUS_UUID, "nine_turn_ring_luck_bonus", 100.0,
                        AttributeModifier.Operation.ADDITION));
            }
        } else {
            if (existing != null) {
                luckAttr.removeModifier(LUCK_BONUS_UUID);
            }
        }
    }

    // 应用血量修饰符（5转）
    private void applyHealthModifier(Player player, IPlayerData data) {
        AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) return;

        AttributeModifier existing = healthAttr.getModifier(HEALTH_BONUS_UUID);
        if (data.isRingEquipped() && data.isActivated(5)) {
            double amount = data.getHealthBonus();
            if (existing == null || existing.getAmount() != amount
                    || existing.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL) {
                if (existing != null) {
                    healthAttr.removeModifier(HEALTH_BONUS_UUID);
                }
                healthAttr.addPermanentModifier(new AttributeModifier(
                        HEALTH_BONUS_UUID, "nine_turn_ring_health_bonus", amount,
                        AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        } else {
            if (existing != null) {
                healthAttr.removeModifier(HEALTH_BONUS_UUID);
            }
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    /** Mark combat immediately, including hits completely absorbed by a shield. */
    public static void enterCombat(Player player, IPlayerData data) {
        data.markCombat();
        revokeRingFlight(player, data, true);
        data.syncToClient(player);
    }

    private static void revokeRingFlight(Player player, IPlayerData data, boolean gentleLanding) {
        if (player.isCreative() || player.isSpectator() || !data.isFlightGrantedByMod()) return;
        boolean airborne = !player.onGround();
        data.setFlightGrantedByMod(false);
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        if (gentleLanding && airborne) {
            player.fallDistance = 0;
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                    ServerConfig.getFlightSlowFallTicks(), 0, false, false, true));
        }
    }

    private static void applyFlightAdaptation(Player player, IPlayerData data) {
        if (player.isCreative() || player.isSpectator()) {
            data.setFlightGrantedByMod(false);
            return;
        }
        boolean eligible = data.isRingEquipped() && data.isActivated(10) && data.hasFlightAdaptation()
                && player.isAlive() && data.getOnlineTicks() >= data.getCombatEndTick();
        if (!eligible) {
            revokeRingFlight(player, data, true);
            return;
        }
        // A pre-existing flight permission belongs to another provider; do not claim it.
        // If another mod revokes our grant, do not forcibly re-enable it every tick.
        if (!data.isFlightGrantedByMod() && !player.getAbilities().mayfly) {
            data.setFlightGrantedByMod(true);
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
        }
        if (!data.hasAnnouncedFlightAdaptation()) {
            data.setAnnouncedFlightAdaptation(true);
            player.sendSystemMessage(Component.translatable("nine_turn_ring.message.fly_adapt"));
            data.syncToClient(player);
        }
    }

    // Vanilla skips fall damage whenever mayfly is true, even when the player is not flying.
    // Re-enter the vanilla fall path without that permission, preserving normal fall events,
    // jump boost, Feather Falling and slow-falling behavior instead of inventing damage by height.
    @SubscribeEvent
    public void onFlyableFall(PlayerFlyableFallEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.isCreative() || player.isSpectator()
                || player.getAbilities().flying || player.hasEffect(MobEffects.SLOW_FALLING)) return;
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isFlightGrantedByMod()) return;
            player.getAbilities().mayfly = false;
            try {
                // Forge 1.20.1 passes distance as the notification's multiplier; use normal gravity.
                player.causeFallDamage(event.getDistance(), 1.0f, player.damageSources().fall());
            } finally {
                if (data.isFlightGrantedByMod()) player.getAbilities().mayfly = true;
            }
        });
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            if (!data.isRingEquipped() || !data.isActivated(10) || !data.hasFlightAdaptation()
                    || data.getOnlineTicks() < data.getCombatEndTick()) return;
            float speed = event.getNewSpeed();
            if (!player.onGround()) speed *= 5.0F;
            if (player.isEyeInFluid(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(player)) speed *= 5.0F;
            event.setNewSpeed(speed);
        });
    }

    private void applyEffectAdaptation(Player player, IPlayerData data) {
        if (!data.isActivated(10)) return;
        for (MobEffectInstance instance : player.getActiveEffects()) {
            var effect = instance.getEffect();
            if (effect.getCategory() != MobEffectCategory.HARMFUL || effect.isInstantenous()
                    || instance.isInfiniteDuration() || instance.getDuration() <= 0) continue;
            ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
            if (id != null && !data.isEffectAdaptationDisabled(id.toString())) {
                data.addEffectExposureTicks(id.toString(), 1);
            }
        }
    }
}
