/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.agent;

import othello.Move;

import java.util.List;

// dispatch agent evaluation requests and handle using event callbacks
public interface AgentDispatcher {

    void dispatchFindMovesEvent(AgentEvent<List<Move>> agentEvent);

    void dispatchFindMoveEvent(AgentEvent<Move> agentEvent);
}
