/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import othello.Tile;
import services.game.exceptions.AlreadyPlayingException;
import services.game.exceptions.InvalidMoveException;
import services.game.exceptions.NotPlayingException;
import services.game.exceptions.TurnException;
import services.player.Player;

import javax.annotation.Nullable;

// performs crud operations on games stored within memory
public interface IGameService {

    Game createGame(Player blackPlayer, Player whitePlayer) throws AlreadyPlayingException;

    Game createBotGame(Player blackPlayer, long level) throws AlreadyPlayingException;

    @Nullable
    Game getGame(Player player);

    void deleteGame(Game game);

    Game makeMove(Player player, Tile move) throws NotPlayingException, InvalidMoveException, TurnException;
}
