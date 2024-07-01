/*
 * Copyright (c) Joseph Prichard 2023.
 */

package commands;

import commands.context.CommandContext;
import commands.messaging.GameResultView;
import commands.messaging.GameStateView;
import commands.messaging.GameView;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.CommandAutoCompleteInteraction;
import othello.BoardRenderer;
import othello.OthelloBoard;
import othello.Tile;
import services.agent.IAgentDispatcher;
import services.game.Game;
import services.game.IGameService;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.player.Player;
import services.stats.IStatsService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

import static utils.Logger.LOGGER;

public class MoveCommand extends Command {

    private final IGameService gameService;
    private final IStatsService statsService;
    private final IAgentDispatcher agentDispatcher;

    public MoveCommand(IGameService gameService, IStatsService statsService, IAgentDispatcher agentDispatcher) {
        this.gameService = gameService;
        this.statsService = statsService;
        this.agentDispatcher = agentDispatcher;
    }

    public GameStateView buildMoveView(Game game, Tile move) {
        var image = BoardRenderer.drawBoardMoves(game.board());
        return GameStateView.createGameView(game, move, image);
    }

    public GameStateView buildMoveView(Game game) {
        var image = BoardRenderer.drawBoardMoves(game.board());
        return GameStateView.createGameView(game, image);
    }

    public GameResultView onGameOver(Game game, Tile move) {
        var result = game.createResult();
        var statsResult = statsService.writeStats(result);
        var image = BoardRenderer.drawBoard(game.board());
        return GameResultView.createGameOverView(result, statsResult, move, game, image);
    }

    public void doBotMove(CommandContext ctx, Game game) {
        var currPlayer = game.getCurrentPlayer();
        var depth = Player.Bot.getDepthFromId(currPlayer.id());

        var latch = new CountDownLatch(1);
        AtomicReference<GameView> view = new AtomicReference<>(null);

        // queue an agent request which will find the best move, make the move, and send back a response
        agentDispatcher.findMove(game.board(), depth, (bestMove) -> {
            try {
                var newGame = gameService.makeMove(currPlayer, bestMove.tile());

                var tempView = newGame.isOver() ?
                    onGameOver(newGame, bestMove.tile()) :
                    buildMoveView(newGame, bestMove.tile());

                view.set(tempView);
                latch.countDown();
            } catch (TurnException | NotPlayingException | InvalidMoveException ex) {
                // this shouldn't happen: the bot should only make legal moves when it is currently it's turn
                // if we get an error like this, the only thing we can do is log it and debug later
                LOGGER.warning("Error occurred in an agent callback thread " + ex);
            }
        });

        try {
            latch.await();
            ctx.sendMessage(view.get());
        } catch (InterruptedException ex) {
            LOGGER.warning("Error occurred while waiting for a bot response " + ex);
        }
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var strMove = Objects.requireNonNull(ctx.getStringParam("move"));
        var player = ctx.getPlayer();

        var move = Tile.fromNotation(strMove);
        try {
            var game = gameService.makeMove(player, move);

            if (game.isOver()) {
                var view = onGameOver(game, move);
                ctx.sendReply(view);
            } else {
                if (game.isAgainstBot()) {
                    var view = buildMoveView(game);
                    ctx.sendReply(view);
                    doBotMove(ctx, game);
                } else {
                    var view = buildMoveView(game, move);
                    ctx.sendReply(view);
                }
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

        var game = gameService.getGame(player);
        if (game != null) {
            var moves = game.findPotentialMoves();

            // don't display duplicate moves
            var duplicate = new boolean[OthelloBoard.getBoardSize()][OthelloBoard.getBoardSize()];

            List<Choice> choices = new ArrayList<>();
            for (var tile : moves) {
                var row = tile.row();
                var col = tile.col();

                if (!duplicate[row][col]) {
                    choices.add(new Choice(tile.toString(), tile.toString()));
                }
                duplicate[row][col] = true;
            }

            interaction.replyChoices(choices).queue();
        } else {
            interaction.replyChoices().queue();
        }
    }
}