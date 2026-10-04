package com.jiuzhuan.client;

import com.jiuzhuan.capability.PlayerDataProvider;
import com.jiuzhuan.config.ServerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import com.jiuzhuan.network.NetworkHandler;
import com.jiuzhuan.network.ToggleAdaptationPacket;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * 适应详情界面
 * 左侧显示伤害适应，右侧显示负面效果适应，两侧均支持滚轮上下滚动查看
 */
public class AdaptationScreen extends Screen {
    private static final int SCREEN_WIDTH = 340;
    private static final int SCREEN_HEIGHT = 220;
    private static final int PANEL_PADDING = 8;
    private static final int PANEL_GAP = 8;
    private static final int TITLE_HEIGHT = 18;
    private static final int LINE_HEIGHT = 11;
    private static final int ENTRY_HEIGHT = LINE_HEIGHT * 3 + 4;
    private static final int SCROLL_STEP = 20;

    private final int panelWidth = (SCREEN_WIDTH - PANEL_PADDING * 3 - PANEL_GAP) / 2;
    private final int panelHeight = SCREEN_HEIGHT - PANEL_PADDING * 2 - TITLE_HEIGHT - 30;

    private int leftX, rightX, panelY;
    private int leftScroll = 0;
    private int rightScroll = 0;

    private List<AdaptEntry> leftEntries = new ArrayList<>();
    private List<AdaptEntry> rightEntries = new ArrayList<>();

    private long lastRefreshTime = 0L;
    private static final long REFRESH_INTERVAL_MS = 50L;

    public static String getEffectName(String id) {
        String shortName = id.contains(":") ? id.substring(id.indexOf(":") + 1) : id;
        String key = "nine_turn_ring.effect." + shortName;
        String translated = Component.translatable(key).getString();
        // 如果翻译不存在，返回原始短名
        return translated.equals(key) ? shortName : translated;
    }

    public AdaptationScreen() {
        super(Component.translatable("nine_turn_ring.screen.title"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        leftX = centerX - SCREEN_WIDTH / 2 + PANEL_PADDING;
        rightX = leftX + panelWidth + PANEL_GAP;
        panelY = centerY - SCREEN_HEIGHT / 2 + 32;

        addRenderableWidget(Button.builder(Component.translatable("nine_turn_ring.screen.close"), b -> onClose())
                .bounds(centerX - 35, centerY + SCREEN_HEIGHT / 2 - 24, 70, 18).build());

        loadData();
        leftScroll = 0;
        rightScroll = 0;
        lastRefreshTime = System.currentTimeMillis();
    }

    private void loadData() {
        leftEntries.clear();
        rightEntries.clear();
        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        player.getCapability(PlayerDataProvider.PLAYER_DATA).ifPresent(data -> {
            long now = data.getOnlineTicks();
            int maturity = (int) data.getBalanceValue(ServerConfig.ROT10_MATURITY_COUNT);
            long stackTicks = (long) data.getBalanceValue(ServerConfig.ROT10_STACK_TICKS);

            // 左侧：伤害适应
            Map<String, Integer> dmgLevels = data.getAllAdaptationLevels();
            Map<String, Long> dmgTimes = data.getAllAdaptationTimes();
            for (Map.Entry<String, Integer> e : dmgLevels.entrySet()) {
                int lvl = e.getValue();
                if (lvl <= 0) continue;
                String name = RotationOverlay.getDamageTypeName(e.getKey());
                double reduction = data.getAdaptationReduction(e.getKey()) * 100;
                Long lastTime = dmgTimes.get(e.getKey());
                long remain = lastTime == null ? 0 : Math.max(0, stackTicks - (now - lastTime));
                String status = Component.translatable(remain == 0 ? "nine_turn_ring.screen.stackable"
                        : "nine_turn_ring.screen.cooldown", number(remain / 20.0)).getString();
                String progress = Component.translatable("nine_turn_ring.screen.hit_progress", lvl, maturity).getString();
                if (lvl >= maturity) progress += " " + Component.translatable("nine_turn_ring.screen.mature").getString();
                String info = Component.translatable("nine_turn_ring.screen.damage_reduction", number(reduction),
                        number(data.getBalanceValue(ServerConfig.ROT10_DAMAGE_CAP) * 100)).getString();
                boolean dmgDisabled = data.isDamageAdaptationDisabled(e.getKey());
                leftEntries.add(new AdaptEntry(e.getKey(), name, lvl, progress, info, status, lvl >= maturity, dmgDisabled));
            }
            leftEntries.sort(Comparator.comparingInt((AdaptEntry a) -> a.level).reversed());

            // 右侧：负面效果适应
            Map<String, Integer> exposure = data.getAllEffectExposureTicks();
            for (Map.Entry<String, Integer> e : exposure.entrySet()) {
                int ticks = e.getValue();
                if (ticks <= 0) continue;
                String name = getEffectName(e.getKey());
                double seconds90 = data.getBalanceValue(ServerConfig.ROT10_EFFECT_SECONDS90);
                String progress = Component.translatable("nine_turn_ring.screen.effect_progress", number(ticks / 20.0)).getString();
                String status = Component.translatable("nine_turn_ring.screen.effect_target", number(seconds90)).getString();
                String effectInfo = Component.translatable("nine_turn_ring.screen.effect_reduction",
                        number(data.getEffectAdaptationReduction(e.getKey()) * 100),
                        number(data.getBalanceValue(ServerConfig.ROT10_EFFECT_CAP) * 100)).getString();
                boolean effDisabled = data.isEffectAdaptationDisabled(e.getKey());
                rightEntries.add(new AdaptEntry(e.getKey(), name, ticks, progress, effectInfo, status,
                        ticks / 20.0 >= seconds90, effDisabled));
            }
            rightEntries.sort(Comparator.comparingInt((AdaptEntry a) -> a.level).reversed());
        });
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        if (now - lastRefreshTime >= REFRESH_INTERVAL_MS) {
            loadData();
            lastRefreshTime = now;
        }

        this.renderBackground(graphics);
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int bgX = centerX - SCREEN_WIDTH / 2;
        int bgY = centerY - SCREEN_HEIGHT / 2;

        graphics.fill(bgX, bgY, bgX + SCREEN_WIDTH, bgY + SCREEN_HEIGHT, 0xEE000000);
        drawBorder(graphics, bgX, bgY, SCREEN_WIDTH, SCREEN_HEIGHT, 0xFFD4AF37);

        // 标题
        String title = Component.translatable("nine_turn_ring.screen.title").getString();
        graphics.drawCenteredString(this.font, title, centerX, bgY + 6, 0xFFFFFF);

        // 左右面板标题
        String leftTitle = Component.translatable("nine_turn_ring.screen.damage_adapt").getString();
        String rightTitle = Component.translatable("nine_turn_ring.screen.effect_adapt").getString();
        graphics.drawString(this.font, leftTitle, leftX, panelY - 16, 0xFFFFFF, false);
        graphics.drawString(this.font, rightTitle, rightX, panelY - 16, 0xFFFFFF, false);

        renderPanel(graphics, leftX, panelY, panelWidth, panelHeight, leftEntries, leftScroll, mouseX, mouseY, true);
        renderPanel(graphics, rightX, panelY, panelWidth, panelHeight, rightEntries, rightScroll, mouseX, mouseY, false);

        renderScrollBar(graphics, leftX, panelY, panelWidth, panelHeight, leftEntries.size(), leftScroll);
        renderScrollBar(graphics, rightX, panelY, panelWidth, panelHeight, rightEntries.size(), rightScroll);

        // 底部统计
        String stats = Component.translatable("nine_turn_ring.screen.stats", leftEntries.size(), rightEntries.size()).getString();
        graphics.drawString(this.font, stats, bgX + PANEL_PADDING, bgY + SCREEN_HEIGHT - 15, 0xAAAAAA, false);

        super.render(graphics, mouseX, mouseY, partialTick);
        AdaptEntry hovered = findEntryAt(mouseX, mouseY);
        if (hovered != null) {
            graphics.renderTooltip(this.font, List.of(Component.literal(hovered.name),
                    Component.literal(hovered.progress), Component.literal(hovered.info), Component.literal(hovered.status),
                    Component.translatable("nine_turn_ring.screen.toggle_hint")), java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private void renderPanel(GuiGraphics graphics, int x, int y, int w, int h,
                             List<AdaptEntry> entries, int scroll, int mouseX, int mouseY, boolean isLeft) {
        graphics.fill(x, y, x + w, y + h, 0x66000000);
        drawBorder(graphics, x, y, w, h, 0xFF555555);

        graphics.enableScissor(x, y, x + w, y + h);
        int curY = y + 4 - scroll;
        for (AdaptEntry entry : entries) {
            if (curY + ENTRY_HEIGHT < y || curY > y + h) {
                curY += ENTRY_HEIGHT;
                continue;
            }
            // 禁用条目加红色半透明背景遮罩
            if (entry.disabled) {
                graphics.fill(x + 1, curY - 1, x + w - 1, curY + ENTRY_HEIGHT - 1, 0x33FF0000);
            }
            String namePrefix = entry.disabled ? "§8§m" : (entry.mature ? "§6" : "§7");
            String line1 = namePrefix + entry.name;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(line1, w - 12), x + 4, curY, 0xFFFFFF, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.info, w - 12), x + 4, curY + LINE_HEIGHT,
                    entry.disabled ? 0x555555 : 0xCCCCCC, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(entry.progress, w - 12), x + 4, curY + LINE_HEIGHT * 2,
                    entry.disabled ? 0x555555 : 0xCCCCCC, false);
            curY += ENTRY_HEIGHT;
        }

        if (entries.isEmpty()) {
            String empty = Component.translatable("nine_turn_ring.screen.empty").getString();
            graphics.drawCenteredString(this.font, empty, x + w / 2, y + h / 2 - 5, 0x888888);
        }
        graphics.disableScissor();
    }

    private void renderScrollBar(GuiGraphics graphics, int x, int y, int w, int h, int total, int scroll) {
        int contentHeight = total * ENTRY_HEIGHT + 4;
        if (contentHeight <= h) return;
        int barHeight = Math.max(20, (int) ((float) h / contentHeight * h));
        int maxScroll = contentHeight - h;
        int barY = y + (int) ((float) scroll / maxScroll * (h - barHeight));
        graphics.fill(x + w - 4, y, x + w, y + h, 0x44FFFFFF);
        graphics.fill(x + w - 4, barY, x + w, barY + barHeight, 0xFFD4AF37);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 收敛成长没有满级，所有已有记录均可右键切换。
        if (button == 1) {
            AdaptEntry clicked = findEntryAt(mouseX, mouseY);
            if (clicked != null) {
                boolean isLeft = mouseX >= leftX && mouseX <= leftX + panelWidth;
                boolean newDisabled = !clicked.disabled;
                NetworkHandler.INSTANCE.send(PacketDistributor.SERVER.noArg(),
                        new ToggleAdaptationPacket(isLeft, clicked.id, newDisabled));
                String msgKey = newDisabled ? "nine_turn_ring.message.adapt_disabled" : "nine_turn_ring.message.adapt_enabled";
                if (getMinecraft().player != null) {
                    getMinecraft().player.displayClientMessage(net.minecraft.network.chat.Component.translatable(msgKey, clicked.name), false);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private AdaptEntry findEntryAt(double mouseX, double mouseY) {
        // 检查左面板
        if (mouseX >= leftX && mouseX <= leftX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            int curY = panelY + 4 - leftScroll;
            for (AdaptEntry entry : leftEntries) {
                if (mouseY >= curY && mouseY <= curY + ENTRY_HEIGHT) {
                    return entry;
                }
                curY += ENTRY_HEIGHT;
            }
        }
        // 检查右面板
        if (mouseX >= rightX && mouseX <= rightX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            int curY = panelY + 4 - rightScroll;
            for (AdaptEntry entry : rightEntries) {
                if (mouseY >= curY && mouseY <= curY + ENTRY_HEIGHT) {
                    return entry;
                }
                curY += ENTRY_HEIGHT;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= leftX && mouseX <= leftX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            int contentHeight = leftEntries.size() * ENTRY_HEIGHT + 4;
            int maxScroll = Math.max(0, contentHeight - panelHeight);
            leftScroll = Math.max(0, Math.min(maxScroll, leftScroll - (int) (delta * SCROLL_STEP)));
            return true;
        }
        if (mouseX >= rightX && mouseX <= rightX + panelWidth && mouseY >= panelY && mouseY <= panelY + panelHeight) {
            int contentHeight = rightEntries.size() * ENTRY_HEIGHT + 4;
            int maxScroll = Math.max(0, contentHeight - panelHeight);
            rightScroll = Math.max(0, Math.min(maxScroll, rightScroll - (int) (delta * SCROLL_STEP)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return false;
    }

    private static class AdaptEntry {
        final String id;
        final String name;
        final int level;
        final String progress;
        final String info;
        final String status;
        final boolean mature;
        final boolean disabled;

        AdaptEntry(String id, String name, int level, String progress, String info, String status, boolean mature, boolean disabled) {
            this.id = id;
            this.name = name;
            this.level = level;
            this.progress = progress;
            this.info = info;
            this.status = status;
            this.mature = mature;
            this.disabled = disabled;
        }
    }

    private static String number(double value) {
        return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
