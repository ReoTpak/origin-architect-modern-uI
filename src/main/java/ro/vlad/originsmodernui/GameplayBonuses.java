package ro.vlad.originsmodernui;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative bonuses from invested Origin Architect stat points. */
@EventBusSubscriber(modid = OriginsModernUI.MOD_ID)
public final class GameplayBonuses {
    private static final ResourceLocation OFFENSE_DAMAGE_ID = id("offense_damage");
    private static final ResourceLocation OFFENSE_SPEED_ID = id("offense_attack_speed");
    private static final ResourceLocation DEFENSE_ARMOR_ID = id("defense_armor");
    private static final ResourceLocation DEFENSE_TOUGHNESS_ID = id("defense_toughness");
    private static final ResourceLocation DEFENSE_CAPSTONE_ID = id("defense_capstone_knockback");
    private static final ResourceLocation UTILITY_SPEED_ID = id("utility_movement_speed");
    private static final ResourceLocation UTILITY_SWIM_ID = id("utility_capstone_swim_speed");
    private static final ResourceLocation SURVIVAL_HEALTH_ID = id("survival_health");

    private GameplayBonuses() {}

    /**
     * Minecraft creates a new Player entity after death. Custom persistent data is not
     * copied automatically, so carry the allocated stats onto the respawned entity.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player replacement = event.getEntity();
        if (replacement.level().isClientSide()) return;

        ArchitectProgression.copyToRespawnedPlayer(event.getOriginal(), replacement);

        if (replacement instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ArchitectNetwork.sync(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        ArchitectProgression.Data data = ArchitectProgression.get(player);
        int offenseRanks = data.offense() / 2;
        int defenseRanks = data.defense() / 2;
        int utilityRanks = data.utility() / 2;
        int survivalRanks = data.survival() / 2;
        int maxRanks = ArchitectProgression.MAX_ALLOCATION_PER_STAT / 2;

        boolean offenseMax = data.offense() >= ArchitectProgression.MAX_ALLOCATION_PER_STAT;
        boolean defenseMax = data.defense() >= ArchitectProgression.MAX_ALLOCATION_PER_STAT;
        boolean utilityMax = data.utility() >= ArchitectProgression.MAX_ALLOCATION_PER_STAT;
        boolean survivalMax = data.survival() >= ArchitectProgression.MAX_ALLOCATION_PER_STAT;

        if (player.tickCount % 20 == 0) {
            // OFFENSE: every two invested points grant one rank.
            // At 60 points: +8 damage and +30% attack speed.
            double offenseDamage = offenseRanks * (8.0D / maxRanks);
            double offenseSpeed = offenseRanks * (0.30D / maxRanks);
            replace(player.getAttribute(Attributes.ATTACK_DAMAGE), OFFENSE_DAMAGE_ID,
                    offenseDamage, AttributeModifier.Operation.ADD_VALUE);
            replace(player.getAttribute(Attributes.ATTACK_SPEED), OFFENSE_SPEED_ID,
                    offenseSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

            // DEFENSE: at 60 points: +6 armor and +1.5 toughness.
            double defenseArmor = defenseRanks * (6.0D / maxRanks);
            double defenseToughness = defenseRanks * (1.5D / maxRanks);
            replace(player.getAttribute(Attributes.ARMOR), DEFENSE_ARMOR_ID,
                    defenseArmor, AttributeModifier.Operation.ADD_VALUE);
            replace(player.getAttribute(Attributes.ARMOR_TOUGHNESS), DEFENSE_TOUGHNESS_ID,
                    defenseToughness, AttributeModifier.Operation.ADD_VALUE);
            // Bulwark capstone: strong knockback resistance without exceeding armor caps.
            replace(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), DEFENSE_CAPSTONE_ID,
                    defenseMax ? 0.20D : 0.0D, AttributeModifier.Operation.ADD_VALUE);

            // UTILITY: at 60 points: +15% movement speed and +30% XP gain.
            double utilitySpeed = utilityRanks * (0.15D / maxRanks);
            replace(player.getAttribute(Attributes.MOVEMENT_SPEED), UTILITY_SPEED_ID,
                    utilitySpeed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            replace(player.getAttribute(NeoForgeMod.SWIM_SPEED), UTILITY_SWIM_ID,
                    utilityMax ? 0.10D : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

            // SURVIVAL: at 60 points: +12% maximum health.
            double survivalHealth = survivalRanks * (0.12D / maxRanks);
            replace(player.getAttribute(Attributes.MAX_HEALTH), SURVIVAL_HEALTH_ID,
                    survivalHealth, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
        }

        // SURVIVAL CAPSTONE — Last Stand: Resistance I while critically wounded.
        if (survivalMax && player.getHealth() > 0.0F
                && player.getHealth() <= player.getMaxHealth() * 0.30F
                && player.tickCount % 40 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 80, 0, false, false, true));
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && player.tickCount % 40 == 0) {
            ArchitectNetwork.sync(serverPlayer);
        }
    }

    /** OFFENSE CAPSTONE — heal for 10% of final damage dealt at maximum Offense. */
    @SubscribeEvent
    public static void onDamageDealt(LivingDamageEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        float dealt = event.getNewDamage();
        if (dealt <= 0.0F) return;
        Entity sourceEntity = event.getSource().getEntity();
        if (!(sourceEntity instanceof Player attacker) || attacker == event.getEntity()) return;
        if (ArchitectProgression.get(attacker).offense() < ArchitectProgression.MAX_ALLOCATION_PER_STAT) return;
        attacker.heal(dealt * 0.10F);
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        int earned = event.getAmount();
        if (earned <= 0) return;
        int utilityRanks = ArchitectProgression.get(event.getEntity()).utility() / 2;
        double multiplier = 1.0D + Math.min(0.30D, utilityRanks * 0.01D);
        event.setAmount(Math.max(1, (int)Math.round(earned * multiplier)));
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ArchitectNetwork.sync(serverPlayer);
        }
    }

    private static void replace(AttributeInstance instance, ResourceLocation id, double amount,
                                AttributeModifier.Operation operation) {
        if (instance == null) return;
        instance.removeModifier(id);
        if (Math.abs(amount) > 0.0001D) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OriginsModernUI.MOD_ID, path);
    }
}
