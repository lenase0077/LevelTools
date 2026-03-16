package com.linceros.leveltools;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.event.events.ecs.DamageBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemTool;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * System that intercepts block damage events and applies bonus mining speed
 * based on the tool's level.
 * 
 * When a player mines with a leveled tool:
 * - The block damage is multiplied by (1 + level * bonusPercent/100)
 * 
 * Example: Level 5 pickaxe with 5% bonus = 1.25x mining speed
 * 
 * Note: XP/leveling is now handled by LevelToolsSystem via durability tracking.
 */
public class LevelToolsMiningSystem extends EntityEventSystem<EntityStore, DamageBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, Player> playerType;
    private final Query<EntityStore> query;

    public LevelToolsMiningSystem() {
        super(DamageBlockEvent.class);
        this.playerType = EntityModule.get().getPlayerComponentType();
        this.query = Query.and(this.playerType);
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public void handle(int index,
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull DamageBlockEvent event) {

        // Skip if event is cancelled
        if (event.isCancelled()) {
            return;
        }

        // Get the player
        Player player = archetypeChunk.getComponent(index, this.playerType);
        if (player == null) {
            return;
        }

        // Get the item used for mining
        ItemStack itemInHand = event.getItemInHand();
        if (ItemStack.isEmpty(itemInHand)) {
            return;
        }

        // Check if it's a tool
        Item item = itemInHand.getItem();
        if (item == null) {
            return;
        }

        ItemTool tool = item.getTool();
        if (tool == null) {
            return;
        }

        // Skip if unbreakable
        if (itemInHand.isUnbreakable()) {
            return;
        }

        // Get the tool level from metadata
        int level = LevelToolsSystem.getToolLevel(itemInHand);

        // Apply speed bonus if leveled
        if (level > 0) {
            LevelToolsConfig config = LevelToolsPlugin.getConfig();
            double speedMultiplier = 1.0 + (level * config.getSpeedBonusPercent() / 100.0);

            float originalDamage = event.getDamage();
            float newDamage = (float) (originalDamage * speedMultiplier);

            event.setDamage(newDamage);

            LOGGER.at(Level.FINE).log("[LevelTools] Mining boosted: %.2f -> %.2f (Level %d tool, %.0f%% bonus)",
                    originalDamage, newDamage, level, (speedMultiplier - 1) * 100);
        }

        // Note: XP tracking is now done by LevelToolsSystem via durability loss
        // detection
    }
}
