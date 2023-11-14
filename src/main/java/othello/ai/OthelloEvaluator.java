/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello.ai;

import othello.board.OthelloBoard;

// https://courses.cs.washington.edu/courses/cse573/04au/Project/mini1/RUSSIA/Final_Paper.pdf
public class OthelloEvaluator
{
    public static final int[][] CORNERS = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
    public static final int[][] XC_SQUARES = {{1, 1}, {1, 6}, {6, 1}, {6, 6}, {0, 1}, {0, 6}, {7, 1}, {7, 6}, {1, 0}, {1, 7}, {6, 0}, {6, 7}};

    private float parityHeuristic(OthelloBoard board) {
        float whiteScore = 0f;
        float blackScore = 0f;
        for (int row = 0; row < OthelloBoard.getBoardSize(); row++) {
            for (int col = 0; col < OthelloBoard.getBoardSize(); col++) {
                if (board.getSquare(row, col) == OthelloBoard.WHITE)
                    whiteScore++;
                if (board.getSquare(row, col) == OthelloBoard.BLACK)
                    blackScore++;
            }
        }
        return (blackScore - whiteScore) / (blackScore + whiteScore);
    }

    private float cornerHeuristic(OthelloBoard board) {
        float whiteCorners = 0;
        float blackCorners = 0;
        // iterate over corners and calculate the number of white and black corners
        for (int[] corner : CORNERS) {
            byte currentColor = board.getSquare(corner[0], corner[1]);
            if (currentColor == OthelloBoard.WHITE) {
                whiteCorners++;
            } else if (currentColor == OthelloBoard.BLACK) {
                blackCorners++;
            }
        }
        if (blackCorners + whiteCorners == 0) {
            return 0f;
        }
        return (blackCorners - whiteCorners) / (blackCorners + whiteCorners);
    }

    private float xcSquareHeuristic(OthelloBoard board) {
        float whiteXCSquares = 0;
        float blackXCSquares = 0;
        // iterate over xc squares and calculate the number of white and black xc squares
        for (int[] square : XC_SQUARES) {
            byte currentColor = board.getSquare(square[0], square[1]);
            if (currentColor == OthelloBoard.WHITE) {
                whiteXCSquares++;
            } else if (currentColor == OthelloBoard.BLACK) {
                blackXCSquares++;
            }
        }
        if (whiteXCSquares + blackXCSquares == 0) {
            return 0f;
        }
        // having more x or c squares is bad
        return (whiteXCSquares - blackXCSquares) / (blackXCSquares + whiteXCSquares);
    }

    private float mobilityHeuristic(OthelloBoard board) {
        float whiteMoves = board.countPotentialMoves(OthelloBoard.WHITE);
        float blackMoves = board.countPotentialMoves(OthelloBoard.BLACK);
        if (whiteMoves + blackMoves == 0) {
            return 0f;
        }
        return (blackMoves - whiteMoves) / (blackMoves + whiteMoves);
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
