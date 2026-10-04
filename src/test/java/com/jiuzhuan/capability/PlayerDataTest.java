package com.jiuzhuan.capability;

import com.jiuzhuan.config.ServerConfig;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDataTest {
    @Test void qualifyingHitsHaveAnOnlineCooldownAndGrowthIsPermanent() {
        PlayerData data = new PlayerData();
        data.addAdaptation("mob", 0);
        data.addAdaptation("mob", 99);
        assertEquals(1, data.getAdaptationLevel("mob"));
        for (int i = 0; i < 100; i++) data.tickOnlineTime();
        data.addAdaptation("mob", data.getOnlineTicks());
        assertEquals(2, data.getAdaptationLevel("mob"));
        data.setDamageAdaptationDisabled("mob", true);
        data.addAdaptation("mob", 200);
        assertEquals(2, data.getAdaptationLevel("mob"));
        PlayerData copy = new PlayerData();
        copy.loadNBT(data.saveNBT());
        assertEquals(2, copy.getAdaptationLevel("mob"));
        assertEquals(100, copy.getOnlineTicks());
        assertTrue(copy.isDamageAdaptationDisabled("mob"));
    }

    @Test void shieldAndRescueCooldownsPersistButUnequippingClearsProtection() {
        PlayerData data = new PlayerData();
        data.setRingEquipped(true);
        data.setActivated(7, true);
        data.setActivated(9, true);
        data.setUndyingCooldownEnd(3600);
        data.setInvincibleEnd(30);
        data.setShieldCooldownEnd(1800);
        data.setEmergencyShield(4);
        data.setShieldEndTick(120);
        PlayerData copy = new PlayerData();
        copy.loadNBT(data.saveNBT());
        assertEquals(4, copy.getEmergencyShield());
        assertEquals(3600, copy.getUndyingCooldownEnd());
        copy.setActivated(7, false);
        copy.setActivated(9, false);
        assertEquals(0, copy.getInvincibleEnd());
        assertEquals(0, copy.getEmergencyShield());
        assertEquals(0, copy.getShieldEndTick());
        assertEquals(3600, copy.getUndyingCooldownEnd());
        assertEquals(1800, copy.getShieldCooldownEnd());
        data.setRingEquipped(false);
        assertEquals(0, data.getInvincibleEnd());
        assertEquals(0, data.getEmergencyShield());
        assertEquals(1800, data.getShieldCooldownEnd());
    }

    @Test void legacySaveIsMigratedOnceWithoutGrantingNewMaturity() {
        CompoundTag legacy = new CompoundTag();
        CompoundTag damage = new CompoundTag();
        for (String key : new String[]{"mob", "fall", "inFire"}) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("lvl", 10);
            entry.putLong("time", System.currentTimeMillis());
            damage.put(key, entry);
        }
        legacy.put("adaptation", damage);
        CompoundTag effects = new CompoundTag();
        CompoundTag effect = new CompoundTag();
        effect.putInt("lvl", 5);
        effects.put("minecraft:slowness", effect);
        legacy.put("effectAdaptation", effects);
        PlayerData data = new PlayerData();
        legacy.putLong("undyingCd", System.currentTimeMillis() + 20000);
        legacy.putLong("invincibleEnd", System.currentTimeMillis() + 10000);
        data.loadNBT(legacy);
        assertEquals(10, data.getAdaptationLevel("mob"));
        assertEquals(0, data.getCompletedAdaptationCount());
        assertEquals(500, data.getEffectExposureTicks("minecraft:slowness"));
        assertTrue(data.getAdaptationReduction("mob") < 0.062);
        assertTrue(data.getEffectAdaptationReduction("minecraft:slowness") < 0.053);
        assertTrue(data.hasFlightAdaptation());
        assertTrue(data.getUndyingCooldownEnd() >= 1790 && data.getUndyingCooldownEnd() <= 1800);
        assertEquals(30, data.getInvincibleEnd());
        CompoundTag migrated = data.saveNBT();
        assertEquals(PlayerData.DATA_VERSION, migrated.getInt("dataVersion"));
        PlayerData copy = new PlayerData();
        copy.loadNBT(migrated);
        assertEquals(500, copy.getEffectExposureTicks("minecraft:slowness"));
        assertEquals(data.getUndyingCooldownEnd(), copy.getUndyingCooldownEnd());
        assertTrue(copy.hasFlightAdaptation());
        assertFalse(copy.getAllAdaptationTimes().containsKey("mob"));
        copy.addAdaptation("mob", 0);
        assertEquals(11, copy.getAdaptationLevel("mob"));
    }

    @Test void clientSnapshotNeverContaminatesServerConfigurationOrSavedData() {
        PlayerData data = new PlayerData();
        data.addPowerKill(1000);
        CompoundTag packet = data.saveNBT();
        CompoundTag snapshot = new CompoundTag();
        snapshot.putDouble(ServerConfig.ROT1_CAP, 1);
        snapshot.putDouble(ServerConfig.ROT1_N90, 1000);
        packet.put("balanceSnapshot", snapshot);
        PlayerData client = new PlayerData();
        client.loadClientNBT(packet);
        assertEquals(0.9, client.getPowerDamageBonus(), 1e-12);
        assertEquals(1.35, data.getPowerDamageBonus(), 1e-12);
        assertFalse(client.saveNBT().contains("balanceSnapshot"));
        client.loadNBT(packet);
        assertEquals(1.35, client.getPowerDamageBonus(), 1e-12);
    }

    @Test void allExposureIncludingTheFirstPartialIntervalSurvivesSaving() {
        PlayerData data = new PlayerData();
        data.addEffectExposureTicks("minecraft:slowness", 1);
        PlayerData copy = new PlayerData();
        copy.loadNBT(data.saveNBT());
        assertEquals(1, copy.getEffectExposureTicks("minecraft:slowness"));
        copy.addEffectExposureTicks("minecraft:slowness", Integer.MAX_VALUE);
        assertEquals(Integer.MAX_VALUE, copy.getEffectExposureTicks("minecraft:slowness"));
        assertTrue(copy.getEffectAdaptationReduction("minecraft:slowness") < 0.3);
    }

    @Test void extremeCountersAndMalformedNegativeCountsCannotOverflowGrowth() {
        PlayerData data = new PlayerData();
        data.addPowerKill(Integer.MAX_VALUE);
        data.addPowerKill(1);
        data.addHealthKill(Integer.MAX_VALUE);
        data.addHealthKill(1);
        assertEquals(Integer.MAX_VALUE, data.getPowerKillCount());
        assertEquals(Integer.MAX_VALUE, data.getHealthKillCount());
        assertTrue(data.getPowerDamageBonus() < 1.5);
        assertTrue(data.getHealthBonus() < 0.3);
        CompoundTag saved = data.saveNBT();
        saved.putLong("onlineTicks", Long.MAX_VALUE);
        saved.putInt("powerKills", -10);
        data.loadNBT(saved);
        data.tickOnlineTime();
        data.markCombat();
        assertEquals(Long.MAX_VALUE, data.getOnlineTicks());
        assertEquals(Long.MAX_VALUE, data.getCombatEndTick());
        assertEquals(0, data.getPowerKillCount());
    }

    @Test void freshPlayersAreRestedAndMaturityUnlocksAnIndependentFlightFlag() {
        PlayerData data = new PlayerData();
        assertTrue(data.getOnlineTicks() - data.getLastCombatTick() >= ServerConfig.getRot4RestTicks());
        for (String type : new String[]{"mob", "fall", "inFire"}) {
            for (int hit = 0; hit < 100; hit++) data.addAdaptation(type, hit * 100L);
        }
        assertEquals(3, data.getCompletedAdaptationCount());
        assertTrue(data.hasFlightAdaptation());
        assertTrue(data.saveNBT().getBoolean("flightUnlocked"));
        data.markCombat();
        assertEquals(400, data.getCombatEndTick());
    }
}
