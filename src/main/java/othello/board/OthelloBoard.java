package othello.board;

import othello.utils.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

public final class OthelloBoard
{
    public static final byte EMPTY = 0;
    public static final byte WHITE = 1;
    public static final byte BLACK = 2;

    private static final int[][] BOARD_DIRECTIONS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final int[][] BOARD_CORNERS = {{0, 0}, {0, 1}, {1, 0}, {1, 1}};

    private byte[][] board;
    private boolean blackMove;

    public OthelloBoard() {
        this(8);
    }

    public OthelloBoard(int boardSize) {
        board = new byte[boardSize][boardSize];

        board[getBoardSize() / 2 - 1][getBoardSize() / 2 - 1] = WHITE;
        board[getBoardSize() / 2][getBoardSize() / 2] = WHITE;
        board[getBoardSize() / 2 - 1][getBoardSize() / 2] = BLACK;
        board[getBoardSize() / 2][getBoardSize() / 2 - 1] = BLACK;

        blackMove = true;
    }

    public OthelloBoard(OthelloBoard othelloBoard) {
        this.board = ArrayUtils.deepCopyOf2DArray(othelloBoard.board);
        this.blackMove = othelloBoard.blackMove;
    }

    public OthelloBoard(byte[][] board, boolean blackMove) {
        this.board = board;
        this.blackMove = blackMove;
    }

    public int getBoardSize() {
        return board.length;
    }

    public byte[][] getBoard() {
        return board;
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

    public float parityHeuristic() {
        float whiteScore = 0f;
        float blackScore = 0f;
        for (int i = 0; i < getBoardSize(); i++) {
            for (int j = 0; j < getBoardSize(); j++) {
                if (board[i][j] == WHITE)
                    whiteScore++;
                if (board[i][j] == BLACK)
                    blackScore++;
            }
        }
        return 100f * (blackScore - whiteScore) / (blackScore + whiteScore);
    }

    public float cornerHeuristic() {
        int whiteCorners = 0;
        int blackCorners = 0;
        int farCorner = getBoardSize() - 1;
        // iterate over corners and calculate the number of white and black corners
        for (int[] corner : BOARD_CORNERS) {
            byte currentColor = board[farCorner * corner[0]][farCorner * corner[1]];
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

    public float mobilityHeuristic() {
        int whiteMoves = countPotentialMoves(WHITE);
        int blackMoves = countPotentialMoves(BLACK);
        if (whiteMoves + blackMoves == 0) {
            return 0f;
        }
        return 100f * (blackMoves - whiteMoves) / (blackMoves + whiteMoves);
    }

    public float heuristic() {
        return parityHeuristic() + cornerHeuristic() + mobilityHeuristic();
    }

    /**
     * Deallocates the board array while retaining other information about the state
     */
    public void setBoardEmpty() {
        board = null;
    }

    /**
     * Check if a row column pair is in value
     * @param row of position
     * @param col of position
     * @return whether the pair is potentially a valid board position
     */
    public boolean inBounds(int row, int col) {
        return row >= 0 && col >= 0 && row < getBoardSize() && col < getBoardSize();
    }

    /**
     * Searches the board for all othello pieces of matching color
     * @param color to find pieces for
     * @return list containing positions of the pieces
     */
    public List<Tile> findPieces(byte color) {
        List<Tile> pieces = new ArrayList<>();

        // iterate through each square and find the pieces
        for (int i = 0; i < getBoardSize(); i++) {
            for (int j = 0; j < getBoardSize(); j++) {
                if (board[i][j] == color)
                    pieces.add(new Tile(i, j));
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
            for (int[] direction : BOARD_DIRECTIONS) {
                int row = piece.getRow() + direction[0];
                int col = piece.getCol() + direction[1];

                // iterate from piece to next opposite color
                int count = 0;
                while (inBounds(row, col)) {
                    if (board[row][col] != oppositeColor)
                        break;
                    row += direction[0];
                    col += direction[1];
                    count++;
                }
                // add move to potential moves list assuming
                // we flank at least once piece, the tile is in bounds and is empty
                if (count > 0 && inBounds(row, col) && board[row][col] == EMPTY) {
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
        board[move.getRow()][move.getCol()] = currentColor;

        // check each direction of new piece position
        for (int[] direction : BOARD_DIRECTIONS) {
            int initialRow = move.getRow() + direction[0];
            int initialCol = move.getCol() + direction[1];

            int row = initialRow;
            int col = initialCol;

            boolean flank = false;

            // iterate from piece until first potential flank
            while (inBounds(row, col)) {
                if (board[row][col] == currentColor) {
                    flank = true;
                    break;
                } else if (board[row][col] == EMPTY) {
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
                if (board[row][col] != oppositeColor)
                    break;

                board[row][col] = currentColor;

                row += direction[0];
                col += direction[1];
            }
        }
    }

    /**
     * Sets a square to a value on the board by othello board notation
     * @param square in othello board notation
     * @param color to set, must be one of the constants
     */
    public void setSquare(String square, byte color) {
        int col = square.charAt(0) - 'a';
        int row = Character.getNumericValue(square.charAt(1)) - 1;
        board[row][col] = color;
    }

    /**
     * Sets a square to a value on the board by othello board notation
     * @param position on the board
     * @param color to set, must be one of the constants
     */
    public void setSquare(int position, byte color) {
        int col = position / getBoardSize();
        int row = position % getBoardSize();
        board[row][col] = color;
    }

    /**
     * Gets a square from the othello board
     * @param square in othello board notation
     */
    public byte getSquare(String square) {
        int col = square.charAt(0) - 'a';
        int row = Character.getNumericValue(square.charAt(1)) - 1;
        return board[row][col];
    }

    /**
     * Gets a square from the othello board
     * @param position on the board
     */
    public byte getSquare(int position) {
        int col = position / getBoardSize();
        int row = position % getBoardSize();
        return board[row][col];
    }

    /**
     * Gets a square from the othello board
     * @param piece position on the board
     */
    public byte getSquare(Tile piece) {
        return board[piece.getRow()][piece.getCol()];
    }

    /**
     * Gets a square from the othello board
     * @param row to get for
     * @param col to get for
     */
    public byte getSquare(int row, int col) {
        return board[row][col];
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
        for (int i = 0; i < getBoardSize(); i++) {
            builder.append(i + 1);
            builder.append(" ");
            for (int j = 0; j < getBoardSize(); j++) {
                builder.append(board[i][j]);
                builder.append(" ");
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    public static void main(String[] args) {
        OthelloBoard board = new OthelloBoard();
        board.setSquare("a1", BLACK);
        board.setSquare("h1", BLACK);
        board.setSquare("a8", BLACK);
        board.setSquare("h8", BLACK);
        System.out.println(board.cornerHeuristic());
    }
}
