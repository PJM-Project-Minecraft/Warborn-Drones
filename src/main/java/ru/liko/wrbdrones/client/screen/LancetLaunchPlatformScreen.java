package ru.liko.wrbdrones.client.screen;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import ru.liko.wrbdrones.network.LancetPlatformActionPacket;

@OnlyIn(Dist.CLIENT)
public class LancetLaunchPlatformScreen extends Screen {
    private static final int GUI_WIDTH = 180;
    private static final int GUI_HEIGHT = 92;
    private static final int COL_BACKGROUND = 0xF5101010;
    private static final int COL_BORDER = 0xFF404040;
    private static final int COL_ACCENT = 0xFF333333;
    private static final int COL_ACCENT_HOVER = 0xFF505050;

    private final int platformEntityId;
    private final boolean loaded;

    public LancetLaunchPlatformScreen(int platformEntityId, boolean loaded) {
        super(Component.translatable("screen.wrbdrones.lancet_platform"));
        this.platformEntityId = platformEntityId;
        this.loaded = loaded;
    }

    @Override
    protected void init() {
        int left = (this.width - GUI_WIDTH) / 2;
        int top = (this.height - GUI_HEIGHT) / 2;
        Button launch = new MinimalButton(left + 18, top + 52, GUI_WIDTH - 36, 20,
                Component.translatable("screen.wrbdrones.lancet_platform.launch").withStyle(ChatFormatting.BOLD),
                button -> launch());
        launch.active = loaded;
        this.addRenderableWidget(launch);
    }

    private void launch() {
        PacketDistributor.sendToServer(new LancetPlatformActionPacket(
                platformEntityId, LancetPlatformActionPacket.ACTION_LAUNCH));
        this.onClose();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = (this.width - GUI_WIDTH) / 2;
        int top = (this.height - GUI_HEIGHT) / 2;
        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, COL_BACKGROUND);
        graphics.renderOutline(left, top, GUI_WIDTH, GUI_HEIGHT, COL_BORDER);
        graphics.fill(left, top, left + GUI_WIDTH, top + 24, 0xFF151515);
        graphics.fill(left, top + 24, left + GUI_WIDTH, top + 25, COL_BORDER);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 8, 0xFFFFFFFF);

        Component status = loaded
                ? Component.translatable("screen.wrbdrones.lancet_platform.loaded")
                : Component.translatable("screen.wrbdrones.lancet_platform.empty");
        int statusColor = loaded ? 0xFF55FF55 : 0xFFFFAA55;
        graphics.drawCenteredString(this.font, status, this.width / 2, top + 34, statusColor);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class MinimalButton extends Button {
        MinimalButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int bgColor = this.active ? (this.isHoveredOrFocused() ? COL_ACCENT_HOVER : COL_ACCENT) : 0xFF202020;
            int textColor = this.active ? 0xFFE0E0E0 : 0xFF555555;
            graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
            graphics.renderOutline(getX(), getY(), width, height, COL_BORDER);
            graphics.drawCenteredString(font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2,
                    textColor);
        }
    }
}
