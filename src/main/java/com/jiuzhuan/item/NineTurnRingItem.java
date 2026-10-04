package com.jiuzhuan.item;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.util.AdvancementUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio.DropRule;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NineTurnRingItem extends Item implements ICurioItem {
    public NineTurnRingItem(Properties properties) {
        super(properties);
    }

    // 附魔闪烁光泽
    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    // 不可摧毁：无法被任何伤害破坏（熔岩/仙人掌/爆炸/虚空等）
    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFireResistant() {
        return true;
    }

    // 允许玩家主动卸下；强制摘取的保护由饰品事件处理器负责。
    @Override
    public boolean canUnequip(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    // 死亡保留：即使关闭死亡不掉落，九转戒也由 Curios 原生保留在戒指槽，不离槽、不掉落（对齐七咒之戒）
    @Override
    public DropRule getDropRule(SlotContext slotContext, DamageSource source, int lootingLevel, boolean recentlyHit, ItemStack stack) {
        return DropRule.ALWAYS_KEEP;
    }

    // 允许手持右键直接装备到戒指槽
    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    // 戒指无法被丢弃
    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        return false;
    }

    // 死亡保留：禁止附上消失诅咒。
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(book);
        if (enchants.containsKey(Enchantments.VANISHING_CURSE)) return false;
        return super.isBookEnchantable(stack, book);
    }

    // 装备戒指时：开启10个轮转槽位（只在真正装备时调用）
    @Override
    public void onEquip(SlotContext slotContext, ItemStack prevStack, ItemStack stack) {
        if (prevStack.is(this)) return;
        LivingEntity entity = slotContext.entity();
        if (entity instanceof Player player && !player.level().isClientSide) {
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                // Older external unbinding patches could leave a ring snapshot behind.
                if (!data.isRingEquipped()) data.clearAccessorySnapshot();
                data.setRingEquipped(true);
                data.syncToClient(player);
            });
            // 授予"无敌之始"进度
            if (player instanceof ServerPlayer sp) {
                AdvancementUtil.grant(sp, "root", "equip_ring");
            }
            PlayerDataProvider.setRotationSlots(player, 10);
        }
    }

    // 卸下戒指时：物品弹回背包，关闭轮转槽位（只在真正卸下时调用）
    @Override
    public void onUnequip(SlotContext slotContext, ItemStack newStack, ItemStack stack) {
        // Curios also calls this when only the equipped stack's NBT changes.
        if (newStack.is(this)) return;
        LivingEntity entity = slotContext.entity();
        if (entity instanceof Player player && !player.level().isClientSide) {
            // 先停用能力并清除旧快照，避免卸下后再次佩戴时恢复出重复物品。
            // 成长、永久解锁和在线冷却均保留。
            player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
                data.setRingEquipped(false);
                for (int i = 1; i <= 10; i++) data.setActivated(i, false);
                data.clearAccessorySnapshot();
                data.syncToClient(player);
            });
            // 先把轮转槽里的物品弹回背包
            Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
            if (curiosOpt.isPresent()) {
                ICuriosItemHandler inv = curiosOpt.get();
                inv.getCurios().forEach((identifier, handler) -> {
                    if ("rotation".equals(identifier)) {
                        for (int i = 0; i < handler.getSlots(); i++) {
                            ItemStack slotStack = handler.getStacks().getStackInSlot(i);
                            if (!slotStack.isEmpty()) {
                                ItemStack returned = slotStack.copy();
                                handler.getStacks().setStackInSlot(i, ItemStack.EMPTY);
                                player.getInventory().add(returned);
                                if (!returned.isEmpty()) player.spawnAtLocation(returned);
                            }
                        }
                    }
                });
            }
            // 关闭轮转槽位
            PlayerDataProvider.setRotationSlots(player, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("nine_turn_ring.ring.desc.removable"));
        tooltip.add(Component.translatable("nine_turn_ring.ring.desc.slots"));
        tooltip.add(Component.translatable("nine_turn_ring.ring.desc.protection_manual"));
        tooltip.add(Component.translatable("nine_turn_ring.ring.desc.death_keep"));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
