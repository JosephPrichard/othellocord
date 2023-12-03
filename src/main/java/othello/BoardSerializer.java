/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import othello.exceptions.BoardDeserializationException;

public class BoardSerializer {

    public String serialize(OthelloBoard board) {
        var builder = new StringBuilder();
        // add the turn indicator
        builder.append(board.isBlackMove() ? "B" : "W");
        // for each square add the number to the serialized string
        for (var tile : OthelloBoard.tiles()) {
            builder.append(board.getSquare(tile));
        }
        return builder.toString();
    }

    public OthelloBoard deserialize(String boardStr) {
        boardStr = boardStr.replaceAll("\\s+", "");

        if (boardStr.isEmpty()) {
            throw new BoardDeserializationException();
        }

        // board size is the sqrt of the length without the turn indicator
        var len = boardStr.length() - 1;
        var sizeReal = Math.sqrt(len);

        if (sizeReal % 1 != 0) {
            throw new BoardDeserializationException();
        }

        // extract the turn indicator
        var isBlack = boardStr.charAt(0) == 'B';

        // populate board array by assigning characters to board
        var board = new OthelloBoard(isBlack);
        for (var i = 1; i < boardStr.length(); i++) {
            var pos = i - 1;
            board.setSquare(pos, (byte) (boardStr.charAt(i) - '0'));
        }

        return board;
    }

    public static void main(String[] args) {
        var serializer = new BoardSerializer();
        var boardStr = serializer.serialize(new OthelloBoard());
        System.out.println(boardStr);
        var board = serializer.deserialize(boardStr);
        System.out.println(board);
    }
}
