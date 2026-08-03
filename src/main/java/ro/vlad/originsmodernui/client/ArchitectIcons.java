package ro.vlad.originsmodernui.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import ro.vlad.originsmodernui.OriginsModernUI;

/** Shared pixel-art icons used by Origin Architect UI and HUD. */
public final class ArchitectIcons {
    public static final ResourceLocation LOGO = icon("origin_architect");
    public static final ResourceLocation OFFENSE = icon("offense");
    public static final ResourceLocation DEFENSE = icon("defense");
    public static final ResourceLocation UTILITY = icon("utility");
    public static final ResourceLocation SURVIVAL = icon("survival");
    public static final ResourceLocation LIFE_STEAL = icon("lifesteal");
    public static final ResourceLocation BULWARK = icon("bulwark");
    public static final ResourceLocation SWIMMER = icon("swimmer");
    public static final ResourceLocation LAST_STAND = icon("last_stand");

    private ArchitectIcons() {}

    private static ResourceLocation icon(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                OriginsModernUI.MOD_ID,
                "textures/gui/icons/" + name + ".png");
    }

    public static ResourceLocation stat(int index) {
        return switch (index) {
            case 0 -> OFFENSE;
            case 1 -> DEFENSE;
            case 2 -> UTILITY;
            default -> SURVIVAL;
        };
    }

    public static ResourceLocation capstone(int index) {
        return switch (index) {
            case 0 -> LIFE_STEAL;
            case 1 -> BULWARK;
            case 2 -> SWIMMER;
            default -> LAST_STAND;
        };
    }

    public static void draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size) {
        graphics.blit(texture, x, y, size, size, 0.0F, 0.0F, 16, 16, 16, 16);
    }
}
