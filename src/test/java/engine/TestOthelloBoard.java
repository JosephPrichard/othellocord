/*
 * Copyright (c) Joseph Prichard 2023.
 */

package engine;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

public class TestOthelloBoard {

    private OthelloBoard othelloBoard;

    @BeforeEach
    public void beforeEach() {
        othelloBoard = OthelloBoard.initial();
    }

    @Test
    public void whenGetNotation_success() {
        othelloBoard.setSquare("c2", OthelloBoard.WHITE);
        byte color = othelloBoard.getSquare(1, 2);

        Assertions.assertEquals(OthelloBoard.WHITE, color);
    }

    @Test
    public void whenSetNotation_success() {
        othelloBoard.setSquare(1, 2, OthelloBoard.WHITE);
        byte color = othelloBoard.getSquare("c2");

        Assertions.assertEquals(OthelloBoard.WHITE, color);
    }

    @Test
    public void whenSetThenGet_success() {
        othelloBoard.setSquare(2, 3, OthelloBoard.WHITE);
        byte color = othelloBoard.getSquare(2, 3);

        Assertions.assertEquals(OthelloBoard.WHITE, color);

        othelloBoard.setSquare(2, 3, OthelloBoard.BLACK);
        color = othelloBoard.getSquare(2, 3);

        Assertions.assertEquals(OthelloBoard.BLACK, color);
    }

    @Test
    public void whenGet_ifEmpty_beBlank() {
        byte color = othelloBoard.getSquare(2, 1);

        Assertions.assertEquals(OthelloBoard.EMPTY, color);
    }

    @Test
    public void whenFindPotentialMoves_success() {
        List<Tile> moves = othelloBoard.findPotentialMoves();
        moves.sort(Comparator.comparing(Tile::toString));

        List<Tile> expected = List.of(
            Tile.fromNotation("c4"),
            Tile.fromNotation("d3"),
            Tile.fromNotation("e6"),
            Tile.fromNotation("f5")
        );
        Assertions.assertEquals(expected, moves);
    }

    @Test
    public void whenCountPotentialMoves_success() {
        int whiteCount = othelloBoard.countPotentialMoves(OthelloBoard.WHITE);
        int blackCount = othelloBoard.countPotentialMoves(OthelloBoard.BLACK);

        Assertions.assertEquals(4, whiteCount);
        Assertions.assertEquals(4, blackCount);
    }

    @Test
    public void whenCountDiscs_success() {
        int whiteCount = othelloBoard.countDiscs(OthelloBoard.WHITE);
        int blackCount = othelloBoard.countDiscs(OthelloBoard.BLACK);

        Assertions.assertEquals(2, whiteCount);
        Assertions.assertEquals(2, blackCount);
    }
}
