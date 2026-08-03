package ro.vlad.originsmodernui;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Vanilla-XP-backed RPG progression and assignable stat upgrades. */
public final class ArchitectProgression {
    private static final String ROOT = "origin_architect_progression";
    private static final String OFFENSE = "offense_points";
    private static final String DEFENSE = "defense_points";
    private static final String UTILITY = "utility_points";
    private static final String SURVIVAL = "survival_points";
    private static final int MAX_LEVEL = 60;
    public static final int MAX_ALLOCATION_PER_STAT = 60;

    private ArchitectProgression() {}

    public static Data get(Player player) {
        if (player == null) return new Data(0, 1, 0, 0, 0, 0, 0);
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        int offense = Mth.clamp(root.getInt(OFFENSE), 0, MAX_ALLOCATION_PER_STAT);
        int defense = Mth.clamp(root.getInt(DEFENSE), 0, MAX_ALLOCATION_PER_STAT);
        int utility = Mth.clamp(root.getInt(UTILITY), 0, MAX_ALLOCATION_PER_STAT);
        int survival = Mth.clamp(root.getInt(SURVIVAL), 0, MAX_ALLOCATION_PER_STAT);
        int spent = offense + defense + utility + survival;
        // Character level now represents purchased upgrades. There is no separate
        // Architect-XP pool and no unspent-point currency.
        int level = Mth.clamp(1 + spent / 4, 1, MAX_LEVEL);
        return new Data(Math.max(0, player.totalExperience), level, offense, defense, utility, survival, 0);
    }

    /** Compatibility helper: updates the player's real vanilla XP total. */
    public static void setXp(Player player, int amount) {
        if (player == null) return;
        setVanillaXp(player, Math.max(0, amount));
    }

    /** Compatibility helper used by admin commands: grants real vanilla XP. */
    public static void addXp(Player player, int amount) {
        if (player == null || amount == 0) return;
        player.giveExperiencePoints(amount);
    }

    public static boolean allocate(Player player, Stat stat, int delta) {
        if (player == null || delta == 0) return false;
        Data data = get(player);
        int current = data.points(stat);

        if (delta < 0) {
            int next = Mth.clamp(current + delta, 0, MAX_ALLOCATION_PER_STAT);
            if (next == current) return false;
            CompoundTag root = player.getPersistentData().getCompound(ROOT);
            root.putInt(key(stat), next);
            player.getPersistentData().put(ROOT, root);
            return true;
        }

        int count = Math.min(delta, MAX_ALLOCATION_PER_STAT - current);
        if (count <= 0) return false;
        int totalCost = upgradeCost(data, stat, count);
        if (player.totalExperience < totalCost) return false;

        // Bulk upgrades are atomic: Ctrl-click (+5) or Shift-click (+10) either
        // purchases the entire batch or leaves the character unchanged.
        player.giveExperiencePoints(-totalCost);
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        root.putInt(key(stat), current + count);
        player.getPersistentData().put(ROOT, root);
        return true;
    }

    /** Raw vanilla XP-point cost for the next point in one specific stat. */
    public static int upgradeCost(Data data, Stat stat) {
        return upgradeCostAtStatLevel(data.points(stat));
    }

    /** Total cost for a batch of consecutive upgrades in one specific stat. */
    public static int upgradeCost(Data data, Stat stat, int count) {
        int allocated = data.points(stat);
        int total = 0;
        for (int i = 0; i < Math.max(0, count); i++) {
            total += upgradeCostAtStatLevel(allocated + i);
        }
        return total;
    }

    /** Lowest next-upgrade cost among stats that are not yet maxed. */
    public static int cheapestUpgradeCost(Data data) {
        int cheapest = Integer.MAX_VALUE;
        for (Stat stat : Stat.values()) {
            if (data.points(stat) < MAX_ALLOCATION_PER_STAT) {
                cheapest = Math.min(cheapest, upgradeCost(data, stat));
            }
        }
        return cheapest == Integer.MAX_VALUE ? 0 : cheapest;
    }

    /**
     * Compatibility overload retained for older UI code. It now returns the
     * cheapest available stat upgrade instead of coupling all four stats.
     */
    public static int upgradeCost(Data data) {
        return cheapestUpgradeCost(data);
    }

    /** Compatibility overload: batch cost for the currently cheapest stat. */
    public static int upgradeCost(Data data, int count) {
        Stat cheapestStat = null;
        int cheapest = Integer.MAX_VALUE;
        for (Stat stat : Stat.values()) {
            if (data.points(stat) >= MAX_ALLOCATION_PER_STAT) continue;
            int cost = upgradeCost(data, stat, count);
            if (cost < cheapest) {
                cheapest = cost;
                cheapestStat = stat;
            }
        }
        return cheapestStat == null ? 0 : cheapest;
    }

    private static int upgradeCostAtStatLevel(int allocated) {
        return 40 + allocated * 12 + (allocated * allocated) / 7;
    }

    /** Copies Origin Architect progression to the replacement player entity created on respawn. */
    public static void copyToRespawnedPlayer(Player original, Player replacement) {
        if (original == null || replacement == null) return;

        CompoundTag originalRoot = original.getPersistentData().getCompound(ROOT);
        if (originalRoot.isEmpty()) {
            replacement.getPersistentData().remove(ROOT);
            return;
        }

        replacement.getPersistentData().put(ROOT, originalRoot.copy());
    }

    public static void reset(Player player) {
        if (player == null) return;
        CompoundTag root = player.getPersistentData().getCompound(ROOT);
        root.putInt(OFFENSE, 0);
        root.putInt(DEFENSE, 0);
        root.putInt(UTILITY, 0);
        root.putInt(SURVIVAL, 0);
        player.getPersistentData().put(ROOT, root);
    }

    // Kept for source compatibility with older UI code. Character progression no
    // longer owns an XP bar, so these simply expose the current vanilla total.
    public static int xpForNextLevel(int level) { return 0; }
    public static int xpIntoLevel(int xp) { return Math.max(0, xp); }
    public static int totalXpForLevel(int targetLevel) { return 0; }

    private static void setVanillaXp(Player player, int target) {
        int difference = target - player.totalExperience;
        if (difference != 0) player.giveExperiencePoints(difference);
    }

    private static String key(Stat stat) {
        return switch (stat) {
            case OFFENSE -> OFFENSE;
            case DEFENSE -> DEFENSE;
            case UTILITY -> UTILITY;
            case SURVIVAL -> SURVIVAL;
        };
    }

    public enum Stat { OFFENSE, DEFENSE, UTILITY, SURVIVAL }

    /** xp is the player's current vanilla total; unspent is always zero. */
    public record Data(int xp, int level, int offense, int defense, int utility, int survival, int unspent) {
        public int points(Stat stat) {
            return switch (stat) {
                case OFFENSE -> offense;
                case DEFENSE -> defense;
                case UTILITY -> utility;
                case SURVIVAL -> survival;
            };
        }
    }
}
