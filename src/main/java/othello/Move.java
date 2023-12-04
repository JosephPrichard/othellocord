/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

public record Move(Tile tile, float heuristic) {

    public String toString() {
        return "Disc: " + tile + ", Heuristic: " + heuristic;
    }
}
