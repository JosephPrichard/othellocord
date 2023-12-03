/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import othello.OthelloBoard;
import othello.Tile;
import services.game.Game;
import services.game.GameStorage;
import services.player.Player;
import services.game.exceptions.AlreadyPlayingException;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.stats.StatsMutator;

@ExtendWith(MockitoExtension.class)
public class TestGameStorage {

    @Mock
    private StatsMutator statsMutator;
    @InjectMocks
    private GameStorage gameStorage;

    @Test
    public void testDuplicateCreate() {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");

        Assertions.assertThrows(AlreadyPlayingException.class, () -> {
            gameStorage.createGame(blackPlayer, whitePlayer);
            gameStorage.createGame(blackPlayer, whitePlayer);
        });
    }

    @Test
    public void testSaveThenDelete() {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");

        var game = new Game(whitePlayer, blackPlayer);
        gameStorage.saveGame(game);
        gameStorage.deleteGame(game);
        game = gameStorage.getGame(whitePlayer);

        Assertions.assertNull(game);
    }

    @Test
    public void testGetGame() throws AlreadyPlayingException {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");
        gameStorage.createGame(blackPlayer, whitePlayer);

        var game = gameStorage.getGame(whitePlayer);

        Assertions.assertNotNull(game);
        Assertions.assertEquals(game.whitePlayer(), whitePlayer);
    }

    @Test
    public void testGetNoGame() {
        var player = new Player(1000, "Player1");

        var game = gameStorage.getGame(player);

        Assertions.assertNull(game);
    }

    @Test
    public void testMakeMoveInvalidMove() throws AlreadyPlayingException {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");
        gameStorage.createGame(blackPlayer, whitePlayer);

        Assertions.assertThrows(InvalidMoveException.class, () ->
            gameStorage.makeMove(blackPlayer, new Tile("a1")));
    }

    @Test
    public void testMakeMoveTurn() throws AlreadyPlayingException {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");
        gameStorage.createGame(blackPlayer, whitePlayer);

        Assertions.assertThrows(TurnException.class, () ->
            gameStorage.makeMove(whitePlayer, new Tile("d3")));
    }

    @Test
    public void testMakeMoveNotPlaying() {
        var player = new Player(1000, "Player1");

        Assertions.assertThrows(NotPlayingException.class, () ->
            gameStorage.makeMove(player, new Tile("d3")));
    }

    @Test
    public void testMakeMove() throws AlreadyPlayingException, TurnException, NotPlayingException, InvalidMoveException {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");
        var game = gameStorage.createGame(blackPlayer, whitePlayer);

        var movedGame = gameStorage.makeMove(blackPlayer, new Tile("d3"));

        Assertions.assertEquals(game, movedGame);
    }
}
