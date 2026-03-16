package com.linceros.leveltools;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.logger.HytaleLogger;
import com.linceros.leveltools.command.LevelToolsCommand;
import com.linceros.leveltools.hud.LevelToolsHudService;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * Level Tools - Por Linceros
 * 
 * Tools and weapons level up based on durability loss:
 * - Every X durability lost = 1 level up
 * - Max level is capped by item quality/rarity
 * - Exception: Tool_Hoe_Crude scales infinitely
 * 
 * Higher level items have:
 * - More maximum durability
 * - Faster mining/attack speed
 * - Increased weapon damage
 * - A HUD showing level and EXP progress
 */
public class LevelToolsPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Config reference for static access
    private static Config<LevelToolsConfig> configRef;

    // HUD service reference for static access
    private static LevelToolsHudService hudService;

    // Config loaded via codec
    private final Config<LevelToolsConfig> config = this.withConfig("LevelTools", LevelToolsConfig.CODEC);

    public LevelToolsPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[LevelTools] Setting up...");

        // Save and store config reference
        this.config.save();
        configRef = this.config;

        LevelToolsConfig cfg = this.config.get();

        if (cfg.isEnabled()) {
            // Initialize HUD service
            hudService = new LevelToolsHudService();

            // Register command
            this.getCommandRegistry().registerCommand(new LevelToolsCommand(hudService));

            // Register the level tools system (handles leveling up based on durability
            // loss)
            this.getEntityStoreRegistry().registerSystem(new LevelToolsSystem());

            // Register the damage system (handles weapon damage bonus)
            this.getEntityStoreRegistry().registerSystem(new LevelToolsDamageSystem());

            // Register the mining system (handles tool mining speed bonus)
            this.getEntityStoreRegistry().registerSystem(new LevelToolsMiningSystem());

            // Register event handlers for HUD management
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
            this.getEventRegistry().register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);

            LOGGER.at(Level.INFO).log("[LevelTools] Level Tools System registered!");
            LOGGER.at(Level.INFO).log("[LevelTools] Weapon Damage Bonus System registered!");
            LOGGER.at(Level.INFO).log("[LevelTools] Tool Mining Speed System registered!");
            LOGGER.at(Level.INFO).log("[LevelTools] HUD System registered!");

            LOGGER.at(Level.INFO).log(
                    "[LevelTools] Config: Durability Loss to Level = %.0f",
                    cfg.getDurabilityLossToLevel());
            LOGGER.at(Level.INFO).log(
                    "[LevelTools] Config: Durability +%.1f%%, Damage +%.1f%%, Speed +%.1f%% per level",
                    cfg.getDurabilityBonusPercent(), cfg.getDamageBonusPercent(), cfg.getSpeedBonusPercent());
            LOGGER.at(Level.INFO).log(
                    "[LevelTools] Max Levels by Quality: Junk=%d, Common=%d, Uncommon=%d, Rare=%d, Epic=%d, Legendary=%s",
                    cfg.getMaxLevelForQuality(0), cfg.getMaxLevelForQuality(1), cfg.getMaxLevelForQuality(2),
                    cfg.getMaxLevelForQuality(3), cfg.getMaxLevelForQuality(4),
                    cfg.getMaxLevelForQuality(5) < 0 ? "∞" : String.valueOf(cfg.getMaxLevelForQuality(5)));
        } else {
            LOGGER.at(Level.INFO).log("[LevelTools] Plugin is DISABLED in config.");
        }
    }

    private void onPlayerReady(PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // Initialize HUD for this player
        PlayerRef ref = Universe.get().getPlayer(player.getUuid());
        if (ref != null && hudService != null) {
            hudService.initHud(player, ref);
        }
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef ref = event.getPlayerRef();
        if (ref != null && ref.getUuid() != null && hudService != null) {
            hudService.removeHud(ref.getUuid());
        }
    }

    @Override
    protected void start() {
        LOGGER.at(Level.INFO).log("[LevelTools] Plugin started!");
        if (config.get().isEnabled()) {
            LOGGER.at(Level.INFO).log(
                    "[LevelTools] Tools/Weapons level up every %.0f durability lost!",
                    config.get().getDurabilityLossToLevel());
        }
    }

    @Override
    protected void shutdown() {
        LOGGER.at(Level.INFO).log("[LevelTools] Plugin disabled.");
        if (hudService != null) {
            hudService.stop();
        }
    }

    /**
     * Get the current configuration.
     */
    public static LevelToolsConfig getConfig() {
        return configRef != null ? configRef.get() : new LevelToolsConfig();
    }

    /**
     * Get the HUD service.
     */
    public static LevelToolsHudService getHudService() {
        return hudService;
    }
}
