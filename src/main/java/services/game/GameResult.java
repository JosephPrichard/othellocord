/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

import services.player.Player;

public record GameResult(Player winner, Player loser, boolean isDraw) {

    public static GameResult Draw(Player playerOne, Player playerTwo) {
        return new GameResult(playerOne, playerTwo, true);
    }

    public static GameResult WinLoss(Player winner, Player loser) {
        return new GameResult(winner, loser, false);
    }

    @Override
    public String toString() {
        return "GameResult{" +
            "winner=" + winner +
            ", loser=" + loser +
            ", isDraw=" + isDraw +
            '}';
    }
}
