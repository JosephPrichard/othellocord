package othello.board;

public class Tile
{
    private final int row;
    private final int col;

    public Tile(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public Tile(String square) {
        this.col = square.charAt(0) - 'a';
        this.row = Character.getNumericValue(square.charAt(1)) - 1;
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

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
}
