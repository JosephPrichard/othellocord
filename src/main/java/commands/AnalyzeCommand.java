/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import commands.messaging.MessageSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import othello.BoardRenderer;
import othello.Move;
import services.agent.AgentDispatcher;
import services.agent.AgentEvent;
import services.game.GameStorage;
import services.player.Player;

import java.awt.*;
import java.util.List;

import static services.player.Player.Bot.MAX_BOT_LEVEL;
import static utils.Logger.LOGGER;
import static utils.StringUtils.rightPad;

public class AnalyzeCommand extends Command {

    private final GameStorage gameStorage;
    private final AgentDispatcher agentDispatcher;

    public AnalyzeCommand(GameStorage gameStorage, AgentDispatcher agentDispatcher) {
        super("analyze");
        this.gameStorage = gameStorage;
        this.agentDispatcher = agentDispatcher;
    }

    public MessageEmbed buildAnalyzeEmbed(List<Move> rankedMoves) {
        var embed = new EmbedBuilder();

        var desc = new StringBuilder();
        desc.append("```");
        var count = 1;
        for (var move : rankedMoves) {
            desc.append(rightPad(count + ")", 5))
                .append(rightPad(move.tile().toString(), 5))
                .append(move.heuristic()).append(" ")
                .append("\n");
            count++;
        }
        desc.append("```");

        embed.setTitle("Move Analysis")
            .setColor(Color.GREEN)
            .setDescription(desc)
            .setFooter("Positive heuristics are better for black, and negative heuristics are better for white");
        return embed.build();
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
        final var finalLevel = level;
        ctx.reply("Analyzing... Wait a second...", hook -> {
            LOGGER.info("Starting board state analysis");

            AgentEvent<List<Move>> event = new AgentEvent<>(game, depth, (rankedMoves) -> {
                var image = BoardRenderer.drawBoardAnalysis(game.board(), rankedMoves);
                var sender = MessageSender.createGameAnalyzeSender(game, image, finalLevel);

                sender.setTag(player);
                sender.editMessageUsingHook(hook);

                LOGGER.info("Finished board state analysis");
            });
            agentDispatcher.dispatchFindMovesEvent(event);
        });
    }
}
