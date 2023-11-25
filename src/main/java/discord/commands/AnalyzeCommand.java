/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import services.AgentRequest;
import services.Game;
import services.Player;
import discord.message.AnalyzeBuilder;
import services.GameService;
import services.AgentService;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import othello.Move;
import utils.Bot;
import utils.Number;

import java.util.List;
import java.util.logging.Logger;

import static utils.Bot.MAX_BOT_LEVEL;

public class AnalyzeCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.analyze");
    private final GameService gameService;
    private final AgentService agentService;

    public AnalyzeCommand(GameService gameService, AgentService agentService) {
        super("analyze", "Runs an analysis of the board until a given level between 1 and " + MAX_BOT_LEVEL, 0, "level");
        this.gameService = gameService;
        this.agentService = agentService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        var event = ctx.getEvent();
        var channel = event.getChannel();

        // retrieve depth parameter and perform type validation
        Integer level = 3;
        var levelStr = ctx.getParam("level");
        if (levelStr != null) {
            level = Number.parseIntOrNull(levelStr);
            if (level == null) {
                channel.sendMessage("Level must be a number.").queue();
                return;
            }
        }

        // check if depth is within range
        if (!Bot.isValidLevel(level)) {
            channel.sendMessage("Invalid level. Type !help analyze for valid levels.").queue();
            return;
        }

        var player = new Player(event.getAuthor());

        // fetch game to analyze
        var game = gameService.getGame(player);
        if (game == null) {
            channel.sendMessage("You're not currently in a game.").queue();
            return;
        }

        // send starting message, then add queue an ai request, send back the results in a message when it's done
        var depth = Bot.getDepthFromId(level);
        channel.sendMessage("Analyzing... Wait a second...").queue(m -> {
            logger.info("Starting board state analysis");

            var r = new AgentRequest<List<Move>>(game, depth, (List<Move> rankedMoves) -> {
                var embed = new AnalyzeBuilder()
                    .setRankedMoves(rankedMoves)
                    .build();

                m.editMessage("<@" + player + "> ").queue();
                m.editMessageEmbeds(embed).queue();

                logger.info("Finished board state analysis");
            });
            agentService.findRankedMoves(r);
        });
    }
}
