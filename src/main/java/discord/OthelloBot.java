/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord;

import discord.commands.*;
import discord.commands.Command;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import services.DataSource;
import services.StatsDao;
import services.ChallengeService;
import services.GameService;
import services.AgentService;
import services.StatsService;
import discord.renderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OthelloBot extends ListenerAdapter
{
    private final Map<String, Command> commandMap = new ConcurrentHashMap<>();
    private final List<Command> commandList = new ArrayList<>();

    public OthelloBot() {
        var ds = new DataSource();

        var statsDao = new StatsDao(ds);

        var agentService = new AgentService();
        var statsService = new StatsService(statsDao);
        var gameService = new GameService(statsService);
        var challengeService = new ChallengeService();

        var boardRenderer = new OthelloBoardRenderer();

        // add all bot commands to the handler map for handling events
        addCommands(
            new ChallengeCommand(challengeService, gameService, boardRenderer),
            new AcceptCommand(gameService, challengeService, boardRenderer),
            new ForfeitCommand(gameService, statsService, boardRenderer),
            new MoveCommand(gameService, statsService, agentService, boardRenderer),
            new ViewCommand(gameService, boardRenderer),
            new AnalyzeCommand(gameService, agentService),
            new StatsCommand(statsService),
            new LeaderBoardCommand(statsService)
        );
    }

    public List<SlashCommandData> getCommandData() {
        return commandList.stream().map(Command::getData).toList();
    }

    public void addCommands(Command... commands) {
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
