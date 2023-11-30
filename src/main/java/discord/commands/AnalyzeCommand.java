/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord.commands;

import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import services.AgentRequest;
import services.Player;
import discord.message.AnalyzeBuilder;
import services.GameService;
import services.AgentService;
import othello.Move;
import utils.Bot;

import java.util.List;
import java.util.logging.Logger;

import static utils.Bot.MAX_BOT_LEVEL;
import static utils.Logger.LOGGER;

public class AnalyzeCommand extends Command
{
    private final GameService gameService;
    private final AgentService agentService;

    public AnalyzeCommand(GameService gameService, AgentService agentService) {
        super("analyze", "Runs an analysis of the board",
            new OptionData(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false));
        this.gameService = gameService;
        this.agentService = agentService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        var levelOpt = ctx.getOptionalParam("level");
        var level = levelOpt != null ? levelOpt.getAsLong() : 3;

        // check if level is within range
        if (!Bot.isValidLevel(level)) {
            ctx.reply("Invalid level, should be between 1 and " + MAX_BOT_LEVEL);
            return;
        }

        var player = new Player(ctx.getAuthor());

        // fetch game to analyze
        var game = gameService.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        // send starting message, then add queue an agent request, send back the results in a message when it's done
        var depth = Bot.getDepthFromId(level);
        ctx.deferReply();
        ctx.sendMessage("Analyzing... Wait a second...", m -> {
            LOGGER.info("Starting board state analysis");

            var r = new AgentRequest<>(game, depth, (List<Move> rankedMoves) -> {
                var embed = new AnalyzeBuilder()
                    .setRankedMoves(rankedMoves)
                    .build();

                m.editMessage("<@" + player + "> ").queue();
                m.editMessageEmbeds(embed).queue();

                LOGGER.info("Finished board state analysis");
            });
            agentService.findRankedMoves(r);
        });
    }
}
