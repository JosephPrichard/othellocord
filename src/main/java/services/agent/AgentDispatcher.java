/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.agent;

import othello.Move;
import othello.OthelloAgent;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import static utils.Logger.LOGGER;

// implementation of the dispatcher that runs an agent evaluator on a specific thread
public class AgentDispatcher implements IAgentDispatcher {

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

    public void dispatchFindMovesEvent(AgentEvent<List<Move>> agentEvent) {
        cpuBndExecutor.submit(() -> {
            try {
                var agent = agentsQueue.take();

                LOGGER.info("Started agent ranked moves calculation of depth " + agentEvent.depth());

                var moves = agent.findRankedMoves(agentEvent.board(), agentEvent.depth());
                agentEvent.applyOnComplete(moves);

                agentsQueue.add(agent);

                LOGGER.info("Finished agent ranked moves calculation of depth " + agentEvent.depth());
            } catch (InterruptedException ex) {
                LOGGER.warning("Failed to take agent off queue" + ex);
            }
        });
    }

    public void dispatchFindMoveEvent(AgentEvent<Move> agentEvent) {
        cpuBndExecutor.submit(() -> {
            try {
                var agent = agentsQueue.take();

                LOGGER.info("Started agent best move calculation of depth " + agentEvent.depth());

                var move = agent.findBestMove(agentEvent.board(), agentEvent.depth());
                agentEvent.applyOnComplete(move);

                agentsQueue.add(agent);

                LOGGER.info("Finished agent best move calculation of depth " + agentEvent.depth());
            } catch (InterruptedException ex) {
                LOGGER.warning("Failed to take agent off queue" + ex);
            }
        });
    }
}
