/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import othello.Tile;
import services.game.exceptions.AlreadyPlayingException;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.player.Player;
import services.stats.IStatsService;

import javax.annotation.Nullable;
import javax.persistence.PersistenceException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static utils.Logger.LOGGER;

// implementation of an in memory game storage using a loading cache
public class GameService implements IGameService {

    private final LoadingCache<Long, Optional<Game>> games;
    private final IStatsService statsService;

    public GameService(IStatsService statsService) {
        this.statsService = statsService;
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

    public Game createGame(Player blackPlayer, Player whitePlayer) throws AlreadyPlayingException {
        var game = new Game(whitePlayer, blackPlayer);

        if (isPlaying(blackPlayer) || isPlaying(whitePlayer)) {
            throw new AlreadyPlayingException();
        }

        try {
            var optGame = Optional.of(game);
            games.put(game.blackPlayer().id(), optGame);
            games.put(game.whitePlayer().id(), optGame);
        } catch (PersistenceException ex) {
            throw new AlreadyPlayingException();
        }

        return new Game(game);
    }

    public Game createBotGame(Player blackPlayer, long level) throws AlreadyPlayingException {
        var whitePlayer = Player.Bot.create(level);
        var game = new Game(whitePlayer, blackPlayer);

        if (isPlaying(blackPlayer)) {
            throw new AlreadyPlayingException();
        }

        try {
            var optGame = Optional.of(game);
            games.put(game.blackPlayer().id(), optGame);
            games.put(game.whitePlayer().id(), optGame);
        } catch (PersistenceException ex) {
            throw new AlreadyPlayingException();
        }
        return new Game(game);
    }

    @Nullable
    public Game getGame(Player player) {
        var game = games.get(player.id()).orElse(null);
        if (game == null) {
            return null;
        }
        synchronized (game) {
            return new Game(game);
        }
    }

    public void deleteGame(Game game) {
        games.invalidate(game.whitePlayer().id());
        games.invalidate(game.blackPlayer().id());
    }

    public boolean isPlaying(Player player) {
        return games.get(player.id()).isPresent();
    }

    // Responsible for making the given move on the player's game. Updates the game in the storage if
    // the game is not complete, deletes the game in the storage if the game is complete
    // returns a mutable copy of the game from the storage
    public Game makeMove(Player player, Tile move) throws NotPlayingException, InvalidMoveException, TurnException {
        var game = games.get(player.id()).orElse(null);
        if (game == null) {
            throw new NotPlayingException();
        }

        synchronized (game) {
            if (!game.getCurrentPlayer().equals(player)) {
                throw new TurnException();
            }

            var potentialMoves = game.findPotentialMoves();

            // check if the move being requested is any of the potential moves, if so make the move
            for (var potentialMove : potentialMoves) {
                if (potentialMove.equals(move)) {
                    // make the move by modifying the game's board state
                    game.makeMove(potentialMove);

                    if (game.hasNoMoves()) {
                        game.skipTurn();
                    }

                    if (game.hasNoMoves()) {
                        deleteGame(game);
                        return new Game(game);
                    }

                    return new Game(game);
                }
            }

            throw new InvalidMoveException();
        }
    }

    private void onGameExpiry(Game game) {
        // call the stats service to update the stats where the current player loses
        var forfeitResult = GameResult.WinLoss(game.getOtherPlayer(), game.getCurrentPlayer());
        statsService.writeStats(forfeitResult);
    }
}
