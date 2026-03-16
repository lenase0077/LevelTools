package com.linceros.leveltools;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.ArrayList;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import com.linceros.leveltools.page.TokenPage;
import com.hypixel.hytale.server.core.inventory.Inventory;

public class LevelToolsDropSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ThreadLocal<Boolean> IS_VEIN_MINING = ThreadLocal.withInitial(() -> false);
    private final ComponentType<EntityStore, Player> playerType;
    private final Query<EntityStore> query;

    public LevelToolsDropSystem() {
        super(BreakBlockEvent.class);
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
            @Nonnull BreakBlockEvent event) {
        if (IS_VEIN_MINING.get()) {
            return;
        }

        BlockType blockType = event.getBlockType();
        String blockId = blockType.getId();

        // Simple heuristic: if it contains "ore", it's vein-mineable
        if (blockId == null || !blockId.toLowerCase().contains("ore")) {
            return;
        }

        ItemStack tool = event.getItemInHand();
        boolean hasVeinMiner = hasAbility(tool, "vein_miner");
        boolean hasSmelting = hasAbility(tool, "smelting");

        if (!hasVeinMiner && !hasSmelting) {
            return;
        }

        Vector3i startPos = event.getTargetBlock();
        World world = store.getExternalData().getWorld();

        // Cancel the game's default block-break processing to prevent natural drops.
        // The event fires before drops are spawned (it is a pre-break event), so
        // cancelling here stops both block removal AND drop generation by the engine.
        // We handle both ourselves below.
        // Note: tool durability is tracked by LevelToolsSystem via polling (EntityTickingSystem),
        // not via this event, so XP accumulation is unaffected.
        event.setCancelled(true);

        // Remove the block silently (no natural drops)
        world.setBlock(startPos.x, startPos.y, startPos.z, "Empty", 256);

        // Spawn our custom drops (smelted if auto smelt is active)
        spawnDrops(store, commandBuffer, blockType, startPos, tool);

        // Retrieve player for statistics tracking
        Player statsPlayer = archetypeChunk.getComponent(index, this.playerType);

        if (!hasVeinMiner) {
            incrementBlocksMined(statsPlayer, 1);
            return;
        }

        // BFS to find connected ores
        Queue<Vector3i> queue = new LinkedList<>();
        Set<Vector3i> visited = new HashSet<>();

        queue.add(startPos);
        visited.add(startPos);

        int brokenCount = 0;
        // Limit max blocks to prevent lags/crashes
        int maxBlocks = LevelToolsPlugin.getConfig().getVeinMinerMaxBlocks();

        long startTime = System.currentTimeMillis();

        IS_VEIN_MINING.set(true);
        try {
            while (!queue.isEmpty() && brokenCount < maxBlocks) {
                Vector3i currentPos = queue.poll();

                // startPos was already handled above; only process extra blocks
                if (!currentPos.equals(startPos)) {
                    BlockType currentBlock = world.getBlockType(currentPos);
                    if (currentBlock != null && currentBlock.getId().equals(blockId)) {
                        world.setBlock(currentPos.x, currentPos.y, currentPos.z, "Empty", 256);
                        brokenCount++;
                        spawnDrops(store, commandBuffer, currentBlock, currentPos, tool);
                    } else {
                        continue; // Not the same ore, stop this branch
                    }
                }

                // Add neighbors
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0)
                                continue;

                            Vector3i neighbor = new Vector3i(currentPos.x + dx, currentPos.y + dy, currentPos.z + dz);

                            // Check bounds/loaded chunks if necessary?
                            // Hytale API might handle unloaded chunks gracefully or return null block

                            if (!visited.contains(neighbor)) {
                                BlockType neighborBlock = world.getBlockType(neighbor);
                                if (neighborBlock != null && neighborBlock.getId().equals(blockId)) {
                                    visited.add(neighbor);
                                    queue.add(neighbor);
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            IS_VEIN_MINING.set(false);
        }

        if (brokenCount > 0) {
            LOGGER.at(Level.FINE).log("[LevelTools] VeinMiner: broke %d extra %s in %dms",
                    brokenCount, blockId, System.currentTimeMillis() - startTime);
        }

        incrementBlocksMined(statsPlayer, 1 + brokenCount);
    }

    private void incrementBlocksMined(Player player, int count) {
        if (player == null || count <= 0) return;

        Inventory inv = player.getInventory();
        if (inv == null) return;

        byte slot = inv.getActiveHotbarSlot();
        if (slot < 0) return;

        ItemStack current = inv.getHotbar().getItemStack(slot);
        if (ItemStack.isEmpty(current)) return;

        BsonDocument meta = current.getMetadata() != null ? current.getMetadata().clone() : new BsonDocument();
        int blocks = meta.containsKey(LevelToolsSystem.BLOCKS_MINED_KEY)
                ? meta.getInt32(LevelToolsSystem.BLOCKS_MINED_KEY).getValue() : 0;
        meta.put(LevelToolsSystem.BLOCKS_MINED_KEY, new BsonInt32(blocks + count));

        ItemStack updated = new ItemStack(
                current.getItemId(), current.getQuantity(),
                current.getDurability(), current.getMaxDurability(), meta);
        inv.getHotbar().replaceItemStackInSlot(slot, current, updated);
        LOGGER.at(Level.FINE).log("[LevelTools] Blocks mined: +%d on %s (total: %d)",
                count, current.getItemId(), blocks + count);
    }

    public static void spawnDrops(Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer,
            BlockType blockType,
            Vector3i pos, ItemStack tool) {
        try {
            List<ItemStack> drops = new ArrayList<>();
            BlockGathering g = blockType.getGathering();

            // Logic adapted from VeinMining mod to handle custom ore drops (raw ore vs
            // block)
            String dropListId = null;
            String itemId = null;
            int quantity = 1;

            if (g != null) {
                if (g.getBreaking() != null) {
                    dropListId = g.getBreaking().getDropListId();
                    itemId = g.getBreaking().getItemId();
                    quantity = g.getBreaking().getQuantity();
                    if (quantity <= 0)
                        quantity = 1;
                } else if (g.getSoft() != null) {
                    // Fallback to soft break if breaking is not defined (unlikely for ores but good
                    // safety)
                    dropListId = g.getSoft().getDropListId();
                    itemId = g.getSoft().getItemId();
                }
            }

            if (dropListId != null || itemId != null) {
                if (dropListId != null) {
                    try {
                        // Reflection to access ItemModule for drop lists (e.g. random ore drops)
                        Class<?> itemModuleClass = Class
                                .forName("com.hypixel.hytale.server.core.modules.item.ItemModule");
                        Object instance = itemModuleClass.getMethod("get").invoke(null);
                        java.lang.reflect.Method getRandomItemDrops = itemModuleClass.getMethod("getRandomItemDrops",
                                String.class);

                        for (int i = 0; i < quantity; ++i) {
                            List<?> randomDrops = (List<?>) getRandomItemDrops.invoke(instance, dropListId);
                            if (randomDrops != null) {
                                for (Object obj : randomDrops) {
                                    if (obj instanceof ItemStack) {
                                        drops.add((ItemStack) obj);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.at(Level.SEVERE).log("Error getting drop list for " + dropListId, e);
                    }
                }
                if (itemId != null) {
                    drops.add(new ItemStack(itemId, quantity));
                }
            } else {
                // Fallback: Drop the block itself
                if (blockType.getItem() != null) {
                    drops.add(new ItemStack(blockType.getItem().getId(), 1));
                } else {
                    drops.add(new ItemStack(blockType.getId(), 1));
                }
            }

            // Smelting Ability Logic
            if (hasAbility(tool, "smelting")) {
                List<ItemStack> smeltedDrops = new ArrayList<>();
                for (ItemStack drop : drops) {
                    String id = drop.getItemId();
                    String newId = getSmeltedId(id);
                    if (newId != null) {
                        smeltedDrops.add(new ItemStack(newId, drop.getQuantity()));
                    } else {
                        smeltedDrops.add(drop);
                    }
                }
                drops = smeltedDrops;
            }

            if (drops.isEmpty())
                return;

            Vector3d dropPos = new Vector3d(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);

            Holder<EntityStore>[] itemEntities = ItemComponent.generateItemDrops(commandBuffer, drops, dropPos,
                    Vector3f.ZERO);

            if (itemEntities != null && itemEntities.length > 0) {
                commandBuffer.addEntities(itemEntities, AddReason.SPAWN);
            }
        } catch (Exception e) {
            LOGGER.at(Level.SEVERE).log("Error spawning drops", e);
        }
    }

    private static String getSmeltedId(String oreId) {
        if (oreId == null || !oreId.startsWith("Ore_"))
            return null;

        if (oreId.contains("Copper"))
            return "Ingredient_Bar_Copper";
        if (oreId.contains("Iron"))
            return "Ingredient_Bar_Iron";
        if (oreId.contains("Gold"))
            return "Ingredient_Bar_Gold";
        if (oreId.contains("Thorium"))
            return "Ingredient_Bar_Thorium";
        if (oreId.contains("Silver"))
            return "Ingredient_Bar_Silver";
        if (oreId.contains("Cobalt"))
            return "Ingredient_Bar_Cobalt";
        if (oreId.contains("Mithril"))
            return "Ingredient_Bar_Mithril";
        if (oreId.contains("Adamantite"))
            return "Ingredient_Bar_Adamantite";
        if (oreId.contains("Onyxium"))
            return "Ingredient_Bar_Onyxium";

        return null;
    }

    @SuppressWarnings("deprecation")
    private static boolean hasAbility(ItemStack item, String abilityId) {
        if (item == null || item.getMetadata() == null || !item.getMetadata().containsKey(TokenPage.ABILITIES_KEY)) {
            return false;
        }
        BsonArray abilities = item.getMetadata().getArray(TokenPage.ABILITIES_KEY);
        for (BsonValue val : abilities) {
            String existingId = val.asString().getValue();
            if (existingId.equals(abilityId))
                return true;
        }
        return false;
    }
}
