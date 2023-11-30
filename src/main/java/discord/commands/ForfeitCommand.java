/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import services.GameService;
import services.StatsService;
import services.Player;
import discord.message.GameOverSender;
import discord.renderers.OthelloBoardRenderer;

import java.util.logging.Logger;

import static utils.Logger.LOGGER;

public class ForfeitCommand extends Command
{
    private final GameService gameService;
    private final StatsService statsService;
    private final OthelloBoardRenderer boardRenderer;

    public ForfeitCommand(
        GameService gameService,
        StatsService statsService,
        OthelloBoardRenderer boardRenderer
    ) {
        super("forfeit", "Forfeits the user's current game");
        this.gameService = gameService;
        this.statsService = statsService;
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

        var image = boardRenderer.drawBoard(game.getBoard());

        // remove game from storage
        gameService.deleteGame(game);
        // update elo from game result
        var result = game.getForfeitResult();
        statsService.updateStats(result);

        var sender = new GameOverSender()
            .setGame(result)
            .addForfeitMessage(result.getWinner())
            .setTag(result)
            .setImage(image);
        sender.sendReply(ctx);

        LOGGER.info(player + " has forfeited");
    }
}
