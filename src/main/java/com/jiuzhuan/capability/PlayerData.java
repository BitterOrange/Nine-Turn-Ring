package com.jiuzhuan.capability;

import com.jiuzhuan.config.ServerConfig;
import com.jiuzhuan.network.NetworkHandler;
import com.jiuzhuan.network.SyncPlayerDataPacket;
import com.jiuzhuan.util.BalanceMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PlayerData implements IPlayerData {
    public static final int DATA_VERSION = 2;
    private boolean ringEquipped = false;
    private final boolean[] activated = new boolean[11]; // index 1-10

    private int powerKillCount = 0;
    private int healthKillCount = 0;

    private long undyingCooldownEnd = 0;
    private long invincibleEnd = 0;
    private long onlineTicks;
    private long combatEndTick;
    private long lastCombatTick = -ServerConfig.getRot4RestTicks();
    private float emergencyShield;
    private long shieldEndTick;
    private long shieldCooldownEnd;
    private boolean flightUnlocked;
    // This map is loaded only by the client packet, never saved or shared globally.
    private transient Map<String, Double> clientBalanceSnapshot;

    private final Map<String, Integer> adaptationLevels = new HashMap<>();
    private final Map<String, Long> adaptationTimes = new HashMap<>();

    private boolean openedFirstChest = false;

    // ===== 十转：负面效果适应 =====
    private final Map<String, Integer> effectExposureTicks = new HashMap<>();
    private final java.util.Set<String> disabledDamageTypes = new java.util.HashSet<>();
    private final java.util.Set<String> disabledEffectTypes = new java.util.HashSet<>();
    // ===== 饰品防收取：已装备的九转戒/轮转物品快照（用于被强制摘取后自动恢复） =====
    private final java.util.Map<String, net.minecraft.nbt.CompoundTag> accessorySnapshot = new java.util.HashMap<>();

    // ===== 自然获得计数 =====
    private int hungerDeathCount = 0;
    private boolean natallyGotRot2 = false;
    private int darkDeathCount = 0;
    private boolean natallyGotRot3 = false;
    private boolean lastDamageInDark = false;
    private boolean nightVisionEnabled = true; // 三转夜视手动开关，默认开启
    private int poisonDeathCount = 0;
    private boolean natallyGotRot4 = false;
    private boolean killedWither = false;
    private boolean killedEnderDragon = false;
    private int magicDeathCount = 0;
    private boolean natallyGotRot6 = false;
    private int totemDeathCount = 0;
    private boolean totemDeathThisCycle = false;
    private boolean natallyGotRot7 = false;
    private boolean totemEffectActive = false;
    private long totemEffectEndTime = 0;
    private boolean announcedTotemBlessing = false;
    private int junkFishCount = 0;
    private boolean natallyGotRot8 = false;
    private int consecutiveTotemCount = 0;
    private long lastTotemTriggerTime = 0;
    private int totemChainDeathCount = 0;
    private boolean natallyGotRot9 = false;
    private boolean natallyGotRot10 = false;
    private boolean announcedFlightAdaptation = false;
    private boolean flightGrantedByMod = false;
    private final Set<String> grantedAdvancements = new HashSet<>();
    // 维度切换修复延迟（临时字段，不持久化）
    private transient int dimensionFixDelay = 0;

    @Override public boolean isRingEquipped() { return ringEquipped; }
    @Override public void setRingEquipped(boolean equipped) {
        this.ringEquipped = equipped;
        if (!equipped) {
            invincibleEnd = 0;
            emergencyShield = 0;
            shieldEndTick = 0;
        }
    }

    @Override
    public boolean isActivated(int rotation) {
        if (rotation < 1 || rotation > 10) return false;
        return activated[rotation];
    }

    @Override
    public void setActivated(int rotation, boolean value) {
        if (rotation >= 1 && rotation <= 10) activated[rotation] = value;
        if (!value && rotation == 7) invincibleEnd = 0;
        if (!value && rotation == 9) {
            emergencyShield = 0;
            shieldEndTick = 0;
        }
    }

    @Override public int getPowerKillCount() { return powerKillCount; }
    @Override public void addPowerKill(int count) { this.powerKillCount = BalanceMath.saturatingAdd(powerKillCount, count); }
    @Override public double getPowerDamageBonus() {
        return BalanceMath.curve(getBalanceValue(ServerConfig.ROT1_CAP), powerKillCount,
                getBalanceValue(ServerConfig.ROT1_N90));
    }

    @Override public int getHealthKillCount() { return healthKillCount; }
    @Override public void addHealthKill(int count) { this.healthKillCount = BalanceMath.saturatingAdd(healthKillCount, count); }
    @Override public double getHealthBonus() {
        return BalanceMath.curve(getBalanceValue(ServerConfig.ROT5_CAP), healthKillCount,
                getBalanceValue(ServerConfig.ROT5_N90));
    }

    @Override public long getOnlineTicks() { return onlineTicks; }
    @Override public void tickOnlineTime() { onlineTicks = BalanceMath.saturatingAdd(onlineTicks, 1); }
    @Override public long getCombatEndTick() { return combatEndTick; }
    @Override public void setCombatEndTick(long tick) { combatEndTick = Math.max(0, tick); }
    @Override public long getLastCombatTick() { return lastCombatTick; }
    @Override public void markCombat() {
        lastCombatTick = onlineTicks;
        combatEndTick = BalanceMath.saturatingAdd(onlineTicks, ServerConfig.getCombatTicks());
    }
    @Override public double getBalanceValue(String key) {
        if (clientBalanceSnapshot != null && clientBalanceSnapshot.containsKey(key)) return clientBalanceSnapshot.get(key);
        return ServerConfig.getBalanceValue(key);
    }

    @Override public long getUndyingCooldownEnd() { return undyingCooldownEnd; }
    @Override public void setUndyingCooldownEnd(long time) { this.undyingCooldownEnd = Math.max(0, time); }
    @Override public long getInvincibleEnd() { return invincibleEnd; }
    @Override public void setInvincibleEnd(long time) { this.invincibleEnd = Math.max(0, time); }
    @Override public boolean isInCooldown(long now) { return now < undyingCooldownEnd; }
    @Override public boolean isInvincible(long now) { return now < invincibleEnd; }
    @Override public float getEmergencyShield() { return emergencyShield; }
    @Override public void setEmergencyShield(float amount) { emergencyShield = Float.isFinite(amount) ? Math.max(0, amount) : 0; }
    @Override public long getShieldEndTick() { return shieldEndTick; }
    @Override public void setShieldEndTick(long tick) { shieldEndTick = Math.max(0, tick); }
    @Override public long getShieldCooldownEnd() { return shieldCooldownEnd; }
    @Override public void setShieldCooldownEnd(long tick) { shieldCooldownEnd = Math.max(0, tick); }

    @Override public int getAdaptationLevel(String damageType) { return adaptationLevels.getOrDefault(damageType, 0); }

    @Override
    public void addAdaptation(String damageType, long now) {
        if (isDamageAdaptationDisabled(damageType)) return;
        now = Math.max(0, now);
        Long lastTime = adaptationTimes.get(damageType);
        long interval = ServerConfig.getRot10StackCooldownTicks();
        if (lastTime != null && (now < lastTime || now - lastTime < interval)) return;
        int current = adaptationLevels.getOrDefault(damageType, 0);
        adaptationLevels.put(damageType, BalanceMath.saturatingAdd(current, 1));
        adaptationTimes.put(damageType, now);
        if (getCompletedAdaptationCount() >= 3) flightUnlocked = true;
    }

    @Override
    public double getAdaptationReduction(String damageType) {
        return BalanceMath.curve(getBalanceValue(ServerConfig.ROT10_DAMAGE_CAP), getAdaptationLevel(damageType),
                getBalanceValue(ServerConfig.ROT10_HITS90));
    }

    @Override public Map<String, Integer> getAllAdaptationLevels() { return adaptationLevels; }
    @Override public Map<String, Long> getAllAdaptationTimes() { return adaptationTimes; }
    @Override public int getCompletedAdaptationCount() {
        int count = 0;
        for (int level : adaptationLevels.values()) {
            if (level >= getBalanceValue(ServerConfig.ROT10_MATURITY_COUNT)) count++;
        }
        return count;
    }
    @Override public boolean hasFullAdaptation(String damageType) {
        return getAdaptationLevel(damageType) >= getBalanceValue(ServerConfig.ROT10_MATURITY_COUNT);
    }
    @Override public boolean hasFlightAdaptation() {
        if (getCompletedAdaptationCount() >= 3) flightUnlocked = true;
        return flightUnlocked;
    }
    @Override public void setFlightUnlocked(boolean value) { flightUnlocked = value; }
    @Override public boolean isDamageAdaptationDisabled(String damageType) { return disabledDamageTypes.contains(damageType); }
    @Override public void setDamageAdaptationDisabled(String damageType, boolean disabled) {
        if (disabled) disabledDamageTypes.add(damageType); else disabledDamageTypes.remove(damageType);
    }
    @Override public java.util.Set<String> getDisabledDamageTypes() { return new java.util.HashSet<>(disabledDamageTypes); }
    // ===== 十转：负面效果适应 =====
    @Override
    public double getEffectAdaptationReduction(String effectId) {
        return BalanceMath.curve(getBalanceValue(ServerConfig.ROT10_EFFECT_CAP), getEffectExposureTicks(effectId) / 20.0,
                getBalanceValue(ServerConfig.ROT10_EFFECT_SECONDS90));
    }
    @Override public Map<String, Integer> getAllEffectExposureTicks() { return effectExposureTicks; }
    @Override public int getEffectExposureTicks(String effectId) {
        return effectExposureTicks.getOrDefault(effectId, 0);
    }
    @Override public void addEffectExposureTicks(String effectId, int ticks) {
        if (ticks <= 0 || isEffectAdaptationDisabled(effectId)) return;
        effectExposureTicks.put(effectId, BalanceMath.saturatingAdd(effectExposureTicks.getOrDefault(effectId, 0), ticks));
    }
    @Override public boolean isEffectAdaptationDisabled(String effectId) { return disabledEffectTypes.contains(effectId); }
    @Override public void setEffectAdaptationDisabled(String effectId, boolean disabled) {
        if (disabled) disabledEffectTypes.add(effectId); else disabledEffectTypes.remove(effectId);
    }
    @Override public java.util.Set<String> getDisabledEffectTypes() { return new java.util.HashSet<>(disabledEffectTypes); }
    @Override public boolean hasAnnouncedFlightAdaptation() { return announcedFlightAdaptation; }
    @Override public void setAnnouncedFlightAdaptation(boolean value) { this.announcedFlightAdaptation = value; }
    @Override public boolean isFlightGrantedByMod() { return flightGrantedByMod; }
    @Override public void setFlightGrantedByMod(boolean value) { this.flightGrantedByMod = value; }
    @Override public boolean hasOpenedFirstChest() { return openedFirstChest; }
    @Override public void setOpenedFirstChest(boolean value) { this.openedFirstChest = value; }

    // 自然获得
    @Override public int getHungerDeathCount() { return hungerDeathCount; }
    @Override public void addHungerDeath() { this.hungerDeathCount++; }
    @Override public boolean hasNatallyGotRot2() { return natallyGotRot2; }
    @Override public void setNatallyGotRot2(boolean value) { this.natallyGotRot2 = value; }

    @Override public int getDarkDeathCount() { return darkDeathCount; }
    @Override public void addDarkDeath() { this.darkDeathCount++; }
    @Override public boolean hasNatallyGotRot3() { return natallyGotRot3; }
    @Override public void setNatallyGotRot3(boolean value) { this.natallyGotRot3 = value; }
    @Override public boolean isLastDamageInDark() { return lastDamageInDark; }
    @Override public void setLastDamageInDark(boolean value) { this.lastDamageInDark = value; }
    @Override public boolean isNightVisionEnabled() { return nightVisionEnabled; }
    @Override public void setNightVisionEnabled(boolean enabled) { this.nightVisionEnabled = enabled; }

    @Override public int getPoisonDeathCount() { return poisonDeathCount; }
    @Override public void addPoisonDeath() { this.poisonDeathCount++; }
    @Override public boolean hasNatallyGotRot4() { return natallyGotRot4; }
    @Override public void setNatallyGotRot4(boolean value) { this.natallyGotRot4 = value; }

    @Override public boolean hasKilledWither() { return killedWither; }
    @Override public void setKilledWither(boolean value) { this.killedWither = value; }
    @Override public boolean hasKilledEnderDragon() { return killedEnderDragon; }
    @Override public void setKilledEnderDragon(boolean value) { this.killedEnderDragon = value; }

    @Override public int getMagicDeathCount() { return magicDeathCount; }
    @Override public void addMagicDeath() { this.magicDeathCount++; }
    @Override public boolean hasNatallyGotRot6() { return natallyGotRot6; }
    @Override public void setNatallyGotRot6(boolean value) { this.natallyGotRot6 = value; }

    @Override public int getTotemDeathCount() { return totemDeathCount; }
    @Override public void addTotemDeath() { this.totemDeathCount++; }
    @Override public boolean hasTotemDeathThisCycle() { return totemDeathThisCycle; }
    @Override public void setTotemDeathThisCycle(boolean value) { this.totemDeathThisCycle = value; }
    @Override public boolean hasNatallyGotRot7() { return natallyGotRot7; }
    @Override public void setNatallyGotRot7(boolean value) { this.natallyGotRot7 = value; }
    @Override public boolean isTotemEffectActive() { return totemEffectActive; }
    @Override public void setTotemEffectActive(boolean value) { this.totemEffectActive = value; }
    @Override public long getTotemEffectEndTime() { return totemEffectEndTime; }
    @Override public void setTotemEffectEndTime(long time) { this.totemEffectEndTime = time; }
    @Override public boolean hasAnnouncedTotemBlessing() { return announcedTotemBlessing; }
    @Override public void setAnnouncedTotemBlessing(boolean value) { this.announcedTotemBlessing = value; }

    @Override public int getJunkFishCount() { return junkFishCount; }
    @Override public void addJunkFish() { this.junkFishCount++; }
    @Override public boolean hasNatallyGotRot8() { return natallyGotRot8; }
    @Override public void setNatallyGotRot8(boolean value) { this.natallyGotRot8 = value; }

    @Override public int getConsecutiveTotemCount() { return consecutiveTotemCount; }
    @Override public void setConsecutiveTotemCount(int count) { this.consecutiveTotemCount = count; }
    @Override public long getLastTotemTriggerTime() { return lastTotemTriggerTime; }
    @Override public void setLastTotemTriggerTime(long time) { this.lastTotemTriggerTime = time; }
    @Override public int getTotemChainDeathCount() { return totemChainDeathCount; }
    @Override public void addTotemChainDeath() { this.totemChainDeathCount++; }
    @Override public boolean hasNatallyGotRot9() { return natallyGotRot9; }
    @Override public void setNatallyGotRot9(boolean value) { this.natallyGotRot9 = value; }

    @Override public boolean hasNatallyGotRot10() { return natallyGotRot10; }
    @Override public void setNatallyGotRot10(boolean value) { this.natallyGotRot10 = value; }

    @Override public boolean hasAdvancement(String key) { return grantedAdvancements.contains(key); }
    @Override public void setAdvancement(String key, boolean value) {
        if (value) grantedAdvancements.add(key); else grantedAdvancements.remove(key);
    }
    @Override public int getDimensionFixDelay() { return dimensionFixDelay; }
    @Override public void setDimensionFixDelay(int ticks) { this.dimensionFixDelay = ticks; }

    // ===== 饰品防收取快照 =====
    @Override
    public java.util.Map<String, net.minecraft.nbt.CompoundTag> getAccessorySnapshot() {
        return accessorySnapshot;
    }
    @Override
    public void setAccessorySnapshot(java.util.Map<String, net.minecraft.nbt.CompoundTag> snapshot) {
        accessorySnapshot.clear();
        accessorySnapshot.putAll(snapshot);
    }
    @Override
    public void putAccessory(String slotIdentifier, net.minecraft.nbt.CompoundTag itemTag) {
        accessorySnapshot.put(slotIdentifier, itemTag);
    }
    @Override
    public void clearAccessorySnapshot() {
        accessorySnapshot.clear();
    }

    @Override
    public void syncToClient(Player player) {
        if (player instanceof ServerPlayer sp) {
            CompoundTag packetData = saveNBT();
            CompoundTag balance = new CompoundTag();
            ServerConfig.snapshot().forEach(balance::putDouble);
            packetData.put("balanceSnapshot", balance);
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sp), new SyncPlayerDataPacket(packetData));
        }
    }

    @Override
    public CompoundTag saveNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("dataVersion", DATA_VERSION);
        tag.putLong("onlineTicks", onlineTicks);
        tag.putLong("combatEndTick", combatEndTick);
        tag.putLong("lastCombatTick", lastCombatTick);
        tag.putFloat("emergencyShield", emergencyShield);
        tag.putLong("shieldEndTick", shieldEndTick);
        tag.putLong("shieldCooldownEnd", shieldCooldownEnd);
        tag.putBoolean("flightUnlocked", hasFlightAdaptation());
        tag.putBoolean("ringEquipped", ringEquipped);
        CompoundTag actTag = new CompoundTag();
        for (int i = 1; i <= 10; i++) actTag.putBoolean("rot_" + i, activated[i]);
        tag.put("activated", actTag);
        tag.putInt("powerKills", powerKillCount);
        tag.putInt("healthKills", healthKillCount);
        tag.putLong("undyingCd", undyingCooldownEnd);
        tag.putLong("invincibleEnd", invincibleEnd);
        tag.putBoolean("firstChest", openedFirstChest);

        CompoundTag adaptTag = new CompoundTag();
        for (Map.Entry<String, Integer> e : adaptationLevels.entrySet()) {
            CompoundEntry entry = new CompoundEntry();
            entry.level = e.getValue();
            entry.time = adaptationTimes.getOrDefault(e.getKey(), -1L);
            adaptTag.put(e.getKey(), entry.save());
        }
        tag.put("adaptation", adaptTag);
        // 十转：负面效果适应
        CompoundTag exposureTag = new CompoundTag();
        effectExposureTicks.forEach(exposureTag::putInt);
        tag.put("effectExposureTicks", exposureTag);

        // 适应禁用列表
        net.minecraft.nbt.ListTag disabledDmg = new net.minecraft.nbt.ListTag();
        for (String s : disabledDamageTypes) disabledDmg.add(net.minecraft.nbt.StringTag.valueOf(s));
        tag.put("disabledDamage", disabledDmg);
        net.minecraft.nbt.ListTag disabledEff = new net.minecraft.nbt.ListTag();
        for (String s : disabledEffectTypes) disabledEff.add(net.minecraft.nbt.StringTag.valueOf(s));
        tag.put("disabledEffect", disabledEff);

        tag.putInt("hungerDeaths", hungerDeathCount);
        tag.putBoolean("gotRot2", natallyGotRot2);
        tag.putInt("darkDeaths", darkDeathCount);
        tag.putBoolean("gotRot3", natallyGotRot3);
        tag.putBoolean("lastDamageInDark", lastDamageInDark);
        tag.putBoolean("nightVisionEnabled", nightVisionEnabled);
        tag.putInt("poisonDeaths", poisonDeathCount);
        tag.putBoolean("gotRot4", natallyGotRot4);
        tag.putBoolean("killedWither", killedWither);
        tag.putBoolean("killedEnderDragon", killedEnderDragon);
        tag.putInt("magicDeaths", magicDeathCount);
        tag.putBoolean("gotRot6", natallyGotRot6);
        tag.putInt("totemDeaths", totemDeathCount);
        tag.putBoolean("gotRot7", natallyGotRot7);
        tag.putBoolean("totemEffectActive", totemEffectActive);
        tag.putLong("totemEffectEnd", totemEffectEndTime);
        tag.putBoolean("announcedTotemBlessing", announcedTotemBlessing);
        tag.putInt("junkFish", junkFishCount);
        tag.putBoolean("gotRot8", natallyGotRot8);
        tag.putInt("consecTotem", consecutiveTotemCount);
        tag.putLong("lastTotemTime", lastTotemTriggerTime);
        tag.putInt("totemChainDeaths", totemChainDeathCount);
        tag.putBoolean("gotRot9", natallyGotRot9);
        tag.putBoolean("gotRot10", natallyGotRot10);
        tag.putBoolean("announcedFlight", announcedFlightAdaptation);
        tag.putBoolean("flightGrantedByMod", flightGrantedByMod);

        CompoundTag advTag = new CompoundTag();
        for (String key : grantedAdvancements) advTag.putBoolean(key, true);
        tag.put("advancements", advTag);
        // 饰品防收取快照
        CompoundTag snapTag = new CompoundTag();
        for (Map.Entry<String, CompoundTag> e : accessorySnapshot.entrySet()) {
            snapTag.put(e.getKey(), e.getValue());
        }
        tag.put("accessorySnapshot", snapTag);
        return tag;
    }

    @Override
    public void loadNBT(CompoundTag tag) {
        clientBalanceSnapshot = null;
        boolean legacy = tag.getInt("dataVersion") < DATA_VERSION;
        onlineTicks = legacy ? 0 : Math.max(0, tag.getLong("onlineTicks"));
        combatEndTick = legacy ? 0 : Math.max(0, tag.getLong("combatEndTick"));
        lastCombatTick = legacy || !tag.contains("lastCombatTick") ? -ServerConfig.getRot4RestTicks()
                : Math.max(-ServerConfig.getRot4RestTicks(), Math.min(onlineTicks, tag.getLong("lastCombatTick")));
        setEmergencyShield(legacy ? 0 : tag.getFloat("emergencyShield"));
        shieldEndTick = legacy ? 0 : Math.max(0, tag.getLong("shieldEndTick"));
        shieldCooldownEnd = legacy ? 0 : Math.max(0, tag.getLong("shieldCooldownEnd"));
        flightUnlocked = !legacy && tag.getBoolean("flightUnlocked");
        ringEquipped = tag.getBoolean("ringEquipped");
        CompoundTag actTag = tag.getCompound("activated");
        for (int i = 1; i <= 10; i++) activated[i] = actTag.getBoolean("rot_" + i);
        powerKillCount = Math.max(0, tag.getInt("powerKills"));
        healthKillCount = Math.max(0, tag.getInt("healthKills"));
        if (legacy) {
            long nowMillis = System.currentTimeMillis();
            undyingCooldownEnd = BalanceMath.migrateCooldown(tag.getLong("undyingCd"), nowMillis, ServerConfig.getLegacyRescueCooldownMillis(),
                    ServerConfig.getRot7CooldownTicks());
            invincibleEnd = BalanceMath.migrateCooldown(tag.getLong("invincibleEnd"), nowMillis, 1500,
                    Math.min(30, ServerConfig.getRot7InvincibleTicks()));
        } else {
            undyingCooldownEnd = Math.max(0, tag.getLong("undyingCd"));
            invincibleEnd = Math.max(0, tag.getLong("invincibleEnd"));
        }
        openedFirstChest = tag.getBoolean("firstChest");

        adaptationLevels.clear();
        adaptationTimes.clear();
        if (tag.contains("adaptation")) {
            CompoundTag adaptTag = tag.getCompound("adaptation");
            for (String key : adaptTag.getAllKeys()) {
                CompoundEntry entry = CompoundEntry.load(adaptTag.getCompound(key));
                adaptationLevels.put(key, Math.max(0, entry.level));
                if (!legacy && entry.time >= 0) adaptationTimes.put(key, Math.min(onlineTicks, entry.time));
            }
        }
        // 十转：负面效果适应
        effectExposureTicks.clear();
        if (legacy && tag.contains("effectAdaptation")) {
            CompoundTag effectAdaptTag = tag.getCompound("effectAdaptation");
            for (String key : effectAdaptTag.getAllKeys()) {
                EffectEntry entry = EffectEntry.load(effectAdaptTag.getCompound(key));
                effectExposureTicks.put(key, BalanceMath.migrateEffectExposure(entry.level, entry.exposure));
            }
        } else if (!legacy) {
            CompoundTag exposureTag = tag.getCompound("effectExposureTicks");
            for (String key : exposureTag.getAllKeys()) effectExposureTicks.put(key, Math.max(0, exposureTag.getInt(key)));
        }
        // 适应禁用列表（必须先清空，否则同步时旧数据残留）
        disabledDamageTypes.clear();
        disabledEffectTypes.clear();
        if (tag.contains("disabledDamage")) {
            net.minecraft.nbt.ListTag list = tag.getList("disabledDamage", 8);
            for (int i = 0; i < list.size(); i++) disabledDamageTypes.add(list.getString(i));
        }
        if (tag.contains("disabledEffect")) {
            net.minecraft.nbt.ListTag list = tag.getList("disabledEffect", 8);
            for (int i = 0; i < list.size(); i++) disabledEffectTypes.add(list.getString(i));
        }

        hungerDeathCount = tag.getInt("hungerDeaths");
        natallyGotRot2 = tag.getBoolean("gotRot2");
        darkDeathCount = tag.getInt("darkDeaths");
        natallyGotRot3 = tag.getBoolean("gotRot3");
        lastDamageInDark = tag.getBoolean("lastDamageInDark");
        nightVisionEnabled = tag.getBoolean("nightVisionEnabled");
        // 兼容旧存档：没有该字段时默认开启
        if (!tag.contains("nightVisionEnabled")) nightVisionEnabled = true;
        poisonDeathCount = tag.getInt("poisonDeaths");
        natallyGotRot4 = tag.getBoolean("gotRot4");
        killedWither = tag.getBoolean("killedWither");
        killedEnderDragon = tag.getBoolean("killedEnderDragon");
        magicDeathCount = tag.getInt("magicDeaths");
        natallyGotRot6 = tag.getBoolean("gotRot6");
        totemDeathCount = tag.getInt("totemDeaths");
        natallyGotRot7 = tag.getBoolean("gotRot7");
        totemEffectActive = tag.getBoolean("totemEffectActive");
        totemEffectEndTime = tag.getLong("totemEffectEnd");
        announcedTotemBlessing = tag.getBoolean("announcedTotemBlessing");
        junkFishCount = tag.getInt("junkFish");
        natallyGotRot8 = tag.getBoolean("gotRot8");
        consecutiveTotemCount = tag.getInt("consecTotem");
        lastTotemTriggerTime = tag.getLong("lastTotemTime");
        totemChainDeathCount = tag.getInt("totemChainDeaths");
        natallyGotRot9 = tag.getBoolean("gotRot9");
        natallyGotRot10 = tag.getBoolean("gotRot10");
        announcedFlightAdaptation = tag.getBoolean("announcedFlight");
        flightGrantedByMod = tag.getBoolean("flightGrantedByMod");
        if (legacy) {
            long legacyMatureTypes = adaptationLevels.values().stream().filter(count -> count >= 10).count();
            flightUnlocked = announcedFlightAdaptation || flightGrantedByMod || legacyMatureTypes >= 3;
        }

        grantedAdvancements.clear();
        if (tag.contains("advancements")) {
            CompoundTag advTag = tag.getCompound("advancements");
            for (String key : advTag.getAllKeys())
                if (advTag.getBoolean(key)) grantedAdvancements.add(key);
        }
        // 饰品防收取快照
        accessorySnapshot.clear();
        if (tag.contains("accessorySnapshot")) {
            CompoundTag snapTag = tag.getCompound("accessorySnapshot");
            for (String key : snapTag.getAllKeys()) {
                accessorySnapshot.put(key, snapTag.getCompound(key));
            }
        }
    }

    @Override
    public void loadClientNBT(CompoundTag tag) {
        loadNBT(tag);
        if (tag.contains("balanceSnapshot")) {
            CompoundTag balance = tag.getCompound("balanceSnapshot");
            clientBalanceSnapshot = new HashMap<>();
            for (String key : ServerConfig.snapshot().keySet()) {
                if (balance.contains(key)) {
                    double value = balance.getDouble(key);
                    if (Double.isFinite(value) && value >= 0) clientBalanceSnapshot.put(key, value);
                }
            }
        }
    }

    private static class CompoundEntry {
        int level;
        long time;
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("lvl", level);
            if (time >= 0) t.putLong("time", time);
            return t;
        }
        static CompoundEntry load(CompoundTag t) {
            CompoundEntry e = new CompoundEntry();
            e.level = t.getInt("lvl");
            e.time = t.contains("time") ? t.getLong("time") : -1;
            return e;
        }
    }
    // Legacy-only effect entry; new saves store permanent exposure directly.
    private static class EffectEntry {
        int level;
        long time;
        int exposure;
        static EffectEntry load(CompoundTag t) {
            EffectEntry e = new EffectEntry();
            e.level = t.getInt("lvl");
            e.time = t.getLong("time");
            e.exposure = t.getInt("exp");
            return e;
        }
    }
}
