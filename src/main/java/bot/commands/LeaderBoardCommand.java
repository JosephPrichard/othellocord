package bot.commands;

import bot.commands.abstracts.CommandContext;
import bot.commands.abstracts.CommandHandler;
import bot.dtos.StatsDto;
import bot.messages.stats.LeaderboardMessageBuilder;
import bot.services.StatsService;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.logging.Logger;

public class LeaderBoardCommand extends CommandHandler
{
    private final Logger logger = Logger.getLogger("command.leaderboard");
    private final StatsService statsService;

    public LeaderBoardCommand(StatsService statsService) {
        super("Fetches the stats leaderboard");
        this.statsService = statsService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        List<StatsDto> statsDtoList = statsService.getTopStats();

        new LeaderboardMessageBuilder()
            .setStats(statsDtoList)
            .sendMessage(channel);

        logger.info("Fetched leaderboard");
    }
}
