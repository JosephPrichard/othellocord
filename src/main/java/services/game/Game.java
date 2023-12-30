/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import othello.OthelloBoard;
import services.player.Player;

import java.util.Objects;

public record Game(OthelloBoard board, Player whitePlayer, Player blackPlayer) {

    public Game(Player whitePlayer, Player blackPlayer) {
        this(new OthelloBoard(), whitePlayer, blackPlayer);
    }

    public Game(Game game) {
        this(game.board.copy(), game.whitePlayer, game.blackPlayer);
    }

    public OthelloBoard board() {
        return board;
    }

    public Player whitePlayer() {
        return whitePlayer;
    }

    public Player blackPlayer() {
        return blackPlayer;
    }

    public Player getCurrentPlayer() {
        return board.isBlackMove() ? blackPlayer : whitePlayer;
    }

    public Player getOtherPlayer() {
        return board.isBlackMove() ? whitePlayer : blackPlayer;
    }

    public int getBlackScore() {
        return (int) board.blackScore();
    }

    public int getWhiteScore() {
        return (int) board.whiteScore();
    }

    public boolean isAgainstBot() {
        return whitePlayer.isBot() || blackPlayer.isBot();
    }

    public boolean isGameOver() {
        return board.isGameOver();
    }

    public boolean isBlackMove() {
        return board.isBlackMove();
    }

    public GameResult createResult() {
        var diff = board.blackScore() - board.whiteScore();
        if (diff > 0) {
            return GameResult.WinLoss(blackPlayer, whitePlayer);
        } else if (diff < 0) {
            return GameResult.WinLoss(whitePlayer, blackPlayer);
        } else {
            return GameResult.Draw(whitePlayer, blackPlayer);
        }
    }

    public GameResult createForfeitResult(Player forfeitingPlayer) {
        Player loser;
        Player winner;
        if (whitePlayer.equals(forfeitingPlayer)) {
            loser = whitePlayer;
            winner = blackPlayer;
        } else if (blackPlayer.equals(forfeitingPlayer)) {
            loser = blackPlayer;
            winner = whitePlayer;
        } else {
            throw new IllegalStateException("Player not part of a game attempted to forfeit");
        }
        return GameResult.WinLoss(winner, loser);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Game game = (Game) o;
        return game.whitePlayer.equals(whitePlayer) && game.blackPlayer.equals(blackPlayer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(whitePlayer, blackPlayer);
    }

    @Override
    public String toString() {
        return "Game{" +
            "board=\n" + board +
            ", whitePlayer=" + whitePlayer +
            ", blackPlayer=" + blackPlayer +
            '}';
    }
}
