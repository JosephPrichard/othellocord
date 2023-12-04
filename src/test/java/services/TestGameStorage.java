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
import othello.Tile;
import services.game.Game;
import services.game.GameCacheStorage;
import services.player.Player;
import services.game.exceptions.AlreadyPlayingException;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.stats.StatsWriter;

@ExtendWith(MockitoExtension.class)
public class TestGameStorage {

    @Mock
    private StatsWriter statsWriter;
    @InjectMocks
    private GameCacheStorage gameStorage;

    @Test
    public void whenDuplicateCreate_fail() {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");

        Assertions.assertThrows(AlreadyPlayingException.class, () -> {
            gameStorage.createGame(blackPlayer, whitePlayer);
            gameStorage.createGame(blackPlayer, whitePlayer);
        });
    }

    @Test
    public void whenSaveThenDelete_success() {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");

        var game = new Game(whitePlayer, blackPlayer);
        gameStorage.saveGame(game);
        gameStorage.deleteGame(game);
        game = gameStorage.getGame(whitePlayer);

        Assertions.assertNull(game);
    }

    @Test
    public void whenGetGame_success() throws AlreadyPlayingException {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");
        gameStorage.createGame(blackPlayer, whitePlayer);

        var game = gameStorage.getGame(whitePlayer);

        Assertions.assertNotNull(game);
        Assertions.assertEquals(game.whitePlayer(), whitePlayer);
    }

    @Test
    public void whenGetInvalidGame_returnNull() {
        var player = new Player(1000, "Player1");

        var game = gameStorage.getGame(player);

        Assertions.assertNull(game);
    }

    @Test
    public void whenMove_ifInvalid_fail() throws AlreadyPlayingException {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");
        gameStorage.createGame(blackPlayer, whitePlayer);

        Assertions.assertThrows(InvalidMoveException.class, () ->
            gameStorage.makeMove(blackPlayer, Tile.fromNotation("a1")));
    }

    @Test
    public void whenMove_ifAlreadyPlaying_fail() throws AlreadyPlayingException {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");
        gameStorage.createGame(blackPlayer, whitePlayer);

        Assertions.assertThrows(TurnException.class, () ->
            gameStorage.makeMove(whitePlayer, Tile.fromNotation("d3")));
    }

    @Test
    public void whenMove_ifNotPlaying_fail() {
        var player = new Player(1000, "Player1");

        Assertions.assertThrows(NotPlayingException.class, () ->
            gameStorage.makeMove(player,  Tile.fromNotation("d3")));
    }

    @Test
    public void whenMove_success() throws AlreadyPlayingException, TurnException, NotPlayingException, InvalidMoveException {
        var whitePlayer = new Player(1000, "Player1");
        var blackPlayer = new Player(1001, "Player2");
        var game = gameStorage.createGame(blackPlayer, whitePlayer);

        var movedGame = gameStorage.makeMove(blackPlayer, Tile.fromNotation("d3"));

        Assertions.assertEquals(game, movedGame);
        Assertions.assertNotEquals(game.board(), movedGame.board());
        Assertions.assertNotEquals(game.board(), movedGame.board().makeMoved("d3"));
    }
}
