/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.LeaderboardEmbedBuilder;
import services.stats.StatsReader;

import java.util.concurrent.ExecutorService;

import static utils.Logger.LOGGER;

public class LeaderBoardCommand extends Command {

    private final StatsReader statsReader;
    private final ExecutorService ioTaskExecutor;

    public LeaderBoardCommand(StatsReader statsReader, ExecutorService ioTaskExecutor) {
        super("leaderboard");
        this.statsReader = statsReader;
        this.ioTaskExecutor = ioTaskExecutor;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        ioTaskExecutor.submit(() -> {
            // getting stats involves reading from an external service
            var statsList = statsReader.readTopStats();
            var embed = new LeaderboardEmbedBuilder().setStats(statsList).build();
            ctx.replyEmbeds(embed);
            LOGGER.info("Fetched leaderboard");
        });
    }
}
