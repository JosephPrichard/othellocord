/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import othello.OthelloBoard;
import othello.Tile;
import services.game.exceptions.AlreadyPlayingException;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.stats.StatsMutator;
import utils.Bot;

import javax.annotation.Nullable;
import javax.persistence.PersistenceException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static utils.Logger.LOGGER;

public class GameStorage
{
    private final LoadingCache<Long, Optional<Game>> games;
    private final StatsMutator statsMutator;

    public GameStorage(StatsMutator statsMutator) {
        this.statsMutator = statsMutator;
        this.games = Caffeine.newBuilder()
            .initialCapacity(1000)
            .scheduler(Scheduler.systemScheduler())
            .expireAfterAccess(7, TimeUnit.DAYS)
            .evictionListener((Long key, Optional<Game> value, RemovalCause cause) -> {
                if (cause.equals(RemovalCause.EXPIRED)) {
                    // call the on expiry method for a game on the non-optional version of the game
                    if (value != null && value.isPresent()) {
                        onGameExpiry(value.get());
                    }
                    LOGGER.info("Game of " + key + " has been expired");
                } else if (cause.equals(RemovalCause.EXPLICIT)) {
                    LOGGER.info("Explicit removal for game of key " + key);
                } else {
                    LOGGER.log(Level.WARNING, "Unknown removal cause for game of key " + key);
                }
            })
            .build(key -> Optional.empty());
    }

    public GameStorage(StatsMutator statsMutator, LoadingCache<Long, Optional<Game>> games) {
        this.statsMutator = statsMutator;
        this.games = games;
    }

    public Game createGame(Player blackPlayer, Player whitePlayer) throws AlreadyPlayingException {
        var game = new Game(new OthelloBoard(), whitePlayer, blackPlayer);

        if (isPlaying(blackPlayer) || isPlaying(whitePlayer)) {
            throw new AlreadyPlayingException();
        }

        try {
            saveGame(game);
        } catch (PersistenceException ex) {
            throw new AlreadyPlayingException();
        }

        return game;
    }

    public Game createBotGame(Player blackPlayer, int level) throws AlreadyPlayingException {
        var whitePlayer = Bot.create(level);
        var game = new Game(new OthelloBoard(), whitePlayer, blackPlayer);

        if (isPlaying(blackPlayer)) {
            throw new AlreadyPlayingException();
        }

        try {
            saveGame(game);
        } catch (PersistenceException ex) {
            throw new AlreadyPlayingException();
        }
        return game;
    }

    @Nullable
    public Game getGame(Player player) {
        var optionalGame = games.get(player.getId());
        if (optionalGame.isPresent()) {
            var game = optionalGame.get();
            return new Game(game.getBoard().copy(), game.getWhitePlayer(), game.getBlackPlayer());
        } else {
            return null;
        }
    }

    public void saveGame(Game game) {
        if (!game.getBlackPlayer().isBot()) {
            games.put(game.getBlackPlayer().getId(), Optional.of(game));
        }
        if (!game.getWhitePlayer().isBot()) {
            games.put(game.getWhitePlayer().getId(), Optional.of(game));
        }
    }

    public void deleteGame(Game game) {
        games.invalidate(game.getWhitePlayer().getId());
        games.invalidate(game.getBlackPlayer().getId());
    }

    public boolean isPlaying(Player player) {
        return games.get(player.getId()).isPresent();
    }

    public void makeMove(Game game, Tile move) {
        game.getBoard().makeMove(move);
        if (!game.isGameOver()) {
            saveGame(game);
        } else {
            deleteGame(game);
        }
    }

    // Responsible for making the given move on the player's game. Updates the game in the storage if
    // the game is not complete, deletes the game in the storage if the game is complete
    // returns a mutable copy of the game from the storage
    public Game makeMove(Player player, Tile move) throws NotPlayingException, InvalidMoveException, TurnException {
        var game = getGame(player);
        if (game == null) {
            throw new NotPlayingException();
        }

        if (!game.getCurrentPlayer().equals(player)) {
            throw new TurnException();
        }

        // calculate the potential moves
        var potentialMoves = game.getBoard().findPotentialMoves();
        // check if the move being requested is any of the potential moves, if so make the move
        for (var potentialMove : potentialMoves) {
            if (potentialMove.equals(move)) {
                makeMove(game, potentialMove);
                return game;
            }
        }

        throw new InvalidMoveException();
    }

    private void onGameExpiry(Game game) {
        // call the stats service to update the stats where the current player loses
        var forfeitResult = new GameResult(game.getOtherPlayer(), game.getCurrentPlayer());
        statsMutator.updateStats(forfeitResult);
    }
}
