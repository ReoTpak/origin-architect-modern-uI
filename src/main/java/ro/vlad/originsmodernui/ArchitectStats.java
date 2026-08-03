package ro.vlad.originsmodernui;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.data.OriginDataManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;
import java.util.Map;

/** Build-derived stats plus the points assigned through the Character Profile. */
public record ArchitectStats(int offense, int defense, int utility, int survival) {
    public static final ArchitectStats DEFAULT = new ArchitectStats(10, 10, 10, 10);

    public static ArchitectStats from(Player player) {
        ArchitectStats base = baseFrom(player);
        ArchitectProgression.Data allocated = ArchitectProgression.get(player);
        return base.withPoints(allocated);
    }

    public static ArchitectStats baseFrom(Player player) {
        if (player == null) return DEFAULT;
        try {
            Map<ResourceLocation, ResourceLocation> origins = player.getData(OriginAttachments.originData()).getOrigins();
            int offense = 10;
            int defense = 10;
            int utility = 10;
            int survival = 10;

            for (ResourceLocation originId : origins.values()) {
                Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
                if (origin == null) continue;
                StringBuilder corpus = new StringBuilder();
                corpus.append(origin.id()).append(' ')
                        .append(origin.name().getString()).append(' ')
                        .append(origin.description().getString()).append(' ')
                        .append(origin.impact().name()).append(' ');
                for (ResourceLocation power : origin.powers()) corpus.append(power).append(' ');
                String text = corpus.toString().toLowerCase(Locale.ROOT);

                if (has(text, "damage", "attack", "melee", "ranged", "strength", "critical", "projectile", "combat")) offense += 4;
                if (has(text, "armor", "resistance", "shield", "toughness", "tank", "protect", "defense")) defense += 4;
                if (has(text, "magic", "spell", "craft", "trade", "loot", "mining", "teleport", "utility", "exploration", "vision")) utility += 4;
                if (has(text, "health", "heal", "regen", "food", "hunger", "survival", "water breathing", "fire resistance")) survival += 4;

                if (has(text, "extra damage", "vulnerable", "weakness", "lower health", "less health")) survival -= 3;
                if (has(text, "cannot wear", "armor restriction", "no armor")) defense -= 3;
                if (has(text, "slowness", "slow movement")) utility -= 2;

                int impact = origin.impact().getDotCount();
                offense += impact;
                utility += impact;
            }
            return new ArchitectStats(
                    Mth.clamp(offense, 5, 55),
                    Mth.clamp(defense, 5, 55),
                    Mth.clamp(utility, 5, 55),
                    Mth.clamp(survival, 5, 55));
        } catch (RuntimeException ignored) {
            return DEFAULT;
        }
    }

    public ArchitectStats withPoints(ArchitectProgression.Data data) {
        return new ArchitectStats(
                Mth.clamp(offense + data.offense(), 5, 100),
                Mth.clamp(defense + data.defense(), 5, 100),
                Mth.clamp(utility + data.utility(), 5, 100),
                Mth.clamp(survival + data.survival(), 5, 100));
    }

    public int average() {
        return Math.round((offense + defense + utility + survival) / 4.0F);
    }

    public static int architectLevel(Player player) {
        return ArchitectProgression.get(player).level();
    }

    private static boolean has(String text, String... values) {
        for (String value : values) if (text.contains(value)) return true;
        return false;
    }
}
