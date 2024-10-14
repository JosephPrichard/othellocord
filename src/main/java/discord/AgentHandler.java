/*
 * Copyright (c) Joseph Prichard 2024.
 */

package discord;

import engine.BoardRenderer;
import engine.OthelloBoard;
import lombok.AllArgsConstructor;
import models.Game;
import models.Player;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import utils.EventUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static models.Player.Bot.MAX_BOT_LEVEL;
import static utils.LogUtils.LOGGER;

@AllArgsConstructor
public class AgentHandler {
    public static final long MAX_DELAY = 5000L;
    public static final long MIN_DELAY = 1000L;

    private BotState state;

    public void handleAnalyze(SlashCommandInteraction event) {
        var gameService = state.getGameService();
        var agentDispatcher = state.getAgentDispatcher();

        var level = EventUtils.getLongParam(event, "level");
        if (level == null) {
            level = 3L;
        }

        // check if level is within range
        if (Player.Bot.isInvalidLevel(level)) {
            event.reply(String.format("Invalid level, should be between 1 and %s", MAX_BOT_LEVEL)).queue();
            return;
        }

        var player = new Player(event.getUser());

        var game = gameService.getGame(player);
        if (game == null) {
            event.reply("You're not currently in a game.").queue();
            return;
        }

        // send starting message, then add queue an agent request, send back the results in a message when it's done
        var depth = Player.Bot.getDepthFromId(level);
        final var finalLevel = level;
        event.reply("Analyzing... Wait a second...")
            .queue(hook -> {
                LOGGER.info("Starting board state analysis");

                try {
                    var future = agentDispatcher.findMoves(game.getBoard(), depth);
                    var rankedMoves = future.get();

                    var image = BoardRenderer.drawBoardAnalysis(game.getBoard(), rankedMoves);
                    var view = GameView.createAnalysisView(game, image, finalLevel, player);

                    view.editUsingHook(hook);
                    LOGGER.info("Finished board state analysis");
                } catch (ExecutionException | InterruptedException e) {
                    LOGGER.warn("Error occurred while responding to an analyze command", e);
                }
            });
    }

    private void simulationGameLoop(Game initialGame, BlockingQueue<Optional<GameView>> queue, String id) {
        int depth = Player.Bot.getDepthFromId(initialGame.getCurrentPlayer().getId());

        state.getTaskExecutor().submit(() -> {
            var game = initialGame;

            var finished = false;
            while (!finished) {
                try {
                    var board = game.getBoard();
                    var future = state.getAgentDispatcher().findMove(board, depth);
                    var bestMove = future.get();

                    var nextGame = Game.from(game);
                    nextGame.makeMove(bestMove.tile());

                    var image = BoardRenderer.drawBoardMoves(nextGame.getBoard());

                    if (nextGame.isOver()) {
                        var view = GameView.createResultSimulationView(nextGame, bestMove.tile(), image);
                        queue.put(Optional.of(view));
                        queue.put(Optional.empty());

                        LOGGER.info("Finished the game simulation: {}", id);
                        finished = true;
                    } else {
                        var view = GameView.createSimulationView(nextGame, bestMove.tile(), image);
                        queue.put(Optional.of(view));
                        game = nextGame;
                    }
                } catch (InterruptedException | ExecutionException x) {
                    LOGGER.error("Failed to put a task on the game view queue", x);
                }
            }
        });
    }

    private void simulationWaitLoop(BlockingQueue<Optional<GameView>> queue, long delay, InteractionHook hook) {
        Runnable scheduled = () -> {
            try {
                var optView = queue.take();
                if (optView.isPresent()) {
                    // each completion callback will recursively schedule the next action
                    optView.get().editUsingHook(hook);
                    simulationWaitLoop(queue, delay, hook);
                } else {
                    LOGGER.info("Finished game simulation wait loop");
                }
            } catch (Exception ex) {
                LOGGER.error("Error occurred in scheduled event", ex);
            }
        };
        // wait at least 1 second before we process each element to avoid overloading a Discord text channel
        state.getScheduler().schedule(scheduled, delay, TimeUnit.MILLISECONDS);
    }

    public void handleSimulate(SlashCommandInteraction event) {
        var blackLevel = EventUtils.getLongParam(event, "black-level");
        if (blackLevel == null) {
            blackLevel = 3L;
        }

        var whiteLevel = EventUtils.getLongParam(event, "white-level");
        if (whiteLevel == null) {
            whiteLevel = 3L;
        }

        var delay = EventUtils.getLongParam(event, "delay");
        if (delay == null) {
            delay = 1500L;
        }

        long finalDelay = delay;
        if (finalDelay < MIN_DELAY || finalDelay > MAX_DELAY) {
            event.reply(String.format("Invalid delay, should be between %s and %s ms", MIN_DELAY, MAX_DELAY)).queue();
            return;
        }

        var startGame = new Game(OthelloBoard.initial(), Player.Bot.create(blackLevel), Player.Bot.create(whiteLevel));

        var id = UUID.randomUUID().toString();
        LOGGER.info("Starting the game simulation: {}", id);

        var image = BoardRenderer.drawBoardMoves(startGame.getBoard());
        var startView = GameView.createSimulationStartView(startGame, image);

        EventUtils.replyView(event, startView, (hook) -> {
            BlockingQueue<Optional<GameView>> queue = new LinkedBlockingQueue<>();
            simulationGameLoop(startGame, queue, id);
            simulationWaitLoop(queue, finalDelay, hook);
        });
    }
}
