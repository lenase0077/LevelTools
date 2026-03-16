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

public class LevelToolsCommand extends CommandBase {

    public LevelToolsCommand(LevelToolsHudService hudService) {
        super("leveltools", "Level Tools Commands");
        this.addSubCommand(new HudSubCommand(hudService));
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }

    @Override
    protected void executeSync(@NonNullDecl CommandContext context) {
        context.sendMessage(Message.raw("=== LevelTools Commands ===").color(Color.YELLOW));
        context.sendMessage(Message.raw("/leveltools hud - Toggle the LevelTools HUD").color(Color.WHITE));
    }

    private static class HudSubCommand extends CommandBase {
        private final LevelToolsHudService hudService;

        public HudSubCommand(LevelToolsHudService hudService) {
            super("hud", "Toggle the LevelTools HUD");
            this.hudService = hudService;
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
}
