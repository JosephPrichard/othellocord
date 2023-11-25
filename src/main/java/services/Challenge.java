/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services;

import java.util.Objects;

public class Challenge
{
    private final Player challenged;
    private final Player challenger;

    public Challenge(Player challenged, Player challenger) {
        this.challenged = challenged;
        this.challenger = challenger;
    }

    public Player getChallenger() {
        return challenger;
    }

    public Player getChallenged() {
        return challenged;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (Challenge) o;
        return Objects.equals(challenged, that.challenged) && Objects.equals(challenger, that.challenger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(challenged, challenger);
    }

    @Override
    public String toString() {
        return "Challenge{" +
            "challenged=" + challenged +
            ", challenger=" + challenger +
            '}';
    }
}
