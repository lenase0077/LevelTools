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
            .build();

    // Default values
    private boolean enabled = true;
    private double durabilityLossToLevel = 100.0; // Total durability loss needed to level up
    private double durabilityBonusPercent = 15.0;
    private double speedBonusPercent = 5.0;
    private double damageBonusPercent = 5.0;
    private boolean showLevelUpMessage = true;
    private boolean showHUD = true;

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

    public double getDurabilityBonusPercent() {
        return durabilityBonusPercent;
    }

    public double getSpeedBonusPercent() {
        return speedBonusPercent;
    }

    public double getDamageBonusPercent() {
        return damageBonusPercent;
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
