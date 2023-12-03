/*
 * Copyright (c) Joseph Prichard 2023.
 */

import commands.*;
import commands.context.SlashCommandContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import services.DataSource;
import services.challenge.ChallengeScheduler;
import services.game.AgentEvaluator;
import services.game.GameCacheStorage;
import services.player.UserFetcher;
import services.stats.StatsOrmDao;
import services.stats.StatsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.Level;

import static services.player.Player.Bot.MAX_BOT_LEVEL;
import static utils.Logger.LOGGER;

public class OthelloBot extends ListenerAdapter {

    private final Map<String, Command> commandMap = new HashMap<>();

    public void initListeners(JDA jda) {
        var ds = new DataSource();

        var cores = Runtime.getRuntime().availableProcessors();
        LOGGER.info("Starting the cpu bounded executor service with " + cores + " cores");

        var cpuExecutor = Executors.newFixedThreadPool(cores);
        var ioExecutor = Executors.newCachedThreadPool();
        var scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

        UserFetcher userFetcher = (id) -> jda
            .retrieveUserById(id)
            .submit()
            .thenApply(User::getAsTag);

        var statsDao = new StatsOrmDao(ds);

        var agentEvaluator = new AgentEvaluator(cpuExecutor);
        var statsService = new StatsService(statsDao, userFetcher, ioExecutor);
        var gameStorage = new GameCacheStorage(statsService);
        var challengeScheduler = new ChallengeScheduler(scheduledExecutor);

        // add all bot commands to the handler map for handling events
        addCommands(
            new ChallengeCommand(challengeScheduler, gameStorage),
            new AcceptCommand(gameStorage, challengeScheduler),
            new ForfeitCommand(gameStorage, statsService),
            new MoveCommand(gameStorage, statsService, agentEvaluator),
            new ViewCommand(gameStorage),
            new AnalyzeCommand(gameStorage, agentEvaluator),
            new StatsCommand(statsService),
            new LeaderBoardCommand(statsService)
        );
    }

    public List<SlashCommandData> getCommandData() {
        return List.of(
            Commands.slash("challenge", "Challenges the bot or another user to an Othello game")
                .addSubcommands(new SubcommandData("user", "Challenges another user to a game")
                    .addOption(OptionType.USER, "opponent", "The opponent to challenge", true))
                .addSubcommands(new SubcommandData("bot", "Challenges the bot to a game")
                    .addOption(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false)),

            Commands.slash("accept", "Accepts a challenge from another discord user")
                .addOptions(new OptionData(OptionType.USER, "challenger", "User who made the challenge", true)),

            Commands.slash("forfeit", "Forfeits the user's current game"),

            Commands.slash("move", "Makes a move on user's current game")
                .addOptions(new OptionData(OptionType.STRING, "move", "Move to make on the board", true, true)),

            Commands.slash("view", "Displays the game state including all the moves that can be made this turn"),

            Commands.slash("analyze",  "Runs an analysis of the board")
                .addOptions(new OptionData(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false)),

            Commands.slash("stats", "Retrieves the stats profile for a player")
                .addOptions(new OptionData(OptionType.USER, "player", "Player to get stats profile for", false)),

            Commands.slash("leaderboard", "Retrieves the highest rated players by ELO")
        );
    }

    private void addCommands(Command... commands) {
        for (var c : commands) {
            commandMap.put(c.getKey(), c);
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        var command = commandMap.get(event.getName());
        if (command != null) {
            command.onAutoComplete(event);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getUser().isBot()) {
            return;
        }
        // fetch command handler from bot.commands map, execute if command exists
        var command = commandMap.get(event.getName());
        if (command != null) {
            var ctx = new SlashCommandContext(event);
            try {
                command.onCommand(ctx);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Fatal error during command", ex);
                ctx.reply("An unexpected error has occurred.");
            }
        }
    }
}
