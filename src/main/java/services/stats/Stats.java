/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.player.Player;

import java.util.Objects;

public class Stats {

    private Player player;
    private float elo;
    private int won;
    private int lost;
    private int drawn;

    public Stats() {
    }

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

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public float getElo() {
        return elo;
    }

    public void setElo(float elo) {
        this.elo = elo;
    }

    public int getWon() {
        return won;
    }

    public void setWon(int won) {
        this.won = won;
    }

    public int getLost() {
        return lost;
    }

    public void setLost(int lost) {
        this.lost = lost;
    }

    public int getDrawn() {
        return drawn;
    }

    public void setDrawn(int drawn) {
        this.drawn = drawn;
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
            && player.getName().equals(stats.player.getName());
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
}
