/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.stats;

import services.game.Player;

public class Stats {
    private Player player;
    private float elo;
    private int won;
    private int lost;
    private int drawn;

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

    public float getWinRate() {
        var total = won + lost + drawn;
        if (total == 0) {
            return 0f;
        }
        return won / (float) (won + lost + drawn) * 100f;
    }
}
