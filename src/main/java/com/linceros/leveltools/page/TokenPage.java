package com.linceros.leveltools.page;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.linceros.leveltools.LevelToolsConfig;
import com.linceros.leveltools.LevelToolsPlugin;
import com.linceros.leveltools.LevelToolsSystem;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TokenPage extends InteractiveCustomUIPage<TokenPage.TokenData> {

    private static final String UI_PATH = "LevelTools/LincerosTokens.ui";
    public static final String ABILITIES_KEY = "LincerosToolAbilities";

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private Ref<EntityStore> entityRef;
    private Store<EntityStore> store;

    private static final Map<UUID, Set<String>> draftSessions = new ConcurrentHashMap<>();

    public TokenPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, TokenData.CODEC);
    }

    public enum ToolAbility {
        FIRE_ASPECT("fire_aspect", "Fire Aspect", 1, "#AbilityFireAspect", true, false),
        VAMPIRISM("vampirism", "Vampirism", 2, "#AbilityVampirism", true, false),
        VEIN_MINER("vein_miner", "Vein Miner", 1, "#AbilityVeinMiner", false, true),
        POISON("poison", "Poison", 1, "#AbilityPoison", true, false),
        IGNORE_DEFENSE("ignore_defense", "Unstoppable", 3, "#AbilityIgnoreDefense", true, false),
        ENERGY("energy", "Energy Saver", 2, "#AbilityEnergy", true, false),
        SMELTING("smelting", "Auto Smelt", 1, "#AbilitySmelting", false, true);

        public final String id;
        public final String diffName;
        public final int cost;
        public final String uiId;
        public final boolean isWeapon;
        public final boolean isTool;

        ToolAbility(String id, String name, int cost, String uiId, boolean weapon, boolean tool) {
            this.id = id;
            this.diffName = name;
            this.cost = cost;
            this.uiId = uiId;
            this.isWeapon = weapon;
            this.isTool = tool;
        }

        public static ToolAbility fromId(String id, boolean searchingForTool) {
            for (ToolAbility a : values()) {
                if (a.id.equals(id)) {
                    if (searchingForTool && a.isTool)
                        return a;
                    if (!searchingForTool && a.isWeapon)
                        return a;
                }
            }
            return null;
        }

        public static ToolAbility fromUiId(String uiId) {
            for (ToolAbility a : values()) {
                if (a.uiId.equals(uiId))
                    return a;
            }
            return null;
        }
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder builder,
            @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        this.entityRef = ref;
        this.store = store;

        builder.append(UI_PATH);
        Player player = store.getComponent(ref, EntityModule.get().getPlayerComponentType());
        if (player == null)
            return;

        Inventory inv = player.getInventory();
        if (inv == null)
            return;

        ItemStack heldItem = inv.getActiveHotbarItem();
        UUID uuid = playerRef.getUuid();
        if (!draftSessions.containsKey(uuid)) {
            Set<String> currentAbilities = getAbilitiesFromItem(heldItem);
            draftSessions.put(uuid, new HashSet<>(currentAbilities));
        }

        Set<String> selectedAbilities = draftSessions.get(uuid);
        if (ItemStack.isEmpty(heldItem)) {
            applyEmptyView(builder);
        } else {
            applyView(builder, heldItem, selectedAbilities);
        }

        bindEvents(events);
    }

    private void bindEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"),
                false);

        for (ToolAbility ability : ToolAbility.values()) {
            events.addEventBinding(CustomUIEventBindingType.Activating, ability.uiId,
                    EventData.of("Action", "toggle_" + ability.uiId), false);
        }
    }

    @Override
    protected void rebuild() {
        if (entityRef == null || store == null)
            return;
        UICommandBuilder builder = new UICommandBuilder();
        Player player = store.getComponent(entityRef, EntityModule.get().getPlayerComponentType());
        if (player == null)
            return;

        Inventory inv = player.getInventory();
        ItemStack heldItem = inv != null ? inv.getActiveHotbarItem() : null;
        Set<String> selectedAbilities = draftSessions.get(playerRef.getUuid());

        if (ItemStack.isEmpty(heldItem))
            applyEmptyView(builder);
        else
            applyView(builder, heldItem, selectedAbilities);

        this.sendUpdate(builder, false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull TokenData data) {
        if (data == null || data.action == null)
            return;
        String action = data.action;
        UUID uuid = playerRef.getUuid();

        if (action.startsWith("toggle_")) {
            String uiId = action.substring("toggle_".length());
            ToolAbility ability = ToolAbility.fromUiId(uiId);
            if (ability == null)
                return;

            Inventory inv = store.getComponent(ref, EntityModule.get().getPlayerComponentType()).getInventory();
            ItemStack heldItem = inv.getActiveHotbarItem();
            int totalPoints = LevelToolsSystem.getPrestigeTokens(heldItem);
            Set<String> draft = draftSessions.getOrDefault(uuid, new HashSet<>());

            if (draft.contains(ability.id)) {
                draft.remove(ability.id);
            } else {
                int spent = calculateSpentPoints(draft);
                if (spent + ability.cost <= totalPoints)
                    draft.add(ability.id);
            }
            draftSessions.put(uuid, draft);
            this.rebuild();
            return;
        }

        if (action.equals("close")) {
            Inventory inv = store.getComponent(ref, EntityModule.get().getPlayerComponentType()).getInventory();
            ItemStack heldItem = inv.getActiveHotbarItem();
            if (!ItemStack.isEmpty(heldItem)) {
                saveAbilitiesToItem(heldItem, draftSessions.getOrDefault(uuid, new HashSet<>()), inv);
            }
            draftSessions.remove(uuid);
            this.close();
            return;
        }
    }

    /**
     * Called when the page is dismissed (either via Close button or forced by server/disconnect).
     * Saves any pending ability draft so changes are never silently lost.
     */
    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUID uuid = playerRef.getUuid();
        // If the close button already saved and removed the draft, skip.
        // Otherwise this would overwrite the item's abilities with an empty set.
        if (!draftSessions.containsKey(uuid)) {
            return;
        }
        Player player = store.getComponent(ref, EntityModule.get().getPlayerComponentType());
        if (player != null) {
            Inventory inv = player.getInventory();
            ItemStack heldItem = inv != null ? inv.getActiveHotbarItem() : null;
            if (!ItemStack.isEmpty(heldItem)) {
                saveAbilitiesToItem(heldItem, draftSessions.getOrDefault(uuid, new HashSet<>()), inv);
            }
        }
        draftSessions.remove(uuid);
    }

    /**
     * Clean up the draft session for a player (e.g. on disconnect).
     * Called externally from LevelToolsPlugin to prevent memory leaks.
     */
    public static void cleanupSession(UUID uuid) {
        draftSessions.remove(uuid);
    }

    private void applyView(UICommandBuilder builder, ItemStack item, Set<String> selectedAbilities) {
        builder.set("#ItemName.Text", formatItemName(item.getItemId()));
        builder.set("#ItemName.Style.TextColor", getQualityColorHex(item));
        int level = LevelToolsSystem.getToolLevel(item);
        builder.set("#ItemLevel.Text", "Level " + level);
        builder.set("#ItemLevel.Style.TextColor", getQualityColorHex(item));
        builder.set("#ItemMaxLevel.Text", "Max: " + getMaxLevelString(item));
        builder.set("#HeldItemSlot.ItemId", item.getItemId());
        builder.set("#HeldItemSlot.Quantity", 1);

        int totalTokens = LevelToolsSystem.getPrestigeTokens(item);
        int spentTokens = calculateSpentPoints(selectedAbilities);
        int availableTokens = totalTokens - spentTokens;
        builder.set("#TokensCount.Text", String.valueOf(availableTokens));
        builder.set("#TokensCount.Style.TextColor", availableTokens == 0 ? "#AAAAAA" : "#FFFF55");

        com.hypixel.hytale.server.core.asset.type.item.config.Item configItem = item.getItem();
        boolean isTool = configItem != null && configItem.getTool() != null;
        boolean isWeapon = configItem != null && configItem.getWeapon() != null;
        String itemId = configItem != null ? configItem.getId() : "";

        if (!isTool && !isWeapon) {
            if (itemId != null && itemId.startsWith("Tool_"))
                isTool = true;
            else if (itemId != null && itemId.startsWith("Weapon_"))
                isWeapon = true;
        }

        builder.set("#MasteryTitle.Text", isTool ? "TOOL MASTERY" : "WEAPON MASTERY");
        builder.set("#WeaponGrid.Visible", isWeapon);
        builder.set("#ToolGrid.Visible", isTool);

        for (ToolAbility ability : ToolAbility.values()) {
            boolean isSelected = selectedAbilities.contains(ability.id);
            boolean isApplicable = (isTool && ability.isTool) || (isWeapon && ability.isWeapon);

            if (isApplicable) {
                builder.set(ability.uiId + "Level.Text", isSelected ? "Active" : "Cost: " + ability.cost);
                builder.set(ability.uiId + "Level.Style.TextColor", isSelected ? "#55FF55" : "#AAAAAA");
            }
        }
    }

    private void applyEmptyView(UICommandBuilder builder) {
        builder.set("#ItemName.Text", "No Valid Item");
        builder.set("#ItemLevel.Text", "Level 0");
        builder.set("#ItemLevel.Style.TextColor", "#AAAAAA");
        builder.set("#ItemMaxLevel.Text", "");
        builder.set("#TokensCount.Text", "0");
        builder.set("#WeaponGrid.Visible", false);
        builder.set("#ToolGrid.Visible", false);
    }

    private int calculateSpentPoints(Set<String> abilities) {
        int spent = 0;
        for (String id : abilities) {
            // Search all abilities regardless of weapon/tool type to avoid missing costs
            ToolAbility found = null;
            for (ToolAbility a : ToolAbility.values()) {
                if (a.id.equals(id)) {
                    found = a;
                    break;
                }
            }
            if (found != null) {
                spent += found.cost;
            }
        }
        return spent;
    }

    @SuppressWarnings("deprecation")
    private Set<String> getAbilitiesFromItem(ItemStack item) {
        Set<String> result = new HashSet<>();
        if (ItemStack.isEmpty(item) || item.getMetadata() == null)
            return result;
        BsonDocument meta = item.getMetadata();
        if (meta.containsKey(ABILITIES_KEY)) {
            BsonArray arr = meta.getArray(ABILITIES_KEY);
            for (BsonValue val : arr) {
                if (val.isString())
                    result.add(val.asString().getValue());
            }
        }
        return result;
    }

    @SuppressWarnings("deprecation")
    private void saveAbilitiesToItem(ItemStack oldItem, Set<String> abilities, Inventory inv) {
        BsonDocument meta = oldItem.getMetadata();
        if (meta == null)
            meta = new BsonDocument();

        BsonArray arr = new BsonArray();
        for (String s : abilities)
            arr.add(new BsonString(s));

        // Ensure we are working with a fresh clone to avoid reference issues
        BsonDocument newMeta = meta.clone();
        newMeta.put(ABILITIES_KEY, arr);

        // Debug
        // System.out.println("[LevelTools] Saving abilities to item: " +
        // arr.toString());

        ItemStack newItem = new ItemStack(oldItem.getItemId(), oldItem.getQuantity(), oldItem.getDurability(),
                oldItem.getMaxDurability(), newMeta);

        int slot = inv.getActiveHotbarSlot();
        if (slot >= 0) {
            // Use setItemStackForSlot to force the update regardless of previous state
            inv.getHotbar().setItemStackForSlot((short) slot, newItem);
        }
        LOGGER.at(Level.FINE).log("[LevelTools] Saved abilities for %s: %s", newItem.getItemId(), newMeta.toString());
    }

    private String getQualityColorHex(ItemStack item) {
        com.hypixel.hytale.server.core.asset.type.item.config.Item configItem = item.getItem();
        if (configItem == null) return "#FFFFFF";
        try {
            int qualityIndex = configItem.getQualityIndex();
            com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality quality =
                    com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality
                            .getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                com.hypixel.hytale.protocol.Color c = quality.getTextColor();
                if (c != null) {
                    return String.format("#%02X%02X%02X",
                            Byte.toUnsignedInt(c.red),
                            Byte.toUnsignedInt(c.green),
                            Byte.toUnsignedInt(c.blue));
                }
            }
        } catch (Throwable t) {
            // fallback
        }
        return "#FFFFFF";
    }

    private String getMaxLevelString(ItemStack item) {
        com.hypixel.hytale.server.core.asset.type.item.config.Item configItem = item.getItem();
        if (configItem == null)
            return "∞";
        // Items with infinite scaling regardless of quality
        if ("Tool_Hoe_Crude".equals(configItem.getId()))
            return "∞";
        LevelToolsConfig config = LevelToolsPlugin.getConfig();
        try {
            int qualityIndex = configItem.getQualityIndex();
            com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality quality =
                    com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality
                            .getAssetMap().getAsset(qualityIndex);
            if (quality != null) {
                int maxLevel = config.getMaxLevelForQuality(quality.getQualityValue());
                return maxLevel < 0 ? "∞" : String.valueOf(maxLevel);
            }
        } catch (Throwable t) {
            // fallback
        }
        return "∞";
    }

    private String formatItemName(String itemId) {
        if (itemId == null)
            return "Unknown";
        if (itemId.startsWith("Tool_"))
            itemId = itemId.substring(5);
        if (itemId.startsWith("Weapon_"))
            itemId = itemId.substring(7);
        return itemId.replace("_", " ");
    }

    public static class TokenData {
        public String action;

        public TokenData() {
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public static final BuilderCodec<TokenData> CODEC = BuilderCodec.builder(TokenData.class, TokenData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), TokenData::setAction, TokenData::getAction)
                .build();
    }
}
