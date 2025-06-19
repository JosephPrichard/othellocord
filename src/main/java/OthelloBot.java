/*
 * Copyright (c) Joseph Prichard 2023.
 */

import discord.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import services.*;

import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static discord.AgentHandler.MAX_DELAY;
import static discord.AgentHandler.MIN_DELAY;
import static models.Player.Bot.MAX_BOT_LEVEL;
import static utils.LogUtils.LOGGER;
import static utils.ThreadUtils.CORES;
import static utils.ThreadUtils.createThreadFactory;

public class OthelloBot extends ListenerAdapter {

    private ExecutorService taskExecutor;
    private StatsHandler statsHandler;
    private GameHandler gameHandler;
    private AgentHandler agentHandler;
    private ChallengeHandler challengeHandler;
    private AutoCompleteHandler autoCompleteHandler;

    public void init(JDA jda) {
        DataSource dataSource = new DataSource();
        ThreadPoolExecutor cpuBndExecutor = new ThreadPoolExecutor(CORES / 2, CORES / 2,
                0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), createThreadFactory("CPU-Bnd-Pool"));
        StatsDao statsDao = new StatsDao(dataSource);

        taskExecutor = Executors.newCachedThreadPool(createThreadFactory("Task-Pool"));

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, createThreadFactory("Schedule-Pool"));
        UserFetcher userFetcher = UserFetcher.usingDiscord(jda);
        AgentDispatcher agentDispatcher = new AgentDispatcher(cpuBndExecutor);
        StatsService statsService = new StatsService(statsDao, userFetcher);
        GameService gameService = new GameService(statsService);
        ChallengeScheduler challengeScheduler = new ChallengeScheduler();

        statsHandler = new StatsHandler(statsService);
        gameHandler = new GameHandler(gameService, statsService, agentDispatcher);
        agentHandler = new AgentHandler(gameService, agentDispatcher, scheduler, taskExecutor);
        challengeHandler = new ChallengeHandler(gameService, challengeScheduler);
        autoCompleteHandler = new AutoCompleteHandler(gameService);
    }

    public static List<SlashCommandData> getCommandData() {
        return List.of(
            // challenge commands
            Commands.slash("challenge", "Challenges the bot or another user to an Othello game")
                .addSubcommands(new SubcommandData("user", "Challenges another user to a game")
                    .addOption(OptionType.USER, "opponent", "The opponent to challenge", true))
                .addSubcommands(new SubcommandData("bot", "Challenges the bot to a game")
                    .addOption(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false)),
            Commands.slash("accept", "Accepts a challenge from another discord user")
                .addOptions(new OptionData(OptionType.USER, "challenger", "User who made the challenge", true)),
            // game flow commands
            Commands.slash("forfeit", "Forfeits the user's current game"),
            Commands.slash("move", "Makes a move on user's current game")
                .addOptions(new OptionData(OptionType.STRING, "move", "Move to make on the board", true, true)),
            Commands.slash("view", "Displays the game state including all the moves that can be made this turn"),
            // engine analysis and simulation commands
            Commands.slash("analyze", "Runs an analysis of the board")
                .addOptions(new OptionData(OptionType.INTEGER, "level", "Level of the bot between 1 and " + MAX_BOT_LEVEL, false)),
            Commands.slash("simulate", "Simulates a game between two bots")
                .addOptions(new OptionData(OptionType.STRING, "black-level",
                    "Level of the bot to play black between 1 and " + MAX_BOT_LEVEL, false))
                .addOptions(new OptionData(OptionType.STRING, "white-level",
                    "Level of the bot to play white between 1 and " + MAX_BOT_LEVEL, false))
                .addOptions(new OptionData(OptionType.INTEGER, "delay",
                    "Delay between moves in seconds between " + MIN_DELAY + " and " + MAX_DELAY + " ms", false)),
            // statistics/telemetry commands
            Commands.slash("stats", "Retrieves the stats profile for a player")
                .addOptions(new OptionData(OptionType.USER, "player", "Player to get stats profile for", false)),
            Commands.slash("leaderboard", "Retrieves the highest rated players by ELO")
        );
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        switch (event.getName()) {
            case "move" -> autoCompleteHandler.handleMove(event);
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getUser().isBot()) {
            return;
        }

        Consumer<SlashCommandInteractionEvent> handler = switch (event.getName()) {
            // challenge commands
            case "challenge" -> challengeHandler::handleChallenge;
            case "accept" -> challengeHandler::handleAccept;
            // game flow commands
            case "forfeit" -> gameHandler::handleForfeit;
            case "move" -> gameHandler::handleMove;
            case "view" -> gameHandler::handleView;
            // engine analysis and simulation commands
            case "analyze" -> agentHandler::handleAnalyze;
            case "simulate" -> agentHandler::handleSimulate;
            // statistics/telemetry commands
            case "stats" -> statsHandler::handleStats;
            case "leaderboard" -> statsHandler::handleLeaderboard;
            default -> null;
        };

        if (handler != null) {
            taskExecutor.submit(() -> {
                try {
                    handler.accept(event);
                } catch (Exception ex) {
                    LOGGER.error("Fatal error during command", ex);
                    event.reply("An unexpected error has occurred.").queue();
                }
            });
        }
    }
}
