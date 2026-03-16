package com.linceros.leveltools.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Custom HUD element for displaying tool/weapon level and EXP progress.
 * Shows on the right side of the screen when holding a levelable item.
 */
public class LevelToolsHud extends CustomUIHud {

    private boolean isLoaded = false;

    public LevelToolsHud(PlayerRef ref) {
        super(ref);
    }

    @Override
    protected void build(UICommandBuilder builder) {
        // Initial build: Load the document and set initial state
        builder.append("Pages/Linceros_LevelTools.ui");
        builder.set("#LevelToolsRoot.Visible", false);
        isLoaded = true;
    }

    /**
     * Refresh the HUD with updated tool data.
     */
    public void refresh(LevelToolsHudView view) {
        UICommandBuilder builder = new UICommandBuilder();

        // If HUD isn't loaded yet (safeguard), append it
        if (!isLoaded) {
            builder.append("Pages/Linceros_LevelTools.ui");
            isLoaded = true;
        }

        if (view == null || !view.visible()) {
            builder.set("#LevelToolsRoot.Visible", false);
            this.update(false, builder);
            return;
        }

        builder.set("#LevelToolsRoot.Visible", true);

        // Set tool/weapon name
        builder.set("#ToolName.Text", view.itemName());
        builder.set("#ToolName.Style.TextColor", view.isWeapon() ? "#FF6B6B" : "#6BCB77");

        // Set level display with max level
        String maxLevelStr = view.maxLevel() < 0 ? "∞" : String.valueOf(view.maxLevel());
        builder.set("#LevelText.Text", "Lv. " + view.level() + "/" + maxLevelStr);

        // Set level color based on level tier
        String levelColor = getLevelColor(view.level());
        builder.set("#LevelText.Style.TextColor", levelColor);

        // Set EXP bar progress (0.0 to 1.0)
        builder.set("#ExpBar.Value", view.expPercentage());

        // Set EXP text (durability lost / durability needed)
        String expText = String.format("%.0f / %.0f", view.durabilityLost(), view.durabilityToLevel());
        builder.set("#ExpText.Text", expText);

        // Set percentage text
        int pctInt = Math.round(view.expPercentage() * 100);
        builder.set("#ExpPercent.Text", pctInt + "%");

        // Efficient incremental update
        this.update(false, builder);
    }

    /**
     * Get color based on level tier.
     */
    private String getLevelColor(int level) {
        if (level >= 20)
            return "#FF55FF"; // Legendary (Magenta)
        if (level >= 15)
            return "#FFAA00"; // Epic (Orange)
        if (level >= 10)
            return "#AA00FF"; // Rare (Purple)
        if (level >= 5)
            return "#5555FF"; // Uncommon (Blue)
        return "#55FFFF"; // Common (Cyan)
    }

    public void show() {
        UICommandBuilder builder = new UICommandBuilder();
        if (!isLoaded) {
            builder.append("Pages/Linceros_LevelTools.ui");
        }
        builder.set("#LevelToolsRoot.Visible", true);
        this.update(false, builder);
        isLoaded = true;
    }

    public void hideHud() {
        UICommandBuilder builder = new UICommandBuilder();
        builder.set("#LevelToolsRoot.Visible", false);
        this.update(false, builder);
    }
}
