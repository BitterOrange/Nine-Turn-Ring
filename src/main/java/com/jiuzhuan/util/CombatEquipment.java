package com.jiuzhuan.util;

import com.jiuzhuan.item.ModItems;
import com.jiuzhuan.item.RotationItem;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

public final class CombatEquipment {
    private CombatEquipment() {}

    public static boolean bypassesRingDefenses(DamageSource source) {
        String id = source.getMsgId();
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || id.equals("outOfWorld") || id.equals("genericKill");
    }

    /** Check temporary defensive abilities against actual slots, not cached activation flags. */
    public static boolean hasEquippedRotation(Player player, int rotation) {
        var inventory = CuriosApi.getCuriosInventory(player).resolve();
        if (inventory.isEmpty()) return false;
        boolean ringPresent = false;
        for (var handler : inventory.get().getCurios().values()) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (handler.getStacks().getStackInSlot(slot).is(ModItems.NINE_TURN_RING.get())) {
                    ringPresent = true;
                    break;
                }
            }
            if (ringPresent) break;
        }
        if (!ringPresent) return false;
        var rotationSlots = inventory.get().getCurios().get("rotation");
        if (rotationSlots == null) return false;
        for (int slot = 0; slot < rotationSlots.getSlots(); slot++) {
            ItemStack stack = rotationSlots.getStacks().getStackInSlot(slot);
            if (stack.getItem() instanceof RotationItem item && item.getRotationLevel() == rotation) return true;
        }
        return false;
    }
}
