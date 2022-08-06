package discord.commands;

import discord.commands.abstracts.CommandContext;
import discord.commands.abstracts.Command;
import modules.game.Game;
import modules.game.GameService;
import modules.Player;
import discord.message.senders.GameViewMessageSender;
import discord.renderers.OthelloBoardRenderer;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import othello.board.OthelloBoard;
import othello.board.Tile;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.logging.Logger;

public class ViewCommand extends Command
{
    private final Logger logger = Logger.getLogger("command.view");
    private final GameService gameService;
    private final OthelloBoardRenderer boardRenderer;

    public ViewCommand(GameService gameService, OthelloBoardRenderer boardRenderer) {
        super("view", "Displays the game state including all the moves that can be made this turn");
        this.gameService = gameService;
        this.boardRenderer = boardRenderer;
    }

    @Override
    public void doCommand(CommandContext ctx) {
        MessageReceivedEvent event = ctx.getEvent();
        MessageChannel channel = event.getChannel();

        Player player = new Player(event.getAuthor());

        Game game = gameService.getGame(player);
        if (game == null) {
            channel.sendMessage("You're not currently in a game.").queue();
            return;
        }

        OthelloBoard board = game.getBoard();
        List<Tile> potentialMoves = board.findPotentialMoves();

        BufferedImage image = boardRenderer.drawBoard(board, potentialMoves);
        new GameViewMessageSender()
            .setGame(game)
            .setImage(image)
            .sendMessage(channel);

        logger.info("Player " + player + " view moves in game");
    }
}
