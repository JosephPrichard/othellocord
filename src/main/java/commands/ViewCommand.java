/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import messaging.senders.GameViewSender;
import othello.BoardRenderer;
import services.game.GameStorage;
import services.game.Player;

import static utils.Logger.LOGGER;

public class ViewCommand extends Command
{
    private final GameStorage gameStorage;
    private final BoardRenderer boardRenderer;

    public ViewCommand(GameStorage gameStorage, BoardRenderer boardRenderer) {
        super("view", "Displays the game state including all the moves that can be made this turn");
        this.gameStorage = gameStorage;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        var player = new Player(ctx.getAuthor());

        var game = gameStorage.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        var board = game.getBoard();
        var potentialMoves = board.findPotentialMoves();

        var image = boardRenderer.drawBoard(board, potentialMoves);
        var sender = new GameViewSender()
            .setGame(game)
            .setImage(image);
        sender.sendReply(ctx);

        LOGGER.info("Player " + player + " view moves in game");
    }
}
