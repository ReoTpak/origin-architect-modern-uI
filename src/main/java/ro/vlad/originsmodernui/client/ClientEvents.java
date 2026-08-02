package ro.vlad.originsmodernui.client;

import com.cyberday1.neoorigins.screen.OriginSelectionScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import ro.vlad.originsmodernui.OriginsModernUI;

@EventBusSubscriber(modid = OriginsModernUI.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (ClientKeyMappings.TOGGLE_HUD.consumeClick()) {
            ArchitectHud.sessionVisible = !ArchitectHud.sessionVisible;
        }
        while (ClientKeyMappings.OPEN_PROFILE.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null && minecraft.screen == null) {
                minecraft.setScreen(ModernOriginSelectionScreen.openProfile());
            }
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof OriginSelectionScreen original
                && !(event.getScreen() instanceof ModernOriginSelectionScreen)) {
            event.setNewScreen(ModernOriginSelectionScreen.from(original));
        }
    }
}
