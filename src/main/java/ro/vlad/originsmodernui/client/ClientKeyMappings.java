package ro.vlad.originsmodernui.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import ro.vlad.originsmodernui.OriginsModernUI;

@EventBusSubscriber(modid = OriginsModernUI.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ClientKeyMappings {
    public static final KeyMapping OPEN_PROFILE = new KeyMapping(
            "key.originsmodernui.open_profile",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            "key.categories.originsmodernui"
    );

    public static final KeyMapping TOGGLE_HUD = new KeyMapping(
            "key.originsmodernui.toggle_hud",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.originsmodernui"
    );

    private ClientKeyMappings() {}

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PROFILE);
        event.register(TOGGLE_HUD);
    }
}
