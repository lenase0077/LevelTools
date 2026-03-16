package com.linceros.leveltools.hud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.linceros.leveltools.LevelToolsConfig;
import com.linceros.leveltools.LevelToolsPlugin;
import com.linceros.leveltools.LevelToolsSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import java.util.logging.Level;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service that manages Level Tools HUD elements for players.
 * Displays tool/weapon level and EXP progress.
 */
public class LevelToolsHudService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Map<UUID, TrackedHud> huds = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> hiddenPlayers = ConcurrentHashMap.newKeySet();
    private final HudPositionStore positionStore;
    private final ScheduledExecutorService refresher;

    public LevelToolsHudService(long refreshIntervalMs, Path dataDirectory) {
        this.positionStore = new HudPositionStore(dataDirectory);
        this.refresher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread t = new Thread(runnable, "LevelTools-HUD-Refresher");
            t.setDaemon(true);
            return t;
        });

        this.refresher.scheduleAtFixedRate(
                this::refreshAll,
                refreshIntervalMs,
                refreshIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    public void initHud(Player player, PlayerRef ref) {
        if (player == null || ref == null || ref.getUuid() == null) {
            return;
        }

        LevelToolsConfig config = LevelToolsPlugin.getConfig();
        if (!config.isShowHUD()) {
            return;
        }

        UUID id = ref.getUuid();

        if (huds.containsKey(id)) {
            return;
        }

        // Check if manually hidden
        if (hiddenPlayers.contains(id)) {
            return;
        }

        refresher.schedule(() -> openHud(player, ref), 500L, TimeUnit.MILLISECONDS);
    }

    /**
     * Show HUD for a player.
     */
    public void showHud(Player player, PlayerRef ref) {
        if (player == null || ref == null || ref.getUuid() == null) {
            return;
        }

        UUID id = ref.getUuid();

        // Check if manually hidden
        if (hiddenPlayers.contains(id)) {
            return;
        }

        // Check if already has HUD
        if (huds.containsKey(id)) {
            refreshSingle(id);
            return;
        }

        refresher.schedule(() -> openHud(player, ref), 500L, TimeUnit.MILLISECONDS);
    }

    /**
     * Hide HUD for a player.
     */
    public void hideHud(Player player, PlayerRef ref) {
        if (player == null || ref == null || ref.getUuid() == null) {
            return;
        }

        UUID id = ref.getUuid();
        TrackedHud tracked = huds.remove(id);

        if (tracked != null) {
            tracked.hud.hideHud();
        }
    }

    /**
     * Toggles HUD visibility for a player.
     *
     * @return true if visible, false if hidden
     */
    public boolean toggleHud(Player player, PlayerRef ref) {
        UUID id = ref.getUuid();
        if (hiddenPlayers.contains(id)) {
            hiddenPlayers.remove(id);
            showHud(player, ref);
            return true;
        } else {
            hiddenPlayers.add(id);
            hideHud(player, ref);
            return false;
        }
    }

    private void openHud(Player player, PlayerRef ref) {
        if (player == null || ref == null || ref.getUuid() == null) {
            return;
        }

        UUID id = ref.getUuid();

        if (huds.containsKey(id)) {
            return;
        }

        // Check if manually hidden
        if (hiddenPlayers.contains(id)) {
            return;
        }

        LevelToolsHud hud = new LevelToolsHud(ref);

        boolean hooked = false;
        try {
            Class.forName("com.buuz135.mhud.MultipleHUD");
            hooked = HookMultipleHUD.register(player, ref, hud);
            if (hooked) {
                LOGGER.at(Level.INFO).log("[LevelTools] Successfully hooked into MultipleHUD!");
            }
        } catch (Throwable t) {
            // Mod not installed
        }

        if (!hooked) {
            hud.show();
        }

        huds.put(id, new TrackedHud(player, hud));

        // Apply saved position if any, falling back to config defaults
        int[] pos = positionStore.get(id);
        if (pos != null) {
            hud.moveTo(pos[0], pos[1]);
        } else {
            LevelToolsConfig cfg = LevelToolsPlugin.getConfig();
            hud.moveTo(cfg.getHudDefaultX(), cfg.getHudDefaultY());
        }

        // Schedule re-arm refreshes like BetterParties
        refresher.schedule(() -> refreshSingle(id), 1000L, TimeUnit.MILLISECONDS);
        refresher.schedule(() -> refreshSingle(id), 5000L, TimeUnit.MILLISECONDS);
        refresher.schedule(() -> refreshSingle(id), 10000L, TimeUnit.MILLISECONDS);

        LOGGER.at(Level.INFO).log("[LevelTools] HUD initialized for %s", player.getDisplayName());
    }

    public void removeHud(UUID playerId) {
        if (playerId != null) {
            TrackedHud tracked = huds.remove(playerId);
            hiddenPlayers.remove(playerId); // Also clear hidden status on disconnect? Maybe keep it?
            // BetterParties keeps it in hiddenPlayers only if explicitly toggled off, but
            // verify logic.
            // BetterParties removeHud clears it: hiddenPlayers.remove(playerId);

            if (tracked != null) {
                tracked.hud.hideHud();
            }
        }
    }

    private void refreshAll() {
        LevelToolsConfig config = LevelToolsPlugin.getConfig();
        if (!config.isShowHUD()) {
            return;
        }

        for (Map.Entry<UUID, TrackedHud> entry : huds.entrySet()) {
            UUID id = entry.getKey();
            TrackedHud tracked = entry.getValue();

            if (tracked == null || tracked.player == null || tracked.player.wasRemoved()) {
                huds.remove(id);
                continue;
            }

            try {
                tracked.hud.refresh(buildView(tracked.player));
            } catch (Throwable t) {
                LevelToolsPlugin.getInstance().getLogger().at(Level.WARNING)
                        .log("Error refreshing LevelTools HUD: " + t.getMessage());
                t.printStackTrace();
            }
        }
    }

    private void refreshSingle(UUID id) {
        TrackedHud tracked = huds.get(id);
        if (tracked == null || tracked.player == null) {
            return;
        }
        try {
            tracked.hud.refresh(buildView(tracked.player));
        } catch (Throwable t) {
            LevelToolsPlugin.getInstance().getLogger().at(Level.WARNING)
                    .log("Error refreshing LevelTools HUD (Single): " + t.getMessage());
            t.printStackTrace();
        }
    }

    public void forceRefresh(UUID playerId) {
        if (playerId != null && huds.containsKey(playerId)) {
            refresher.execute(() -> refreshSingle(playerId));
        }
    }

    private LevelToolsHudView buildView(Player player) {
        if (player == null) {
            return LevelToolsHudView.hidden();
        }

        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return LevelToolsHudView.hidden();
        }

        ItemStack itemInHand = inventory.getItemInHand();
        if (ItemStack.isEmpty(itemInHand)) {
            return LevelToolsHudView.hidden();
        }

        Item item = itemInHand.getItem();
        if (item == null) {
            return LevelToolsHudView.hidden();
        }

        // Check by property first
        boolean isTool = item.getTool() != null;
        boolean isWeapon = item.getWeapon() != null;

        // Also check by item ID for tools that don't have formal Tool property
        String itemId = item.getId();
        if (!isTool && !isWeapon) {
            if (itemId != null && itemId.startsWith("Tool_")) {
                isTool = true;
            } else if (itemId != null && itemId.startsWith("Weapon_")) {
                isWeapon = true;
            }
        }

        if (!isTool && !isWeapon) {
            return LevelToolsHudView.hidden();
        }

        // Must have durability
        if (item.getMaxDurability() <= 0) {
            return LevelToolsHudView.hidden();
        }

        if (itemInHand.isUnbreakable()) {
            return LevelToolsHudView.hidden();
        }

        int level = LevelToolsSystem.getToolLevel(itemInHand);
        double durabilityLost = LevelToolsSystem.getToolDurabilityLost(itemInHand);

        LevelToolsConfig config = LevelToolsPlugin.getConfig();
        double durabilityToLevel = config.getDurabilityLossToLevel();
        float expPct = LevelToolsSystem.getExpPercentage(itemInHand);

        // Get max level and quality color in a single lookup
        int maxLevel = -1; // Default infinite
        String qualityColor = "#FFFFFF";
        try {
            int qualityIndex = item.getQualityIndex();
            com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality quality = com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality
                    .getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                maxLevel = config.getMaxLevelForQuality(quality.getQualityValue());
                com.hypixel.hytale.protocol.Color c = quality.getTextColor();
                if (c != null) {
                    qualityColor = String.format("#%02X%02X%02X",
                            Byte.toUnsignedInt(c.red),
                            Byte.toUnsignedInt(c.green),
                            Byte.toUnsignedInt(c.blue));
                }
            }
        } catch (Throwable t) {
            // Fallback
        }

        // Check for infinite scaling items
        if ("Tool_Hoe_Crude".equals(itemId)) {
            maxLevel = -1;
        }

        String itemName = formatItemName(itemId);

        return new LevelToolsHudView(
                true,
                itemName,
                level,
                maxLevel,
                durabilityLost,
                durabilityToLevel,
                expPct,
                isWeapon,
                qualityColor);
    }

    private String formatItemName(String itemId) {
        String name = itemId;
        if (name.startsWith("Tool_")) {
            name = name.substring(5);
        } else if (name.startsWith("Weapon_")) {
            name = name.substring(7);
        }
        name = name.replace("_", " ");

        if (name.length() > 20) {
            name = name.substring(0, 17) + "...";
        }

        return name;
    }

    /**
     * Move the HUD to a custom position for this player and persist the setting.
     * x = Left offset, y = Bottom offset (pixels from screen edge).
     */
    public void setHudPosition(UUID playerId, int x, int y) {
        positionStore.set(playerId, x, y);
        TrackedHud tracked = huds.get(playerId);
        if (tracked != null) {
            tracked.hud.moveTo(x, y);
        }
    }

    /**
     * Reset the HUD position to the config default for this player and remove the persisted override.
     */
    public void resetHudPosition(UUID playerId) {
        positionStore.remove(playerId);
        TrackedHud tracked = huds.get(playerId);
        if (tracked != null) {
            LevelToolsConfig cfg = LevelToolsPlugin.getConfig();
            tracked.hud.moveTo(cfg.getHudDefaultX(), cfg.getHudDefaultY());
        }
    }

    /**
     * Returns the saved position for this player, or the config default.
     */
    public int[] getHudPosition(UUID playerId) {
        int[] saved = positionStore.get(playerId);
        if (saved != null) {
            return saved;
        }
        LevelToolsConfig cfg = LevelToolsPlugin.getConfig();
        return new int[]{cfg.getHudDefaultX(), cfg.getHudDefaultY()};
    }

    public void stop() {
        huds.clear();
        refresher.shutdownNow();
    }

    private static class HookMultipleHUD {
        static boolean register(Player player, PlayerRef ref, LevelToolsHud hud) {
            try {
                com.buuz135.mhud.MultipleHUD.getInstance().setCustomHud(player, ref, "LevelTools", hud);
                return true;
            } catch (Throwable e) {
                LevelToolsPlugin.getInstance().getLogger().at(Level.WARNING)
                        .log("Failed to hook LevelTools into MultipleHUD: " + e.getMessage());
                return false;
            }
        }
    }

    private static class TrackedHud {
        final Player player;
        final LevelToolsHud hud;

        TrackedHud(Player player, LevelToolsHud hud) {
            this.player = player;
            this.hud = hud;
        }
    }
}
