/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.agent;

import othello.Move;
import othello.OthelloBoard;

import java.util.List;
import java.util.function.Consumer;

// dispatch agent evaluation requests and handle using event callbacks
public interface IAgentDispatcher {

    void findMoves(OthelloBoard board, int depth, Consumer<List<Move>> onComplete);

    void findMove(OthelloBoard board, int depth, Consumer<Move> onComplete);
}
