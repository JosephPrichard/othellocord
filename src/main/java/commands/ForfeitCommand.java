/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import messaging.senders.GameOverSender;
import othello.BoardRenderer;
import services.game.GameStorage;
import services.game.Player;
import services.stats.StatsService;

import static utils.Logger.LOGGER;

public class ForfeitCommand extends Command {
    private final GameStorage gameStorage;
    private final StatsService statsService;
    private final BoardRenderer boardRenderer;

    public ForfeitCommand(
        GameStorage gameStorage,
        StatsService statsService,
        BoardRenderer boardRenderer
    ) {
        super("forfeit", "Forfeits the user's current game");
        this.gameStorage = gameStorage;
        this.statsService = statsService;
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

        var image = boardRenderer.drawBoard(game.board());

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
        sender.sendReply(ctx);

        LOGGER.info(player + " has forfeited");
    }
}
