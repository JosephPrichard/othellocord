/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.senders.GameOverSender;
import othello.BoardRenderer;
import services.game.GameStorage;
import services.player.Player;
import services.stats.StatsService;

import static utils.Logger.LOGGER;

public class ForfeitCommand extends Command {
    private final GameStorage gameStorage;
    private final StatsService statsService;

    public ForfeitCommand(
        GameStorage gameStorage,
        StatsService statsService
    ) {
        super("forfeit", "Forfeits the user's current game");
        this.gameStorage = gameStorage;
        this.statsService = statsService;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        var player = new Player(ctx.getAuthor());

        var game = gameStorage.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        var image = BoardRenderer.drawBoard(game.board());

        // remove game from storage
        gameStorage.deleteGame(game);
        // update elo from game result
        var result = game.getForfeitResult();
        statsService.updateStats(result);

        var sender = new GameOverSender()
            .setGame(result)
            .addForfeitMessage(result.getWinner())
            .setTag(result)
            .setImage(image);
        ctx.replyWithSender(sender);

        LOGGER.info(player + " has forfeited");
    }
}
