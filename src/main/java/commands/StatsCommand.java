/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.builders.StatsEmbedBuilder;
import services.player.Player;
import services.stats.StatsReader;

import java.util.concurrent.ExecutorService;

import static utils.Logger.LOGGER;

public class StatsCommand extends Command {

    private final StatsReader statsReader;
    private final ExecutorService ioTaskExecutor;

    public StatsCommand(StatsReader statsReader, ExecutorService ioTaskExecutor) {
        super("stats");
        this.statsReader = statsReader;
        this.ioTaskExecutor = ioTaskExecutor;
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var userOpt = ctx.getParam("player");
        var user = userOpt != null ? userOpt.getAsUser() : ctx.getUser();

        var player = new Player(user);

        ioTaskExecutor.submit(() -> {
            // getting stats involves reading from an external service
            var stats = statsReader.readStats(player);

            var embed = new StatsEmbedBuilder()
                .setStats(stats)
                .setAuthor(user)
                .build();
            ctx.replyEmbeds(embed);

            LOGGER.info("Retrieved stats for " + stats.getPlayer());
        });
    }
}
