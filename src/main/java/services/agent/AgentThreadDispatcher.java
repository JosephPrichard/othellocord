/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.agent;

import othello.Move;
import othello.OthelloAgent;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static utils.Logger.LOGGER;

// implementation of the dispatcher that runs an agent evaluator on a specific thread
public class AgentThreadDispatcher implements AgentDispatcher {

    private final ExecutorService cpuBndExecutor;

    public AgentThreadDispatcher(ExecutorService cpuBndExecutor) {
        this.cpuBndExecutor = cpuBndExecutor;
    }

    public void dispatchFindMovesEvent(AgentEvent<List<Move>> agentEvent) {
        cpuBndExecutor.submit(() -> {
            var agent = new OthelloAgent(agentEvent.depth());
            var moves = agent.findRankedMoves(agentEvent.game().board());
            agentEvent.applyOnComplete(moves);
        });
        LOGGER.info("Started agent ranked moves calculation of depth " + agentEvent.depth());
    }

    public void dispatchFindMoveEvent(AgentEvent<Move> agentEvent) {
        cpuBndExecutor.submit(() -> {
            var agent = new OthelloAgent(agentEvent.depth());
            var move = agent.findBestMove(agentEvent.game().board());
            agentEvent.applyOnComplete(move);
        });
        LOGGER.info("Started agent best move calculation of depth " + agentEvent.depth());
    }
}
