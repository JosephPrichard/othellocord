package othello.board;

import java.util.ArrayList;
import java.util.List;

import static othello.board.OthelloBoard.getBoardSize;
import static othello.board.OthelloBoard.inBounds;

public final class OthelloBitBoard implements OthelloBoard
{
    private long boardA;
    private long boardB;
    private boolean blackMove;

    public OthelloBitBoard() {
        setSquare(getBoardSize() / 2 - 1, getBoardSize() / 2 - 1, WHITE);
        setSquare(getBoardSize() / 2, getBoardSize() / 2, WHITE);
        setSquare(getBoardSize() / 2 - 1, getBoardSize() / 2, BLACK);
        setSquare(getBoardSize() / 2, getBoardSize() / 2 - 1, BLACK);
        blackMove = true;
    }

    public OthelloBitBoard(boolean blackMove) {
        this.blackMove = blackMove;
    }

    public OthelloBitBoard(OthelloBitBoard othelloBoard) {
        this.boardA = othelloBoard.boardA;
        this.boardB = othelloBoard.boardB;
        this.blackMove = othelloBoard.blackMove;
    }

    @Override
    public boolean isBlackMove() {
        return blackMove;
    }

    @Override
    public float whiteScore() {
        return findPieces(WHITE).size();
    }

    @Override
    public float blackScore() {
        return findPieces(BLACK).size();
    }

    @Override
    public OthelloBoard copy() {
        return new OthelloBitBoard(this);
    }

    @Override
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

    @Override
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
    @Override
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

    @Override
    public boolean isGameOver() {
        return countPotentialMoves(blackMove ? BLACK : WHITE) <= 0;
    }

    /**
     * Makes a move on a position on the othello board
     * @param move to make move
     */
    @Override
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
        int p = ((row < HALF_SIZE ? row : row - HALF_SIZE) * getBoardSize() + col) * 2;
        if (row < HALF_SIZE ) {
            boardA &= ~(1L << p) & ~(1L << (p + 1));
            boardA |= (long) color << p;
        } else {
            boardB &= ~(1L << p) & ~(1L << (p + 1));
            boardB |= (long) color << p;
        }
    }

    private byte getSquare(int row, int col) {
        int p = ((row < HALF_SIZE ? row : row - HALF_SIZE) * getBoardSize() + col) * 2;
        return row < HALF_SIZE ? (byte) (((1 << 2) - 1) & (boardA >> p)) : (byte) (((1 << 2) - 1) & (boardB >> p));
    }

    /**
     * Sets a square to a value on the board by othello board notation
     * @param square in othello board notation
     * @param color to set, must be one of the constants
     */
    @Override
    public void setSquare(String square, byte color) {
        int col = square.charAt(0) - 'a';
        int row = Character.getNumericValue(square.charAt(1)) - 1;
        setSquare(row, col, color);
    }

    /**
     * Gets a square from the othello board
     * @param position on the board
     */
    @Override
    public void setSquare(int position, byte color) {
        setSquare(position / getBoardSize(), position % getBoardSize(), color);
    }

    /**
     * Gets a square from the othello board
     * @param square in othello board notation
     */
    @Override
    public byte getSquare(String square) {
        int col = square.charAt(0) - 'a';
        int row = Character.getNumericValue(square.charAt(1)) - 1;
        return getSquare(row, col);
    }

    /**
     * Gets a square from the othello board
     * @param position on the board
     */
    @Override
    public byte getSquare(int position) {
        return getSquare(position / getBoardSize(), position % getBoardSize());
    }

    /**
     * Gets a square from the othello board
     * @param tile to get for
     */
    @Override
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

    public static void main(String[] args) {
        OthelloBitBoard board = new OthelloBitBoard();
        board.setSquare(1, 1, WHITE);
        System.out.println(board);
        board.setSquare(2, 2, BLACK);
        System.out.println(board);
        board.setSquare(1, 1, BLACK);
        System.out.println(board);
        board.setSquare(2, 2, EMPTY);
        System.out.println(board);
    }
}
