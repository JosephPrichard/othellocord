package bot.commands;

import bot.commands.abstracts.CommandContext;
import bot.commands.abstracts.Command;
import bot.dtos.StatsDto;
import bot.builders.embed.LeaderboardEmbedBuilder;
import bot.services.StatsService;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;
import java.util.logging.Logger;

public class LeaderBoardCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.leaderboard");
    private final StatsService statsService;

    public LeaderBoardCommand(StatsService statsService) {
        super("leaderboard", "Retrieves the highest rated players by ELO");
        this.statsService = statsService;
    }

    @Override
    protected void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        List<StatsDto> statsDtoList = statsService.getTopStats();

        MessageEmbed embed = new LeaderboardEmbedBuilder()
            .setStats(statsDtoList)
            .build();

        channel.sendMessageEmbeds(embed).queue();

        logger.info("Fetched leaderboard");
    }
}
