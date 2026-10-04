package com.jiuzhuan.event;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.RegistryObject;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioUnequipEvent;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;

import java.util.*;

/**
 * 饰品防收取处理器
 *
 * 允许通过 Curios 界面手动卸下，拒绝界面外的摘取权限查询并禁止丢弃。
 * 快照每 5 tick 检查绕过权限查询的物品栏改动；实际手动卸下时移除对应快照。
 * Curios 不提供卸装原因，防强制摘取只能尽力保护，不能覆盖其他模组的任意直接写入。
 */
public class AccessoryProtectionHandler {

    // 需要保护的物品RegistryObject列表（引用本身安全，.get()需等注册完成后调用）
    @SuppressWarnings("unchecked")
    private static final RegistryObject<net.minecraft.world.item.Item>[] PROTECTED_REFS = new RegistryObject[]{
            ModItems.NINE_TURN_RING,
            ModItems.ROTATION_1_POWER,
            ModItems.ROTATION_2_SATIETY,
            ModItems.ROTATION_3_NIGHT_VISION,
            ModItems.ROTATION_4_REGEN,
            ModItems.ROTATION_5_HEALTH,
            ModItems.ROTATION_6_RESISTANCE,
            ModItems.ROTATION_7_UNDYING,
            ModItems.ROTATION_8_LUCK,
            ModItems.ROTATION_9_IMMORTAL,
            ModItems.ROTATION_10_ADAPTATION
    };

    // 懒加载：首次调用时才构建实际Item集合（此时注册已完成）
    private static volatile Set<net.minecraft.world.item.Item> protectedItemsCache = null;
    // Permission queries may be simulated. Keep a short-lived candidate, never mutate snapshots
    // until onUnequip confirms the stack actually left. This also survives immediately closing GUI.
    private static final Map<UUID, Map<String, UnequipCheck>> manualUnequipChecks = new HashMap<>();
    private record UnequipCheck(ItemStack stack, int tick) {
        boolean isRecent(int now) { return now - tick >= 0 && now - tick <= 2; }
    }

    private static Set<net.minecraft.world.item.Item> getProtectedItems() {
        Set<net.minecraft.world.item.Item> cache = protectedItemsCache;
        if (cache != null) return cache;
        synchronized (AccessoryProtectionHandler.class) {
            cache = protectedItemsCache;
            if (cache != null) return cache;
            cache = new HashSet<>();
            for (RegistryObject<net.minecraft.world.item.Item> ref : PROTECTED_REFS) {
                if (ref.isPresent()) {
                    cache.add(ref.get());
                }
            }
            protectedItemsCache = cache;
            return cache;
        }
    }

    /**
     * 判断物品是否为受保护的九转戒/轮转物品
     */
    public static boolean isProtectedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return getProtectedItems().contains(stack.getItem());
    }

    /**
     * 第三层防御：阻止受保护物品被玩家扔出
     */
    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        ItemStack stack = event.getEntity().getItem();
        if (isProtectedItem(stack)) {
            event.setCanceled(true);
        }
    }

    /**
     * 这是权限查询，不是卸装通知，也可能由右键装备或模拟提取触发。
     * 该事件只有 HasResult，不能 setCanceled；这里绝不删除饰品快照。
     */
    @SubscribeEvent
    public void onCurioUnequip(CurioUnequipEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;
        ItemStack stack = event.getStack();
        if (!isProtectedItem(stack)) return;
        // 死亡导致的卸下（轮转物品死亡掉落）一律放行，不取消、不删快照
        if (player.isDeadOrDying()) return;
        boolean isManual = player.containerMenu instanceof CuriosContainer;
        if (!isManual) {
            event.setResult(Event.Result.DENY);
            return;
        }
        String slotKey = event.getSlotContext().identifier() + ":" + event.getSlotContext().index();
        manualUnequipChecks.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
                .put(slotKey, new UnequipCheck(stack.copy(), player.tickCount));
    }

    /**
     * 仅在实际卸下轮转物品时提交此前的界面权限查询。
     */
    public static void onAccessoryUnequipped(SlotContext context, ItemStack stack) {
        if (!(context.entity() instanceof Player player) || player.level().isClientSide) return;
        Map<String, UnequipCheck> checks = manualUnequipChecks.get(player.getUUID());
        if (checks == null) return;
        String key = context.identifier() + ":" + context.index();
        UnequipCheck check = checks.remove(key);
        if (checks.isEmpty()) manualUnequipChecks.remove(player.getUUID());
        if (check != null && check.isRecent(player.tickCount) && ItemStack.isSameItemSameTags(check.stack(), stack)) {
            player.getCapability(PlayerDataProvider.PLAYER_DATA)
                    .ifPresent(data -> data.getAccessorySnapshot().remove(key));
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        manualUnequipChecks.remove(event.getEntity().getUUID());
    }

    /**
     * Tick级快照监控和槽位保护，不从背包自动装备。
     * 每5tick扫描一次，确保所有受保护物品始终在Curios槽位中，且槽位数量不被篡改
     * LOWEST优先级：确保在其他模组（如Boss禁饰品）之后执行，强制恢复被篡改的状态
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;
        Map<String, UnequipCheck> checks = manualUnequipChecks.get(player.getUUID());
        if (checks != null) {
            checks.values().removeIf(check -> !check.isRecent(player.tickCount));
            if (checks.isEmpty()) manualUnequipChecks.remove(player.getUUID());
        }
        // 玩家死亡流程中不干预饰品：保证轮转按 Curios 规则正常掉落（九转戒由 ALWAYS_KEEP 自行保留，无需恢复）
        if (player.isDeadOrDying()) return;
        if (player.tickCount % 5 != 0) return; // 每5tick检查一次（0.25秒）

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            // 只在戒指装备状态下启用防护（戒指没装备时轮转槽也不存在）
            if (!data.isRingEquipped()) return;

            try {
                Optional<ICuriosItemHandler> curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
                if (curiosOpt.isEmpty()) return;
                ICuriosItemHandler inv = curiosOpt.get();

                // ===== 第六层：槽位数量保护 =====
                // 防止Boss/其他模组通过减少槽位数量来"封禁"饰品
                // 检查 rotation 槽位数量，戒指装备时必须为10
                var rotationHandler = inv.getCurios().get("rotation");
                int rotationSlots = rotationHandler != null ? rotationHandler.getSlots() : 0;
                if (rotationSlots != 10) {
                    // 重新应用我们的槽位修饰符（内部会先移除旧的再添加，避免叠加）
                    PlayerDataProvider.setRotationSlots(player, 10);
                    // 刷新引用
                    curiosOpt = CuriosApi.getCuriosInventory(player).resolve();
                    if (curiosOpt.isPresent()) {
                        inv = curiosOpt.get();
                    }
                }

                // ===== 第一步：扫描当前Curios栏中所有受保护物品，更新快照 =====
                Map<String, ItemStack> currentEquipped = new HashMap<>();
                for (var entry : inv.getCurios().entrySet()) {
                    String identifier = entry.getKey();
                    var handler = entry.getValue();
                    for (int i = 0; i < handler.getSlots(); i++) {
                        ItemStack stack = handler.getStacks().getStackInSlot(i);
                        if (isProtectedItem(stack)) {
                            String slotKey = identifier + ":" + i;
                            currentEquipped.put(slotKey, stack);
                            // 更新快照（保存NBT，用于恢复）
                            net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                            stack.save(tag);
                            data.putAccessory(slotKey, tag);
                        }
                    }
                }

                // ===== 第二步：检查快照中是否有物品被移除，若有则恢复 =====
                boolean restored = false;
                for (Map.Entry<String, net.minecraft.nbt.CompoundTag> snapEntry : data.getAccessorySnapshot().entrySet()) {
                    String slotKey = snapEntry.getKey();
                    if (currentEquipped.containsKey(slotKey)) continue; // 还在原位，跳过

                    // 该槽位的受保护物品不见了，需要恢复
                    int separator = slotKey.lastIndexOf(':');
                    if (separator < 0) continue;
                    String identifier = slotKey.substring(0, separator);
                    int slotIdx;
                    try {
                        slotIdx = Integer.parseInt(slotKey.substring(separator + 1));
                    } catch (NumberFormatException e) {
                        continue;
                    }

                    // 从NBT恢复物品
                    ItemStack restoredStack = ItemStack.of(snapEntry.getValue());
                    if (restoredStack.isEmpty()) continue;

                    // 检查目标槽位是否存在且为空
                    var handler = inv.getCurios().get(identifier);
                    if (handler == null || slotIdx < 0 || slotIdx >= handler.getSlots()) continue;

                    ItemStack currentInSlot = handler.getStacks().getStackInSlot(slotIdx);
                    if (currentInSlot.isEmpty()) {
                        // 槽位空了，直接放回去
                        handler.getStacks().setStackInSlot(slotIdx, restoredStack);
                        restored = true;
                    } else if (isProtectedItem(currentInSlot)) {
                        // 槽位被另一个受保护物品占据（可能是换位了），更新快照即可
                        net.minecraft.nbt.CompoundTag newTag = new net.minecraft.nbt.CompoundTag();
                        currentInSlot.save(newTag);
                        data.putAccessory(slotKey, newTag);
                    } else {
                        // 槽位被其他物品占据，把入侵者移到背包，再放回受保护物品
                        ItemStack intruder = currentInSlot.copy();
                        handler.getStacks().setStackInSlot(slotIdx, ItemStack.EMPTY);
                        if (!player.getInventory().add(intruder)) {
                            player.spawnAtLocation(intruder);
                        }
                        handler.getStacks().setStackInSlot(slotIdx, restoredStack);
                        restored = true;
                    }
                }

                if (restored) {
                    data.syncToClient(player);
                    // 静默恢复，不发送聊天提示
                }

            } catch (Exception ignored) {
                // Curios API 调用异常时静默跳过，不影响游戏
            }
        });
    }
}
