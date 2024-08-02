/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import lombok.AllArgsConstructor;
import domain.BoardRenderer;
import services.AgentDispatcher;
import services.GameService;
import models.Player;

import java.util.concurrent.ExecutionException;

import static models.Player.Bot.MAX_BOT_LEVEL;
import static utils.Log.LOGGER;

@AllArgsConstructor
public class AnalyzeCommand extends CommandHandler {

    private final GameService gameService;
    private final AgentDispatcher agentDispatcher;

    @Override
    public void onCommand(CommandContext ctx) {
        var level = ctx.getLongParam("level");
        if (level == null) {
            level = 3L;
        }

        // check if level is within range
        if (Player.Bot.isInvalidLevel(level)) {
            ctx.reply("Invalid level, should be between 1 and " + MAX_BOT_LEVEL);
            return;
        }

        var player = ctx.getPlayer();

        var game = gameService.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        // send starting message, then add queue an agent request, send back the results in a message when it's done
        var depth = Player.Bot.getDepthFromId(level);
        final var finalLevel = level;
        ctx.reply("Analyzing... Wait a second...", hook -> {
            LOGGER.info("Starting board state analysis");

            try {
                var future = agentDispatcher.findMoves(game.board(), depth);
                var rankedMoves = future.get();

                var image = BoardRenderer.drawBoardAnalysis(game.board(), rankedMoves);
                var view = GameStateView.createAnalysisView(game, image, finalLevel, player);

                view.editUsingHook(hook);
                LOGGER.info("Finished board state analysis");
            } catch (ExecutionException | InterruptedException e) {
                LOGGER.warning("Error occurred while responding to an analyze command " + e);
            }
        });
    }
}
