package othello.board;

import java.util.ArrayList;
import java.util.List;

public class OthelloBoard
{
    public static final int BOARD_SIZE = 8;
    public static final int HALF_SIZE = BOARD_SIZE / 2;

    public static final byte EMPTY = 0;
    public static final byte WHITE = 1;
    public static final byte BLACK = 2;

    public static final int[][] DIRECTIONS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    public static final int[][] CORNERS = {{0, 0}, {0, 7}, {7, 0}, {7, 7}};
    public static final int[][] XC_SQUARES = {{1, 1}, {1, 6}, {6, 1}, {6, 6}, {0, 1}, {0, 6}, {7, 1}, {7, 6}, {1, 0}, {1, 7}, {6, 0}, {6, 7}};

    private long boardA;
    private long boardB;
    private boolean blackMove;

    public OthelloBoard() {
        setSquare(getBoardSize() / 2 - 1, getBoardSize() / 2 - 1, WHITE);
        setSquare(getBoardSize() / 2, getBoardSize() / 2, WHITE);
        setSquare(getBoardSize() / 2 - 1, getBoardSize() / 2, BLACK);
        setSquare(getBoardSize() / 2, getBoardSize() / 2 - 1, BLACK);
        blackMove = true;
    }

    public OthelloBoard(boolean blackMove) {
        this.blackMove = blackMove;
    }

    public OthelloBoard(OthelloBoard othelloBoard) {
        this.boardA = othelloBoard.boardA;
        this.boardB = othelloBoard.boardB;
        this.blackMove = othelloBoard.blackMove;
    }

    public static int getBoardSize() {
        return BOARD_SIZE;
    }

    public static boolean inBounds(int row, int col) {
        return row >= 0 && col >= 0 && row < getBoardSize() && col < getBoardSize();
    }

    public boolean isBlackMove() {
        return blackMove;
    }

    public float whiteScore() {
        return findPieces(WHITE).size();
    }

    public float blackScore() {
        return findPieces(BLACK).size();
    }

    public OthelloBoard copy() {
        return new OthelloBoard(this);
    }

    public List<Tile> tiles() {
        List<Tile> tiles = new ArrayList<>();
        for (int row = 0; row < getBoardSize(); row++) {
            for (int col = 0; col < getBoardSize(); col++) {
                tiles.add(new Tile(row, col));
            }
        }
        return tiles;
    }

    private float parityHeuristic() {
        float whiteScore = 0f;
        float blackScore = 0f;
        for (int row = 0; row < getBoardSize(); row++) {
            for (int col = 0; col < getBoardSize(); col++) {
                if (getSquare(row, col) == WHITE)
                    whiteScore++;
                if (getSquare(row, col) == BLACK)
                    blackScore++;
            }
        }
        return 100f * (blackScore - whiteScore) / (blackScore + whiteScore);
    }

    private float cornerHeuristic() {
        int whiteCorners = 0;
        int blackCorners = 0;
        // iterate over corners and calculate the number of white and black corners
        for (int[] corner : CORNERS) {
            byte currentColor = getSquare(corner[0], corner[1]);
            if (currentColor == WHITE) {
                whiteCorners++;
            } else if (currentColor == BLACK) {
                blackCorners++;
            }
        }
        if (blackCorners + whiteCorners == 0) {
            return 0f;
        }
        return 100f * (blackCorners - whiteCorners) / (blackCorners + whiteCorners);
    }

    private float xcSquareHeuristic() {
        int whiteXCSquares = 0;
        int blackXCSquares = 0;
        // iterate over x squares and calculate the number of white and black xc squares
        for (int[] corner : XC_SQUARES) {
            byte currentColor = getSquare(corner[0], corner[1]);
            if (currentColor == WHITE) {
                whiteXCSquares++;
            } else if (currentColor == BLACK) {
                blackXCSquares++;
            }
        }
        if (whiteXCSquares + blackXCSquares == 0) {
            return 0f;
        }
        // having more x or c squares is bad
        return 50f * (whiteXCSquares - blackXCSquares) / (blackXCSquares + whiteXCSquares);
    }

    private float mobilityHeuristic() {
        int whiteMoves = countPotentialMoves(WHITE);
        int blackMoves = countPotentialMoves(BLACK);
        if (whiteMoves + blackMoves == 0) {
            return 0f;
        }
        return 100f * (blackMoves - whiteMoves) / (blackMoves + whiteMoves);
    }

    public float heuristic() {
        return parityHeuristic() + cornerHeuristic() + mobilityHeuristic() + xcSquareHeuristic();
    }

    /**
     * Searches the board for all othello pieces of matching color
     * @param color to find pieces for
     * @return list containing positions of the pieces
     */
    private List<Tile> findPieces(byte color) {
        List<Tile> pieces = new ArrayList<>();

        // iterate through each square and find the pieces
        for (int row = 0; row < getBoardSize(); row++) {
            for (int col = 0; col < getBoardSize(); col++) {
                if (getSquare(row, col) == color)
                    pieces.add(new Tile(row, col));
            }
        }
        return pieces;
    }

    /**
     * Find the available moves the othello board depending on the board state
     * @return a list containing the moves
     */
    public List<Tile> findPotentialMoves() {
        return findPotentialMoves(blackMove ? BLACK : WHITE);
    }

    /**
     * Find the available moves the othello board depending on the board state
     * @return a list containing the moves
     */
    private List<Tile> findPotentialMoves(byte color) {
        List<Tile> moves = new ArrayList<>();

        List<Tile> pieces = findPieces(color);
        int oppositeColor = color == BLACK ? WHITE : BLACK;

        // check each piece for potential flanks
        for (Tile piece : pieces) {
            // check each direction from piece for potential flank
            for (int[] direction : DIRECTIONS) {
                int row = piece.getRow() + direction[0];
                int col = piece.getCol() + direction[1];

                // iterate from piece to next opposite color
                int count = 0;
                while (inBounds(row, col)) {
                    if (getSquare(row, col) != oppositeColor)
                        break;
                    row += direction[0];
                    col += direction[1];
                    count++;
                }
                // add move to potential moves list assuming
                // we flank at least once piece, the tile is in bounds and is empty
                if (count > 0 && inBounds(row, col) && getSquare(row, col) == EMPTY) {
                    moves.add(new Tile(row, col));
                }
            }
        }

        return moves;
    }

    /**
     * Find the available moves the othello board depending on the board state
     * @return a list containing the moves
     */
    private int countPotentialMoves(byte color) {
        return findPotentialMoves(color).size();
    }

    public boolean isGameOver() {
        return countPotentialMoves(blackMove ? BLACK : WHITE) <= 0;
    }

    /**
     * Makes a move on a position on the othello board
     * @param move to make move
     */
    public void makeMove(Tile move) {
        byte oppositeColor = blackMove ? WHITE : BLACK;
        byte currentColor = blackMove ? BLACK : WHITE;

        blackMove = !blackMove;
        setSquare(move.getRow(), move.getCol(), currentColor);

        // check each direction of new piece position
        for (int[] direction : DIRECTIONS) {
            int initialRow = move.getRow() + direction[0];
            int initialCol = move.getCol() + direction[1];

            int row = initialRow;
            int col = initialCol;

            boolean flank = false;

            // iterate from piece until first potential flank
            while (inBounds(row, col)) {
                if (getSquare(row, col) == currentColor) {
                    flank = true;
                    break;
                } else if (getSquare(row, col) == EMPTY) {
                    break;
                }
                row += direction[0];
                col += direction[1];
            }

            if (!flank) {
                continue;
            }

            row = initialRow;
            col = initialCol;

            // flip each piece to opposite color to flank, update piece counts
            while (inBounds(row, col)) {
                if (getSquare(row, col) != oppositeColor)
                    break;

                setSquare(row, col, currentColor);

                row += direction[0];
                col += direction[1];
            }
        }
    }

    private void setSquare(int row, int col, byte color) {
        // calculate bit position
        int p = ((row < HALF_SIZE ? row : row - HALF_SIZE) * getBoardSize() + col) * 2;
        long clearMask = ~(1L << p) & ~(1L << (p + 1));
        // clear bits then set bits
        if (row < HALF_SIZE ) {
            boardA &= clearMask;
            boardA |= (long) color << p;
        } else {
            boardB &= clearMask;
            boardB |= (long) color << p;
        }
    }

    private byte getSquare(int row, int col) {
        int mask = (1 << 2) - 1;
        int p = ((row < HALF_SIZE ? row : row - HALF_SIZE) * getBoardSize() + col) * 2;
        return row < HALF_SIZE ? (byte) (mask & (boardA >> p)) : (byte) (mask & (boardB >> p));
    }

    /**
     * Sets a square to a value on the board by othello board notation
     * @param square in othello board notation
     * @param color to set, must be one of the constants
     */
    public void setSquare(String square, byte color) {
        int col = square.charAt(0) - 'a';
        int row = Character.getNumericValue(square.charAt(1)) - 1;
        setSquare(row, col, color);
    }

    /**
     * Gets a square from the othello board
     * @param position on the board
     */
    public void setSquare(int position, byte color) {
        setSquare(position / getBoardSize(), position % getBoardSize(), color);
    }

    /**
     * Gets a square from the othello board
     * @param square in othello board notation
     */
    public byte getSquare(String square) {
        int col = square.charAt(0) - 'a';
        int row = Character.getNumericValue(square.charAt(1)) - 1;
        return getSquare(row, col);
    }

    /**
     * Gets a square from the othello board
     * @param position on the board
     */
    public byte getSquare(int position) {
        return getSquare(position / getBoardSize(), position % getBoardSize());
    }

    /**
     * Gets a square from the othello board
     * @param tile to get for
     */
    public byte getSquare(Tile tile) {
        return getSquare(tile.getRow(), tile.getCol());
    }

    /**
     * Converts the internal board to a parsable, readable string
     * @return internal board as a string
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        // add space for better board indentation
        builder.append("  ");
        // add each column header as letter
        for (int i = 0; i < getBoardSize(); i++) {
            builder.append((char)('a' + (char)i));
            builder.append(" ");
        }
        builder.append("\n");
        // add each matrix element in board with row header
        for (int row = 0; row < getBoardSize(); row++) {
            builder.append(row + 1);
            builder.append(" ");
            for (int col = 0; col < getBoardSize(); col++) {
                builder.append(getSquare(row, col));
                builder.append(" ");
            }
            builder.append("\n");
        }
        return builder.toString();
    }
}
