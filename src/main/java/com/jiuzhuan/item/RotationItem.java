package com.jiuzhuan.item;

import com.jiuzhuan.capability.IPlayerData;
import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.config.ServerConfig;
import com.jiuzhuan.util.AdvancementUtil;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * 转生物品通用类 - 装备到轮转槽后立即激活对应转生效果
 */
public class RotationItem extends Item implements ICurioItem {
    private final int rotationLevel; // 1-10
    private final String rotationName;

    public RotationItem(int rotationLevel, String rotationName, Properties properties) {
        super(properties);
        this.rotationLevel = rotationLevel;
        this.rotationName = rotationName;
    }

    public int getRotationLevel() {
        return rotationLevel;
    }

    // 各转主题颜色（索引1-10对应一转到十转）
    private static final String[] ROTATION_COLORS = {
            "", "§c", "§6", "§9", "§d", "§5", "§3", "§4", "§a", "§e", "§f"
    };

    // 彩虹渐变色（RGB），用于九转名称的彩色流转效果
    private static final int[] RAINBOW_COLORS = {
            0xFF0000, // 红
            0xFF8800, // 橙
            0xFFFF00, // 黄
            0x00FF00, // 绿
            0x00FFFF, // 青
            0x0088FF, // 蓝
            0x8800FF, // 紫
            0xFF00FF  // 粉
    };

    /**
     * 颜色线性插值：在 c1 和 c2 之间按 t(0~1) 平滑过渡
     */
    private static int lerpColor(int c1, int c2, float t) {
        int r = (int) (((c1 >> 16) & 0xFF) * (1 - t) + ((c2 >> 16) & 0xFF) * t);
        int g = (int) (((c1 >> 8) & 0xFF) * (1 - t) + ((c2 >> 8) & 0xFF) * t);
        int b = (int) ((c1 & 0xFF) * (1 - t) + (c2 & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 覆盖物品名称颜色，根据转生等级显示对应效果
     * 七~九转：同色系正弦平滑呼吸（无生硬跳变）
     * 十转：彩虹流转 + 柔和亮度呼吸
     */
    @Override
    public Component getName(ItemStack stack) {
        String name = super.getName(stack).getString();
        long time = System.currentTimeMillis();
        if (rotationLevel == 7) {
            float t = (float) (0.5 + 0.5 * Math.sin(time / 1200.0 * Math.PI * 2));
            int color = lerpColor(0xAA0000, 0xFF5555, t);
            return Component.literal(name).withStyle(s -> s.withColor(TextColor.fromRgb(color)));
        }
        if (rotationLevel == 8) {
            float t = (float) (0.5 + 0.5 * Math.sin(time / 1200.0 * Math.PI * 2));
            int color = lerpColor(0x00AA00, 0x55FF55, t);
            return Component.literal(name).withStyle(s -> s.withColor(TextColor.fromRgb(color)));
        }
        if (rotationLevel == 9) {
            float t = (float) (0.5 + 0.5 * Math.sin(time / 1200.0 * Math.PI * 2));
            int color = lerpColor(0xFFAA00, 0xFFFF88, t);
            return Component.literal(name).withStyle(s -> s.withColor(TextColor.fromRgb(color)));
        }
        if (rotationLevel == 10) {
            MutableComponent result = Component.literal("");
            int offset = (int) ((time / 250) % RAINBOW_COLORS.length);
            float flashT = (float) (0.5 + 0.5 * Math.sin(time / 600.0 * Math.PI * 2));
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                int rainbow = RAINBOW_COLORS[(i + offset) % RAINBOW_COLORS.length];
                int color = lerpColor(rainbow, 0xFFFFFF, flashT * 0.6f);
                result.append(Component.literal(String.valueOf(c))
                        .withStyle(s -> s.withColor(TextColor.fromRgb(color))));
            }
            return result;
        }
        String color = (rotationLevel >= 1 && rotationLevel <= 10) ? ROTATION_COLORS[rotationLevel] : "§f";
        return Component.literal(color + name);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFireResistant() {
        return true;
    }

    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (prevStack.is(this)) return;
        LivingEntity entity = slotContext.entity();
        if (entity instanceof Player player && !player.level().isClientSide) {
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                data.setActivated(rotationLevel, true);
                data.syncToClient(player);
            });
            if (player instanceof ServerPlayer sp && rotationLevel >= 1 && rotationLevel <= 10) {
                AdvancementUtil.grant(sp, "rotation_" + rotationLevel, "rot" + rotationLevel);
            }
        }
    }

    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        if (newStack.is(this)) return;
        LivingEntity entity = slotContext.entity();
        if (entity instanceof Player player && !player.level().isClientSide) {
            com.jiuzhuan.event.AccessoryProtectionHandler.onAccessoryUnequipped(slotContext, stack);
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                data.setActivated(rotationLevel, false);
                data.syncToClient(player);
            });
        }
    }

    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }

    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        return slotContext.identifier().contains("rotation");
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        IPlayerData data = (level != null && level.isClientSide) ? getClientPlayerData() : null;
        String title = Component.translatable("nine_turn_ring.rotation." + rotationLevel + ".title").getString();
        if (rotationLevel >= 7 && rotationLevel <= 9) {
            int[] dark = {0xAA0000, 0x00AA00, 0xFFAA00};
            int[] light = {0xFF5555, 0x55FF55, 0xFFFF88};
            float phase = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 1200.0 * Math.PI * 2));
            int color = lerpColor(dark[rotationLevel - 7], light[rotationLevel - 7], phase);
            tooltip.add(Component.literal(title).withStyle(s -> s.withColor(TextColor.fromRgb(color))));
        } else if (rotationLevel == 10) {
            MutableComponent rainbow = Component.literal("");
            int offset = (int) ((System.currentTimeMillis() / 250) % RAINBOW_COLORS.length);
            for (int i = 0; i < title.length(); i++) {
                int color = RAINBOW_COLORS[(i + offset) % RAINBOW_COLORS.length];
                rainbow.append(Component.literal(String.valueOf(title.charAt(i)))
                        .withStyle(s -> s.withColor(TextColor.fromRgb(color))));
            }
            tooltip.add(rainbow);
        } else {
            tooltip.add(Component.literal(title));
        }

        switch (rotationLevel) {
            case 1, 5 -> {
                String capKey = rotationLevel == 1 ? ServerConfig.ROT1_CAP : ServerConfig.ROT5_CAP;
                String targetKey = rotationLevel == 1 ? ServerConfig.ROT1_N90 : ServerConfig.ROT5_N90;
                String prefix = "nine_turn_ring.rotation." + rotationLevel;
                tooltip.add(Component.translatable(prefix + ".desc", number(1 + balance(data, capKey)),
                        (int) balance(data, targetKey)));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.growth_rule"));
                if (data != null) {
                    double bonus = rotationLevel == 1 ? data.getPowerDamageBonus() : data.getHealthBonus();
                    int kills = rotationLevel == 1 ? data.getPowerKillCount() : data.getHealthKillCount();
                    tooltip.add(Component.translatable(prefix + ".bonus", number(bonus * 100)));
                    tooltip.add(Component.translatable(prefix + ".kills", kills));
                }
            }
            case 2 -> tooltip.add(Component.translatable("nine_turn_ring.rotation.2.desc",
                    (int) balance(data, ServerConfig.ROT2_FOOD_FLOOR), percent(data, ServerConfig.SPECIALIST_EFFECT_REDUCTION)));
            case 3 -> tooltip.add(Component.translatable("nine_turn_ring.rotation.3.desc",
                    percent(data, ServerConfig.SPECIALIST_EFFECT_REDUCTION)));
            case 4 -> {
                tooltip.add(Component.translatable("nine_turn_ring.rotation.4.desc",
                        number(balance(data, ServerConfig.ROT4_COMBAT_HEAL)),
                        seconds(data, ServerConfig.ROT4_REST_TICKS), number(balance(data, ServerConfig.ROT4_REST_HEAL))));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.4.effects",
                        percent(data, ServerConfig.SPECIALIST_EFFECT_REDUCTION)));
            }
            case 6 -> {
                tooltip.add(Component.translatable("nine_turn_ring.rotation.6.desc", percent(data, ServerConfig.ROT6_REDUCTION)));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.bypass_rule"));
            }
            case 7 -> {
                tooltip.add(Component.translatable("nine_turn_ring.rotation.7.desc", percent(data, ServerConfig.ROT7_HEAL),
                        seconds(data, ServerConfig.ROT7_INVINCIBLE_TICKS), seconds(data, ServerConfig.ROT7_COOLDOWN_TICKS)));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.7.priority"));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.bypass_rule"));
                if (data != null) {
                    long now = data.getOnlineTicks();
                    if (data.isInvincible(now)) tooltip.add(Component.translatable("nine_turn_ring.rotation.7.invincible",
                            number((data.getInvincibleEnd() - now) / 20.0)));
                    tooltip.add(data.isInCooldown(now)
                            ? Component.translatable("nine_turn_ring.rotation.7.cooldown", number((data.getUndyingCooldownEnd() - now) / 20.0))
                            : Component.translatable("nine_turn_ring.rotation.7.ready"));
                }
            }
            case 8 -> tooltip.add(Component.translatable("nine_turn_ring.rotation.8.desc"));
            case 9 -> {
                tooltip.add(Component.translatable("nine_turn_ring.rotation.9.desc",
                        percent(data, ServerConfig.ROT9_TRIGGER_RATIO), percent(data, ServerConfig.ROT9_SHIELD_RATIO),
                        seconds(data, ServerConfig.ROT9_SHIELD_TICKS), seconds(data, ServerConfig.ROT9_COOLDOWN_TICKS)));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.9.rule"));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.bypass_rule"));
                if (data != null) {
                    long now = data.getOnlineTicks();
                    if (data.getEmergencyShield() > 0 && data.getShieldEndTick() > now) {
                        tooltip.add(Component.translatable("nine_turn_ring.hud.shield", number(data.getEmergencyShield()),
                                number((data.getShieldEndTick() - now) / 20.0)));
                    }
                    tooltip.add(data.getShieldCooldownEnd() > now
                            ? Component.translatable("nine_turn_ring.hud.cooldown", number((data.getShieldCooldownEnd() - now) / 20.0))
                            : Component.translatable("nine_turn_ring.hud.ready"));
                }
            }
            case 10 -> {
                tooltip.add(Component.translatable("nine_turn_ring.rotation.10.desc1",
                        percent(data, ServerConfig.ROT10_DAMAGE_CAP), (int) balance(data, ServerConfig.ROT10_HITS90),
                        seconds(data, ServerConfig.ROT10_STACK_TICKS)));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.10.valid_hit",
                        number(balance(data, ServerConfig.ROT10_MIN_DAMAGE)), percent(data, ServerConfig.ROT10_MIN_HEALTH_RATIO)));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.10.desc2",
                        percent(data, ServerConfig.ROT10_EFFECT_CAP), number(balance(data, ServerConfig.ROT10_EFFECT_SECONDS90))));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.10.effects_rule"));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.10.flight",
                        (int) balance(data, ServerConfig.ROT10_MATURITY_COUNT), seconds(data, ServerConfig.COMBAT_TICKS),
                        seconds(data, ServerConfig.FLIGHT_SLOW_FALL_TICKS)));
                tooltip.add(Component.translatable("nine_turn_ring.rotation.bypass_rule"));
                if (data != null) appendAdaptationDetails(tooltip, data);
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }

    private static void appendAdaptationDetails(List<Component> tooltip, IPlayerData data) {
        long now = data.getOnlineTicks();
        int maturity = (int) data.getBalanceValue(ServerConfig.ROT10_MATURITY_COUNT);
        long interval = (long) data.getBalanceValue(ServerConfig.ROT10_STACK_TICKS);
        tooltip.add(Component.translatable("nine_turn_ring.rotation.10.adapting"));
        if (data.getAllAdaptationLevels().isEmpty()) {
            tooltip.add(Component.translatable("nine_turn_ring.rotation.10.no_damage"));
        }
        for (Map.Entry<String, Integer> entry : data.getAllAdaptationLevels().entrySet()) {
            String name = com.jiuzhuan.util.NameUtil.getDamageTypeName(entry.getKey());
            tooltip.add(Component.translatable("nine_turn_ring.rotation.10.damage_entry", name, entry.getValue(), maturity,
                    number(data.getAdaptationReduction(entry.getKey()) * 100), percent(data, ServerConfig.ROT10_DAMAGE_CAP)));
            Long lastHit = data.getAllAdaptationTimes().get(entry.getKey());
            long remain = lastHit == null ? 0 : Math.max(0, interval - (now - lastHit));
            MutableComponent status = Component.literal("  ");
            if (entry.getValue() >= maturity) status.append(Component.translatable("nine_turn_ring.screen.mature")).append(" ");
            status.append(Component.translatable(remain > 0 ? "nine_turn_ring.screen.cooldown" : "nine_turn_ring.screen.stackable",
                    number(remain / 20.0)));
            if (data.isDamageAdaptationDisabled(entry.getKey())) status.append(" ").append(Component.translatable("nine_turn_ring.screen.disabled"));
            tooltip.add(status);
        }
        if (!data.getAllEffectExposureTicks().isEmpty()) {
            tooltip.add(Component.translatable("nine_turn_ring.rotation.10.effect_header"));
            for (Map.Entry<String, Integer> entry : data.getAllEffectExposureTicks().entrySet()) {
                String name = com.jiuzhuan.util.NameUtil.getEffectName(entry.getKey());
                MutableComponent line = Component.translatable("nine_turn_ring.rotation.10.effect_entry", name,
                        number(entry.getValue() / 20.0), number(data.getEffectAdaptationReduction(entry.getKey()) * 100),
                        percent(data, ServerConfig.ROT10_EFFECT_CAP));
                if (data.isEffectAdaptationDisabled(entry.getKey())) line.append(" ").append(Component.translatable("nine_turn_ring.screen.disabled"));
                tooltip.add(line);
            }
        }
        if (data.hasFlightAdaptation()) {
            tooltip.add(data.getCombatEndTick() > now
                    ? Component.translatable("nine_turn_ring.hud.flight_combat", number((data.getCombatEndTick() - now) / 20.0))
                    : Component.translatable("nine_turn_ring.hud.flight_ready"));
        }
    }

    private static double balance(@Nullable IPlayerData data, String key) {
        return data == null ? ServerConfig.getBalanceValue(key) : data.getBalanceValue(key);
    }

    private static String percent(@Nullable IPlayerData data, String key) {
        return number(balance(data, key) * 100);
    }

    private static String seconds(@Nullable IPlayerData data, String key) {
        return number(balance(data, key) / 20.0);
    }

    private static String number(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
    @OnlyIn(Dist.CLIENT)
    private IPlayerData getClientPlayerData() {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null) {
            return player.getCapability(PlayerDataProvider.PLAYER_DATA).resolve().orElse(null);
        }
        return null;
    }
}
