/*
 * Copyright (c) Joseph Prichard 2024.
 */

package models;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Stats {
    private Player player;
    private float elo;
    private int won;
    private int lost;
    private int drawn;

    public Stats(Player player) {
        this(player, 0f, 0, 0, 0);
    }

    public Stats(StatsEntity statsEntity, String playerName) {
        this(new Player(statsEntity.getPlayerId(), playerName), statsEntity.getElo(),
            statsEntity.getWon(), statsEntity.getLost(), statsEntity.getDrawn());
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
