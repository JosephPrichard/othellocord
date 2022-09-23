package modules.game;

import modules.player.Player;
import modules.game.exceptions.AlreadyPlayingException;
import modules.game.exceptions.InvalidMoveException;
import modules.game.exceptions.NotPlayingException;
import modules.game.exceptions.TurnException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jetbrains.annotations.NotNull;
import othello.board.OthelloBoard;
import othello.board.Tile;
import utils.BotUtils;

import javax.annotation.Nullable;
import javax.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class GameService
{
    private final LoadingCache<Long, Optional<Game>> games = CacheBuilder.newBuilder()
        .concurrencyLevel(8)
        .initialCapacity(1000)
        .expireAfterAccess(7, TimeUnit.DAYS)
        .build(
            new CacheLoader<>() {
                public @NotNull Optional<Game> load(@NotNull Long key) {
                    return Optional.empty();
                }
            }
        );

    public Game createGame(Player blackPlayer, Player whitePlayer) throws AlreadyPlayingException {
        Game game = new Game(new OthelloBoard(), whitePlayer, blackPlayer);

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
        Player whitePlayer = BotUtils.Bot(level);
        Game game = new Game(new OthelloBoard(), whitePlayer, blackPlayer);

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
        Optional<Game> optionalGame;
        try {
            optionalGame = games.get(player.getId());
            if (optionalGame.isPresent()) {
                Game game = optionalGame.get();
                return new Game(game.getBoard().copy(), game.getWhitePlayer(), game.getBlackPlayer());
            } else {
                return null;
            }
        } catch (ExecutionException e) {
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
        try {
            return games.get(player.getId()).isPresent();
        } catch(ExecutionException e) {
            return true;
        }
    }

    /**
     * Makes a move directly on a game
     * @param game to make the move on
     * @param move to be made
     */
    public void makeMove(Game game, Tile move) {
        game.getBoard().makeMove(move);
        if (!game.isGameOver()) {
            saveGame(game);
        } else {
            deleteGame(game);
        }
    }

    /**
     * Responsible for making the given move on the player's game. Updates the game in the storage if
     * the game is not complete, deletes the game in the storage if the game is complete
     * @param player to make move for
     * @param move to make move
     * @return a mutable copy of the game from the storage
     */
    public Game makeMove(Player player, Tile move) throws NotPlayingException, InvalidMoveException, TurnException {
        Game game = getGame(player);
        if (game == null) {
            throw new NotPlayingException();
        }

        if (!game.getCurrentPlayer().equals(player)) {
            throw new TurnException();
        }

        // calculate the potential moves
        List<Tile> potentialMoves = game.getBoard().findPotentialMoves();
        // check if the move being requested is any of the potential moves, if so make the move
        for (Tile potentialMove : potentialMoves) {
            if (potentialMove.equals(move)) {
                makeMove(game, potentialMove);
                return game;
            }
        }

        throw new InvalidMoveException();
    }
}
