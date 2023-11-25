/*
 * Copyright (c) Joseph Prichard 2023.
 */

package discord;

import discord.commands.*;
import discord.commands.Command;
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
            new ChallengeCommand(challengeService),
            new ChallengeBotCommand(gameService, boardRenderer),
            new AcceptCommand(gameService, challengeService, boardRenderer),
            new ForfeitCommand(gameService, statsService, boardRenderer),
            new MoveCommand(gameService, statsService, agentService, boardRenderer),
            new ViewCommand(gameService, boardRenderer),
            new AnalyzeCommand(gameService, agentService),
            new StatsCommand(statsService),
            new LeaderBoardCommand(statsService)
        );
    }

    public void addCommands(Command... commands) {
        for (var c : commands) {
            commandMap.put("!" + c.getKey(), c);
            commandList.add(c);
        }
    }

    public void onHelpForCommand(MessageChannel channel, String key) {
        key = "!" + key;
        var command = commandMap.get(key);
        if (command != null) {
            var text = new StringBuilder(command.getDesc() + "\n" + key);
            for (var param : command.getParams()) {
                text.append(" `").append(param).append("`");
            }
            channel.sendMessage(text.toString()).queue();
        } else {
            channel.sendMessage("No such command: " + key).queue();
        }
    }

    public void onHelp(MessageChannel channel) {
        var stringBuilder = new StringBuilder();
        stringBuilder.append("Commands: ");
        for (var command : commandList) {
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

        var message = event.getMessage().getContentRaw();
        // extracts the command key from the string
        var tokens = message.split("\\s+");
        var key = tokens[0];

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
        var command = commandMap.get(key);
        if (command != null) {
            command.onMessageEvent(event);
        }
    }
}
