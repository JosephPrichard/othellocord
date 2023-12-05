/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import commands.messaging.GameOverSender;
import othello.BoardRenderer;
import services.game.GameStorage;
import services.stats.StatsWriter;

import static utils.Logger.LOGGER;

public class ForfeitCommand extends Command {

    private final GameStorage gameStorage;
    private final StatsWriter statsWriter;

    public ForfeitCommand(
        GameStorage gameStorage,
        StatsWriter statsWriter
    ) {
        super("forfeit");
        this.gameStorage = gameStorage;
        this.statsWriter = statsWriter;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var player = ctx.getPlayer();

        var game = gameStorage.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        var image = BoardRenderer.drawBoard(game.board());
        gameStorage.deleteGame(game);
        var result = game.createForfeitResult(player);

        var statsResult = statsWriter.writeStats(result);

        var sender = new GameOverSender()
            .setResults(result, statsResult)
            .addForfeitMessage(result.winner())
            .setTag(result)
            .setImage(image);
        ctx.replyWithSender(sender);

        LOGGER.info(player + " has forfeited");
    }
}
