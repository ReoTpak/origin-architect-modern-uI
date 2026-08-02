package ro.vlad.originsmodernui;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModList;
import ro.vlad.originsmodernui.compat.ApothicAttributesCompat;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import ro.vlad.originsmodernui.client.ArchitectClientConfig;

@Mod(OriginsModernUI.MOD_ID)
public final class OriginsModernUI {
    public static final String MOD_ID = "originsmodernui";

    public OriginsModernUI(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ArchitectClientConfig.SPEC, "originarchitect-client.toml");

        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
            if (ModList.get().isLoaded("apothic_attributes")) {
                ApothicAttributesCompat.initialize();
            }
        }
    }
}
