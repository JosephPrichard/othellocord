/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.StatsEmbedBuilder;
import services.player.Player;
import services.stats.StatsReader;

import static utils.Logger.LOGGER;

public class StatsCommand extends Command {

    private final StatsReader statsReader;

    public StatsCommand(StatsReader statsReader) {
        super("stats");
        this.statsReader = statsReader;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var userOpt = ctx.getParam("player");
        var user = userOpt != null ? userOpt.getAsUser() : ctx.getUser();

        var player = new Player(user);
        var stats = statsReader.getStats(player);

        var embed = new StatsEmbedBuilder()
            .setStats(stats)
            .setAuthor(user)
            .build();
        ctx.replyEmbeds(embed);

        LOGGER.info("Retrieved stats for " + stats.getPlayer());
    }
}
