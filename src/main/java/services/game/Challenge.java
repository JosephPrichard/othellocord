/*
 * Copyright (c) Joseph Prichard 2023.
 */

package services.game;

public record Challenge(Player challenged, Player challenger) {

    @Override
    public String toString() {
        return "Challenge{" +
            "challenged=" + challenged +
            ", challenger=" + challenger +
            '}';
    }
}
