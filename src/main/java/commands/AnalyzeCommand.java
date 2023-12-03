/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.AnalyzeBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import othello.Move;
import services.game.EvalRequest;
import services.game.GameEvaluator;
import services.game.GameStorage;
import services.player.Player;
import utils.Bot;

import java.util.List;

import static utils.Bot.MAX_BOT_LEVEL;
import static utils.Logger.LOGGER;

public class AnalyzeCommand extends Command {
    private final GameStorage gameStorage;
    private final GameEvaluator gameEvaluator;

    public AnalyzeCommand(GameStorage gameStorage, GameEvaluator gameEvaluator) {
        super("analyze", "Runs an analysis of the board",
            new OptionData(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false));
        this.gameStorage = gameStorage;
        this.gameEvaluator = gameEvaluator;
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
        var game = gameStorage.getGame(player);
        if (game == null) {
            ctx.reply("You're not currently in a game.");
            return;
        }

        // send starting message, then add queue an agent request, send back the results in a message when it's done
        var depth = Bot.getDepthFromId(level);
        ctx.deferReply();
        ctx.sendMessage("Analyzing... Wait a second...", m -> {
            LOGGER.info("Starting board state analysis");

            var r = new EvalRequest<>(game, depth, (List<Move> rankedMoves) -> {
                var embed = new AnalyzeBuilder()
                    .setRankedMoves(rankedMoves)
                    .build();

                m.editMessage("<@" + player + "> ").queue();
                m.editMessageEmbeds(embed).queue();

                LOGGER.info("Finished board state analysis");
            });
            gameEvaluator.findRankedMoves(r);
        });
    }
}
