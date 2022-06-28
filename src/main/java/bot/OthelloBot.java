package bot;

import bot.commands.*;
import bot.commands.abstracts.Command;
import bot.dao.ChallengeDao;
import bot.dao.GameDao;
import bot.dao.StatsDao;
import bot.services.ChallengeService;
import bot.services.GameService;
import bot.services.OthelloAiService;
import bot.services.StatsService;
import bot.imagerenderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OthelloBot extends ListenerAdapter
{
    private final Map<String, Command> commandMap = new HashMap<>();
    private final List<Command> commandList = new ArrayList<>();

    public OthelloBot() {
        DataSource ds = new DataSource();

        GameDao gameDao = new GameDao(ds);
        StatsDao statsDao = new StatsDao(ds);
        ChallengeDao challengeDao = new ChallengeDao();

        OthelloAiService aiService = new OthelloAiService();
        GameService gameService = new GameService(gameDao);
        StatsService statsService = new StatsService(statsDao);
        ChallengeService challengeService = new ChallengeService(challengeDao);

        OthelloBoardRenderer boardRenderer = new OthelloBoardRenderer(8);

        // add all bot commands to the handler map for handling events
        addCommand(new ChallengeCommand(challengeService));
        addCommand(new ChallengeBotCommand(gameService, boardRenderer));
        addCommand(new AcceptCommand(gameService, challengeService, boardRenderer));
        addCommand(new ForfeitCommand(gameService, statsService, boardRenderer));
        addCommand(new MoveCommand(gameService, statsService, aiService, boardRenderer));
        addCommand(new ViewCommand(gameService, boardRenderer));
        addCommand(new AnalyzeCommand(gameService, aiService));
        addCommand(new StatsCommand(statsService));
        addCommand(new LeaderboardCommand(statsService));
    }

    public void addCommand(Command command) {
        commandMap.put("!" + command.getKey(), command);
        commandList.add(command);
    }

    public void onHelpForCommand(MessageChannel channel, String key) {
        key = "!" + key;
        Command command = commandMap.get(key);
        if (command != null) {
            StringBuilder text = new StringBuilder(command.getDesc() + "\n" + key);
            for (String param : command.getParams()) {
                text.append(" `").append(param).append("`");
            }
            channel.sendMessage(text.toString()).queue();
        } else {
            channel.sendMessage("No such command: " + key).queue();
        }
    }

    public void onHelp(MessageChannel channel) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Commands: ");
        for (Command command : commandList) {
            stringBuilder.append("`!")
                .append(command.getKey())
                .append("` ");
        }
        channel.sendMessage(stringBuilder.toString()).queue();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        String message = event.getMessage().getContentRaw();
        // extracts the command key from the string
        String[] tokens = message.split("\\s+");
        String key = tokens[0];

        // special case for help command
        if (key.equals("!help")) {
            if (tokens.length >= 2) {
                onHelpForCommand(event.getChannel(), tokens[1]);
            } else {
                onHelp(event.getChannel());
            }

            return;
        }

        // fetch command handler from bot.commands map, execute if command exists
        Command command = commandMap.get(key);
        if (command != null) {
            command.onMessageEvent(event);
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        super.onSlashCommandInteraction(event);
    }
}
