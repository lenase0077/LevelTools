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
 * New System (Durability-Based Leveling) - Optimized for Crossbows/Rapid items:
 * - We do NOT update metadata on every durability loss (avoids interrupting
 * item state).
 * - XP is calculated dynamically: StoredXP + (SnapshotDurability -
 * CurrentDurability).
 * - Metadata is ONLY updated (item replaced) when a LEVEL UP occurs.
 * 
 * Formula:
 * XP = storedExcessBase + (lastSnapshotDurability - currentDurability)
 */
public class LevelToolsSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // Metadata keys
    public static final String LEVEL_KEY = "LincerosToolLevel";
    public static final String DURABILITY_EXCESS_KEY = "LincerosDurabilityExcess"; // Stores XP carried over from
                                                                                   // previous level
    public static final String SNAPSHOT_DURABILITY_KEY = "LincerosSnapshotDurability"; // The durability at the start of
                                                                                       // this level segment
    public static final String ORIGINAL_NAME_KEY = "LincerosOriginalName";
    public static final String BASE_MAX_DURABILITY_KEY = "LincerosBaseMaxDurability";
    public static final String PRESTIGE_TOKENS_KEY = "LincerosPrestigeTokens";
    public static final String KILL_COUNT_KEY = "LincerosKillCount";
    public static final String BLOCKS_MINED_KEY = "LincerosBlocksMined";

    // Levels that trigger a milestone announcement
    private static final int[] MILESTONE_LEVELS = { 5, 10, 15, 25, 50 };

    // Backward compatibility for metadata migration if needed (though we just
    // overwrite old keys mostly)

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
        return false;
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        Player player = archetypeChunk.getComponent(index, this.playerType);

        if (player == null)
            return;

        Inventory inventory = player.getInventory();
        if (inventory == null)
            return;

        byte activeSlot = inventory.getActiveHotbarSlot();
        if (activeSlot < 0)
            return;

        ItemContainer hotbar = inventory.getHotbar();
        if (hotbar == null)
            return;

        ItemStack itemStack = hotbar.getItemStack(activeSlot);
        if (ItemStack.isEmpty(itemStack))
            return;

        // Check if item is valid tool/weapon
        Item item = itemStack.getItem();
        if (item == null)
            return;

        boolean isTool = item.getTool() != null;
        boolean isWeapon = item.getWeapon() != null;

        // ID-based fallback detection
        String itemId = item.getId();
        if (!isTool && !isWeapon) {
            if (itemId != null && itemId.startsWith("Tool_"))
                isTool = true;
            else if (itemId != null && itemId.startsWith("Weapon_"))
                isWeapon = true;
        }

        if (!isTool && !isWeapon)
            return;
        if (itemStack.isUnbreakable())
            return;

        // --- CORE LOGIC START ---

        // Calculate total durability lost (XP) dynamically
        double totalLost = getToolDurabilityLost(itemStack);

        // Check against requirement
        LevelToolsConfig config = LevelToolsPlugin.getConfig();
        double lossToLevel = config.getDurabilityLossToLevel();

        // Apply weapon XP multiplier - weapons are used less frequently than tools
        // (mining blocks vs hitting mobs), so they gain bonus XP to compensate
        if (isWeapon) {
            totalLost *= config.getWeaponXPMultiplier();
        }

        // Apply tool XP multiplier (configurable baseline, default 1.0x)
        if (isTool) {
            totalLost *= config.getToolXPMultiplier();
        }

        if (totalLost >= lossToLevel) {
            // Check caps
            int currentLevel = getToolLevel(itemStack);
            int qualityValue = getItemQualityValue(item);
            boolean hasInfiniteScaling = isInfiniteScalingItem(itemId);

            if (!hasInfiniteScaling && config.isAtMaxLevel(currentLevel, qualityValue)) {
                // At max level.
                // We do NOT reset or repair constantly. We potentially could, but that would
                // replace item constantly.
                // Strategy: Do nothing. Let it break naturally or let user repair.
                // Or: If we want "perma-repair" at max level, we'd need to check if durability
                // is low.
                // For now: Do nothing.
                return;
            }

            // LEVEL UP!
            // This is the ONLY time we modify the item stack (write metadata)
            processToolLevelUp(ref, player, hotbar, activeSlot, itemStack, item, isWeapon,
                    totalLost - lossToLevel, // Excess XP to carry over
                    commandBuffer);
        }

        // --- CORE LOGIC END ---
        // Note: No "else updateDurabilityTracking()" calls. We are passive listeners
        // now.
    }

    private boolean isInfiniteScalingItem(String itemId) {
        if (itemId == null)
            return false;
        for (String id : INFINITE_SCALING_ITEMS) {
            if (id.equals(itemId))
                return true;
        }
        return false;
    }

    private int getItemQualityValue(Item item) {
        try {
            int qualityIndex = item.getQualityIndex();
            ItemQuality quality = ItemQuality.getAssetMap().getAsset(qualityIndex);
            if (quality != null)
                return quality.getQualityValue();
        } catch (Throwable t) {
        }
        return 1; // Default Common
    }

    private void processToolLevelUp(Ref<EntityStore> ref, Player player,
            ItemContainer hotbar, byte slot, ItemStack currentTool, Item item, boolean isWeapon,
            double excessXP, CommandBuffer<EntityStore> commandBuffer) {

        LevelToolsConfig config = LevelToolsPlugin.getConfig();
        BsonDocument metadata = currentTool.getMetadata();

        int currentLevel = getLevel(metadata);
        String originalName = getOriginalName(metadata, item.getId());
        double baseMaxDurability = getBaseMaxDurability(metadata, item.getMaxDurability());

        int newLevel = currentLevel + 1;
        double bonusMultiplier = 1.0 + (newLevel * config.getDurabilityBonusPercent() / 100.0);
        double newMaxDurability = baseMaxDurability * bonusMultiplier;

        // Prepare new metadata
        BsonDocument newMetadata = metadata != null ? metadata.clone() : new BsonDocument();
        newMetadata.put(LEVEL_KEY, new BsonInt32(newLevel));

        // KEY CHANGE:
        // excessXP becomes our "stored base".
        // snapshot becomes the new Max Durability (since we fully repair).
        newMetadata.put(DURABILITY_EXCESS_KEY, new BsonDouble(excessXP));
        newMetadata.put(SNAPSHOT_DURABILITY_KEY, new BsonDouble(newMaxDurability));

        if (!newMetadata.containsKey(ORIGINAL_NAME_KEY)) {
            newMetadata.put(ORIGINAL_NAME_KEY, new BsonString(originalName));
        }
        if (!newMetadata.containsKey(BASE_MAX_DURABILITY_KEY)) {
            newMetadata.put(BASE_MAX_DURABILITY_KEY, new BsonDouble(baseMaxDurability));
        }

        // Remove old deprecated keys if present to clean up
        if (newMetadata.containsKey("LincerosDurabilityLost"))
            newMetadata.remove("LincerosDurabilityLost");
        if (newMetadata.containsKey("LincerosLastDurability"))
            newMetadata.remove("LincerosLastDurability");

        // Create new item — optionally fully repaired based on config
        double newCurrentDurability = config.isLevelUpFullRepair() ? newMaxDurability : currentTool.getDurability();
        ItemStack leveledTool = new ItemStack(
                currentTool.getItemId(),
                currentTool.getQuantity(),
                newCurrentDurability, // current durability (repair or keep)
                newMaxDurability,     // max durability always increases
                newMetadata);

        // Replace item
        hotbar.replaceItemStackInSlot(slot, currentTool, leveledTool);

        // Notify
        int qualityValue = getItemQualityValue(item);
        int maxLevel = config.getMaxLevelForQuality(qualityValue);
        boolean hasInfiniteScaling = isInfiniteScalingItem(item.getId());
        String maxLevelStr = (hasInfiniteScaling || maxLevel < 0) ? "∞" : String.valueOf(maxLevel);

        if (config.isShowLevelUpMessage()) {
            String toolName = formatToolName(originalName);
            String statBonus = isWeapon ? "(+" + String.format("%.0f", config.getDamageBonusPercent()) + "% dmg)"
                    : "(+" + String.format("%.0f", config.getEfficiencyBonusPercent()) + "% eff)";

            Message levelUpMsg = Message.join(
                    Message.raw("⬆ LEVEL UP! ").color("#55FF55").bold(true),
                    Message.raw(toolName + " ").color("#FFFF55"),
                    Message.raw("is now ").color("#FFFFFF"),
                    Message.raw("Level " + newLevel).color("#55FFFF").bold(true),
                    Message.raw("/" + maxLevelStr + " ").color("#AAAAAA"),
                    Message.raw("(+" + String.format("%.0f", config.getDurabilityBonusPercent()) + "% dur, " + statBonus
                            + ")")
                            .color("#AAAAAA"));
            player.sendMessage(levelUpMsg);
        }

        LevelToolsPlugin.getHudService().forceRefresh(player.getUuid());

        // Milestone check — special message at landmark levels
        if (isMilestoneLevel(newLevel)) {
            sendMilestoneMessage(player, originalName, newLevel, isWeapon);
        }

        LOGGER.at(Level.INFO).log("[LevelTools] %s leveled up: %s -> Lv %d", isWeapon ? "W" : "T",
                currentTool.getItemId(), newLevel);
    }

    private String formatToolName(String itemId) {
        String name = itemId;
        if (name.startsWith("Tool_"))
            name = name.substring(5);
        else if (name.startsWith("Weapon_"))
            name = name.substring(7);
        return name.replace("_", " ");
    }

    private boolean isMilestoneLevel(int level) {
        for (int m : MILESTONE_LEVELS) {
            if (m == level) return true;
        }
        return false;
    }

    private void sendMilestoneMessage(Player player, String originalName, int level, boolean isWeapon) {
        String toolName = formatToolName(originalName);
        player.sendMessage(Message.raw("=========================================").color("#E8A93B"));
        player.sendMessage(Message.join(
                Message.raw("  *** MILESTONE REACHED! ***").color("#FFD700").bold(true)));
        player.sendMessage(Message.join(
                Message.raw("  ").color("#FFFFFF"),
                Message.raw(toolName).color("#55FFFF").bold(true),
                Message.raw(" is now ").color("#FFFFFF"),
                Message.raw("Level " + level + "!").color("#FFD700").bold(true)));
        player.sendMessage(Message.raw("=========================================").color("#E8A93B"));
    }

    // --- GETTERS (Dynamic Calculation) ---

    public static int getLevel(BsonDocument metadata) {
        if (metadata != null && metadata.containsKey(LEVEL_KEY))
            return metadata.getInt32(LEVEL_KEY).getValue();
        return 0;
    }

    public static int getToolLevel(ItemStack itemStack) {
        if (itemStack == null || ItemStack.isEmpty(itemStack))
            return 0;
        return getLevel(itemStack.getMetadata());
    }

    /**
     * Calculates total durability lost (XP) dynamically.
     * XP = ExcessStored + (Snapshot - Current)
     */
    public static double getToolDurabilityLost(ItemStack itemStack) {
        if (itemStack == null || ItemStack.isEmpty(itemStack))
            return 0;
        BsonDocument meta = itemStack.getMetadata();

        // 1. Get stored excess from previous levels (or migration)
        double excess = 0;
        if (meta != null) {
            if (meta.containsKey(DURABILITY_EXCESS_KEY)) {
                excess = meta.getDouble(DURABILITY_EXCESS_KEY).getValue();
            } else if (meta.containsKey("LincerosDurabilityLost")) {
                // Migration support for old key
                excess = meta.getDouble("LincerosDurabilityLost").getValue();
            }
        }

        // 2. Get snapshot
        double snapshot = itemStack.getMaxDurability(); // Default to MAX if not set (Fresh item)
        if (meta != null) {
            if (meta.containsKey(SNAPSHOT_DURABILITY_KEY)) {
                snapshot = meta.getDouble(SNAPSHOT_DURABILITY_KEY).getValue();
            } else if (meta.containsKey("LincerosLastDurability")) {
                // Migration support
                snapshot = meta.getDouble("LincerosLastDurability").getValue();
            }
        }

        // 3. Get current
        double current = itemStack.getDurability();

        // 4. Calculate diff
        double diff = snapshot - current;

        // If repaired (current > snapshot), diff is negative.
        // We clamp it to 0 effectively resetting progress for this "segment",
        // effectively punishing repairs by removing XP progress.
        if (diff < 0)
            diff = 0;

        return excess + diff;
    }

    public static String getOriginalName(BsonDocument metadata, String defaultName) {
        if (metadata != null && metadata.containsKey(ORIGINAL_NAME_KEY))
            return metadata.getString(ORIGINAL_NAME_KEY).getValue();
        return defaultName;
    }

    public static double getBaseMaxDurability(BsonDocument metadata, double defaultValue) {
        if (metadata != null && metadata.containsKey(BASE_MAX_DURABILITY_KEY))
            return metadata.getDouble(BASE_MAX_DURABILITY_KEY).getValue();
        return defaultValue;
    }

    public static int getPrestigeTokens(ItemStack itemStack) {
        if (itemStack == null || ItemStack.isEmpty(itemStack))
            return 0;
        BsonDocument meta = itemStack.getMetadata();
        if (meta != null && meta.containsKey(PRESTIGE_TOKENS_KEY))
            return meta.getInt32(PRESTIGE_TOKENS_KEY).getValue();
        return 0;
    }

    public static float getExpPercentage(ItemStack itemStack) {
        if (itemStack == null || ItemStack.isEmpty(itemStack))
            return 0f;

        LevelToolsConfig config = LevelToolsPlugin.getConfig();
        double lossToLevel = config.getDurabilityLossToLevel();
        double lost = getToolDurabilityLost(itemStack);

        // Apply weapon multiplier for accurate HUD display
        Item item = itemStack.getItem();
        if (item != null) {
            if (item.getWeapon() != null || (item.getId() != null && item.getId().startsWith("Weapon_"))) {
                lost *= config.getWeaponXPMultiplier();
            } else if (item.getTool() != null || (item.getId() != null && item.getId().startsWith("Tool_"))) {
                lost *= config.getToolXPMultiplier();
            }
        }

        return Math.min(1.0f, (float) (lost / lossToLevel));
    }
}
