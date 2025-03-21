/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services;

import engine.Tile;
import models.Game;
import models.Player;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@ExtendWith(MockitoExtension.class)
public class TestGameService {

    @InjectMocks
    private GameService gameService;

    @Test
    public void whenDuplicateCreate_fail() {
        Player whitePlayer = new Player(1000, "Player1");
        Player blackPlayer = new Player(1001, "Player2");

        Assertions.assertThrows(GameService.AlreadyPlayingException.class, () -> {
            gameService.createGame(blackPlayer, whitePlayer);
            gameService.createGame(blackPlayer, whitePlayer);
        });
    }

    @Test
    public void whenSaveThenDelete_success() throws GameService.AlreadyPlayingException {
        Player whitePlayer = new Player(1000, "Player1");
        Player blackPlayer = new Player(1001, "Player2");

        gameService.createGame(blackPlayer, whitePlayer);

        Game game = gameService.getGame(blackPlayer);
        assert game != null;

        gameService.deleteGame(game);
        game = gameService.getGame(whitePlayer);

        Assertions.assertNull(game);
    }

    @Test
    public void whenGetGame_success() throws GameService.AlreadyPlayingException {
        Player whitePlayer = new Player(1000, "Player1");
        Player blackPlayer = new Player(1001, "Player2");
        gameService.createGame(blackPlayer, whitePlayer);

        Game game = gameService.getGame(whitePlayer);

        Assertions.assertNotNull(game);
        Assertions.assertEquals(game.getWhitePlayer(), whitePlayer);
    }

    @Test
    public void whenGetInvalidGame_returnNull() {
        Player player = new Player(1000, "Player1");

        Game game = gameService.getGame(player);

        Assertions.assertNull(game);
    }

    @Test
    public void whenMove_ifInvalid_fail() throws GameService.AlreadyPlayingException {
        Player whitePlayer = new Player(1000, "Player1");
        Player blackPlayer = new Player(1001, "Player2");
        gameService.createGame(blackPlayer, whitePlayer);

        Assertions.assertThrows(GameService.InvalidMoveException.class, () ->
            gameService.makeMove(blackPlayer, Tile.fromNotation("a1")));
    }

    @Test
    public void whenMove_ifAlreadyPlaying_fail() throws GameService.AlreadyPlayingException {
        Player whitePlayer = new Player(1000, "Player1");
        Player blackPlayer = new Player(1001, "Player2");
        gameService.createGame(blackPlayer, whitePlayer);

        Assertions.assertThrows(GameService.TurnException.class, () ->
            gameService.makeMove(whitePlayer, Tile.fromNotation("d3")));
    }

    @Test
    public void whenMove_ifNotPlaying_fail() {
        Player player = new Player(1000, "Player1");

        Assertions.assertThrows(GameService.NotPlayingException.class, () ->
            gameService.makeMove(player, Tile.fromNotation("d3")));
    }

    @Test
    public void whenMove_success() throws GameService.AlreadyPlayingException, GameService.TurnException, GameService.NotPlayingException, GameService.InvalidMoveException {
        Player whitePlayer = new Player(1000, "Player1");
        Player blackPlayer = new Player(1001, "Player2");
        Game game = gameService.createGame(blackPlayer, whitePlayer);

        Game movedGame = gameService.makeMove(blackPlayer, Tile.fromNotation("d3"));

        Assertions.assertEquals(game, movedGame);
        Assertions.assertNotEquals(game.getBoard(), movedGame.getBoard());
        Assertions.assertNotEquals(game.getBoard(), movedGame.getBoard().makeMoved("d3"));
    }

    @Test
    public void whenMove_parallel_success() throws Exception {
        Player whitePlayer = new Player(1000, "Player1");
        Player blackPlayer = new Player(1001, "Player2");
        Game game = gameService.createGame(blackPlayer, whitePlayer);

        List<Pair<Game, Exception>> results = Stream
            .generate(() -> CompletableFuture.supplyAsync(() -> {
                try {
                    Game movedGame = gameService.makeMove(blackPlayer, Tile.fromNotation("d3"));

                    Assertions.assertEquals(game, movedGame);
                    Assertions.assertNotEquals(game.getBoard(), movedGame.getBoard());
                    Assertions.assertNotEquals(game.getBoard(), movedGame.getBoard().makeMoved("d3"));

                    return Pair.<Game, Exception>of(movedGame, null);
                } catch (Exception e) {
                    return Pair.<Game, Exception>of(null, e);
                }
            }))
            .limit(100)
            .map(CompletableFuture::join)
            .toList();

        int successCount = 0;

        for (Pair<Game, Exception> result : results) {
            if (result.getLeft() != null) {
                Game movedGame = result.getLeft();

                Assertions.assertEquals(game, movedGame);
                Assertions.assertNotEquals(game.getBoard(), movedGame.getBoard());
                Assertions.assertNotEquals(game.getBoard(), movedGame.getBoard().makeMoved("d3"));

                successCount += 1;
            } else {
                if (!(result.getRight() instanceof GameService.TurnException)) {
                    throw result.getRight();
                }
            }
        }

        Assertions.assertEquals(1, successCount);
    }
}
