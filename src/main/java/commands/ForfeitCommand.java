/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import lombok.AllArgsConstructor;
import domain.BoardRenderer;
import services.GameService;
import services.StatsService;

import static utils.Log.LOGGER;

@AllArgsConstructor
public class ForfeitCommand extends CommandHandler {

    private final GameService gameService;
    private final StatsService statsService;

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

        LOGGER.info("Player: " + player + " has forfeited");
    }
}
