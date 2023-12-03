/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import othello.Move;

import java.util.List;

public interface GameEvaluator {

    void findRankedMoves(EvalRequest<List<Move>> evalRequest);

    void findBestMove(EvalRequest<Move> evalRequest);
}
