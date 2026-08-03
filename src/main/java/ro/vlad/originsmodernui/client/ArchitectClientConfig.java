package ro.vlad.originsmodernui.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only options for the vanilla-integrated Origin Architect HUD. */
public final class ArchitectClientConfig {
    public enum Position {
        ABOVE_XP_BAR,
        LEFT_OF_XP_BAR,
        RIGHT_OF_XP_BAR
    }

    public enum Style {
        MINIMAL,
        VANILLA,
        RPG
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.EnumValue<Position> HUD_POSITION;
    public static final ModConfigSpec.EnumValue<Style> HUD_STYLE;
    public static final ModConfigSpec.DoubleValue HUD_SCALE;
    public static final ModConfigSpec.DoubleValue HUD_OPACITY;
    public static final ModConfigSpec.IntValue HUD_X_OFFSET;
    public static final ModConfigSpec.IntValue HUD_Y_OFFSET;
    public static final ModConfigSpec.BooleanValue SHOW_LEVEL;
    public static final ModConfigSpec.BooleanValue SHOW_UNSPENT_POINTS;
    public static final ModConfigSpec.BooleanValue SHOW_XP_GAIN_POPUP;
    public static final ModConfigSpec.BooleanValue SHOW_LEVEL_UP_POPUP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("hud");
        HUD_ENABLED = builder
                .comment("Show the small Origin Architect badge integrated near the vanilla XP bar.")
                .translation("originsmodernui.config.hud.enabled")
                .define("enabled", true);
        HUD_POSITION = builder
                .comment("Where the Origin Architect badge is placed relative to the vanilla XP bar.")
                .translation("originsmodernui.config.hud.position")
                .defineEnum("position", Position.ABOVE_XP_BAR);
        HUD_STYLE = builder
                .comment("Visual style of the integrated HUD badge.")
                .translation("originsmodernui.config.hud.style")
                .defineEnum("style", Style.VANILLA);
        HUD_SCALE = builder
                .comment("HUD scale. 1.0 is the default size.")
                .translation("originsmodernui.config.hud.scale")
                .defineInRange("scale", 1.0D, 0.65D, 1.75D);
        HUD_OPACITY = builder
                .comment("HUD background opacity.")
                .translation("originsmodernui.config.hud.opacity")
                .defineInRange("opacity", 0.82D, 0.20D, 1.0D);
        HUD_X_OFFSET = builder
                .comment("Additional horizontal offset in GUI pixels.")
                .translation("originsmodernui.config.hud.x_offset")
                .defineInRange("xOffset", 0, -300, 300);
        HUD_Y_OFFSET = builder
                .comment("Additional vertical offset in GUI pixels.")
                .translation("originsmodernui.config.hud.y_offset")
                .defineInRange("yOffset", 0, -200, 200);
        SHOW_LEVEL = builder
                .translation("originsmodernui.config.hud.show_level")
                .define("showLevel", true);
        SHOW_UNSPENT_POINTS = builder
                .translation("originsmodernui.config.hud.show_points")
                .define("showUnspentPoints", true);
        SHOW_XP_GAIN_POPUP = builder
                .translation("originsmodernui.config.hud.show_xp_popup")
                .define("showXpGainPopup", true);
        SHOW_LEVEL_UP_POPUP = builder
                .translation("originsmodernui.config.hud.show_level_popup")
                .define("showLevelUpPopup", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ArchitectClientConfig() {}
}
