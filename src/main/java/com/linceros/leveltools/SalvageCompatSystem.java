package com.linceros.leveltools;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.windows.ItemContainerWindow;
import com.hypixel.hytale.server.core.entity.entities.player.windows.Window;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.logger.HytaleLogger;
import com.linceros.leveltools.page.TokenPage;

import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.logging.Level;

/**
 * Salvage Workbench Compatibility System
 * 
 * Problem: LevelTools adds custom metadata (LincerosToolLevel, etc.) to
 * tools/weapons.
 * Hytale's ProcessingBenchState uses isEquivalentType() to match items against
 * salvage recipes,
 * which compares metadata EXACTLY. Items with Linceros metadata fail to match
 * vanilla recipes.
 * 
 * Solution: When a player has a Processing Bench window open, this system
 * monitors
 * the bench's input container. If it detects an item with Linceros metadata
 * keys,
 * it silently replaces the item with a "clean" copy (same item, no Linceros
 * metadata).
 * This allows the vanilla salvage recipe to match correctly.
 * 
 * The player doesn't lose their leveled item - the salvage process destroys the
 * item anyway.
 * If the player removes the item from the bench, they get back the clean
 * version
 * (which is identical to a fresh item of that type).
 */
public class SalvageCompatSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // All metadata keys that LevelTools adds to items
    private static final String[] LINCEROS_KEYS = {
            LevelToolsSystem.LEVEL_KEY,               // "LincerosToolLevel"
            LevelToolsSystem.DURABILITY_EXCESS_KEY,   // "LincerosDurabilityExcess"
            LevelToolsSystem.SNAPSHOT_DURABILITY_KEY, // "LincerosSnapshotDurability"
            LevelToolsSystem.ORIGINAL_NAME_KEY,       // "LincerosOriginalName"
            LevelToolsSystem.BASE_MAX_DURABILITY_KEY, // "LincerosBaseMaxDurability"
            TokenPage.ABILITIES_KEY,                  // "LincerosToolAbilities"
            "LincerosDurabilityLost",                 // Legacy key
            "LincerosLastDurability"                  // Legacy key
    };

    private final ComponentType<EntityStore, Player> playerType;
    private final Query<EntityStore> query;

    public SalvageCompatSystem() {
        this.playerType = EntityModule.get().getPlayerComponentType();
        this.query = Query.and(this.playerType);
    }

    @Override
    @Nonnull
    public Query<EntityStore> getQuery() {
        return this.query;
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return false;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Player player = archetypeChunk.getComponent(index, this.playerType);
        if (player == null)
            return;

        if (!LevelToolsPlugin.getConfig().isSalvageCompatEnabled())
            return;

        // Get the player's WindowManager and check for open ProcessingBenchWindows
        WindowManager windowManager = player.getWindowManager();
        List<Window> openWindows = windowManager.getWindows();
        if (openWindows == null || openWindows.isEmpty())
            return;

        for (Window window : openWindows) {
            if (window == null)
                continue;

            // Only target ProcessingBenchWindow — other ItemContainerWindows (chests, etc.)
            // must not have their contents stripped of Linceros metadata
            if (!"ProcessingBenchWindow".equals(window.getClass().getSimpleName()))
                continue;

            // Safe cast via interface instead of reflection
            if (!(window instanceof ItemContainerWindow))
                continue;

            ItemContainer itemContainer = ((ItemContainerWindow) window).getItemContainer();

            // Scan all slots of the container for items with Linceros metadata
            cleanLincerosMetadata(itemContainer);
        }
    }

    /**
     * Scan all slots of a container and replace items that have Linceros metadata
     * with clean copies (no Linceros keys). This makes them match vanilla salvage
     * recipes.
     */
    private void cleanLincerosMetadata(ItemContainer itemContainer) {
        short capacity = itemContainer.getCapacity();

        for (short slot = 0; slot < capacity; slot++) {
            ItemStack itemStack = itemContainer.getItemStack(slot);
            if (ItemStack.isEmpty(itemStack))
                continue;

            BsonDocument metadata = itemStack.getMetadata();
            if (metadata == null)
                continue;

            // Check if this item has any Linceros metadata keys
            if (!hasLincerosMetadata(metadata))
                continue;

            // Get the ORIGINAL base max durability (before level bonuses)
            double baseMaxDurability = LevelToolsSystem.getBaseMaxDurability(metadata, itemStack.getMaxDurability());

            // Create a clean copy without Linceros metadata
            BsonDocument cleanMetadata = metadata.clone();
            for (String key : LINCEROS_KEYS) {
                cleanMetadata.remove(key);
            }

            // If after removing our keys the metadata is empty, set it to null
            // This makes the item identical to a fresh vanilla item
            BsonDocument finalMetadata = cleanMetadata.isEmpty() ? null : cleanMetadata;

            // Create clean item with original base durability
            ItemStack cleanItem = new ItemStack(
                    itemStack.getItemId(),
                    itemStack.getQuantity(),
                    baseMaxDurability, // Reset to original max durability
                    baseMaxDurability, // Set current durability to max (full repair for salvage)
                    finalMetadata);

            // Replace the item in the container
            itemContainer.replaceItemStackInSlot(slot, itemStack, cleanItem);

            LOGGER.at(Level.FINE).log(
                    "[LevelTools] Cleaned salvage item: %s (was Lv %d) for recipe matching",
                    itemStack.getItemId(), LevelToolsSystem.getToolLevel(itemStack));
        }
    }

    /**
     * Check if the metadata contains any Linceros-specific keys.
     */
    private boolean hasLincerosMetadata(BsonDocument metadata) {
        for (String key : LINCEROS_KEYS) {
            if (metadata.containsKey(key)) {
                return true;
            }
        }
        return false;
    }
}
