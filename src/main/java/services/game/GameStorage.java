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
import services.player.Player;
import services.stats.StatsMutator;
import utils.Bot;

import javax.annotation.Nullable;
import javax.persistence.PersistenceException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static utils.Logger.LOGGER;

public interface GameStorage {
    Game createGame(Player blackPlayer, Player whitePlayer) throws AlreadyPlayingException;

    Game createBotGame(Player blackPlayer, int level) throws AlreadyPlayingException;

    @Nullable
    Game getGame(Player player);

    void saveGame(Game game);

    void deleteGame(Game game);

    void makeMove(Game game, Tile move);

    Game makeMove(Player player, Tile move) throws NotPlayingException, InvalidMoveException, TurnException;
}
