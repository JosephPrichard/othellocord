/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import commands.messaging.MessageSender;
import othello.BoardRenderer;
import services.game.GameStorage;

import static utils.Logger.LOGGER;

public class ViewCommand extends Command {

    private final GameStorage gameStorage;

    public ViewCommand(GameStorage gameStorage) {
        super("view");
        this.gameStorage = gameStorage;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var player = ctx.getPlayer();

        var game = gameStorage.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        var board = game.board();
        var potentialMoves = board.findPotentialMoves();

        var image = BoardRenderer.drawBoard(board, potentialMoves);
        var sender = MessageSender.createGameViewSender(game, image);
        ctx.replyWithSender(sender);

        LOGGER.info("Player " + player + " view moves in game");
    }
}
