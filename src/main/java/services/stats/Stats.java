/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.player.Player;

import java.util.Objects;

public class Stats {

    private final Player player;
    private final float elo;
    private final int won;
    private final int lost;
    private final int drawn;

    public Stats(Player player, float elo, int won, int lost, int drawn) {
        this.player = player;
        this.elo = elo;
        this.won = won;
        this.lost = lost;
        this.drawn = drawn;
    }

    public Stats(Player player) {
        this(player, 0f, 0, 0, 0);
    }

    public Stats(StatsEntity statsEntity, String playerName) {
        this(new Player(statsEntity.getPlayerId(), playerName), statsEntity.getElo(),
            statsEntity.getWon(), statsEntity.getLost(), statsEntity.getDrawn());
    }

    public Player getPlayer() {
        return player;
    }

    public float getElo() {
        return elo;
    }

    public int getWon() {
        return won;
    }

    public int getLost() {
        return lost;
    }

    public int getDrawn() {
        return drawn;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Stats stats = (Stats) o;
        return Float.compare(elo, stats.elo) == 0
            && won == stats.won
            && lost == stats.lost
            && drawn == stats.drawn
            && player.equals(stats.player)
            && player.name().equals(stats.player.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(player, elo, won, lost, drawn);
    }

    public float getWinRate() {
        var total = won + lost + drawn;
        if (total == 0) {
            return 0f;
        }
        return won / (float) (won + lost + drawn) * 100f;
    }

    @Override
    public String toString() {
        return "Stats{" +
            "player=" + player +
            ", elo=" + elo +
            ", won=" + won +
            ", lost=" + lost +
            ", drawn=" + drawn +
            '}';
    }
}
