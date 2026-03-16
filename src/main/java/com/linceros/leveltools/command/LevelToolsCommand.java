package com.linceros.leveltools.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.linceros.leveltools.hud.LevelToolsHudService;
import com.hypixel.hytale.server.core.command.system.exceptions.SenderTypeException;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import java.awt.Color;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.linceros.leveltools.LevelToolsSystem;
import com.linceros.leveltools.LevelToolsPlugin;
import com.linceros.leveltools.LevelToolsConfig;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonDouble;
import org.bson.BsonString;
import java.util.UUID;
import com.linceros.leveltools.page.TokenPage;
import com.linceros.leveltools.page.StatsPage;

public class LevelToolsCommand extends CommandBase {

    public LevelToolsCommand(LevelToolsHudService hudService) {
        super("leveltools", "Level Tools Commands");
        this.addSubCommand(new HudSubCommand(hudService));
        this.addSubCommand(new TokensSubCommand());
        this.addSubCommand(new StatsSubCommand());
        this.addSubCommand(new SetLevelSubCommand());
        this.addSubCommand(new SetXpSubCommand());
        this.addSubCommand(new PrestigeSubCommand());
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@NonNullDecl CommandContext context) {
        context.sendMessage(Message.raw("=== LevelTools Commands ===").color(Color.YELLOW));
        context.sendMessage(Message.raw("/leveltools hud - Toggle the LevelTools HUD").color(Color.WHITE));
        context.sendMessage(Message.raw("/leveltools hud position <x> <y> - Move HUD to screen position").color(Color.WHITE));
        context.sendMessage(Message.raw("/leveltools hud reset - Reset HUD to default position").color(Color.WHITE));
        context.sendMessage(Message.raw("/leveltools tokens - Open Weapon Mastery menu").color(Color.WHITE));
        context.sendMessage(Message.raw("/leveltools stats - View item statistics").color(Color.WHITE));
        context.sendMessage(Message.raw("/leveltools prestige - Convert levels into ability tokens").color(Color.WHITE));
        context.sendMessage(Message.raw("/leveltools setlevel <level> - Force set item level (OP only)").color(Color.WHITE));
        context.sendMessage(Message.raw("/leveltools setxp <0-100> - Set XP progress % of held item (OP only)").color(Color.WHITE));
    }

    private static class HudSubCommand extends CommandBase {
        private final LevelToolsHudService hudService;

        public HudSubCommand(LevelToolsHudService hudService) {
            super("hud", "Toggle the LevelTools HUD");
            this.hudService = hudService;
            this.addSubCommand(new HudPositionSubCommand(hudService));
            this.addSubCommand(new HudResetSubCommand(hudService));
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@NonNullDecl CommandContext context) {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("Only players can toggle HUD."));
                return;
            }

            Player player;
            try {
                player = context.senderAs(Player.class);
            } catch (SenderTypeException e) {
                return;
            }

            if (player == null)
                return;

            PlayerRef ref = Universe.get().getPlayer(player.getUuid());

            if (hudService != null) {
                boolean visible = hudService.toggleHud(player, ref);
                if (visible) {
                    context.sendMessage(Message.raw("LevelTools HUD: Enabled").color(Color.GREEN));
                } else {
                    context.sendMessage(Message.raw("LevelTools HUD: Disabled").color(Color.RED));
                }
            } else {
                context.sendMessage(Message.raw("HUD Service not available."));
            }
        }
    }

    private static class HudPositionSubCommand extends CommandBase {
        private final LevelToolsHudService hudService;

        public HudPositionSubCommand(LevelToolsHudService hudService) {
            super("position", "Move the HUD to a custom screen position");
            this.hudService = hudService;
            this.setAllowsExtraArguments(true);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@NonNullDecl CommandContext context) {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("Only players can use this command."));
                return;
            }

            Player player;
            try {
                player = context.senderAs(Player.class);
            } catch (SenderTypeException e) {
                return;
            }

            if (player == null) return;

            String input = context.getInputString();
            String[] tokens = input.trim().split("\\s+");

            // /leveltools hud position <x> <y>
            if (tokens.length < 5) {
                context.sendMessage(Message.raw("Usage: /leveltools hud position <x> <y>").color(Color.RED));
                context.sendMessage(Message.raw("  x = pixels from left edge of screen").color(Color.GRAY));
                context.sendMessage(Message.raw("  y = pixels from bottom edge of screen").color(Color.GRAY));
                return;
            }

            int x, y;
            try {
                x = Integer.parseInt(tokens[3]);
                y = Integer.parseInt(tokens[4]);
            } catch (NumberFormatException e) {
                context.sendMessage(Message.raw("Invalid position — x and y must be integers.").color(Color.RED));
                return;
            }

            if (x < 0 || y < 0 || x > 3840 || y > 2160) {
                context.sendMessage(Message.raw("Position out of range (0–3840 for x, 0–2160 for y).").color(Color.RED));
                return;
            }

            hudService.setHudPosition(player.getUuid(), x, y);
            context.sendMessage(Message.raw("HUD moved to (" + x + ", " + y + ").").color(Color.GREEN));
        }
    }

    private static class HudResetSubCommand extends CommandBase {
        private final LevelToolsHudService hudService;

        public HudResetSubCommand(LevelToolsHudService hudService) {
            super("reset", "Reset HUD to default position");
            this.hudService = hudService;
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@NonNullDecl CommandContext context) {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("Only players can use this command."));
                return;
            }

            Player player;
            try {
                player = context.senderAs(Player.class);
            } catch (SenderTypeException e) {
                return;
            }

            if (player == null) return;

            hudService.resetHudPosition(player.getUuid());
            context.sendMessage(Message.raw("HUD position reset to default.").color(Color.GREEN));
        }
    }

    private static class TokensSubCommand extends CommandBase {
        public TokensSubCommand() {
            super("tokens", "Open Weapon Mastery");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@NonNullDecl CommandContext context) {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("Only players can use this command."));
                return;
            }

            try {
                Player player = context.senderAs(Player.class);
                if (player == null)
                    return;

                World world = player.getWorld();

                PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
                var ref = player.getReference();

                if (playerRef != null && ref != null && ref.isValid()) {
                    // Must run on world thread to interact with Store/Component system safely
                    world.execute(() -> {
                        player.getPageManager().openCustomPage(ref, ref.getStore(),
                                new com.linceros.leveltools.page.TokenPage(playerRef));
                    });
                } else {
                    context.sendMessage(Message.raw("Error: Could not resolve player reference.").color(Color.RED));
                }

            } catch (SenderTypeException e) {
                context.sendMessage(Message.raw("Error executing command."));
            }
        }
    }

    private static class StatsSubCommand extends CommandBase {
        public StatsSubCommand() {
            super("stats", "View item statistics");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@NonNullDecl CommandContext context) {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("Only players can use this command."));
                return;
            }

            try {
                Player player = context.senderAs(Player.class);
                if (player == null) return;

                World world = player.getWorld();
                PlayerRef playerRef = Universe.get().getPlayer(player.getUuid());
                var ref = player.getReference();

                if (playerRef != null && ref != null && ref.isValid()) {
                    world.execute(() -> {
                        player.getPageManager().openCustomPage(ref, ref.getStore(),
                                new StatsPage(playerRef));
                    });
                } else {
                    context.sendMessage(Message.raw("Error: Could not resolve player reference.").color(Color.RED));
                }

            } catch (SenderTypeException e) {
                context.sendMessage(Message.raw("Error executing command."));
            }
        }
    }

    private static class SetLevelSubCommand extends CommandBase {
        public SetLevelSubCommand() {
            super("setlevel", "Force set item level");
            this.setAllowsExtraArguments(true);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@NonNullDecl CommandContext context) {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("Only players can use this command."));
                return;
            }

            try {
                Player player = context.senderAs(Player.class);
                if (player == null)
                    return;

                // OP-only: requires leveltools.admin permission
                if (!player.hasPermission("leveltools.admin")) {
                    context.sendMessage(Message.raw("This command requires OP.").color(Color.RED));
                    return;
                }

                // Manual parsing of arguments
                String input = context.getInputString();
                String[] tokens = input != null ? input.trim().split("\\s+") : new String[0];

                // tokens[0] is 'leveltools', tokens[1] is 'setlevel', tokens[2] is the level
                if (tokens.length < 3) {
                    context.sendMessage(Message.raw("Usage: /leveltools setlevel <level>").color(Color.RED));
                    return;
                }

                int targetLevel;
                try {
                    targetLevel = Integer.parseInt(tokens[2]);
                } catch (NumberFormatException e) {
                    context.sendMessage(Message.raw("Invalid level number.").color(Color.RED));
                    return;
                }

                if (targetLevel < 0) {
                    context.sendMessage(Message.raw("Level cannot be negative.").color(Color.RED));
                    return;
                }

                Inventory inventory = player.getInventory();
                if (inventory == null)
                    return;

                byte activeSlot = inventory.getActiveHotbarSlot();
                if (activeSlot < 0)
                    return;

                ItemStack itemStack = inventory.getHotbar().getItemStack(activeSlot);
                if (ItemStack.isEmpty(itemStack)) {
                    context.sendMessage(Message.raw("You must hold a tool or weapon!").color(Color.RED));
                    return;
                }

                com.hypixel.hytale.server.core.asset.type.item.config.Item itemConfig = itemStack.getItem();
                if (itemConfig == null)
                    return;

                boolean isTool = itemConfig.getTool() != null;
                boolean isWeapon = itemConfig.getWeapon() != null;
                String itemId = itemConfig.getId();

                if (!isTool && !isWeapon) {
                    if (itemId != null && itemId.startsWith("Tool_"))
                        isTool = true;
                    else if (itemId != null && itemId.startsWith("Weapon_"))
                        isWeapon = true;
                }

                if (!isTool && !isWeapon) {
                    context.sendMessage(Message.raw("This item is not a valid tool or weapon!").color(Color.RED));
                    return;
                }

                // Logic to update the item
                LevelToolsConfig config = LevelToolsPlugin.getConfig();
                BsonDocument metadata = itemStack.getMetadata();
                if (metadata == null)
                    metadata = new BsonDocument();

                String originalName = LevelToolsSystem.getOriginalName(metadata, itemId);
                double baseMaxDurability = LevelToolsSystem.getBaseMaxDurability(metadata,
                        itemConfig.getMaxDurability());

                // Calculate scales
                double bonusMultiplier = 1.0 + (targetLevel * config.getDurabilityBonusPercent() / 100.0);
                double newMaxDurability = baseMaxDurability * bonusMultiplier;

                // Update metadata
                BsonDocument newMetadata = metadata.clone();
                newMetadata.put(LevelToolsSystem.LEVEL_KEY, new BsonInt32(targetLevel));
                newMetadata.put(LevelToolsSystem.DURABILITY_EXCESS_KEY, new BsonDouble(0));
                newMetadata.put(LevelToolsSystem.SNAPSHOT_DURABILITY_KEY, new BsonDouble(newMaxDurability));

                if (!newMetadata.containsKey(LevelToolsSystem.ORIGINAL_NAME_KEY)) {
                    newMetadata.put(LevelToolsSystem.ORIGINAL_NAME_KEY, new BsonString(originalName));
                }
                if (!newMetadata.containsKey(LevelToolsSystem.BASE_MAX_DURABILITY_KEY)) {
                    newMetadata.put(LevelToolsSystem.BASE_MAX_DURABILITY_KEY, new BsonDouble(baseMaxDurability));
                }

                // Create new item (Full repair)
                ItemStack leveledTool = new ItemStack(
                        itemStack.getItemId(),
                        itemStack.getQuantity(),
                        newMaxDurability,
                        newMaxDurability,
                        newMetadata);

                // Replace
                inventory.getHotbar().replaceItemStackInSlot(activeSlot, itemStack, leveledTool);

                context.sendMessage(Message.raw("Successfully set item level to " + targetLevel).color(Color.GREEN));

                // Refresh HUD
                LevelToolsPlugin.getHudService().forceRefresh(player.getUuid());

            } catch (SenderTypeException e) {
                context.sendMessage(Message.raw("Error executing command."));
            }
        }
    }

    private static class SetXpSubCommand extends CommandBase {
        public SetXpSubCommand() {
            super("setxp", "Set XP progress percentage of held item");
            this.setAllowsExtraArguments(true);
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@NonNullDecl CommandContext context) {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("Only players can use this command."));
                return;
            }

            try {
                Player player = context.senderAs(Player.class);
                if (player == null) return;

                if (!player.hasPermission("leveltools.admin")) {
                    context.sendMessage(Message.raw("This command requires OP.").color(Color.RED));
                    return;
                }

                String input = context.getInputString();
                String[] parts = input != null ? input.trim().split("\\s+") : new String[0];

                // /leveltools setxp <percent>
                if (parts.length < 3) {
                    context.sendMessage(Message.raw("Usage: /leveltools setxp <0-100>").color(Color.RED));
                    return;
                }

                int percent;
                try {
                    percent = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    context.sendMessage(Message.raw("Invalid percentage — must be an integer.").color(Color.RED));
                    return;
                }

                if (percent < 0 || percent > 100) {
                    context.sendMessage(Message.raw("Percentage must be between 0 and 100.").color(Color.RED));
                    return;
                }

                Inventory inventory = player.getInventory();
                if (inventory == null) return;

                byte activeSlot = inventory.getActiveHotbarSlot();
                if (activeSlot < 0) return;

                ItemStack itemStack = inventory.getHotbar().getItemStack(activeSlot);
                if (ItemStack.isEmpty(itemStack)) {
                    context.sendMessage(Message.raw("You must hold a tool or weapon!").color(Color.RED));
                    return;
                }

                com.hypixel.hytale.server.core.asset.type.item.config.Item itemConfig = itemStack.getItem();
                if (itemConfig == null) return;

                boolean isTool = itemConfig.getTool() != null;
                boolean isWeapon = itemConfig.getWeapon() != null;
                String itemId = itemConfig.getId();

                if (!isTool && !isWeapon) {
                    if (itemId != null && itemId.startsWith("Tool_")) isTool = true;
                    else if (itemId != null && itemId.startsWith("Weapon_")) isWeapon = true;
                }

                if (!isTool && !isWeapon) {
                    context.sendMessage(Message.raw("This item is not a valid tool or weapon!").color(Color.RED));
                    return;
                }

                LevelToolsConfig config = LevelToolsPlugin.getConfig();
                double lossToLevel = config.getDurabilityLossToLevel();

                // Apply the same multiplier the tick system uses, so the displayed
                // percentage in the HUD matches the value the player typed.
                double multiplier = isWeapon
                        ? config.getWeaponXPMultiplier()
                        : config.getToolXPMultiplier();

                // targetRawExcess * multiplier / lossToLevel = percent / 100
                double targetRawExcess = (percent / 100.0) * lossToLevel / multiplier;

                BsonDocument metadata = itemStack.getMetadata();
                BsonDocument newMetadata = metadata != null ? metadata.clone() : new BsonDocument();

                // Set snapshot = current durability so (snapshot - current) = 0.
                // All XP comes from the stored excess key.
                newMetadata.put(LevelToolsSystem.DURABILITY_EXCESS_KEY, new BsonDouble(targetRawExcess));
                newMetadata.put(LevelToolsSystem.SNAPSHOT_DURABILITY_KEY,
                        new BsonDouble(itemStack.getDurability()));

                ItemStack updated = new ItemStack(
                        itemStack.getItemId(),
                        itemStack.getQuantity(),
                        itemStack.getDurability(),
                        itemStack.getMaxDurability(),
                        newMetadata);

                inventory.getHotbar().replaceItemStackInSlot(activeSlot, itemStack, updated);
                LevelToolsPlugin.getHudService().forceRefresh(player.getUuid());

                context.sendMessage(Message.raw(
                        "XP set to " + percent + "% — HUD updated.").color(Color.GREEN));

            } catch (SenderTypeException e) {
                context.sendMessage(Message.raw("Error executing command."));
            }
        }
    }

    private static class PrestigeSubCommand extends CommandBase {
        public PrestigeSubCommand() {
            super("prestige", "Convert item levels into ability tokens");
        }

        @Override
        protected boolean canGeneratePermission() {
            return false;
        }

        @Override
        protected void executeSync(@NonNullDecl CommandContext context) {
            if (!context.isPlayer()) {
                context.sendMessage(Message.raw("Only players can use this command."));
                return;
            }

            try {
                Player player = context.senderAs(Player.class);
                if (player == null)
                    return;

                Inventory inventory = player.getInventory();
                if (inventory == null)
                    return;

                byte activeSlot = inventory.getActiveHotbarSlot();
                if (activeSlot < 0)
                    return;

                ItemStack itemStack = inventory.getHotbar().getItemStack(activeSlot);
                if (ItemStack.isEmpty(itemStack)) {
                    context.sendMessage(Message.raw("You must hold a tool or weapon!").color(Color.RED));
                    return;
                }

                com.hypixel.hytale.server.core.asset.type.item.config.Item itemConfig = itemStack.getItem();
                if (itemConfig == null)
                    return;

                boolean isTool = itemConfig.getTool() != null;
                boolean isWeapon = itemConfig.getWeapon() != null;
                String itemId = itemConfig.getId();

                if (!isTool && !isWeapon) {
                    if (itemId != null && itemId.startsWith("Tool_"))
                        isTool = true;
                    else if (itemId != null && itemId.startsWith("Weapon_"))
                        isWeapon = true;
                }

                if (!isTool && !isWeapon) {
                    context.sendMessage(Message.raw("This item is not a valid tool or weapon!").color(Color.RED));
                    return;
                }

                LevelToolsConfig config = LevelToolsPlugin.getConfig();
                int currentLevel = LevelToolsSystem.getToolLevel(itemStack);
                int tokensGained = currentLevel / config.getTokensPerLevel();

                if (tokensGained < 1) {
                    context.sendMessage(Message.raw(
                        "Not enough levels! Need at least level " + config.getTokensPerLevel() + " to prestige."
                    ).color(Color.RED));
                    return;
                }

                int newLevel = currentLevel % config.getTokensPerLevel();

                BsonDocument metadata = itemStack.getMetadata();
                if (metadata == null)
                    metadata = new BsonDocument();
                BsonDocument newMetadata = metadata.clone();

                // Recalculate durability for the new (lower) level
                double baseMaxDurability = LevelToolsSystem.getBaseMaxDurability(newMetadata, itemConfig.getMaxDurability());
                double bonusMultiplier = 1.0 + (newLevel * config.getDurabilityBonusPercent() / 100.0);
                double newMaxDurability = baseMaxDurability * bonusMultiplier;

                newMetadata.put(LevelToolsSystem.LEVEL_KEY, new BsonInt32(newLevel));
                newMetadata.put(LevelToolsSystem.DURABILITY_EXCESS_KEY, new BsonDouble(0));
                newMetadata.put(LevelToolsSystem.SNAPSHOT_DURABILITY_KEY, new BsonDouble(newMaxDurability));

                // Accumulate prestige tokens on the item
                int existing = LevelToolsSystem.getPrestigeTokens(itemStack);
                newMetadata.put(LevelToolsSystem.PRESTIGE_TOKENS_KEY, new BsonInt32(existing + tokensGained));

                ItemStack prestigedItem = new ItemStack(
                        itemStack.getItemId(),
                        itemStack.getQuantity(),
                        newMaxDurability,
                        newMaxDurability,
                        newMetadata);

                inventory.getHotbar().replaceItemStackInSlot(activeSlot, itemStack, prestigedItem);

                String tokenWord = tokensGained == 1 ? "token" : "tokens";
                context.sendMessage(Message.raw(
                    "Prestige! Lv " + currentLevel + " -> Lv " + newLevel +
                    " (+" + tokensGained + " " + tokenWord + ")"
                ).color(Color.GREEN));

                LevelToolsPlugin.getHudService().forceRefresh(player.getUuid());

            } catch (SenderTypeException e) {
                context.sendMessage(Message.raw("Error executing command."));
            }
        }
    }
}
