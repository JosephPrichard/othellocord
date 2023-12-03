/*
 * Copyright (c) Joseph Prichard 2023.
 */

package othello;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Comparator;
import java.util.List;

@ExtendWith(MockitoExtension.class)
public class TestOthelloBoard {

    private OthelloBoard othelloBoard;

    @BeforeEach
    public void beforeEach() {
        othelloBoard = new OthelloBoard();
    }

    @Test
    public void testGetNotation() {
        othelloBoard.setSquare("c2", OthelloBoard.WHITE);
        var color = othelloBoard.getSquare(1, 2);

        Assertions.assertEquals(OthelloBoard.WHITE, color);
    }

    @Test
    public void testSetNotation() {
        othelloBoard.setSquare(1, 2, OthelloBoard.WHITE);
        var color = othelloBoard.getSquare("c2");

        Assertions.assertEquals(OthelloBoard.WHITE, color);
    }

    @Test
    public void testSetThenGetSquare() {
        othelloBoard.setSquare(2, 3, OthelloBoard.WHITE);
        var color = othelloBoard.getSquare(2, 3);

        Assertions.assertEquals(OthelloBoard.WHITE, color);

        othelloBoard.setSquare(2, 3, OthelloBoard.BLACK);
        color = othelloBoard.getSquare(2, 3);

        Assertions.assertEquals(OthelloBoard.BLACK, color);
    }

    @Test
    public void testGetEmptySquare() {
        var color = othelloBoard.getSquare(2, 1);

        Assertions.assertEquals(OthelloBoard.EMPTY, color);
    }

    @Test
    public void testFindPotentialMoves() {
        var moves = othelloBoard.findPotentialMoves();
        moves.sort(Comparator.comparing(Tile::toString));

        var expected = List.of(
            new Tile("c4"),
            new Tile("d3"),
            new Tile("e6"),
            new Tile("f5")
        );
        Assertions.assertEquals(expected, moves);
    }

    @Test
    public void testCountPotentialMoves() {
        var whiteCount = othelloBoard.countPotentialMoves(OthelloBoard.WHITE);
        var blackCount = othelloBoard.countPotentialMoves(OthelloBoard.BLACK);

        Assertions.assertEquals(4, whiteCount);
        Assertions.assertEquals(4, blackCount);
    }

    @Test
    public void testCountDiscs() {
        var whiteCount = othelloBoard.countDiscs(OthelloBoard.WHITE);
        var blackCount = othelloBoard.countDiscs(OthelloBoard.BLACK);

        Assertions.assertEquals(2, whiteCount);
        Assertions.assertEquals(2, blackCount);
    }
}
