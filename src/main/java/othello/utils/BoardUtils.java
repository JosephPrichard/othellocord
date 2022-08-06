package othello.utils;

import othello.board.BoardDeserializationException;
import othello.board.OthelloBitBoard;
import othello.board.OthelloBoard;
import othello.board.Tile;

public class BoardUtils
{
    public static String serialize(OthelloBoard board) {
        StringBuilder builder = new StringBuilder();
        // add the turn indicator
        builder.append(board.isBlackMove() ? "B" : "W");
        // for each square add the number to the serialized string
        for (Tile tile : board.tiles()) {
            builder.append(board.getSquare(tile));
        }
        return builder.toString();
    }

    /**
     * Algorithm to deserialize the board string into a board in memory,
     * assumes the serialization format is valid
     * @param boardStr to deserialize
     * @return board in memory
     */
    public static OthelloBoard deserialize(String boardStr) {
        boardStr = boardStr.replaceAll("\\s+","");

        if (boardStr.length() < 1) {
            throw new BoardDeserializationException();
        }

        // board size is the sqrt of the length without the turn indicator
        int len = boardStr.length() - 1;
        double sizeReal = Math.sqrt(len);

        if (sizeReal % 1 != 0) {
            throw new BoardDeserializationException();
        }

        // extract the turn indicator
        boolean isBlack = boardStr.charAt(0) == 'B';

        // populate board array by assigning characters to board
        OthelloBoard board = new OthelloBitBoard(isBlack);
        for (int i = 1; i < boardStr.length(); i++) {
            int pos = i - 1;
            board.setSquare(pos, (byte) (boardStr.charAt(i) - '0'));
        }

        return board;
    }

    public static void main(String[] args) {
        OthelloBoard board = deserialize("B 000000 000000 001200 002100 000000 000000");
        System.out.println(board);
        String boardStr = serialize(board);
        System.out.println(boardStr);
    }
}
