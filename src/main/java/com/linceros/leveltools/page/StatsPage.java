package com.linceros.leveltools.page;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
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
import com.linceros.leveltools.LevelToolsPlugin;
import com.linceros.leveltools.LevelToolsSystem;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;

/**
 * Read-only statistics page showing per-item history:
 * enemies defeated (weapons), ore blocks mined (tools), and total XP earned.
 *
 * Opened via /leveltools stats
 */
public class StatsPage extends InteractiveCustomUIPage<StatsPage.StatsData> {

    private static final String UI_PATH = "LevelTools/LincerosStats.ui";

    private Ref<EntityStore> entityRef;
    private Store<EntityStore> store;

    public StatsPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, StatsData.CODEC);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder builder,
            @Nonnull UIEventBuilder events, @Nonnull Store<EntityStore> store) {
        this.entityRef = ref;
        this.store = store;

        builder.append(UI_PATH);

        Player player = store.getComponent(ref, EntityModule.get().getPlayerComponentType());
        if (player == null) return;

        Inventory inv = player.getInventory();
        if (inv == null) return;

        ItemStack heldItem = inv.getActiveHotbarItem();
        applyView(builder, heldItem);

        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
                EventData.of("Action", "close"), false);
    }

    @Override
    protected void rebuild() {
        if (entityRef == null || store == null) return;
        UICommandBuilder builder = new UICommandBuilder();
        Player player = store.getComponent(entityRef, EntityModule.get().getPlayerComponentType());
        if (player == null) return;

        Inventory inv = player.getInventory();
        ItemStack heldItem = inv != null ? inv.getActiveHotbarItem() : null;
        applyView(builder, heldItem);
        this.sendUpdate(builder, false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
            @Nonnull StatsData data) {
        if (data == null || data.action == null) return;
        if ("close".equals(data.action)) {
            this.close();
        }
    }

    private void applyView(UICommandBuilder builder, ItemStack item) {
        if (ItemStack.isEmpty(item)) {
            builder.set("#StatsItemName.Text", "No Item Held");
            builder.set("#StatsItemLevel.Text", "Level 0");
            builder.set("#KillsValue.Text", "—");
            builder.set("#KillsValue.Style.TextColor", "#5a6a7a");
            builder.set("#BlocksValue.Text", "—");
            builder.set("#BlocksValue.Style.TextColor", "#5a6a7a");
            builder.set("#XpValue.Text", "—");
            return;
        }

        BsonDocument meta = item.getMetadata();
        int level = LevelToolsSystem.getToolLevel(item);

        // Item name and level
        builder.set("#StatsItemName.Text", formatItemName(item.getItemId()));
        builder.set("#StatsItemName.Style.TextColor", getQualityColorHex(item));
        builder.set("#StatsItemLevel.Text", "Level " + level);
        builder.set("#StatsItemLevel.Style.TextColor", getQualityColorHex(item));

        // Item slot display
        builder.set("#StatsItemSlot.ItemId", item.getItemId());
        builder.set("#StatsItemSlot.Quantity", 1);

        // Detect type — same pattern used by LevelToolsSystem
        com.hypixel.hytale.server.core.asset.type.item.config.Item configItem = item.getItem();
        String itemId = configItem != null ? configItem.getId() : "";
        boolean isWeapon = (configItem != null && configItem.getWeapon() != null)
                || (itemId != null && itemId.startsWith("Weapon_"));
        boolean isTool = (configItem != null && configItem.getTool() != null)
                || (itemId != null && itemId.startsWith("Tool_"));

        // Enemies Defeated — weapons only
        if (isWeapon) {
            int kills = (meta != null && meta.containsKey(LevelToolsSystem.KILL_COUNT_KEY))
                    ? meta.getInt32(LevelToolsSystem.KILL_COUNT_KEY).getValue() : 0;
            builder.set("#KillsValue.Text", formatNumber(kills));
            builder.set("#KillsValue.Style.TextColor", "#E8A93B");
        } else {
            builder.set("#KillsValue.Text", "N/A");
            builder.set("#KillsValue.Style.TextColor", "#3d4a5a");
        }

        // Ore Blocks Mined — tools only
        if (isTool) {
            int blocks = (meta != null && meta.containsKey(LevelToolsSystem.BLOCKS_MINED_KEY))
                    ? meta.getInt32(LevelToolsSystem.BLOCKS_MINED_KEY).getValue() : 0;
            builder.set("#BlocksValue.Text", formatNumber(blocks));
            builder.set("#BlocksValue.Style.TextColor", "#E8A93B");
        } else {
            builder.set("#BlocksValue.Text", "N/A");
            builder.set("#BlocksValue.Style.TextColor", "#3d4a5a");
        }

        // Total XP earned: approximately level * durabilityLossToLevel
        long totalXP = (long) (level * LevelToolsPlugin.getConfig().getDurabilityLossToLevel());
        builder.set("#XpValue.Text", formatNumber((int) Math.min(totalXP, Integer.MAX_VALUE)));
    }

    // Format large numbers as 1.2K / 3.4M etc.
    private String formatNumber(int num) {
        if (num >= 1_000_000) return String.format("%.1fM", num / 1_000_000.0);
        if (num >= 1_000) return String.format("%.1fK", num / 1_000.0);
        return String.valueOf(num);
    }

    private String formatItemName(String itemId) {
        if (itemId == null) return "Unknown";
        if (itemId.startsWith("Tool_")) itemId = itemId.substring(5);
        if (itemId.startsWith("Weapon_")) itemId = itemId.substring(7);
        return itemId.replace("_", " ");
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

    public static class StatsData {
        public String action;

        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }

        public static final BuilderCodec<StatsData> CODEC = BuilderCodec.builder(StatsData.class, StatsData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), StatsData::setAction, StatsData::getAction)
                .build();
    }
}
