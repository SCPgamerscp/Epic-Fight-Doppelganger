package scpgamerscp.efdoppelganger.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class DoppelConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue BASE_HEALTH;
    public static final ForgeConfigSpec.BooleanValue SCALE_WITH_PLAYER_MAX_HEALTH;
    public static final ForgeConfigSpec.DoubleValue HEALTH_PER_PLAYER_HEART;
    public static final ForgeConfigSpec.IntValue POISON_DURATION_TICKS;
    public static final ForgeConfigSpec.DoubleValue STUN_ARMOR;
    public static final ForgeConfigSpec.IntValue XP_REWARD;
    public static final ForgeConfigSpec.IntValue NETHERITE_BLOCKS;
    public static final ForgeConfigSpec.IntValue DIAMOND_BLOCKS;
    public static final ForgeConfigSpec.IntValue ELIXIRS;
    public static final ForgeConfigSpec.IntValue WEAPON_SWITCH_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue HEAL_BELOW_PERCENT;
    public static final ForgeConfigSpec.IntValue MAX_HEAL_COUNT;
    public static final ForgeConfigSpec.IntValue HEAL_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue HEAL_AMOUNT_PERCENT;
    public static final ForgeConfigSpec.BooleanValue COPY_ARMOR;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("combat");
        BASE_HEALTH = b.comment("Default max health of yourself.").defineInRange("baseHealth", 500, 1, 100000);
        SCALE_WITH_PLAYER_MAX_HEALTH = b.comment("If true, health scales with the copied player's max health (20 = no extra).")
                .define("scaleWithPlayerMaxHealth", true);
        HEALTH_PER_PLAYER_HEART = b.comment("Extra health added per player heart above 10 when scaling is on.")
                .defineInRange("healthPerPlayerHeart", 10.0, 0.0, 1000.0);
        POISON_DURATION_TICKS = b.comment("Poison II duration applied on hit (ticks).").defineInRange("poisonDurationTicks", 80, 0, 20 * 60);
        STUN_ARMOR = b.comment("Epic Fight stun armor. High values make the boss barely flinch.")
                .defineInRange("stunArmor", 24.0, 0.0, 200.0);
        XP_REWARD = b.comment("Experience dropped on death. 12000 matches the Ender Dragon.")
                .defineInRange("xpReward", 12000, 0, 100000);
        WEAPON_SWITCH_INTERVAL_TICKS = b.comment("How often the boss may switch to another remembered weapon.")
                .defineInRange("weaponSwitchIntervalTicks", 120, 20, 1200);
        HEAL_BELOW_PERCENT = b.comment("Boss uses a remembered healing item when health falls below this percent.")
                .defineInRange("healBelowPercent", 40, 1, 99);
        MAX_HEAL_COUNT = b.comment("Maximum times the boss can heal with items during combat. 0 disables healing.")
                .defineInRange("maxHealCount", 64, 0, Integer.MAX_VALUE);
        HEAL_COOLDOWN_TICKS = b.comment("Cooldown ticks between healing item uses.")
                .defineInRange("healCooldownTicks", 220, 0, Integer.MAX_VALUE);
        HEAL_AMOUNT_PERCENT = b.comment("Percent of max health restored per healing item use.")
                .defineInRange("healAmountPercent", 15, 1, 100);
        COPY_ARMOR = b.comment("Copy the target player's armor on spawn.").define("copyArmor", true);
        b.pop();

        b.push("loot");
        NETHERITE_BLOCKS = b.defineInRange("netheriteBlocks", 5, 0, 64);
        DIAMOND_BLOCKS = b.defineInRange("diamondBlocks", 5, 0, 64);
        ELIXIRS = b.defineInRange("elixirs", 5, 0, 64);
        b.pop();

        SPEC = b.build();
    }

    private DoppelConfig() {}
}
