package com.linceros.leveltools;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration for Level Tools mod.
 * Defines how tools/weapons level up based on durability loss.
 */
public class LevelToolsConfig {

    public static final BuilderCodec<LevelToolsConfig> CODEC = BuilderCodec
            .builder(LevelToolsConfig.class, LevelToolsConfig::new)
            .append(new KeyedCodec<>("Enabled", Codec.BOOLEAN),
                    (config, enabled, extra) -> config.enabled = enabled,
                    (config, extra) -> config.enabled)
            .add()
            .append(new KeyedCodec<>("DurabilityLossToLevel", Codec.DOUBLE),
                    (config, loss, extra) -> config.durabilityLossToLevel = loss,
                    (config, extra) -> config.durabilityLossToLevel)
            .add()
            .append(new KeyedCodec<>("DurabilityBonusPercent", Codec.DOUBLE),
                    (config, bonus, extra) -> config.durabilityBonusPercent = bonus,
                    (config, extra) -> config.durabilityBonusPercent)
            .add()
            .append(new KeyedCodec<>("SpeedBonusPercent", Codec.DOUBLE),
                    (config, bonus, extra) -> config.speedBonusPercent = bonus,
                    (config, extra) -> config.speedBonusPercent)
            .add()
            .append(new KeyedCodec<>("DamageBonusPercent", Codec.DOUBLE),
                    (config, bonus, extra) -> config.damageBonusPercent = bonus,
                    (config, extra) -> config.damageBonusPercent)
            .add()
            .append(new KeyedCodec<>("WeaponXPMultiplier", Codec.DOUBLE),
                    (config, mult, extra) -> config.weaponXPMultiplier = mult,
                    (config, extra) -> config.weaponXPMultiplier)
            .add()
            .append(new KeyedCodec<>("ShowLevelUpMessage", Codec.BOOLEAN),
                    (config, show, extra) -> config.showLevelUpMessage = show,
                    (config, extra) -> config.showLevelUpMessage)
            .add()
            .append(new KeyedCodec<>("ShowHUD", Codec.BOOLEAN),
                    (config, show, extra) -> config.showHUD = show,
                    (config, extra) -> config.showHUD)
            .add()
            // Max levels per quality (QualityValue)
            .append(new KeyedCodec<>("MaxLevelJunk", Codec.INTEGER),
                    (config, level, extra) -> config.maxLevelJunk = level,
                    (config, extra) -> config.maxLevelJunk)
            .add()
            .append(new KeyedCodec<>("MaxLevelCommon", Codec.INTEGER),
                    (config, level, extra) -> config.maxLevelCommon = level,
                    (config, extra) -> config.maxLevelCommon)
            .add()
            .append(new KeyedCodec<>("MaxLevelUncommon", Codec.INTEGER),
                    (config, level, extra) -> config.maxLevelUncommon = level,
                    (config, extra) -> config.maxLevelUncommon)
            .add()
            .append(new KeyedCodec<>("MaxLevelRare", Codec.INTEGER),
                    (config, level, extra) -> config.maxLevelRare = level,
                    (config, extra) -> config.maxLevelRare)
            .add()
            .append(new KeyedCodec<>("MaxLevelEpic", Codec.INTEGER),
                    (config, level, extra) -> config.maxLevelEpic = level,
                    (config, extra) -> config.maxLevelEpic)
            .add()
            .append(new KeyedCodec<>("MaxLevelLegendary", Codec.INTEGER),
                    (config, level, extra) -> config.maxLevelLegendary = level,
                    (config, extra) -> config.maxLevelLegendary)
            .add()
            .append(new KeyedCodec<>("VeinMinerMaxBlocks", Codec.INTEGER),
                    (config, max, extra) -> config.veinMinerMaxBlocks = max,
                    (config, extra) -> config.veinMinerMaxBlocks)
            .add()
            // --- Ability tuning ---
            .append(new KeyedCodec<>("VampirismLifestealPercent", Codec.DOUBLE),
                    (config, val, extra) -> config.vampirismLifestealPercent = val,
                    (config, extra) -> config.vampirismLifestealPercent)
            .add()
            .append(new KeyedCodec<>("IgnoreDefenseMultiplier", Codec.DOUBLE),
                    (config, val, extra) -> config.ignoreDefenseMultiplier = val,
                    (config, extra) -> config.ignoreDefenseMultiplier)
            .add()
            .append(new KeyedCodec<>("EnergySaverStaminaRefund", Codec.DOUBLE),
                    (config, val, extra) -> config.energySaverStaminaRefund = val,
                    (config, extra) -> config.energySaverStaminaRefund)
            .add()
            .append(new KeyedCodec<>("FireBurnDurationSeconds", Codec.DOUBLE),
                    (config, val, extra) -> config.fireBurnDurationSeconds = val,
                    (config, extra) -> config.fireBurnDurationSeconds)
            .add()
            .append(new KeyedCodec<>("PoisonDurationSeconds", Codec.DOUBLE),
                    (config, val, extra) -> config.poisonDurationSeconds = val,
                    (config, extra) -> config.poisonDurationSeconds)
            .add()
            // --- XP multipliers ---
            .append(new KeyedCodec<>("ToolXPMultiplier", Codec.DOUBLE),
                    (config, val, extra) -> config.toolXPMultiplier = val,
                    (config, extra) -> config.toolXPMultiplier)
            .add()
            // --- System toggles ---
            .append(new KeyedCodec<>("SalvageCompatEnabled", Codec.BOOLEAN),
                    (config, val, extra) -> config.salvageCompatEnabled = val,
                    (config, extra) -> config.salvageCompatEnabled)
            .add()
            .append(new KeyedCodec<>("LevelUpFullRepair", Codec.BOOLEAN),
                    (config, val, extra) -> config.levelUpFullRepair = val,
                    (config, extra) -> config.levelUpFullRepair)
            .add()
            // --- Token economy ---
            .append(new KeyedCodec<>("TokensPerLevel", Codec.INTEGER),
                    (config, val, extra) -> config.tokensPerLevel = val,
                    (config, extra) -> config.tokensPerLevel)
            .add()
            // --- HUD ---
            .append(new KeyedCodec<>("HudRefreshIntervalMs", Codec.INTEGER),
                    (config, val, extra) -> config.hudRefreshIntervalMs = val,
                    (config, extra) -> config.hudRefreshIntervalMs)
            .add()
            .append(new KeyedCodec<>("HudDefaultX", Codec.INTEGER),
                    (config, val, extra) -> config.hudDefaultX = val,
                    (config, extra) -> config.hudDefaultX)
            .add()
            .append(new KeyedCodec<>("HudDefaultY", Codec.INTEGER),
                    (config, val, extra) -> config.hudDefaultY = val,
                    (config, extra) -> config.hudDefaultY)
            .add()
            .build();

    // Default values
    private boolean enabled = true;
    private double durabilityLossToLevel = 100.0; // Total durability loss needed to level up
    private double durabilityBonusPercent = 15.0;
    private double speedBonusPercent = 5.0;
    private double damageBonusPercent = 5.0;
    private double weaponXPMultiplier = 2.5; // Weapons gain XP 2.5x faster to compensate for less frequent use
    private boolean showLevelUpMessage = true;
    private boolean showHUD = true;
    private int veinMinerMaxBlocks = 20;

    // Ability tuning
    private double vampirismLifestealPercent = 20.0; // % of damage dealt healed back
    private double ignoreDefenseMultiplier = 1.5;    // damage multiplied by this when Unstoppable is active
    private double energySaverStaminaRefund = 20.0;  // flat stamina restored per hit with Energy Saver
    private double fireBurnDurationSeconds = 5.0;    // seconds the Burn effect lasts from Fire Aspect
    private double poisonDurationSeconds = 4.0;      // seconds the Poison effect lasts

    // XP multipliers
    private double toolXPMultiplier = 1.0;  // Tools use 1x by default (baseline)

    // System toggles
    private boolean salvageCompatEnabled = true; // Strip Linceros metadata when item is placed in Salvage Workbench
    private boolean levelUpFullRepair = true;    // Fully repair the item when it levels up

    // Token economy
    private int tokensPerLevel = 5; // One token earned every N levels

    // HUD
    private int hudRefreshIntervalMs = 500; // How often the HUD refreshes in milliseconds
    private int hudDefaultX = 20;  // Default HUD left offset in pixels
    private int hudDefaultY = 100; // Default HUD bottom offset in pixels

    // Max levels per quality tier (QualityValue based)
    // -1 means infinite (no cap)
    private int maxLevelJunk = 3; // QualityValue 0
    private int maxLevelCommon = 5; // QualityValue 1
    private int maxLevelUncommon = 10; // QualityValue 2
    private int maxLevelRare = 15; // QualityValue 3
    private int maxLevelEpic = 25; // QualityValue 4
    private int maxLevelLegendary = -1; // QualityValue 5+ (infinite)

    public LevelToolsConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getDurabilityLossToLevel() {
        return durabilityLossToLevel;
    }

    public int getVeinMinerMaxBlocks() {
        return veinMinerMaxBlocks;
    }

    public double getVampirismLifestealPercent() {
        return vampirismLifestealPercent;
    }

    public double getIgnoreDefenseMultiplier() {
        return ignoreDefenseMultiplier;
    }

    public double getEnergySaverStaminaRefund() {
        return energySaverStaminaRefund;
    }

    public double getFireBurnDurationSeconds() {
        return fireBurnDurationSeconds;
    }

    public double getPoisonDurationSeconds() {
        return poisonDurationSeconds;
    }

    public double getToolXPMultiplier() {
        return toolXPMultiplier;
    }

    public boolean isSalvageCompatEnabled() {
        return salvageCompatEnabled;
    }

    public boolean isLevelUpFullRepair() {
        return levelUpFullRepair;
    }

    public int getTokensPerLevel() {
        return tokensPerLevel > 0 ? tokensPerLevel : 1; // Guard against 0/negative
    }

    public int getHudRefreshIntervalMs() {
        return hudRefreshIntervalMs;
    }

    public int getHudDefaultX() {
        return hudDefaultX;
    }

    public int getHudDefaultY() {
        return hudDefaultY;
    }

    public double getDurabilityBonusPercent() {
        return durabilityBonusPercent;
    }

    public double getSpeedBonusPercent() {
        return speedBonusPercent;
    }

    public double getEfficiencyBonusPercent() {
        return speedBonusPercent;
    }

    public double getDamageBonusPercent() {
        return damageBonusPercent;
    }

    /**
     * Multiplier for weapon XP gain.
     * Tools are used much more frequently (mining blocks constantly) vs weapons
     * (hitting mobs).
     * This multiplier compensates so weapons level at a comparable rate.
     * Default: 2.5x
     */
    public double getWeaponXPMultiplier() {
        return weaponXPMultiplier;
    }

    public boolean isShowLevelUpMessage() {
        return showLevelUpMessage;
    }

    public boolean isShowHUD() {
        return showHUD;
    }

    /**
     * Get max level for a given quality value.
     * 
     * @param qualityValue The quality value (0=Junk, 1=Common, 2=Uncommon, 3=Rare,
     *                     4=Epic, 5+=Legendary)
     * @return Max level allowed, or -1 for infinite
     */
    public int getMaxLevelForQuality(int qualityValue) {
        switch (qualityValue) {
            case 0:
                return maxLevelJunk;
            case 1:
                return maxLevelCommon;
            case 2:
                return maxLevelUncommon;
            case 3:
                return maxLevelRare;
            case 4:
                return maxLevelEpic;
            default:
                return maxLevelLegendary; // 5+ = Legendary tier, infinite
        }
    }

    /**
     * Check if a level is at or above max for a given quality.
     * 
     * @param currentLevel Current tool level
     * @param qualityValue The quality value of the item
     * @return true if at max level
     */
    public boolean isAtMaxLevel(int currentLevel, int qualityValue) {
        int maxLevel = getMaxLevelForQuality(qualityValue);
        if (maxLevel < 0) {
            return false; // Infinite, never at max
        }
        return currentLevel >= maxLevel;
    }
}
