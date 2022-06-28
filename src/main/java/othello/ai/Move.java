package othello.ai;

import othello.board.Tile;

public class Move
{
    private Tile piece;
    private float heuristic;

    public Move(Tile piece, float heuristic) {
        this.piece = piece;
        this.heuristic = heuristic;
    }

    public Tile getTile() {
        return piece;
    }

    public void setPiece(Tile piece) {
        this.piece = piece;
    }

    public float getHeuristic() {
        return heuristic;
    }

    public void setHeuristic(float heuristic) {
        this.heuristic = heuristic;
    }

    public String toString() {
        return "Piece: " + piece + ", Heuristic: " + heuristic;
    }
}
