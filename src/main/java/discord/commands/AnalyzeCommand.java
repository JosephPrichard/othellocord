/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import services.agent.AgentRequest;
import services.game.Game;
import services.player.Player;
import discord.message.AnalyzeBuilder;
import services.game.GameService;
import services.agent.AgentService;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import othello.Move;
import utils.Bot;
import utils.Number;

import java.util.List;
import java.util.logging.Logger;

public class AnalyzeCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.analyze");
    private final GameService gameService;
    private final AgentService agentService;

    public AnalyzeCommand(GameService gameService, AgentService agentService) {
        super("analyze", "Runs an analysis of the board until a given depth between 5 and 15", 0, "depth");
        this.gameService = gameService;
        this.agentService = agentService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        // retrieve depth parameter and perform type validation
        Integer depth = 5;
        String depthStr = ctx.getParam("depth");
        if (depthStr != null) {
            depth = Number.parseIntOrNull(depthStr);
            if (depth == null) {
                channel.sendMessage("Depth must be a number.").queue();
                return;
            }
        }

        // check if depth is within range
        if (!Bot.isValidLevel(depth)) {
            channel.sendMessage("Invalid depth. Type !help analyze for valid depths.").queue();
            return;
        }

        Player player = new Player(event.getAuthor());

        // fetch game to analyze
        Game game = gameService.getGame(player);
        if (game == null) {
            channel.sendMessage("You're not currently in a game.").queue();
            return;
        }

        // send starting message, then add queue an ai request, send back the results in a message when its done
        int d = depth;
        channel.sendMessage("Analyzing... Wait a second...").queue(m -> {
            logger.info("Starting board state analysis");

            agentService.findRankedMoves(
                new AgentRequest<>(game, d, (List<Move> rankedMoves) -> {
                    MessageEmbed embed = new AnalyzeBuilder()
                        .setRankedMoves(rankedMoves)
                        .build();

                    m.editMessage("<@" + player + "> ").queue();
                    m.editMessageEmbeds(embed).queue();

                    logger.info("Finished board state analysis");
                })
            );
        });
    }
}
