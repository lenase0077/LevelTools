package com.linceros.leveltools;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemWeapon;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.protocol.ChangeStatBehaviour;
import com.hypixel.hytale.protocol.ValueType;
import com.linceros.leveltools.page.TokenPage;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonValue;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
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
    private final Set<Dependency<EntityStore>> dependencies;

    public LevelToolsDamageSystem() {
        this.playerType = EntityModule.get().getPlayerComponentType();
        this.query = Query.any();
        // Run AFTER ArmorDamageReduction to modify final damage or apply effects based
        // on it
        this.dependencies = Set.of(new SystemDependency(Order.AFTER, DamageSystems.ArmorDamageReduction.class));
    }

    @Override
    @Nullable
    public SystemGroup<EntityStore> getGroup() {
        // Use the FilterDamageGroup like RPGCombatSystem to ensure proper ordering in
        // the pipeline
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    @Nonnull
    public Set<Dependency<EntityStore>> getDependencies() {
        return this.dependencies;
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

        // Get config once for the entire handler
        LevelToolsConfig config = LevelToolsPlugin.getConfig();

        // Apply damage bonus if leveled
        if (level > 0) {
            double damageMultiplier = 1.0 + (level * config.getDamageBonusPercent() / 100.0);

            float originalDamage = damage.getAmount();
            float newDamage = (float) (originalDamage * damageMultiplier);

            damage.setAmount(newDamage);

            LOGGER.at(Level.FINE).log("[LevelTools] Damage boosted: %.1f -> %.1f (Level %d weapon, %.0f%% bonus)",
                    originalDamage, newDamage, level, (damageMultiplier - 1) * 100);
        }

        // --- FIRE ASPECT SKILL ---
        if (hasAbility(itemInHand, "fire_aspect")) {
            LOGGER.at(Level.FINE).log("[LevelTools] Triggering Fire Aspect!");
            applyFireAspect(store, archetypeChunk, index, commandBuffer);
        }

        // --- VAMPIRISM SKILL ---
        if (hasAbility(itemInHand, "vampirism")) {
            LOGGER.at(Level.FINE).log("[LevelTools] Triggering Vampirism!");
            applyVampirism(attackerRef, store, commandBuffer, damage.getAmount());
        }

        // --- POISON SKILL ---
        if (hasAbility(itemInHand, "poison")) {
            LOGGER.at(Level.FINE).log("[LevelTools] Triggering Poison!");
            applyPoison(store, archetypeChunk, index, commandBuffer);
        }

        // --- IGNORE DEFENSE (UNSTOPPABLE) ---
        if (hasAbility(itemInHand, "ignore_defense")) {
            float currentDmg = damage.getAmount();
            damage.setAmount(currentDmg * (float) config.getIgnoreDefenseMultiplier());
            LOGGER.at(Level.FINE).log("[LevelTools] Triggering Unstoppable (Damage x%.2f)", config.getIgnoreDefenseMultiplier());
        }

        // --- ENERGY SAVER ---
        if (hasAbility(itemInHand, "energy")) {
            LOGGER.at(Level.FINE).log("[LevelTools] Triggering Energy Saver!");
            refundStamina(attackerRef, store);
        }

        // --- KILL DETECTION (Statistics) ---
        // Check if this hit will be lethal and record a kill on the weapon
        Ref<EntityStore> victimRef = archetypeChunk.getReferenceTo(index);
        if (victimRef != null) {
            EntityStatMap victimStats = store.getComponent(victimRef, EntityStatMap.getComponentType());
            if (victimStats != null) {
                EntityStatValue victimHealth = victimStats.get(DefaultEntityStatTypes.getHealth());
                if (victimHealth != null && victimHealth.get() - damage.getAmount() <= 0) {
                    incrementKillOnItem(attacker);
                }
            }
        }
    }

    private boolean hasAbility(ItemStack item, String abilityId) {
        if (item.getMetadata() == null || !item.getMetadata().containsKey(TokenPage.ABILITIES_KEY)) {
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

    private void applyFireAspect(Store<EntityStore> store, ArchetypeChunk<EntityStore> chunk, int index,
            CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
        if (victimRef == null)
            return;

        EffectControllerComponent effects = store.getComponent(victimRef, EffectControllerComponent.getComponentType());
        if (effects != null) {
            EntityEffect burn = EntityEffect.getAssetMap().getAsset("Burn");
            if (burn != null) {
                float burnDuration = (float) LevelToolsPlugin.getConfig().getFireBurnDurationSeconds();
                effects.addEffect(victimRef, burn, burnDuration,
                        com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior.OVERWRITE,
                        commandBuffer);
            }
        }
    }

    private void applyVampirism(Ref<EntityStore> attackerRef, Store<EntityStore> store,
            CommandBuffer<EntityStore> commandBuffer, float damageAmount) {
        if (damageAmount <= 0)
            return;

        EntityStatMap stats = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (stats != null) {
            // Use DefaultEntityStatTypes for reliable lookup
            EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
            if (health != null) {
                float healAmount = damageAmount * (float) (LevelToolsPlugin.getConfig().getVampirismLifestealPercent() / 100.0);
                float currentHealth = health.get();
                float maxHealth = health.getMax();

                // Heal only if not full health
                if (currentHealth < maxHealth) {
                    it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap changes = new it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap();
                    changes.put(health.getIndex(), healAmount);
                    stats.processStatChanges(
                            com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.Predictable.ALL, changes,
                            ValueType.Absolute, ChangeStatBehaviour.Add);
                }
            }
        }
    }

    private void applyPoison(Store<EntityStore> store, ArchetypeChunk<EntityStore> chunk, int index,
            CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> victimRef = chunk.getReferenceTo(index);
        if (victimRef == null)
            return;

        EffectControllerComponent effects = store.getComponent(victimRef, EffectControllerComponent.getComponentType());
        if (effects != null) {
            EntityEffect poison = EntityEffect.getAssetMap().getAsset("Poison");
            if (poison != null) {
                float poisonDuration = (float) LevelToolsPlugin.getConfig().getPoisonDurationSeconds();
                effects.addEffect(victimRef, poison, poisonDuration,
                        com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior.OVERWRITE,
                        commandBuffer);
            } else {
                LOGGER.at(Level.WARNING).log("[LevelTools] Could not find 'Poison' effect asset.");
            }
        }
    }

    private void refundStamina(Ref<EntityStore> attackerRef, Store<EntityStore> store) {
        EntityStatMap stats = store.getComponent(attackerRef, EntityStatMap.getComponentType());
        if (stats != null) {
            // Use DefaultEntityStatTypes for reliable lookup
            EntityStatValue stamina = stats.get(DefaultEntityStatTypes.getStamina());
            if (stamina != null) {
                // Refund stamina amount from config
                float refund = (float) LevelToolsPlugin.getConfig().getEnergySaverStaminaRefund();
                float current = stamina.get();
                float max = stamina.getMax();

                if (current < max) {
                    it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap changes = new it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap();
                    changes.put(stamina.getIndex(), refund);
                    stats.processStatChanges(
                            com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap.Predictable.ALL, changes,
                            ValueType.Absolute, ChangeStatBehaviour.Add);
                }
            }
        }
    }

    private void incrementKillOnItem(Player attacker) {
        Inventory inv = attacker.getInventory();
        if (inv == null) return;

        byte slot = inv.getActiveHotbarSlot();
        if (slot < 0) return;

        ItemStack current = inv.getHotbar().getItemStack(slot);
        if (ItemStack.isEmpty(current)) return;

        com.hypixel.hytale.server.core.asset.type.item.config.Item itemConfig = current.getItem();
        if (itemConfig == null || itemConfig.getWeapon() == null) return;

        BsonDocument meta = current.getMetadata() != null ? current.getMetadata().clone() : new BsonDocument();
        int kills = meta.containsKey(LevelToolsSystem.KILL_COUNT_KEY)
                ? meta.getInt32(LevelToolsSystem.KILL_COUNT_KEY).getValue() : 0;
        meta.put(LevelToolsSystem.KILL_COUNT_KEY, new BsonInt32(kills + 1));

        ItemStack updated = new ItemStack(
                current.getItemId(), current.getQuantity(),
                current.getDurability(), current.getMaxDurability(), meta);
        inv.getHotbar().replaceItemStackInSlot(slot, current, updated);
        LOGGER.at(Level.FINE).log("[LevelTools] Kill recorded on %s (total: %d)", current.getItemId(), kills + 1);
    }
}
