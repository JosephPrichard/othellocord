/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import commands.views.GameStateView;
import othello.BoardRenderer;
import services.game.IGameService;

import static utils.Logger.LOGGER;

public class ViewCommand extends Command {

    private final IGameService gameService;

    public ViewCommand(IGameService gameService) {
        this.gameService = gameService;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var player = ctx.getPlayer();

        var game = gameService.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        var board = game.board();
        var potentialMoves = board.findPotentialMoves();

        var image = BoardRenderer.drawBoard(board, potentialMoves);
        var view = GameStateView.createGameView(game, image);
        ctx.replyView(view);

        LOGGER.info("Player " + player + " view moves in game");
    }
}
