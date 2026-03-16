package com.linceros.leveltools.hud;

/**
 * View model for Level Tools HUD data.
 * Now uses durability-based XP instead of use counts.
 */
public record LevelToolsHudView(
        boolean visible,
        String itemName,
        int level,
        int maxLevel, // -1 means infinite
        double durabilityLost,
        double durabilityToLevel,
        float expPercentage,
        boolean isWeapon,
        String qualityColor) {

    /**
     * Create an empty/hidden view.
     */
    public static LevelToolsHudView hidden() {
        return new LevelToolsHudView(false, "", 0, -1, 0, 1, 0f, false, "#FFFFFF");
    }
}
