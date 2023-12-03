/*
 * Copyright (c) Joseph Prichard 2023.
 */

import commands.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import othello.BoardRenderer;
import services.DataSource;
import services.challenge.ChallengeScheduler;
import services.game.GameEvaluator;
import services.game.GameStorage;
import services.stats.StatsOrmDao;
import services.stats.StatsService;
import services.player.UserFetcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static utils.Logger.LOGGER;

public class OthelloBot extends ListenerAdapter {
    private final Map<String, Command> commandMap = new HashMap<>();
    private final List<Command> commandList = new ArrayList<>();

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

        var gameEvaluator = new GameEvaluator(cpuExecutor);
        var statsService = new StatsService(statsDao, userFetcher, ioExecutor);
        var gameStorage = new GameStorage(statsService);
        var challengeScheduler = new ChallengeScheduler(scheduledExecutor);

        var boardRenderer = new BoardRenderer();

        // add all bot commands to the handler map for handling events
        addCommands(
            new ChallengeCommand(challengeScheduler, gameStorage, boardRenderer),
            new AcceptCommand(gameStorage, challengeScheduler, boardRenderer),
            new ForfeitCommand(gameStorage, statsService, boardRenderer),
            new MoveCommand(gameStorage, statsService, gameEvaluator, boardRenderer),
            new ViewCommand(gameStorage, boardRenderer),
            new AnalyzeCommand(gameStorage, gameEvaluator),
            new StatsCommand(statsService),
            new LeaderBoardCommand(statsService)
        );
    }

    public List<SlashCommandData> getCommandData() {
        return commandList.stream().map(Command::getData).toList();
    }

    private void addCommands(Command... commands) {
        for (var c : commands) {
            commandMap.put(c.getKey(), c);
            commandList.add(c);
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
            command.onMessageEvent(event);
        }
    }
}
