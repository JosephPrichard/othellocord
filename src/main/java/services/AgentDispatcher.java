/*
 * Copyright (c) Joseph Prichard 2024.
 */

package services;

import domain.OthelloAgent;
import domain.OthelloBoard;
import domain.Tile;

import java.util.List;
import java.util.concurrent.*;

import static utils.Log.LOGGER;

public class AgentDispatcher {
    private final ExecutorService cpuBndExecutor;
    private final BlockingQueue<OthelloAgent> agentsQueue;

    public AgentDispatcher(ThreadPoolExecutor cpuBndExecutor) {
        this(cpuBndExecutor, new LinkedBlockingQueue<>());

        var agentCount = cpuBndExecutor.getMaximumPoolSize();
        assert agentCount > 0;

        for (int i = 0; i < agentCount; i++) {
            agentsQueue.add(new OthelloAgent());
        }
    }

    public AgentDispatcher(ExecutorService cpuBndExecutor, BlockingQueue<OthelloAgent> agentsQueue) {
        this.cpuBndExecutor = cpuBndExecutor;
        this.agentsQueue = agentsQueue;
    }

    public Future<List<Tile.Move>> findMoves(OthelloBoard board, int depth) {
        CompletableFuture<List<Tile.Move>> future = new CompletableFuture<>();
        cpuBndExecutor.submit(() -> {
            try {
                var agent = agentsQueue.take();
                LOGGER.info("Started agent ranked moves calculation of depth " + depth);

                var moves = agent.findRankedMoves(board, depth);
                agentsQueue.add(agent);

                LOGGER.info("Finished agent ranked moves calculation of depth " + depth + ": "
                    + moves.stream().map(Tile.Move::toString).reduce("", String::concat));
                future.complete(moves);
            } catch (Exception ex) {
                LOGGER.warning("Error occurred while processing a find moves event " + ex);
            }
        });
        return future;
    }

    public Future<Tile.Move> findMove(OthelloBoard board, int depth) {
        CompletableFuture<Tile.Move> future = new CompletableFuture<>();
        cpuBndExecutor.submit(() -> {
            try {
                var agent = agentsQueue.take();

                LOGGER.info("Started agent best move calculation of depth " + depth);

                var move = agent.findBestMove(board, depth);
                agentsQueue.add(agent);

                LOGGER.info("Finished agent best move calculation of depth " + depth + ": " + move);
                future.complete(move);
            } catch (Exception ex) {
                LOGGER.warning("Error occurred while processing a find move event " + ex);
            }
        });
        return future;
    }
}
