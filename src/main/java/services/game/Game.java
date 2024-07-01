/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import othello.OthelloBoard;
import othello.Tile;
import services.player.Player;

import java.util.List;
import java.util.Objects;

public class Game {

    private final OthelloBoard board;
    private final Player whitePlayer;
    private final Player blackPlayer;
    private List<Tile> currPotentialMoves;

    public Game(OthelloBoard board, Player blackPlayer, Player whitePlayer) {
        this.board = board;
        this.blackPlayer = blackPlayer;
        this.whitePlayer = whitePlayer;
        this.currPotentialMoves = null;
    }

    public static Game start(Player blackPlayer, Player whitePlayer) {
        return new Game(OthelloBoard.initial(), blackPlayer, whitePlayer);
    }

    public static Game from(Game game) {
        var copiedGame = new Game(OthelloBoard.from(game.board), game.blackPlayer, game.whitePlayer);
        copiedGame.currPotentialMoves = game.currPotentialMoves;
        return copiedGame;
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

    public List<Tile> findPotentialMoves() {
        if (currPotentialMoves != null) {
            return currPotentialMoves;
        }
        var potentialMoves = board.findPotentialMoves();
        currPotentialMoves = potentialMoves;
        return potentialMoves;
    }

    public void makeMove(Tile move) {
        board.makeMove(move);
        currPotentialMoves = null;

        if (findPotentialMoves().isEmpty()) {
            board.skipTurn();
            currPotentialMoves = null;
        }
    }

    public boolean isOver() {
        return findPotentialMoves().isEmpty();
    }

    public boolean isBlackMove() {
        return board.isBlackMove();
    }

    public GameResult createResult() {
        var diff = getBlackScore() - getWhiteScore();
        if (diff > 0) {
            return GameResult.WinLoss(blackPlayer, whitePlayer);
        } else if (diff < 0) {
            return GameResult.WinLoss(whitePlayer, blackPlayer);
        } else {
            return GameResult.Draw(whitePlayer, blackPlayer);
        }
    }

    public GameResult createForfeitResult(Player forfeitingPlayer) {
        if (whitePlayer.equals(forfeitingPlayer)) {
            return GameResult.WinLoss(blackPlayer, whitePlayer);
        } else if (blackPlayer.equals(forfeitingPlayer)) {
            return GameResult.WinLoss(whitePlayer, blackPlayer);
        } else {
            throw new IllegalStateException("Player not part of a game attempted to forfeit");
        }
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
