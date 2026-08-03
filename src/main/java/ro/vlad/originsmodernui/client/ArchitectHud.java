package ro.vlad.originsmodernui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import ro.vlad.originsmodernui.ArchitectProgression;
import ro.vlad.originsmodernui.OriginsModernUI;

/**
 * Vanilla-integrated upgrade indicator. The regular Minecraft XP bar remains the
 * only XP display. A pulsing star appears beside it only when the player can
 * afford at least one character upgrade.
 */
@EventBusSubscriber(modid = OriginsModernUI.MOD_ID, value = Dist.CLIENT)
public final class ArchitectHud {
    public static boolean sessionVisible = true;

    private static long popupStartedAt;
    private static Component popupText;
    private static int popupColor = 0xFFFFFFFF;

    private ArchitectHud() {}

    public static void onProgressUpdate(ArchitectProgression.Data previous, ArchitectProgression.Data current) {
        if (current.level() > previous.level() && ArchitectClientConfig.SHOW_LEVEL_UP_POPUP.get()) {
            popupText = Component.literal("Character Level " + current.level() + "!");
            popupColor = 0xFFFFD966;
            popupStartedAt = System.currentTimeMillis();
            return;
        }

        int gained = current.xp() - previous.xp();
        if (gained > 0 && ArchitectClientConfig.SHOW_XP_GAIN_POPUP.get()) {
            popupText = Component.literal("+" + gained + " XP");
            popupColor = 0xFF8FE8A8;
            popupStartedAt = System.currentTimeMillis();
        }
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!sessionVisible || !ArchitectClientConfig.HUD_ENABLED.get()
                || mc.player == null || mc.options.hideGui || mc.screen != null) {
            return;
        }

        ArchitectProgression.Data data = ClientArchitectState.data;
        boolean hasAvailableStat = data.offense() < ArchitectProgression.MAX_ALLOCATION_PER_STAT
                || data.defense() < ArchitectProgression.MAX_ALLOCATION_PER_STAT
                || data.utility() < ArchitectProgression.MAX_ALLOCATION_PER_STAT
                || data.survival() < ArchitectProgression.MAX_ALLOCATION_PER_STAT;
        int cost = ArchitectProgression.cheapestUpgradeCost(data);
        boolean canUpgrade = hasAvailableStat && mc.player.totalExperience >= cost;

        GuiGraphics g = event.getGuiGraphics();
        int centerX = g.guiWidth() / 2;
        int xpY = g.guiHeight() - 31;

        if (canUpgrade) {
            renderUpgradeIcon(g, centerX, xpY);
        }
        renderPopup(g, mc, centerX, xpY);
    }

    private static void renderUpgradeIcon(GuiGraphics g, int centerX, int xpY) {
        double scale = ArchitectClientConfig.HUD_SCALE.get();
        // Safe defaults keep the HUD valid even if a new config enum value is added.
        int baseX = centerX - 8;
        int baseY = xpY - 24;
        switch (ArchitectClientConfig.HUD_POSITION.get()) {
            case LEFT_OF_XP_BAR -> {
                baseX = centerX - 112;
                baseY = xpY - 13;
            }
            case RIGHT_OF_XP_BAR -> {
                baseX = centerX + 96;
                baseY = xpY - 13;
            }
            case ABOVE_XP_BAR -> {
                // Center the upgrade icon above the vanilla XP bar instead of
                // falling through to the right-side placement.
                baseX = centerX - 8;
                baseY = xpY - 24;
            }
        }
        baseX += ArchitectClientConfig.HUD_X_OFFSET.get();
        baseY += ArchitectClientConfig.HUD_Y_OFFSET.get();

        float pulse = 0.5F + 0.5F * Mth.sin((System.currentTimeMillis() % 1600L) / 1600.0F * (float) (Math.PI * 2.0D));
        int glowAlpha = Mth.clamp((int) ((45 + pulse * 90) * ArchitectClientConfig.HUD_OPACITY.get()), 0, 180);
        int glow = (glowAlpha << 24) | 0xB976FF;
        int bright = (Mth.clamp(glowAlpha + 40, 0, 220) << 24) | 0xE2C6FF;

        g.pose().pushPose();
        g.pose().translate(baseX, baseY, 0);
        g.pose().scale((float) scale, (float) scale, 1.0F);

        // Soft pixel glow and four tiny sparkle points.
        g.fill(-3, -3, 19, 19, (glowAlpha / 3 << 24) | 0x28153E);
        g.fill(-2, 2, 0, 14, glow);
        g.fill(16, 2, 18, 14, glow);
        g.fill(2, -2, 14, 0, glow);
        g.fill(2, 16, 14, 18, glow);
        g.fill(-4, 7, -2, 9, bright);
        g.fill(18, 7, 20, 9, bright);
        g.fill(7, -4, 9, -2, bright);
        g.fill(7, 18, 9, 20, bright);

        ArchitectIcons.draw(g, ArchitectIcons.LOGO, 0, 0, 16);
        g.pose().popPose();
    }

    private static void renderPopup(GuiGraphics g, Minecraft mc, int centerX, int xpY) {
        if (popupText == null) return;
        long age = System.currentTimeMillis() - popupStartedAt;
        if (age >= 2600L) {
            popupText = null;
            return;
        }

        float alpha;
        if (age < 220L) alpha = age / 220.0F;
        else if (age > 2100L) alpha = 1.0F - ((age - 2100L) / 500.0F);
        else alpha = 1.0F;
        alpha = Mth.clamp(alpha, 0.0F, 1.0F);

        int color = ((int) (alpha * 255.0F) << 24) | (popupColor & 0x00FFFFFF);
        int y = xpY - 30 - Math.round((1.0F - alpha) * 5.0F);
        g.drawCenteredString(mc.font, popupText, centerX, y, color);
    }
}
