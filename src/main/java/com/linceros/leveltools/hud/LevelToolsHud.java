package com.linceros.leveltools.hud;

import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/**
 * Custom HUD element for displaying tool/weapon level and EXP progress.
 * Shows on the right side of the screen when holding a levelable item.
 */
public class LevelToolsHud extends CustomUIHud {

    public static final int DEFAULT_X = 20;
    public static final int DEFAULT_Y = 100;
    private static final int HUD_WIDTH = 210;
    private static final int HUD_HEIGHT = 100;

    private boolean isLoaded = false;
    private int posX = DEFAULT_X;
    private int posY = DEFAULT_Y;

    public LevelToolsHud(PlayerRef ref) {
        super(ref);
    }

    @Override
    protected void build(UICommandBuilder builder) {
        builder.append("LevelTools/LincerosLevelTools.ui");
        builder.set("#LevelToolsRoot.Visible", false);
        isLoaded = true;
    }

    /**
     * Refresh the HUD with updated tool data.
     */
    public void refresh(LevelToolsHudView view) {
        UICommandBuilder builder = new UICommandBuilder();

        if (!isLoaded) {
            builder.append("LevelTools/LincerosLevelTools.ui");
            isLoaded = true;
        }

        if (view == null || !view.visible()) {
            builder.set("#LevelToolsRoot.Visible", false);
            this.update(false, builder);
            return;
        }

        builder.set("#LevelToolsRoot.Visible", true);

        builder.set("#ToolName.Text", view.itemName());
        builder.set("#ToolName.Style.TextColor", view.qualityColor());

        String maxLevelStr = view.maxLevel() < 0 ? "∞" : String.valueOf(view.maxLevel());
        builder.set("#LevelText.Text", "Lv. " + view.level() + "/" + maxLevelStr);

        String levelColor = getLevelColor(view.level());
        builder.set("#LevelText.Style.TextColor", levelColor);

        builder.set("#ExpBar.Value", view.expPercentage());

        String expText = String.format("%.0f / %.0f", view.durabilityLost(), view.durabilityToLevel());
        builder.set("#ExpText.Text", expText);

        int pctInt = Math.round(view.expPercentage() * 100);
        builder.set("#ExpPercent.Text", pctInt + "%");

        this.update(false, builder);
    }

    /**
     * Move the HUD to a new screen position immediately.
     * x = Left offset from screen left edge (pixels).
     * y = Bottom offset from screen bottom edge (pixels).
     */
    public void moveTo(int x, int y) {
        this.posX = x;
        this.posY = y;
        UICommandBuilder builder = new UICommandBuilder();
        applyPosition(builder);
        this.update(false, builder);
    }

    /**
     * Reset HUD to its default screen position.
     */
    public void resetPosition() {
        moveTo(DEFAULT_X, DEFAULT_Y);
    }

    private void applyPosition(UICommandBuilder builder) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(posX));
        anchor.setBottom(Value.of(posY));
        anchor.setWidth(Value.of(HUD_WIDTH));
        anchor.setHeight(Value.of(HUD_HEIGHT));
        builder.setObject("#LevelToolsRoot.Anchor", anchor);
    }

    private String getLevelColor(int level) {
        if (level >= 20) return "#FF55FF";
        if (level >= 15) return "#FFAA00";
        if (level >= 10) return "#AA00FF";
        if (level >= 5)  return "#5555FF";
        return "#55FFFF";
    }

    public void show() {
        UICommandBuilder builder = new UICommandBuilder();
        if (!isLoaded) {
            builder.append("LevelTools/LincerosLevelTools.ui");
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
