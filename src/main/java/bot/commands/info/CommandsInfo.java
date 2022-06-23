package bot.commands.info;

import java.util.ArrayList;
import java.util.List;

public class CommandsInfo
{
    private final List<CommandInfo> commandInfo = new ArrayList<>();

    public CommandsInfo() {
        commandInfo.add(
            new CommandInfo(
                "challenge",
                "Challenges an another discord user to an Othello game",
                new CommandParam("opponent", "The @tag of the opposing player you want to challenge to a game"),
                new CommandParam("size", "The board size of the game to play. Can be 4, 6, or 8. (optional) (defaults to 8)")
            )
        );
        commandInfo.add(
            new CommandInfo(
                "accept",
                "Accepts a challenge from another discord user"
            )
        );
        commandInfo.add(
            new CommandInfo(
                "forfeit",
                "Forfeits the user's current game"
            )
        );
        commandInfo.add(
            new CommandInfo(
                "move",
                "Makes a move on user's current game",
                new CommandParam("notation", "The notation of move to make on the Reversi board (c3, D2, etc.)" )
            )
        );
        commandInfo.add(
            new CommandInfo(
                "view",
                "Displays the game state including all the moves that can be made this turn"
            )
        );
        commandInfo.add(
            new CommandInfo(
                "stats",
                "Retrieves the stats profile for a player",
                new CommandParam("player", "The player to retrieve stats for (optional) (defaults to self)")
            )
        );
        commandInfo.add(
            new CommandInfo(
                "leaderboard",
                "Retrieves the highest rated players by ELO"
            )
        );
        commandInfo.add(
            new CommandInfo(
                "help",
                "Displays help for any command",
                new CommandParam("command", "The command to display help for (optional)" )
            )
        );
    }

    public List<CommandInfo> getCommandInfo() {
        return commandInfo;
    }
}
