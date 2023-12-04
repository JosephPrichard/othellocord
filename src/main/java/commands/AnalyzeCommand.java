/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.AnalyzeEmbedBuilder;
import othello.Move;
import services.agent.AgentDispatcher;
import services.agent.AgentEvent;
import services.game.GameStorage;
import services.player.Player;

import java.util.List;

import static services.player.Player.Bot.MAX_BOT_LEVEL;
import static utils.Logger.LOGGER;

public class AnalyzeCommand extends Command {

    private final GameStorage gameStorage;
    private final AgentDispatcher agentDispatcher;

    public AnalyzeCommand(GameStorage gameStorage, AgentDispatcher agentDispatcher) {
        super("analyze");
        this.gameStorage = gameStorage;
        this.agentDispatcher = agentDispatcher;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var level = ctx.getLongParam("level");
        if (level == null) {
            level = 3L;
        }

        // check if level is within range
        if (!Player.Bot.isValidLevel(level)) {
            ctx.reply("Invalid level, should be between 1 and " + MAX_BOT_LEVEL);
            return;
        }

        var player = ctx.getPlayer();

        var game = gameStorage.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        // send starting message, then add queue an agent request, send back the results in a message when it's done
        var depth = Player.Bot.getDepthFromId(level);
        ctx.reply("Analyzing... Wait a second...", hook -> {
            LOGGER.info("Starting board state analysis");

            var event = new AgentEvent<>(game, depth, (List<Move> rankedMoves) -> {
                var embed = new AnalyzeEmbedBuilder()
                    .setRankedMoves(rankedMoves)
                    .build();

                hook.editOriginal("<@" + player + "> ").queue();
                hook.editOriginalEmbeds(embed).queue();

                LOGGER.info("Finished board state analysis");
            });
            agentDispatcher.dispatchFindMovesEvent(event);
        });
    }
}
