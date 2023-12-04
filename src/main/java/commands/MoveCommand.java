/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import messaging.senders.GameOverSender;
import messaging.senders.GameViewSender;
import messaging.senders.MessageSender;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import othello.BoardRenderer;
import othello.Move;
import othello.Tile;
import services.agent.AgentDispatcher;
import services.agent.AgentEvent;
import services.game.Game;
import services.game.GameStorage;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.player.Player;
import services.stats.StatsWriter;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static utils.Logger.LOGGER;

public class MoveCommand extends Command {

    private final GameStorage gameStorage;
    private final StatsWriter statsWriter;
    private final AgentDispatcher agentDispatcher;
    private final ExecutorService ioTaskExecutor;

    public MoveCommand(
        GameStorage gameStorage,
        StatsWriter statsWriter,
        AgentDispatcher agentDispatcher,
        ExecutorService ioTaskExecutor
    ) {
        super("move");
        this.gameStorage = gameStorage;
        this.statsWriter = statsWriter;
        this.agentDispatcher = agentDispatcher;
        this.ioTaskExecutor = ioTaskExecutor;
    }

    public MessageSender onMoved(Game game, Tile move) {
        var image = BoardRenderer.drawBoardMoves(game.board());
        return new GameViewSender()
            .setGame(game, move)
            .setTag(game)
            .setImage(image);
    }

    public MessageSender onMoved(Game game) {
        var image = BoardRenderer.drawBoardMoves(game.board());
        return new GameViewSender()
            .setGame(game)
            .setImage(image);
    }

    public MessageSender onGameOver(Game game, Tile move) {
        var result = game.createResult();
        var statsResult = statsWriter.writeStats(result);

        var image = BoardRenderer.drawBoard(game.board());
        return new GameOverSender()
            .setResults(result, statsResult)
            .addMoveMessage(result.winner(), move.toString())
            .addScoreMessage(game.getWhiteScore(), game.getBlackScore())
            .setTag(result)
            .setImage(image);
    }

    public void doBotMove(CommandContext ctx, Game game) {
        // queue an agent request which will find the best move, make the move, and send back a response
        var depth = Player.Bot.getDepthFromId(game.getCurrentPlayer().id());
        var event = new AgentEvent<>(game, depth, (Move bestMove) -> {
            var newGame = gameStorage.makeMove(game, bestMove.tile());
            MessageSender sender;
            if (!newGame.isGameOver()) {
                sender = onMoved(newGame, bestMove.tile());
            } else {
                sender = onGameOver(newGame, bestMove.tile());
            }
            ctx.msgWithSender(sender);
        });
        agentDispatcher.dispatchFindMoveEvent(event);
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var strMove = Objects.requireNonNull(ctx.getStringParam("move"));
        var player = ctx.getPlayer();

        var move = Tile.fromNotation(strMove);
        try {
            var game = gameStorage.makeMove(player, move);

            if (!game.isGameOver()) {
                if (!game.isAgainstBot()) {
                    var sender = onMoved(game, move);
                    ctx.replyWithSender(sender);
                } else {
                    var sender = onMoved(game);
                    ctx.replyWithSender(sender);
                    doBotMove(ctx, game);
                }
            } else {
                ioTaskExecutor.submit(() -> {
                    // handling the game over event involves writing stats to an external service
                    var sender = onGameOver(game, move);
                    ctx.replyWithSender(sender);
                });
            }

            LOGGER.info("Player " + player + " made move on game");
        } catch (TurnException e) {
            ctx.reply("It isn't your turn.");
        } catch (NotPlayingException e) {
            ctx.reply("You're not currently in a game.");
        } catch (InvalidMoveException e) {
            ctx.reply("Can't make a move to " + strMove + ".");
        }
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteraction interaction) {
        var player = new Player(interaction.getUser());
        var game = gameStorage.getGame(player);
        if (game != null) {
            var moves = game.board().findPotentialMoves();
            var choices = moves.stream()
                .map((tile) -> new Choice(tile.toString(), tile.toString()))
                .collect(Collectors.toList());
            interaction.replyChoices(choices).queue();
        }
    }
}