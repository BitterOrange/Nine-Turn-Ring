package com.jiuzhuan.capability;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public interface IPlayerData {
    // 戒指是否装备
    boolean isRingEquipped();
    void setRingEquipped(boolean equipped);

    // 十转激活状态 (1-10)
    boolean isActivated(int rotation);
    void setActivated(int rotation, boolean activated);

    // 1转：击杀数（力量成长）
    int getPowerKillCount();
    void addPowerKill(int count);
    double getPowerDamageBonus();

    // 5转：击杀数（血量成长）
    int getHealthKillCount();
    void addHealthKill(int count);
    double getHealthBonus();

    // Online gameplay time: persisted, never advances while logged out.
    long getOnlineTicks();
    void tickOnlineTime();
    long getCombatEndTick();
    void setCombatEndTick(long tick);
    long getLastCombatTick();
    void markCombat();
    double getBalanceValue(String key);

    // 7转：不死
    long getUndyingCooldownEnd();
    void setUndyingCooldownEnd(long time);
    long getInvincibleEnd();
    void setInvincibleEnd(long time);
    boolean isInCooldown(long now);
    boolean isInvincible(long now);

    // Nine: finite emergency shield, independent of vanilla absorption hearts.
    float getEmergencyShield();
    void setEmergencyShield(float amount);
    long getShieldEndTick();
    void setShieldEndTick(long tick);
    long getShieldCooldownEnd();
    void setShieldCooldownEnd(long tick);

    // 10转：适应
    int getAdaptationLevel(String damageType);
    void addAdaptation(String damageType, long now);
    double getAdaptationReduction(String damageType);
    Map<String, Integer> getAllAdaptationLevels();
    Map<String, Long> getAllAdaptationTimes();
    // Damage level is a permanent qualifying-hit count; times are online ticks.
    // A mature type reaches the configured finite milestone, never immunity.
    int getCompletedAdaptationCount();
    // Whether the damage type has reached the maturity milestone.
    boolean hasFullAdaptation(String damageType);
    // 是否满足飞行适应条件（完成3种伤害类型适应）
    boolean hasFlightAdaptation();
    void setFlightUnlocked(boolean value);
    // 伤害类型适应禁用列表（右键关闭后不再继续适应）
    boolean isDamageAdaptationDisabled(String damageType);
    void setDamageAdaptationDisabled(String damageType, boolean disabled);
    java.util.Set<String> getDisabledDamageTypes();
    // ===== 十转：负面效果适应 =====
    // Permanent actual exposure, in gameplay ticks. Reduces finite duration only.
    double getEffectAdaptationReduction(String effectId);
    Map<String, Integer> getAllEffectExposureTicks();
    int getEffectExposureTicks(String effectId);
    void addEffectExposureTicks(String effectId, int ticks);
    // 负面效果适应禁用列表（右键关闭后不再继续适应）
    boolean isEffectAdaptationDisabled(String effectId);
    void setEffectAdaptationDisabled(String effectId, boolean disabled);
    java.util.Set<String> getDisabledEffectTypes();
    // 是否已播放过"我适应了大地"提示（避免重复）
    boolean hasAnnouncedFlightAdaptation();
    void setAnnouncedFlightAdaptation(boolean value);

    // 模组是否授予了飞行能力（用于区分模组飞行与其他模组飞行，避免冲突）
    boolean isFlightGrantedByMod();
    void setFlightGrantedByMod(boolean value);

    // 首箱标记
    boolean hasOpenedFirstChest();
    void setOpenedFirstChest(boolean value);

    // ===== 自然获得计数 =====
    // 二转：饥饿死亡计数
    int getHungerDeathCount();
    void addHungerDeath();
    boolean hasNatallyGotRot2();
    void setNatallyGotRot2(boolean value);

    // 三转：黑暗/低亮度死亡计数
    int getDarkDeathCount();
    void addDarkDeath();
    boolean hasNatallyGotRot3();
    void setNatallyGotRot3(boolean value);
    // 三转：最后一次受伤时是否处于黑暗/低亮度
    boolean isLastDamageInDark();
    void setLastDamageInDark(boolean value);
    // 三转：夜视手动开关状态
    boolean isNightVisionEnabled();
    void setNightVisionEnabled(boolean enabled);

    // 四转：中毒/凋灵死亡计数
    int getPoisonDeathCount();
    void addPoisonDeath();
    boolean hasNatallyGotRot4();
    void setNatallyGotRot4(boolean value);

    // 五转：是否已击杀凋灵
    boolean hasKilledWither();
    void setKilledWither(boolean value);
    // 九转：是否已击杀末影龙
    boolean hasKilledEnderDragon();
    void setKilledEnderDragon(boolean value);

    // 六转：魔法/虚空/真实伤害死亡计数
    int getMagicDeathCount();
    void addMagicDeath();
    boolean hasNatallyGotRot6();
    void setNatallyGotRot6(boolean value);

    // 七转：佩戴不死图腾死亡计数
    int getTotemDeathCount();
    void addTotemDeath();
    boolean hasTotemDeathThisCycle();
    void setTotemDeathThisCycle(boolean value);
    boolean hasNatallyGotRot7();
    void setNatallyGotRot7(boolean value);
    // 七转：图腾效果标记（触发后5秒内死亡才算）
    boolean isTotemEffectActive();
    void setTotemEffectActive(boolean value);
    long getTotemEffectEndTime();
    void setTotemEffectEndTime(long time);
    // 是否已播放过图腾祝福提示（避免重复）
    boolean hasAnnouncedTotemBlessing();
    void setAnnouncedTotemBlessing(boolean value);

    // 八转：钓鱼垃圾计数
    int getJunkFishCount();
    void addJunkFish();
    boolean hasNatallyGotRot8();
    void setNatallyGotRot8(boolean value);

    // 九转：连续不死图腾触发计数（间隔5s内）
    int getConsecutiveTotemCount();
    void setConsecutiveTotemCount(int count);
    long getLastTotemTriggerTime();
    void setLastTotemTriggerTime(long time);
    int getTotemChainDeathCount(); // 满足连续条件后的死亡计数
    void addTotemChainDeath();
    boolean hasNatallyGotRot9();
    void setNatallyGotRot9(boolean value);

    // 十转：是否已自然获得
    boolean hasNatallyGotRot10();
    void setNatallyGotRot10(boolean value);

    // 进度授予标记（避免重复）
    boolean hasAdvancement(String key);
    void setAdvancement(String key, boolean value);

    // 维度切换修复延迟（临时字段，不持久化）：维度切换后等几tick再重建轮转槽和同步
    int getDimensionFixDelay();
    void setDimensionFixDelay(int ticks);

    // ===== 饰品防收取快照 =====
    java.util.Map<String, net.minecraft.nbt.CompoundTag> getAccessorySnapshot();
    void setAccessorySnapshot(java.util.Map<String, net.minecraft.nbt.CompoundTag> snapshot);
    void putAccessory(String slotIdentifier, net.minecraft.nbt.CompoundTag itemTag);
    void clearAccessorySnapshot();

    // 同步到客户端
    void syncToClient(Player player);

    // NBT
    CompoundTag saveNBT();
    void loadNBT(CompoundTag tag);
    void loadClientNBT(CompoundTag tag);
}
