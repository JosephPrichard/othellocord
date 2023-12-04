/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;

public final class OthelloAgent {

    private final Logger logger = Logger.getLogger("othello.ai");

    private static final float INF = Float.MAX_VALUE;
    private static final int MIN_DEPTH = 5;

    private final int maxTime;
    private long stopTime = 0;
    private final int maxDepth;

    private final OthelloEvaluator evaluator;

    private final ZHasher hasher;
    private final TTable table;

    public OthelloAgent(int maxDepth) {
        this(maxDepth, (int) Math.pow(2, 12) + 1, 3000);
    }

    public OthelloAgent(int maxDepth, int ttSize, int maxTime) {
        this.maxDepth = maxDepth;
        this.hasher = new ZHasher();
        this.evaluator = new OthelloEvaluator();
        this.table = new TTable(ttSize);
        this.maxTime = maxTime;
    }

    public List<Move> findRankedMoves(OthelloBoard board) {
        var moves = board.findPotentialMoves();
        List<Move> rankedMoves = new ArrayList<>();

        // call the iterative deepening negamax to calculate the heuristic for each move and add it to list
        for (var move : moves) {
            var child = board.makeMoved(move);
            var heuristic = evaluate(child, maxDepth - 1);
            rankedMoves.add(new Move(move, heuristic));
        }

        // sort the moves to rank them properly
        Comparator<Move> comparator = board.isBlackMove() ?
            (m1, m2) -> Float.compare(m2.heuristic(), m1.heuristic()) :
            (m1, m2) -> Float.compare(m1.heuristic(), m2.heuristic());
        rankedMoves.sort(comparator);

        // remove duplicate moves (this is possible, has minimal effect on speed of algo due to transposition tables)
        var isDup = new boolean[OthelloBoard.getBoardSize()][OthelloBoard.getBoardSize()];
        for (var iterator = rankedMoves.iterator(); iterator.hasNext(); ) {
            var move = iterator.next();
            var r = move.tile().row();
            var c = move.tile().col();
            if (isDup[r][c]) {
                iterator.remove();
            }
            isDup[r][c] = true;
        }

        return rankedMoves;
    }

    public Move findBestMove(OthelloBoard board) {
        var startTime = System.currentTimeMillis();
        stopTime = startTime + maxTime;

        var moves = board.findPotentialMoves();
        Tile bestMove = null;
        var bestHeuristic = board.isBlackMove() ? -INF : INF;

        // comparator to find the best move by heuristic
        Comparator<Float> comparator = board.isBlackMove() ? Float::compare : (m1, m2) -> Float.compare(m2, m1);

        // call the iterative deepening negamax to calculate the heuristic for each potential move and determine the best one
        for (var move : moves) {
            var child = board.makeMoved(move);

            var heuristic = evaluate(child, maxDepth - 1);
            if (comparator.compare(heuristic, bestHeuristic) > 0) {
                bestMove = move;
                bestHeuristic = heuristic;
            }
        }

        var endTime = System.currentTimeMillis();
        var timeTaken = endTime - startTime;

        logger.info("Finished ai analysis, " +
            "max_depth: " + maxDepth + ", " +
            "tt_hits: " + table.getHits() + ", " +
            "tt_misses: " + table.getMisses() + ", " +
            "time_taken: " + timeTaken + "ms"
        );

        return new Move(bestMove, bestHeuristic);
    }

    /**
     * Searches othello game tree with iterative deepening depth first search
     * Starts from a relative depth of 1 until a specified relative max depth
     */
    public float evaluate(OthelloBoard board, int maxDepth) {
        float heuristic = 0;
        for (var depthLimit = 1; depthLimit < maxDepth; depthLimit++) {
            heuristic = evaluate(board.copy(), depthLimit, board.isBlackMove(), -INF, INF);
        }
        return heuristic;
    }

    /**
     * Searches othello game tree using minimax with alpha beta pruning to evaluate how good a board is
     */
    public float evaluate(OthelloBoard board, int depth, boolean maximizer, float alpha, float beta) {
        // stop early when we reach depth floor, or we've gone over time
        if (depth == 0 || (depth >= MIN_DEPTH && System.currentTimeMillis() > stopTime)) {
            return evaluator.heuristic(board);
        }

        var moves = board.findPotentialMoves();

        // stop when we cannot expand node's children
        if (moves.isEmpty()) {
            return evaluator.heuristic(board);
        }

        var hashKey = hasher.hash(board);

        // check tt table to see if we have a cache hit
        var node = table.get(hashKey);
        if (node != null && node.depth() >= depth) {
            return node.heuristic();
        }

        // find the children for the board state
        List<OthelloBoard> children = new ArrayList<>();
        // create a new child board with a corresponding node for each move
        for (var move : moves) {
            var child = board.makeMoved(move);
            children.add(child);
        }

        if (maximizer) {
            // explore the best children first for move ordering, find the best moves and return them
            for (var child : children) {
                alpha = Math.max(alpha, evaluate(child, depth - 1, false, alpha, beta));
                // prune this branch, it cannot possibly be better than any child found so far
                if (alpha >= beta) {
                    break;
                }
            }
            table.put(new TTNode(hashKey, alpha, depth));
            return alpha;
        } else {
            // explore the best children first for move ordering, find the best moves and return them
            for (var child : children) {
                beta = Math.min(beta, evaluate(child, depth - 1, true, alpha, beta));
                // prune this branch, it cannot possibly be better than any child found so far
                if (beta <= alpha) {
                    break;
                }
            }
            table.put(new TTNode(hashKey, beta, depth));
            return beta;
        }
    }

    public static void main(String[] args) {
        var startTime = System.currentTimeMillis();

        var board = new OthelloBoard();
        for (var j = 0; j < 10; j++) {
            var agent = new OthelloAgent(10);
            var bestMove = agent.findBestMove(board);
            System.out.println(bestMove);
            board = board.makeMoved(bestMove.tile());
        }

        var endTime = System.currentTimeMillis();
        System.out.println("Total execution time: " + (endTime - startTime) + " ms");
    }
}
