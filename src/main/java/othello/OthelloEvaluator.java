/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

public class OthelloEvaluator {

    public static final int[][] CORNERS = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
    public static final int[][] XC_SQUARES = {{1, 1}, {1, 6}, {6, 1}, {6, 6}, {0, 1}, {0, 6}, {7, 1}, {7, 6}, {1, 0}, {1, 7}, {6, 0}, {6, 7}};

    private float heuristic(float blackScore, float whiteScore) {
        return (blackScore - whiteScore) / (blackScore + whiteScore);
    }

    private float parityHeuristic(OthelloBoard board) {
        var whiteScore = 0f;
        var blackScore = 0f;
        for (var row = 0; row < OthelloBoard.getBoardSize(); row++) {
            for (var col = 0; col < OthelloBoard.getBoardSize(); col++) {
                if (board.getSquare(row, col) == OthelloBoard.WHITE) {
                    whiteScore++;
                }
                if (board.getSquare(row, col) == OthelloBoard.BLACK) {
                    blackScore++;
                }
            }
        }
        return heuristic(blackScore, whiteScore);
    }

    private float tilesHeuristic(OthelloBoard board, int[][] tiles) {
        float whiteTiles = 0;
        float blackTiles = 0;
        // iterate over corners and calculate the number of white and black corners
        for (var tile : tiles) {
            var currentColor = board.getSquare(tile[0], tile[1]);
            if (currentColor == OthelloBoard.WHITE) {
                whiteTiles++;
            } else if (currentColor == OthelloBoard.BLACK) {
                blackTiles++;
            }
        }
        if (blackTiles + whiteTiles == 0) {
            return 0f;
        }
        return heuristic(blackTiles, whiteTiles);
    }

    private float cornerHeuristic(OthelloBoard board) {
        return tilesHeuristic(board, CORNERS);
    }

    private float xcSquareHeuristic(OthelloBoard board) {
        return tilesHeuristic(board, XC_SQUARES);
    }

    private float mobilityHeuristic(OthelloBoard board) {
        float whiteMoves = board.countPotentialMoves(OthelloBoard.WHITE);
        float blackMoves = board.countPotentialMoves(OthelloBoard.BLACK);
        if (whiteMoves + blackMoves == 0) {
            return 0f;
        }
        return heuristic(blackMoves, whiteMoves);
    }

    private float stabilityHeuristic(OthelloBoard board) {
        return 0f;
    }

    public float heuristic(OthelloBoard board) {
        return 50f * parityHeuristic(board)
            + 100f * cornerHeuristic(board)
            + 100f * mobilityHeuristic(board)
            + 50f * xcSquareHeuristic(board)
            + 100f * stabilityHeuristic(board);
    }
}
