/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.LeaderboardBuilder;
import services.stats.StatsService;

import static utils.Logger.LOGGER;

public class LeaderBoardCommand extends Command {

    private final StatsService statsService;

    public LeaderBoardCommand(StatsService statsService) {
        super("leaderboard", "Retrieves the highest rated players by ELO");
        this.statsService = statsService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        var statsList = statsService.getTopStats();
        var embed = new LeaderboardBuilder().setStats(statsList).build();
        ctx.replyEmbeds(embed);
        LOGGER.info("Fetched leaderboard");
    }
}
