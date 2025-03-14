/*
 * Copyright (c) Joseph Prichard 2024.
 */

package models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Stats {
    public Player player;
    public float elo;
    public int won;
    public int lost;
    public int drawn;

    public Stats(Player player) {
        this(player, 0f, 0, 0, 0);
    }

    public Stats(StatsEntity statsEntity, String playerName) {
        this(new Player(statsEntity.playerId, playerName), statsEntity.elo,
            statsEntity.won, statsEntity.lost, statsEntity.drawn);
    }

    public float winRate() {
        int total = won + lost + drawn;
        if (total == 0) {
            return 0f;
        }
        return won / (float) (won + lost + drawn) * 100f;
    }

    public record Result(float winnerElo, float loserElo, float winnerEloDiff, float loserEloDiff) {

        public Result() {
            this(0, 0, 0, 0);
        }

        private static String formatElo(float elo) {
            return elo >= 0 ? "+" + elo : Float.toString(elo);
        }

        public String formatWinnerEloDiff() {
            return formatElo(winnerEloDiff);
        }

        public String formatLoserEloDiff() {
            return formatElo(loserEloDiff);
        }
    }
}
