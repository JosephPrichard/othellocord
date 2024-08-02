/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import lombok.AllArgsConstructor;
import domain.BoardRenderer;
import services.GameService;

import static utils.Log.LOGGER;

@AllArgsConstructor
public class ViewCommand extends CommandHandler {

    private final GameService gameService;

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
