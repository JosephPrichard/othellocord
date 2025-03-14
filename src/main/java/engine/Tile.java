/*
 * Copyright (c) Joseph Prichard 2023.
 */

package engine;

public record Tile(int row, int col) {

    public static Tile fromNotation(String square) {
        square = square.toLowerCase();
        if (square.length() != 2) {
            return new Tile(-1, -1);
        } else {
            int row = Character.getNumericValue(square.charAt(1)) - 1;
            int col = square.charAt(0) - 'a';
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
        char c = (char) (col + 'a');
        String r = Integer.toString(row + 1);
        return c + r;
    }

    public boolean equalsNotation(String notation) {
        char cNotLower = (char) (col + 'a');
        char cNotUpper = (char) (col + 'A');
        char rNot = (char) ((row + 1) + '0');

        if (notation.length() == 2) {
            char c = notation.charAt(0);
            char r = notation.charAt(1);
            return (c == cNotUpper || c == cNotLower) && r == rNot;
        }
        return false;
    }

    public record Move(Tile tile, float heuristic) {
    }
}
