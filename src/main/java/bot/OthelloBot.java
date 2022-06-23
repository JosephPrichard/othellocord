package bot;

import bot.commands.info.CommandsInfo;
import bot.commands.*;
import bot.commands.abstracts.CommandHandler;
import bot.dao.ChallengeDao;
import bot.dao.GameDao;
import bot.dao.StatsDao;
import bot.services.ChallengeService;
import bot.services.GameService;
import bot.services.StatsService;
import bot.imagerenderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

public class OthelloBot extends ListenerAdapter
{
    private final Map<String, CommandHandler> handlers = new HashMap<>();

    public OthelloBot() {
        DataSource ds = new DataSource();

        GameDao gameDao = new GameDao(ds);
        StatsDao statsDao = new StatsDao(ds);
        ChallengeDao challengeDao = new ChallengeDao();

        GameService gameService = new GameService(gameDao);
        StatsService statsService = new StatsService(statsDao);
        ChallengeService challengeService = new ChallengeService(challengeDao);

        OthelloBoardRenderer boardRenderer = new OthelloBoardRenderer(8);

        CommandsInfo commandsInfo = new CommandsInfo();

        // add all bot.commands to the handler map for handling events
        handlers.put("!challenge", new ChallengeCommandHandler(challengeService));
        handlers.put("!accept", new AcceptCommandHandler(gameService, challengeService, boardRenderer));
        handlers.put("!forfeit", new ForfeitCommandHandler(gameService, statsService, boardRenderer));
        handlers.put("!move", new MoveCommandHandler(gameService, statsService, boardRenderer));
        handlers.put("!view", new ViewCommandHandler(gameService, boardRenderer));
        handlers.put("!stats", new StatsCommandHandler(statsService));
        handlers.put("!leaderboard", new LeaderBoardCommand(statsService));
        handlers.put("!help", new HelpCommandHandler(commandsInfo));
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw();
        // extracts the command key from the string
        int i = message.indexOf(' ');
        String key = i != -1 ? message.substring(0, i) : message;

        // fetch command handler from bot.commands map, execute if command exists
        CommandHandler handler = handlers.get(key);
        if (handler != null) {
            handler.onMessageEvent(event);
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        super.onSlashCommandInteraction(event);
    }
}
