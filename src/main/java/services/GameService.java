/*
 * Copyright (c) Joseph Prichard 2024.
 */

package services;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import engine.Tile;
import models.Game;
import models.Player;

import javax.annotation.Nullable;
import javax.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static utils.LogUtils.LOGGER;

public class GameService {

    private final LoadingCache<Long, Optional<Game>> games;
    private final StatsService statsService;

    public GameService(StatsService statsService) {
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
                    LOGGER.info("Game of {} has been expired", key);
                } else if (cause.equals(RemovalCause.EXPLICIT)) {
                    LOGGER.info("Explicit removal for game of key {}", key);
                } else {
                    LOGGER.warn("Unknown removal cause for game of key {}", key);
                }
            })
            .build(key -> Optional.empty());
    }

    public static class InvalidMoveException extends Exception {
    }

    public static class AlreadyPlayingException extends Exception {
    }

    public static class TurnException extends Exception {
    }

    public static class NotPlayingException extends Exception {
    }

    public Game createGame(Player blackPlayer, Player whitePlayer) throws AlreadyPlayingException {
        Game game = Game.start(blackPlayer, whitePlayer);

        if (isPlaying(blackPlayer) || isPlaying(whitePlayer)) {
            throw new AlreadyPlayingException();
        }

        try {
            Optional<Game> optGame = Optional.of(game);
            games.put(game.getBlackPlayer().getId(), optGame);
            games.put(game.getWhitePlayer().getId(), optGame);
        } catch (PersistenceException ex) {
            throw new AlreadyPlayingException();
        }

        return Game.from(game);
    }

    public Game createBotGame(Player blackPlayer, long level) throws AlreadyPlayingException {
        Player whitePlayer = Player.Bot.create(level);
        Game game = Game.start(blackPlayer, whitePlayer);

        if (isPlaying(blackPlayer)) {
            throw new AlreadyPlayingException();
        }

        try {
            Optional<Game> optGame = Optional.of(game);
            games.put(game.getBlackPlayer().getId(), optGame);
            games.put(game.getWhitePlayer().getId(), optGame);
        } catch (PersistenceException ex) {
            throw new AlreadyPlayingException();
        }
        return Game.from(game);
    }

    @Nullable
    public Game getGame(Player player) {
        Game game = games.get(player.getId()).orElse(null);
        if (game == null) {
            return null;
        }
        synchronized (game) {
            return Game.from(game);
        }
    }

    public void deleteGame(Game game) {
        games.invalidate(game.getWhitePlayer().getId());
        games.invalidate(game.getBlackPlayer().getId());
    }

    public boolean isPlaying(Player player) {
        return games.get(player.id).isPresent();
    }

    // Responsible for making the given move on the player's game. Updates the game in the storage if
    // the game is not complete, deletes the game in the storage if the game is complete
    // returns a mutable copy of the game from the storage
    public Game makeMove(Player player, Tile move) throws NotPlayingException, InvalidMoveException, TurnException {
        Game game = games.get(player.getId()).orElse(null);
        if (game == null) {
            throw new NotPlayingException();
        }

        synchronized (game) {
            if (!game.getCurrentPlayer().equals(player)) {
                throw new TurnException();
            }

            List<Tile> potentialMoves = game.findPotentialMoves();

            // check if the move being requested is any of the potential moves, if so make the move
            for (Tile potentialMove : potentialMoves) {
                if (potentialMove.equals(move)) {
                    // make the move by modifying the game's board state
                    game.makeMove(potentialMove);

                    if (game.isOver()) {
                        deleteGame(game);
                        return Game.from(game);
                    }

                    return Game.from(game);
                }
            }

            throw new InvalidMoveException();
        }
    }

    private void onGameExpiry(Game game) {
        // call the stats service to update the stats where the current player loses
        Game.Result forfeitResult = Game.Result.WinLoss(game.getOtherPlayer(), game.getCurrentPlayer());
        statsService.writeStats(forfeitResult);
    }
}
