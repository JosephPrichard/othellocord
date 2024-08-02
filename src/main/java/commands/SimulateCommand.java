/*
 * Copyright (c) Joseph Prichard 2024.
 */

package commands;

import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.interactions.InteractionHook;
import domain.BoardRenderer;
import domain.OthelloBoard;
import services.AgentDispatcher;
import models.Game;
import models.Player;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Level;

import static utils.Log.LOGGER;

@AllArgsConstructor
public class SimulateCommand extends CommandHandler {

    public static final long MAX_DELAY = 5000L;
    public static final long MIN_DELAY = 1000L;

    private final ScheduledExecutorService scheduler;
    private final AgentDispatcher agentDispatcher;

    private void gameLoop(Game game, BlockingQueue<Optional<GameView>> queue, String id) {
        int depth = Player.Bot.getDepthFromId(game.currentPlayer().id());

        var finished = false;
        while (!finished) {
            try {
                var board = game.board();
                var future = agentDispatcher.findMove(board, depth);
                var bestMove = future.get();

                var nextGame = Game.from(game);
                nextGame.makeMove(bestMove.tile());

                var image = BoardRenderer.drawBoardMoves(nextGame.board());

                if (nextGame.isOver()) {
                    var view = GameResultView.createSimulationView(nextGame, bestMove.tile(), image);
                    queue.put(Optional.of(view));
                    queue.put(Optional.empty());

                    LOGGER.info("Finished the game simulation: " + id);
                    finished = true;
                } else {
                    var view = GameStateView.createSimulationView(nextGame, bestMove.tile(), image);
                    queue.put(Optional.of(view));
                    game = nextGame;
                }
            } catch (InterruptedException | ExecutionException x) {
                LOGGER.log(Level.WARNING, "Failed to put a task on the game view queue", x);
            }
        }
    }

    private void waitLoop(BlockingQueue<Optional<GameView>> queue, long delay, InteractionHook hook) {
        Runnable scheduled = () -> {
            try {
                var optView = queue.take();
                if (optView.isPresent()) {
                    // each completion callback will recursively schedule the next action
                    optView.get().editUsingHook(hook);
                    waitLoop(queue, delay, hook);
                } else {
                    LOGGER.info("Finished game simulation wait loop");
                }
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error occurred in scheduled event ", ex);
            }
        };
        // wait at least 1 second before we process each element to avoid overloading a Discord text channel
        scheduler.schedule(scheduled, delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onCommand(CommandContext ctx) {
        var blackLevel = ctx.getLongParam("black-level");
        if (blackLevel == null) {
            blackLevel = 3L;
        }

        var whiteLevel = ctx.getLongParam("white-level");
        if (whiteLevel == null) {
            whiteLevel = 3L;
        }

        var delay = ctx.getLongParam("delay");
        if (delay == null) {
            delay = 1500L;
        }

        long finalDelay = delay;
        if (finalDelay < MIN_DELAY || finalDelay > MAX_DELAY) {
            ctx.reply("Invalid delay, should be between " + MIN_DELAY + " and " + MAX_DELAY + " ms");
            return;
        }

        var startGame = new Game(OthelloBoard.initial(), Player.Bot.create(blackLevel), Player.Bot.create(whiteLevel));

        var id = UUID.randomUUID().toString();
        LOGGER.info("Starting the game simulation: " + id);

        var image = BoardRenderer.drawBoardMoves(startGame.board());
        var startView = GameStateView.createSimulationStartView(startGame, image);


        ctx.replyView(startView, (hook) -> {
            BlockingQueue<Optional<GameView>> queue = new LinkedBlockingQueue<>();
            gameLoop(startGame, queue, id);
            waitLoop(queue, finalDelay, hook);
        });
    }
}
