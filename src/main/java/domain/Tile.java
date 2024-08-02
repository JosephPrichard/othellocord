/*
 * Copyright (c) Joseph Prichard 2023.
 */

package domain;

public record Tile(int row, int col) {

    public static Tile fromNotation(String square) {
        square = square.toLowerCase();
        if (square.length() != 2) {
            return new Tile(-1, -1);
        } else {
            var row = Character.getNumericValue(square.charAt(1)) - 1;
            var col = square.charAt(0) - 'a';
            return new Tile(row, col);
        }
    }

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }

    @Override
    public String toString() {
        var c = (char) (col + 'a');
        var r = Integer.toString(row + 1);
        return c + r;
    }

    public boolean equalsNotation(String notation) {
        var cNotLower = (char) (col + 'a');
        var cNotUpper = (char) (col + 'A');
        var rNot = (char) ((row + 1) + '0');

        if (notation.length() == 2) {
            var c = notation.charAt(0);
            var r = notation.charAt(1);
            return (c == cNotUpper || c == cNotLower) && r == rNot;
        }
        return false;
    }

    public record Move(Tile tile, float heuristic) {
    }
}
