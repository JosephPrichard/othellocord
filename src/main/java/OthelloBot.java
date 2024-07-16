/*
 * Copyright (c) Joseph Prichard 2023.
 */

import commands.*;
import commands.context.SlashCommandContext;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import services.DataSource;
import services.agent.AgentDispatcher;
import services.challenge.ChallengeScheduler;
import services.game.GameService;
import services.player.UserFetcher;
import services.stats.StatsDao;
import services.stats.StatsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.*;
import java.util.logging.Level;

import static commands.SimulateCommand.MAX_DELAY;
import static commands.SimulateCommand.MIN_DELAY;
import static services.player.Player.Bot.MAX_BOT_LEVEL;
import static utils.Logger.LOGGER;

public class OthelloBot extends ListenerAdapter {

    private final Map<String, Command> commandMap = new HashMap<>();

    public static final int CORES = Runtime.getRuntime().availableProcessors();

    private final ExecutorService taskExecutor = Executors.newCachedThreadPool(createThreadFactory("Task-Pool"));

    public static ThreadFactory createThreadFactory(String pool) {
        return (task) -> {
            Thread thread = new Thread(task, pool);
            thread.setDaemon(true);
            return thread;
        };
    }

    public void initMessageHandlers(JDA jda) {
        var dataSource = new DataSource();

        var cpuBndExecutor = new ThreadPoolExecutor(CORES / 2, CORES / 2,
            0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), createThreadFactory("CPU-Bnd-Pool"));
        var scheduler = Executors.newScheduledThreadPool(1, createThreadFactory("Schedule-Pool"));

        var statsDao = new StatsDao(dataSource);

        var userFetcher = UserFetcher.usingDiscord(jda);
        var agentDispatcher = new AgentDispatcher(cpuBndExecutor);
        var statsService = new StatsService(statsDao, userFetcher);
        var gameService = new GameService(statsService);
        var challengeScheduler = new ChallengeScheduler();

        commandMap.put("challenge", new ChallengeCommand(gameService, challengeScheduler));
        commandMap.put("accept", new AcceptCommand(gameService, challengeScheduler));
        commandMap.put("forfeit", new ForfeitCommand(gameService, statsService));
        commandMap.put("move", new MoveCommand(gameService, statsService, agentDispatcher));
        commandMap.put("view", new ViewCommand(gameService));
        commandMap.put("analyze", new AnalyzeCommand(gameService, agentDispatcher));
        commandMap.put("stats", new StatsCommand(statsService));
        commandMap.put("leaderboard", new LeaderBoardCommand(statsService));
        commandMap.put("simulate", new SimulateCommand(agentDispatcher, scheduler));
    }

    public static List<SlashCommandData> getCommandData() {
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

            Commands.slash("analyze", "Runs an analysis of the board")
                .addOptions(new OptionData(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false)),

            Commands.slash("stats", "Retrieves the stats profile for a player")
                .addOptions(new OptionData(OptionType.USER, "player", "Player to get stats profile for", false)),

            Commands.slash("leaderboard", "Retrieves the highest rated players by ELO"),

            Commands.slash("simulate", "Simulates a game between two bots")
                .addOptions(new OptionData(OptionType.STRING, "black-level",
                    "Level of the bot to play black between 1 and " + MAX_BOT_LEVEL, false))
                .addOptions(new OptionData(OptionType.STRING, "white-level",
                    "Level of the bot to play white between 1 and " + MAX_BOT_LEVEL, false))
                .addOptions(new OptionData(OptionType.INTEGER, "delay",
                    "Delay between moves in seconds between " + MIN_DELAY + " and " + MAX_DELAY + " ms", false))
        );
    }

    public static String readToken() {
        var envFile = Main.class.getResourceAsStream(".env");
        if (envFile == null) {
            System.out.println("Needs a .env file with a BOT_TOKEN field");
            System.exit(1);
        }

        String botToken = null;

        var envScanner = new Scanner(envFile);
        while (envScanner.hasNext()) {
            var line = envScanner.nextLine();
            var tokens = line.split("=");
            if (tokens[0].equals("BOT_TOKEN")) {
                botToken = tokens[1];
            }
        }
        if (botToken == null) {
            System.out.println("You have to provide the BOT_TOKEN key  in the .env file");
            System.exit(1);
        }

        return botToken;
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
            // start the command call on a separate task executor - it will contain blocking io and/or cpu bounded image work
            taskExecutor.submit(() -> {
                var ctx = new SlashCommandContext(event);
                try {
                    command.onCommand(ctx);
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Fatal error during command", ex);
                    ctx.reply("An unexpected error has occurred.");
                }
            });
        }
    }
}
