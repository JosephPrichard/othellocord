/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

public class Move {
    private Tile disc;
    private float heuristic;

    public Move(Tile disc, float heuristic) {
        this.disc = disc;
        this.heuristic = heuristic;
    }

    public Tile getTile() {
        return disc;
    }

    public void setDisc(Tile disc) {
        this.disc = disc;
    }

    public float getHeuristic() {
        return heuristic;
    }

    public void setHeuristic(float heuristic) {
        this.heuristic = heuristic;
    }

    public String toString() {
        return "Disc: " + disc + ", Heuristic: " + heuristic;
    }
}
