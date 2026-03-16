package com.linceros.leveltools;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.logging.Level;

/**
 * System that intercepts damage events and applies bonus damage
 * based on the weapon's level.
 * 
 * When a player attacks with a leveled weapon:
 * - The damage is multiplied by (1 + level * bonusPercent/100)
 * 
 * Example: Level 5 weapon with 5% bonus = 1.25x damage multiplier
 * 
 * Note: XP/leveling is now handled by LevelToolsSystem via durability tracking.
 */
public class LevelToolsDamageSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final ComponentType<EntityStore, Player> playerType;
    private final Query<EntityStore> query;

    public LevelToolsDamageSystem() {
        this.playerType = EntityModule.get().getPlayerComponentType();
        this.query = Query.any();
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
            @Nonnull Damage damage) {

        // Skip if damage is cancelled
        if (damage.isCancelled()) {
            return;
        }

        // Get the source of the damage
        Damage.Source source = damage.getSource();
        if (source == null) {
            return;
        }

        // We only care about EntitySource (player/NPC attacking)
        if (!(source instanceof Damage.EntitySource)) {
            return;
        }

        Damage.EntitySource entitySource = (Damage.EntitySource) source;
        Ref<EntityStore> attackerRef = entitySource.getRef();

        if (attackerRef == null) {
            return;
        }

        // Try to get the attacker's Player component
        Player attacker = store.getComponent(attackerRef, this.playerType);
        if (attacker == null) {
            return;
        }

        // Get attacker's inventory
        Inventory inventory = attacker.getInventory();
        if (inventory == null) {
            return;
        }

        // Get the item in the active hotbar slot
        ItemStack itemInHand = inventory.getItemInHand();
        if (ItemStack.isEmpty(itemInHand)) {
            return;
        }

        // Check if it's a weapon
        Item item = itemInHand.getItem();
        if (item == null) {
            return;
        }

        ItemWeapon weapon = item.getWeapon();
        if (weapon == null) {
            return;
        }

        // Skip if unbreakable
        if (itemInHand.isUnbreakable()) {
            return;
        }

        // Get the weapon level from metadata
        int level = LevelToolsSystem.getToolLevel(itemInHand);

        // Apply damage bonus if leveled
        if (level > 0) {
            LevelToolsConfig config = LevelToolsPlugin.getConfig();
            double damageMultiplier = 1.0 + (level * config.getDamageBonusPercent() / 100.0);

            float originalDamage = damage.getAmount();
            float newDamage = (float) (originalDamage * damageMultiplier);

            damage.setAmount(newDamage);

            LOGGER.at(Level.FINE).log("[LevelTools] Damage boosted: %.1f -> %.1f (Level %d weapon, %.0f%% bonus)",
                    originalDamage, newDamage, level, (damageMultiplier - 1) * 100);
        }

        // Note: XP tracking is now done by LevelToolsSystem via durability loss
        // detection
    }
}
