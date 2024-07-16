/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import commands.views.GameResultView;
import othello.BoardRenderer;
import services.game.IGameService;
import services.stats.IStatsService;

import static utils.Logger.LOGGER;

public class ForfeitCommand extends Command {

    private final IGameService gameService;
    private final IStatsService statsService;

    public ForfeitCommand(IGameService gameService, IStatsService statsService) {
        this.gameService = gameService;
        this.statsService = statsService;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var player = ctx.getPlayer();

        var game = gameService.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        gameService.deleteGame(game);
        var result = game.createForfeitResult(player);

        var statsResult = statsService.writeStats(result);

        var image = BoardRenderer.drawBoard(game.board());
        var view = GameResultView.createForfeitView(result, statsResult, image);
        ctx.replyView(view);

        LOGGER.info(player + " has forfeited");
    }
}
