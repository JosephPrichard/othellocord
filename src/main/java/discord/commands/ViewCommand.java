/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import services.GameService;
import services.Player;
import discord.message.GameViewSender;
import discord.renderers.OthelloBoardRenderer;

import java.util.logging.Logger;

import static utils.Logger.LOGGER;

public class ViewCommand extends Command
{
    private final GameService gameService;
    private final OthelloBoardRenderer boardRenderer;

    public ViewCommand(GameService gameService, OthelloBoardRenderer boardRenderer) {
        super("view", "Displays the game state including all the moves that can be made this turn");
        this.gameService = gameService;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        var player = new Player(ctx.getAuthor());

        var game = gameService.getGame(player);
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
