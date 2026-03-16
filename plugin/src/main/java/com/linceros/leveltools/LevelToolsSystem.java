package com.linceros.leveltools;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.logger.HytaleLogger;

import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonDouble;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * System that monitors tool/weapon durability loss and levels them up.
 * 
 * New System (Durability-Based Leveling):
 * - Tools and Weapons gain EXP when they lose durability
 * - Level up when accumulated durability loss reaches threshold
 * - Max level is capped by item quality/rarity
 * - Exception: Tool_Hoe_Crude has infinite scaling
 * 
 * When a tool/weapon levels up:
 * 1. Its level increases by 1
 * 2. Accumulated durability loss resets
 * 3. Maximum durability increases based on level
 * 4. Player receives a level up notification
 */
public class LevelToolsSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Metadata keys for storing tool data
    public static final String LEVEL_KEY = "LincerosToolLevel";
    public static final String DURABILITY_LOST_KEY = "LincerosDurabilityLost";
    public static final String LAST_DURABILITY_KEY = "LincerosLastDurability";
    public static final String ORIGINAL_NAME_KEY = "LincerosOriginalName";
    public static final String BASE_MAX_DURABILITY_KEY = "LincerosBaseMaxDurability";

    // Special items that scale infinitely regardless of quality
    private static final String[] INFINITE_SCALING_ITEMS = {
            "Tool_Hoe_Crude"
    };

    private final ComponentType<EntityStore, Player> playerType;
    private final Query<EntityStore> query;

    public LevelToolsSystem() {
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
        return false; // Process sequentially for thread safety
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = archetypeChunk.getComponent(index, this.playerType);

        if (player == null) {
            return;
        }

        // Get player's inventory
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }

        // Check the item in the active hotbar slot
        byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot < 0) {
            return;
        }

        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null) {
            return;
        }

        ItemStack itemStack = hotbar.getItemStack(activeSlot);

        // Skip if no item or empty
        if (ItemStack.isEmpty(itemStack)) {
            return;
        }

        // Check if it's a tool OR a weapon (both can level up)
        Item item = itemStack.getItem();
        if (item == null) {
            return;
        }

        // Must be either a Tool or a Weapon to level up
        boolean isTool = item.getTool() != null;
        boolean isWeapon = item.getWeapon() != null;

        if (!isTool && !isWeapon) {
            return;
        }

        // Skip if unbreakable
        if (itemStack.isUnbreakable()) {
            return;
        }

        // Track durability changes
        BsonDocument metadata = itemStack.getMetadata();
        double currentDurability = itemStack.getDurability();
        double lastDurability = getLastDurability(metadata, itemStack.getMaxDurability());

        // Check if durability decreased
        if (currentDurability < lastDurability) {
            double durabilityLost = lastDurability - currentDurability;

            // Accumulate durability loss
            double totalLost = getDurabilityLost(metadata) + durabilityLost;
            int currentLevel = getLevel(metadata);

            // Get config
            LevelToolsConfig config = LevelToolsPlugin.getConfig();
            double lossToLevel = config.getDurabilityLossToLevel();

            // Check if we can level up
            if (totalLost >= lossToLevel) {
                // Check max level based on quality
                int qualityValue = getItemQualityValue(item);
                boolean hasInfiniteScaling = isInfiniteScalingItem(item.getId());

                if (!hasInfiniteScaling && config.isAtMaxLevel(currentLevel, qualityValue)) {
                    // At max level - just reset accumulated loss
                    updateDurabilityTracking(hotbar, activeSlot, itemStack, 0, currentDurability);
                    return;
                }

                // Level up!
                processToolLevelUp(ref, player, hotbar, activeSlot, itemStack, item, isWeapon,
                        totalLost - lossToLevel, currentDurability, commandBuffer);
            } else {
                // Just update tracking
                updateDurabilityTracking(hotbar, activeSlot, itemStack, totalLost, currentDurability);
            }
        } else if (currentDurability > lastDurability) {
            // Durability was restored (repair, level up, etc) - update tracking
            updateDurabilityTracking(hotbar, activeSlot, itemStack,
                    getDurabilityLost(metadata), currentDurability);
        }
    }

    /**
     * Check if an item has infinite scaling (ignores quality cap).
     */
    private boolean isInfiniteScalingItem(String itemId) {
        for (String id : INFINITE_SCALING_ITEMS) {
            if (id.equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the quality value of an item.
     */
    private int getItemQualityValue(Item item) {
        try {
            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                return quality.getQualityValue();
            }
        } catch (Throwable t) {
            // Fallback if quality system not available
        }
        return 1; // Default to Common
    }

    /**
     * Update durability tracking metadata.
     */
    private void updateDurabilityTracking(ItemContainer hotbar, byte slot, ItemStack itemStack,
            double durabilityLost, double lastDurability) {

        BsonDocument metadata = itemStack.getMetadata();
        BsonDocument newMetadata = metadata != null ? metadata.clone() : new BsonDocument();

        newMetadata.put(DURABILITY_LOST_KEY, new BsonDouble(durabilityLost));
        newMetadata.put(LAST_DURABILITY_KEY, new BsonDouble(lastDurability));

        // Ensure level key exists
        if (!newMetadata.containsKey(LEVEL_KEY)) {
            newMetadata.put(LEVEL_KEY, new BsonInt32(0));
        }

        ItemStack updatedItem = itemStack.withMetadata(newMetadata);
        hotbar.replaceItemStackInSlot(slot, itemStack, updatedItem);
    }

    /**
     * Level up a tool/weapon.
     */
    private void processToolLevelUp(Ref<EntityStore> ref, Player player,
            ItemContainer hotbar, byte slot, ItemStack currentTool, Item item, boolean isWeapon,
            double carryOverLoss, double currentDurability,
            CommandBuffer<EntityStore> commandBuffer) {

        LevelToolsConfig config = LevelToolsPlugin.getConfig();

        // Get current data from metadata
        BsonDocument metadata = currentTool.getMetadata();
        int currentLevel = getLevel(metadata);
        String originalName = getOriginalName(metadata, item.getId());
        double baseMaxDurability = getBaseMaxDurability(metadata, item.getMaxDurability());

        // Level up!
        int newLevel = currentLevel + 1;

        // Calculate new max durability (base + bonus per level)
        double bonusMultiplier = 1.0 + (newLevel * config.getDurabilityBonusPercent() / 100.0);
        double newMaxDurability = baseMaxDurability * bonusMultiplier;

        // Create new metadata with updated level and carry over loss
        BsonDocument newMetadata = metadata != null ? metadata.clone() : new BsonDocument();
        newMetadata.put(LEVEL_KEY, new BsonInt32(newLevel));
        newMetadata.put(DURABILITY_LOST_KEY, new BsonDouble(carryOverLoss)); // Carry over excess
        newMetadata.put(LAST_DURABILITY_KEY, new BsonDouble(newMaxDurability)); // Will be at max

        if (!newMetadata.containsKey(ORIGINAL_NAME_KEY)) {
            newMetadata.put(ORIGINAL_NAME_KEY, new BsonString(originalName));
        }
        if (!newMetadata.containsKey(BASE_MAX_DURABILITY_KEY)) {
            newMetadata.put(BASE_MAX_DURABILITY_KEY, new BsonDouble(baseMaxDurability));
        }

        // Create the leveled up item - restore durability to max
        ItemStack leveledTool = new ItemStack(
                currentTool.getItemId(),
                currentTool.getQuantity(),
                newMaxDurability, // Full durability
                newMaxDurability, // New max durability
                newMetadata);

        // Replace the tool with the leveled up version
        hotbar.replaceItemStackInSlot(slot, currentTool, leveledTool);

        // Get quality info for message
        int qualityValue = getItemQualityValue(item);
        int maxLevel = config.getMaxLevelForQuality(qualityValue);
        boolean hasInfiniteScaling = isInfiniteScalingItem(item.getId());

        String maxLevelStr = (hasInfiniteScaling || maxLevel < 0) ? "∞" : String.valueOf(maxLevel);

        // Send level up message to player
        if (config.isShowLevelUpMessage()) {
            String toolName = formatToolName(originalName);

            Message levelUpMsg = Message.join(
                    Message.raw("⬆ LEVEL UP! ").color("#55FF55").bold(true),
                    Message.raw(toolName + " ").color("#FFFF55"),
                    Message.raw("is now ").color("#FFFFFF"),
                    Message.raw("Level " + newLevel).color("#55FFFF").bold(true),
                    Message.raw("/" + maxLevelStr + " ").color("#AAAAAA"),
                    Message.raw("(+" + String.format("%.0f", config.getDurabilityBonusPercent()) + "% durability)")
                            .color("#AAAAAA"));

            player.sendMessage(levelUpMsg);
        }

        // Notify HUD service to update
        LevelToolsPlugin.getHudService().forceRefresh(player.getUuid());

        LOGGER.at(Level.INFO).log("[LevelTools] %s leveled up: %s -> Level %d/%s (Max Durability: %.0f)",
                isWeapon ? "Weapon" : "Tool", currentTool.getItemId(), newLevel, maxLevelStr, newMaxDurability);
    }

    /**
     * Format the tool name for display (convert Item_ID_Format to readable name).
     */
    private String formatToolName(String itemId) {
        String name = itemId;
        if (name.startsWith("Tool_")) {
            name = name.substring(5);
        } else if (name.startsWith("Weapon_")) {
            name = name.substring(7);
        }
        name = name.replace("_", " ");
        return name;
    }

    /**
     * Get the level of a tool from its metadata.
     */
    public static int getLevel(BsonDocument metadata) {
        if (metadata != null && metadata.containsKey(LEVEL_KEY)) {
            return metadata.getInt32(LEVEL_KEY).getValue();
        }
        return 0;
    }

    /**
     * Get the level of a tool from an ItemStack.
     */
    public static int getToolLevel(ItemStack itemStack) {
        if (itemStack == null || ItemStack.isEmpty(itemStack)) {
            return 0;
        }
        return getLevel(itemStack.getMetadata());
    }

    /**
     * Get the accumulated durability loss from metadata.
     */
    public static double getDurabilityLost(BsonDocument metadata) {
        if (metadata != null && metadata.containsKey(DURABILITY_LOST_KEY)) {
            return metadata.getDouble(DURABILITY_LOST_KEY).getValue();
        }
        return 0;
    }

    /**
     * Get the last recorded durability.
     */
    public static double getLastDurability(BsonDocument metadata, double defaultValue) {
        if (metadata != null && metadata.containsKey(LAST_DURABILITY_KEY)) {
            return metadata.getDouble(LAST_DURABILITY_KEY).getValue();
        }
        return defaultValue;
    }

    /**
     * Get the durability lost from an ItemStack.
     */
    public static double getToolDurabilityLost(ItemStack itemStack) {
        if (itemStack == null || ItemStack.isEmpty(itemStack)) {
            return 0;
        }
        return getDurabilityLost(itemStack.getMetadata());
    }

    /**
     * Get the original name from metadata.
     */
    public static String getOriginalName(BsonDocument metadata, String defaultName) {
        if (metadata != null && metadata.containsKey(ORIGINAL_NAME_KEY)) {
            return metadata.getString(ORIGINAL_NAME_KEY).getValue();
        }
        return defaultName;
    }

    /**
     * Get the base max durability from metadata.
     */
    public static double getBaseMaxDurability(BsonDocument metadata, double defaultValue) {
        if (metadata != null && metadata.containsKey(BASE_MAX_DURABILITY_KEY)) {
            return metadata.getDouble(BASE_MAX_DURABILITY_KEY).getValue();
        }
        return defaultValue;
    }

    /**
     * Calculate the EXP percentage towards the next level.
     */
    public static float getExpPercentage(ItemStack itemStack) {
        if (itemStack == null || ItemStack.isEmpty(itemStack)) {
            return 0f;
        }

        LevelToolsConfig config = LevelToolsPlugin.getConfig();
        double lossToLevel = config.getDurabilityLossToLevel();
        double durabilityLost = getToolDurabilityLost(itemStack);

        return Math.min(1.0f, (float) (durabilityLost / lossToLevel));
    }
}
