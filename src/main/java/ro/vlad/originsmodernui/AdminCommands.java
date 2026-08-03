package ro.vlad.originsmodernui;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.data.OriginClaimsData;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.GlobalPowerService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashMap;
import java.util.Map;

/** Administrative commands for Origin Architect. */
@EventBusSubscriber(modid = OriginsModernUI.MOD_ID)
public final class AdminCommands {
    private static final int ADMIN_PERMISSION_LEVEL = 2;

    private AdminCommands() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("originarchitect")
                .requires(source -> source.hasPermission(ADMIN_PERMISSION_LEVEL))
                .then(Commands.literal("reset")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> resetSelections(
                                        context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("stats")
                        .then(Commands.literal("reset")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> resetStats(
                                                context.getSource(), EntityArgument.getPlayer(context, "player"))))))
                .then(Commands.literal("xp")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                                .executes(context -> addArchitectXp(
                                                        context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "amount")))))))
                .then(Commands.literal("level")
                        .then(Commands.literal("set")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 50))
                                                .executes(context -> setArchitectLevel(
                                                        context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "level"))))))));
    }

    private static int resetStats(CommandSourceStack source, ServerPlayer target) {
        ArchitectProgression.reset(target);
        ArchitectNetwork.sync(target);
        source.sendSuccess(() -> Component.literal("Reset Origin Architect stats for "
                + target.getName().getString() + "."), true);
        return 1;
    }

    private static int addArchitectXp(CommandSourceStack source, ServerPlayer target, int amount) {
        ArchitectProgression.addXp(target, amount);
        ArchitectNetwork.sync(target);
        source.sendSuccess(() -> Component.literal("Added " + amount + " vanilla XP to "
                + target.getName().getString() + "."), true);
        return 1;
    }

    private static int setArchitectLevel(CommandSourceStack source, ServerPlayer target, int level) {
        ArchitectProgression.setXp(target, ArchitectProgression.totalXpForLevel(level));
        ArchitectNetwork.sync(target);
        source.sendSuccess(() -> Component.literal("Set " + target.getName().getString()
                + " to Architect Level " + level + "."), true);
        return 1;
    }

    private static int resetSelections(CommandSourceStack source, ServerPlayer target)
            throws CommandSyntaxException {
        PlayerOriginData data = target.getData(OriginAttachments.originData());
        Map<ResourceLocation, ResourceLocation> previousSelections =
                new HashMap<>(data.getOrigins());

        if (previousSelections.isEmpty()) {
            source.sendFailure(Component.literal(
                    target.getName().getString() + " has no Origin Architect selections to reset."
            ));
            return 0;
        }

        // Remove all currently applied powers before clearing the layer choices.
        ActiveOriginService.revokeAllPowers(target);

        // Release claims for any layer configured as unique by NeoOrigins.
        OriginClaimsData claims = OriginClaimsData.get(source.getServer());
        previousSelections.forEach((layerId, originId) -> {
            if (originId != null && AdminConfig.isUniqueLayer(layerId)) {
                claims.releaseIfOwner(layerId, originId, target.getUUID());
            }
        });

        // Clears Origin, Class, Background, and any other selected NeoOrigins layer.
        data.clear();
        data.setHadAllOrigins(false);
        data.setPendingAdminReselect(true);

        // Rebuild and synchronize NeoOrigins' authoritative player state.
        GlobalPowerService.reconcilePlayer(target);
        ActiveOriginService.invalidate(target.getUUID());
        NeoOriginsNetwork.syncRegistryToPlayer(target);
        NeoOriginsNetwork.syncToPlayer(target);
        NeoOriginsNetwork.openSelectionScreen(target, false);

        source.sendSuccess(
                () -> Component.literal(
                        "Reset Origin, Class, and Background for "
                                + target.getName().getString()
                                + ". The selection screen has been reopened."
                ),
                true
        );

        target.sendSystemMessage(Component.literal(
                "An administrator reset your Origin, Class, and Background. Please choose again."
        ));
        return 1;
    }
}
